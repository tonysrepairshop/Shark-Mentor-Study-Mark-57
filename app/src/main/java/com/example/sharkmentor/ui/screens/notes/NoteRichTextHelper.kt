package com.example.sharkmentor.ui.screens.notes

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class NoteFontOption(
    val displayName: String,
    val shortLabel: String
) {
    DEFAULT("Varsayılan", "Std"),
    SANS("Modern Sans", "Sans"),
    SERIF("Klasik Serif", "Serif"),
    MONO("Daktilo Mono", "Mono"),
    CURSIVE("El Yazısı", "El"),
    CONDENSED("Dar Sans", "Dar"),
    CASUAL("Rahat", "Rahat");

    fun resolveFontFamily(): FontFamily = when (this) {
        DEFAULT -> FontFamily.Default
        SANS -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONO -> FontFamily.Monospace
        CURSIVE -> FontFamily.Cursive
        CONDENSED -> condensedFamily
        CASUAL -> casualFamily
    }

    companion object {
        private val condensedFamily: FontFamily by lazy {
            runCatching {
                FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
            }.getOrDefault(FontFamily.SansSerif)
        }
        private val casualFamily: FontFamily by lazy {
            runCatching {
                FontFamily(Typeface.create("casual", Typeface.NORMAL))
            }.getOrDefault(FontFamily.SansSerif)
        }

        fun fromName(name: String?): NoteFontOption {
            if (name.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: DEFAULT
        }

        fun fromFontFamily(family: FontFamily?): NoteFontOption? {
            if (family == null) return null
            return when (family) {
                FontFamily.Default -> DEFAULT
                FontFamily.SansSerif -> SANS
                FontFamily.Serif -> SERIF
                FontFamily.Monospace -> MONO
                FontFamily.Cursive -> CURSIVE
                condensedFamily -> CONDENSED
                casualFamily -> CASUAL
                else -> null
            }
        }
    }
}

object NoteRichTextHelper {

    const val PAGE_SENTINEL_CHAR: Char = '\u200B'
    const val PAGE_SENTINEL: String = "\u200B"
    const val DEFAULT_FONT_SIZE_SP: Float = 16f
    const val MIN_FONT_SIZE_SP: Float = 10f
    const val MAX_FONT_SIZE_SP: Float = 40f

    fun stripPageSentinel(raw: String): String =
        if (raw.indexOf(PAGE_SENTINEL_CHAR) >= 0) raw.replace(PAGE_SENTINEL, "") else raw

    /**
     * Determines what list prefix (bullet `• `, next numbered `N. `, or checkbox `[ ] `) should
     * carry over onto Page N+1 when Page N overflows or a new page is opened.
     */
    fun extractTrailingListContinuationPrefix(
        prevPageText: String,
        isBulletActive: Boolean,
        isNumberedActive: Boolean,
        isCheckboxActive: Boolean
    ): String {
        val clean = stripPageSentinel(prevPageText)
        val lastLine = clean.substringAfterLast('\n', clean)
        val numMatch = Regex("""^(\d+)\.\s+.*""").find(lastLine)
        return when {
            lastLine.startsWith("• ") || isBulletActive -> "• "
            numMatch != null -> {
                val nextNum = (numMatch.groupValues[1].toIntOrNull() ?: 1) + 1
                "$nextNum. "
            }
            isNumberedActive -> "1. "
            lastLine.startsWith("[ ] ") || lastLine.startsWith("[✓] ") ||
                lastLine.startsWith("[x] ") || lastLine.startsWith("[X] ") ||
                lastLine.startsWith("☐ ") || lastLine.startsWith("☑ ") ||
                isCheckboxActive -> "[ ] "
            else -> ""
        }
    }

    /**
     * Fast O(N) span coalescing: merges touching/overlapping ranges with identical [SpanStyle]
     * so typing thousands of characters NEVER explodes span count into thousands of 1-char spans.
     */
    fun coalesceSpans(
        spans: List<AnnotatedString.Range<SpanStyle>>,
        textLength: Int
    ): List<AnnotatedString.Range<SpanStyle>> {
        if (spans.size <= 1) return spans
        val byStyle = LinkedHashMap<SpanStyle, MutableList<AnnotatedString.Range<SpanStyle>>>()
        for (r in spans) {
            val s = r.start.coerceIn(0, textLength)
            val e = r.end.coerceIn(0, textLength)
            if (e <= s) continue
            byStyle.getOrPut(r.item) { mutableListOf() }.add(AnnotatedString.Range(r.item, s, e))
        }
        val merged = ArrayList<AnnotatedString.Range<SpanStyle>>(byStyle.size * 2)
        for ((style, list) in byStyle) {
            if (list.size == 1) {
                merged.add(list[0])
                continue
            }
            list.sortBy { it.start }
            var curStart = list[0].start
            var curEnd = list[0].end
            for (i in 1 until list.size) {
                val nxt = list[i]
                if (nxt.start <= curEnd) {
                    if (nxt.end > curEnd) curEnd = nxt.end
                } else {
                    merged.add(AnnotatedString.Range(style, curStart, curEnd))
                    curStart = nxt.start
                    curEnd = nxt.end
                }
            }
            merged.add(AnnotatedString.Range(style, curStart, curEnd))
        }
        return merged
    }

    val FontSizePresets: List<Int> = listOf(10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36)

    enum class StyleType {
        BOLD, ITALIC, UNDERLINE
    }

    data class HighlightOption(
        val label: String,
        val color: Color,
        val hex: String
    )

    val HighlightPalette = listOf(
        HighlightOption("Sarı", Color(0xFFFFEB3B), "#FFEB3B"),
        HighlightOption("Yeşil", Color(0xFFA5D6A7), "#A5D6A7"),
        HighlightOption("Mavi", Color(0xFF81D4FA), "#81D4FA"),
        HighlightOption("Pembe", Color(0xFFF48FB1), "#F48FB1"),
        HighlightOption("Turuncu", Color(0xFFFFCC80), "#FFCC80"),
        HighlightOption("Mor", Color(0xFFCE93D8), "#CE93D8")
    )

    val TextColorPalette = listOf(
        "#000000" to Color(0xFF000000), // Siyah (Koyu Mürekkep)
        "#FFFFFF" to Color(0xFFFFFFFF), // Beyaz (Koyu Kağıt için)
        "#E53935" to Color(0xFFE53935), // Kırmızı
        "#1E88E5" to Color(0xFF1E88E5), // Mavi
        "#43A047" to Color(0xFF43A047), // Yeşil
        "#FB8C00" to Color(0xFFFB8C00), // Turuncu
        "#8E24AA" to Color(0xFF8E24AA), // Mor
        "#00897B" to Color(0xFF00897B)  // Turkuaz
    )

    fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return if (a < 255) {
            String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
        } else {
            String.format(Locale.US, "#%02X%02X%02X", r, g, b)
        }
    }

    fun parseHexColor(hex: String): Color? {
        val clean = hex.trim().removePrefix("#")
        return try {
            val longVal = clean.toLong(16)
            when (clean.length) {
                6 -> Color((0xFF000000 or longVal).toInt())
                8 -> Color(longVal.toInt())
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun toPlainText(raw: String): String {
        return raw.replace(Regex("<[^>]+>"), "")
    }

    /**
     * Parses tagged text (e.g. <b>, <i>, <u>, <color hex="#...">, <mark hex="#...">,
     * <size pt="20">, <font name="SERIF">) into a Jetpack Compose AnnotatedString.
     */
    fun parseTaggedText(raw: String): AnnotatedString {
        if (raw.isEmpty()) return AnnotatedString("")
        if (!raw.contains('<')) return AnnotatedString(raw)

        val plainTextBuilder = StringBuilder()
        val spanStyles = mutableListOf<AnnotatedString.Range<SpanStyle>>()

        data class OpenTag(
            val name: String,
            val startIndex: Int,
            val attrVal: String? = null
        )
        val openTags = mutableListOf<OpenTag>()

        var i = 0
        val len = raw.length
        val tagRegex = Regex("""^<\s*(/)?\s*([a-zA-Z0-9]+)(?:\s+(?:hex|color|pt|name)=["']?([^"'>\s]+)["']?)?\s*>""")

        while (i < len) {
            if (raw[i] == '<') {
                val match = tagRegex.find(raw.substring(i))
                if (match != null) {
                    val isClosing = match.groupValues[1] == "/"
                    val tagName = match.groupValues[2].lowercase(Locale.ROOT)
                    val attr = match.groupValues[3].ifBlank { null }
                    val currentPlainLen = plainTextBuilder.length

                    if (!isClosing) {
                        openTags.add(OpenTag(tagName, currentPlainLen, attr))
                    } else {
                        val idx = openTags.indexOfLast { it.name == tagName }
                        if (idx != -1) {
                            val opened = openTags.removeAt(idx)
                            val start = opened.startIndex
                            val end = currentPlainLen
                            if (end > start) {
                                val style = createSpanStyle(opened.name, opened.attrVal)
                                if (style != null) {
                                    spanStyles.add(AnnotatedString.Range(style, start, end))
                                }
                            }
                        }
                    }
                    i += match.value.length
                    continue
                }
            }
            plainTextBuilder.append(raw[i])
            i++
        }

        val finalLen = plainTextBuilder.length
        while (openTags.isNotEmpty()) {
            val opened = openTags.removeAt(openTags.lastIndex)
            if (finalLen > opened.startIndex) {
                val style = createSpanStyle(opened.name, opened.attrVal)
                if (style != null) {
                    spanStyles.add(AnnotatedString.Range(style, opened.startIndex, finalLen))
                }
            }
        }

        return AnnotatedString(plainTextBuilder.toString(), spanStyles)
    }

    private fun createSpanStyle(tagName: String, attrVal: String?): SpanStyle? {
        return when (tagName) {
            "b" -> SpanStyle(fontWeight = FontWeight.Bold)
            "i" -> SpanStyle(fontStyle = FontStyle.Italic)
            "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
            "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            "color" -> attrVal?.let { parseHexColor(it) }?.let { SpanStyle(color = it) }
            "mark" -> attrVal?.let { parseHexColor(it) }?.let {
                val highlightBg = if (it.alpha >= 0.99f) it.copy(alpha = 0.55f) else it
                SpanStyle(background = highlightBg)
            }
            "size" -> attrVal?.toFloatOrNull()?.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)?.let {
                SpanStyle(fontSize = it.sp)
            }
            "font" -> attrVal?.let { name ->
                val opt = NoteFontOption.fromName(name)
                SpanStyle(fontFamily = opt.resolveFontFamily())
            }
            else -> null
        }
    }

    /**
     * Serializes an AnnotatedString with all its SpanStyles into human-readable, cleanly nested tags
     * using an O(N log N) event sweep-line (eliminates O(N^2) freezes on long multi-page notes).
     * Automatically strips internal [PAGE_SENTINEL] characters without breaking span offsets.
     */
    fun toTaggedString(annotatedString: AnnotatedString): String {
        val rawText = annotatedString.text
        if (rawText.isEmpty()) return ""
        val hasSentinel = rawText.indexOf(PAGE_SENTINEL_CHAR) >= 0
        val cleanText = if (hasSentinel) rawText.replace(PAGE_SENTINEL, "") else rawText
        if (cleanText.isEmpty()) return ""
        val rawSpans = annotatedString.spanStyles
        if (rawSpans.isEmpty()) return cleanText

        // Map raw character index -> cleaned character index when PAGE_SENTINEL is present
        val indexMap: IntArray? = if (hasSentinel) {
            IntArray(rawText.length + 1).also { map ->
                var outIdx = 0
                for (i in rawText.indices) {
                    map[i] = outIdx
                    if (rawText[i] != PAGE_SENTINEL_CHAR) outIdx++
                }
                map[rawText.length] = outIdx
            }
        } else null

        data class SpanEvent(
            val pos: Int,
            val isStart: Boolean,
            val type: String,
            val attrVal: String?,
            val order: Int
        )

        val events = ArrayList<SpanEvent>(rawSpans.size * 2)
        var orderCounter = 0
        for (range in rawSpans) {
            val rawS = range.start.coerceIn(0, rawText.length)
            val rawE = range.end.coerceIn(0, rawText.length)
            val s = if (indexMap != null) indexMap[rawS] else rawS
            val e = if (indexMap != null) indexMap[rawE] else rawE
            if (e <= s) continue
            val style = range.item

            fun emit(type: String, attr: String?) {
                val ord = orderCounter++
                events.add(SpanEvent(s, true, type, attr, ord))
                events.add(SpanEvent(e, false, type, attr, ord))
            }

            if (style.fontWeight == FontWeight.Bold) emit("b", null)
            if (style.fontStyle == FontStyle.Italic) emit("i", null)
            if (style.textDecoration?.contains(TextDecoration.Underline) == true) emit("u", null)
            if (style.textDecoration?.contains(TextDecoration.LineThrough) == true) emit("s", null)
            if (style.color != Color.Unspecified && style.color != Color.Transparent) {
                emit("color", colorToHex(style.color))
            }
            if (style.background != Color.Unspecified && style.background != Color.Transparent) {
                emit("mark", colorToHex(style.background))
            }
            if (style.fontSize != TextUnit.Unspecified && style.fontSize.isSp) {
                val pt = style.fontSize.value.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
                val ptLabel = if (pt % 1f == 0f) pt.toInt().toString() else String.format(Locale.US, "%.1f", pt)
                emit("size", ptLabel)
            }
            if (style.fontFamily != null) {
                val fontOpt = NoteFontOption.fromFontFamily(style.fontFamily)
                if (fontOpt != null && fontOpt != NoteFontOption.DEFAULT) {
                    emit("font", fontOpt.name)
                }
            }
        }

        if (events.isEmpty()) return cleanText
        events.sortWith(compareBy<SpanEvent> { it.pos }.thenBy { if (it.isStart) 1 else 0 }.thenBy { it.order })

        var boldCount = 0
        var italicCount = 0
        var underlineCount = 0
        var strikeCount = 0
        val colorStack = ArrayList<Pair<Int, String>>()
        val markStack = ArrayList<Pair<Int, String>>()
        val sizeStack = ArrayList<Pair<Int, String>>()
        val fontStack = ArrayList<Pair<Int, String>>()

        val sb = StringBuilder(cleanText.length + events.size * 6)
        var cursor = 0
        var evIdx = 0

        fun emitSegment(segStart: Int, segEnd: Int) {
            if (segEnd <= segStart) return
            val bActive = boldCount > 0
            val iActive = italicCount > 0
            val uActive = underlineCount > 0
            val sActive = strikeCount > 0
            val colorHex = colorStack.lastOrNull()?.second
            val markHex = markStack.lastOrNull()?.second
            val sizePt = sizeStack.lastOrNull()?.second
            val fontName = fontStack.lastOrNull()?.second

            if (bActive) sb.append("<b>")
            if (iActive) sb.append("<i>")
            if (uActive) sb.append("<u>")
            if (sActive) sb.append("<s>")
            if (fontName != null) sb.append("<font name=\"").append(fontName).append("\">")
            if (sizePt != null) sb.append("<size pt=\"").append(sizePt).append("\">")
            if (colorHex != null) sb.append("<color hex=\"").append(colorHex).append("\">")
            if (markHex != null) sb.append("<mark hex=\"").append(markHex).append("\">")

            sb.append(cleanText, segStart, segEnd)

            if (markHex != null) sb.append("</mark>")
            if (colorHex != null) sb.append("</color>")
            if (sizePt != null) sb.append("</size>")
            if (fontName != null) sb.append("</font>")
            if (sActive) sb.append("</s>")
            if (uActive) sb.append("</u>")
            if (iActive) sb.append("</i>")
            if (bActive) sb.append("</b>")
        }

        while (evIdx < events.size) {
            val pos = events[evIdx].pos
            if (pos > cursor) {
                emitSegment(cursor, pos)
                cursor = pos
            }
            while (evIdx < events.size && events[evIdx].pos == pos) {
                val ev = events[evIdx++]
                when (ev.type) {
                    "b" -> boldCount = (boldCount + if (ev.isStart) 1 else -1).coerceAtLeast(0)
                    "i" -> italicCount = (italicCount + if (ev.isStart) 1 else -1).coerceAtLeast(0)
                    "u" -> underlineCount = (underlineCount + if (ev.isStart) 1 else -1).coerceAtLeast(0)
                    "s" -> strikeCount = (strikeCount + if (ev.isStart) 1 else -1).coerceAtLeast(0)
                    "color" -> if (ev.isStart && ev.attrVal != null) {
                        colorStack.add(ev.order to ev.attrVal)
                    } else {
                        val idx = colorStack.indexOfLast { it.first == ev.order }
                        if (idx >= 0) colorStack.removeAt(idx)
                    }
                    "mark" -> if (ev.isStart && ev.attrVal != null) {
                        markStack.add(ev.order to ev.attrVal)
                    } else {
                        val idx = markStack.indexOfLast { it.first == ev.order }
                        if (idx >= 0) markStack.removeAt(idx)
                    }
                    "size" -> if (ev.isStart && ev.attrVal != null) {
                        sizeStack.add(ev.order to ev.attrVal)
                    } else {
                        val idx = sizeStack.indexOfLast { it.first == ev.order }
                        if (idx >= 0) sizeStack.removeAt(idx)
                    }
                    "font" -> if (ev.isStart && ev.attrVal != null) {
                        fontStack.add(ev.order to ev.attrVal)
                    } else {
                        val idx = fontStack.indexOfLast { it.first == ev.order }
                        if (idx >= 0) fontStack.removeAt(idx)
                    }
                }
            }
        }
        if (cursor < cleanText.length) {
            emitSegment(cursor, cleanText.length)
        }
        return sb.toString()
    }

    /**
     * Toggles Bold, Italic, or Underline on the selected range.
     */
    fun toggleStyleOnSelection(
        value: TextFieldValue,
        type: StyleType
    ): TextFieldValue {
        val selection = value.selection
        if (selection.collapsed) return value
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)

        val currentSpans = value.annotatedString.spanStyles
        val isAlreadyApplied = currentSpans.any { range ->
            range.start <= start && range.end >= end && when (type) {
                StyleType.BOLD -> range.item.fontWeight == FontWeight.Bold
                StyleType.ITALIC -> range.item.fontStyle == FontStyle.Italic
                StyleType.UNDERLINE -> range.item.textDecoration?.contains(TextDecoration.Underline) == true
            }
        }

        val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        if (isAlreadyApplied) {
            for (range in currentSpans) {
                val matchesType = when (type) {
                    StyleType.BOLD -> range.item.fontWeight == FontWeight.Bold
                    StyleType.ITALIC -> range.item.fontStyle == FontStyle.Italic
                    StyleType.UNDERLINE -> range.item.textDecoration?.contains(TextDecoration.Underline) == true
                }
                if (!matchesType) {
                    updatedSpans.add(range)
                    continue
                }
                if (range.end <= start || range.start >= end) {
                    updatedSpans.add(range)
                } else {
                    if (range.start < start) {
                        updatedSpans.add(AnnotatedString.Range(range.item, range.start, start))
                    }
                    if (range.end > end) {
                        updatedSpans.add(AnnotatedString.Range(range.item, end, range.end))
                    }
                }
            }
        } else {
            updatedSpans.addAll(currentSpans)
            val newStyle = when (type) {
                StyleType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                StyleType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                StyleType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            }
            updatedSpans.add(AnnotatedString.Range(newStyle, start, end))
        }

        return value.copy(
            annotatedString = AnnotatedString(value.text, updatedSpans)
        )
    }

    /**
     * Applies a text color to the selected text range.
     */
    fun applyColorToSelection(
        value: TextFieldValue,
        color: Color
    ): TextFieldValue {
        val selection = value.selection
        if (selection.collapsed) return value
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)

        val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in value.annotatedString.spanStyles) {
            if (range.item.color == Color.Unspecified || range.item.color == Color.Transparent) {
                updatedSpans.add(range)
                continue
            }
            if (range.end <= start || range.start >= end) {
                updatedSpans.add(range)
            } else {
                if (range.start < start) {
                    updatedSpans.add(AnnotatedString.Range(range.item, range.start, start))
                }
                if (range.end > end) {
                    updatedSpans.add(AnnotatedString.Range(range.item, end, range.end))
                }
            }
        }

        updatedSpans.add(AnnotatedString.Range(SpanStyle(color = color), start, end))
        return value.copy(
            annotatedString = AnnotatedString(value.text, updatedSpans)
        )
    }

    /**
     * Applies a highlight color to the selected text range, or removes highlight if null.
     */
    fun applyHighlightToSelection(
        value: TextFieldValue,
        highlightColor: Color?
    ): TextFieldValue {
        val selection = value.selection
        if (selection.collapsed) return value
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)

        val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in value.annotatedString.spanStyles) {
            if (range.item.background == Color.Unspecified || range.item.background == Color.Transparent) {
                updatedSpans.add(range)
                continue
            }
            if (range.end <= start || range.start >= end) {
                updatedSpans.add(range)
            } else {
                if (range.start < start) {
                    updatedSpans.add(AnnotatedString.Range(range.item, range.start, start))
                }
                if (range.end > end) {
                    updatedSpans.add(AnnotatedString.Range(range.item, end, range.end))
                }
            }
        }

        if (highlightColor != null && highlightColor != Color.Transparent) {
            val bg = if (highlightColor.alpha >= 0.99f) highlightColor.copy(alpha = 0.55f) else highlightColor
            updatedSpans.add(AnnotatedString.Range(SpanStyle(background = bg), start, end))
        }

        return value.copy(
            annotatedString = AnnotatedString(value.text, updatedSpans)
        )
    }

    /**
     * Applies a font size (punto) to the selected text range.
     */
    fun applyFontSizeToSelection(
        value: TextFieldValue,
        sizeSp: Float
    ): TextFieldValue {
        val selection = value.selection
        if (selection.collapsed) return value
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)
        val clamped = sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)

        val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in value.annotatedString.spanStyles) {
            if (range.item.fontSize == TextUnit.Unspecified) {
                updatedSpans.add(range)
                continue
            }
            if (range.end <= start || range.start >= end) {
                updatedSpans.add(range)
            } else {
                if (range.start < start) {
                    updatedSpans.add(AnnotatedString.Range(range.item, range.start, start))
                }
                if (range.end > end) {
                    updatedSpans.add(AnnotatedString.Range(range.item, end, range.end))
                }
            }
        }

        updatedSpans.add(AnnotatedString.Range(SpanStyle(fontSize = clamped.sp), start, end))
        return value.copy(
            annotatedString = AnnotatedString(value.text, updatedSpans)
        )
    }

    /**
     * Applies a font family to the selected text range.
     */
    fun applyFontFamilyToSelection(
        value: TextFieldValue,
        fontOption: NoteFontOption
    ): TextFieldValue {
        val selection = value.selection
        if (selection.collapsed) return value
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)

        val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in value.annotatedString.spanStyles) {
            if (range.item.fontFamily == null) {
                updatedSpans.add(range)
                continue
            }
            if (range.end <= start || range.start >= end) {
                updatedSpans.add(range)
            } else {
                if (range.start < start) {
                    updatedSpans.add(AnnotatedString.Range(range.item, range.start, start))
                }
                if (range.end > end) {
                    updatedSpans.add(AnnotatedString.Range(range.item, end, range.end))
                }
            }
        }

        if (fontOption != NoteFontOption.DEFAULT) {
            updatedSpans.add(AnnotatedString.Range(SpanStyle(fontFamily = fontOption.resolveFontFamily()), start, end))
        }
        return value.copy(
            annotatedString = AnnotatedString(value.text, updatedSpans)
        )
    }

    /**
     * Shifts existing SpanStyles cleanly when characters are inserted or deleted at a single point,
     * guaranteeing existing note formatting/colors are NEVER wiped on list Enter operations.
     */
    private fun shiftSpansForInsertion(
        oldSpans: List<AnnotatedString.Range<SpanStyle>>,
        insertAt: Int,
        insertedCount: Int,
        newLength: Int
    ): MutableList<AnnotatedString.Range<SpanStyle>> {
        val shifted = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in oldSpans) {
            when {
                range.end <= insertAt -> shifted.add(range)
                range.start >= insertAt -> {
                    val ns = (range.start + insertedCount).coerceIn(0, newLength)
                    val ne = (range.end + insertedCount).coerceIn(0, newLength)
                    if (ne > ns) shifted.add(AnnotatedString.Range(range.item, ns, ne))
                }
                else -> {
                    // Split across insertion point so styles before & after remain intact without leaking onto new prefix
                    if (insertAt > range.start) {
                        shifted.add(AnnotatedString.Range(range.item, range.start, insertAt.coerceAtMost(newLength)))
                    }
                    val tailStart = (insertAt + insertedCount).coerceIn(0, newLength)
                    val tailEnd = (range.end + insertedCount).coerceIn(0, newLength)
                    if (tailEnd > tailStart) {
                        shifted.add(AnnotatedString.Range(range.item, tailStart, tailEnd))
                    }
                }
            }
        }
        return shifted
    }

    private fun shiftSpansForDeletion(
        oldSpans: List<AnnotatedString.Range<SpanStyle>>,
        deleteStart: Int,
        deleteEnd: Int,
        newLength: Int
    ): MutableList<AnnotatedString.Range<SpanStyle>> {
        val deletedCount = (deleteEnd - deleteStart).coerceAtLeast(0)
        val shifted = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in oldSpans) {
            when {
                range.end <= deleteStart -> shifted.add(range)
                range.start >= deleteEnd -> {
                    val ns = (range.start - deletedCount).coerceIn(0, newLength)
                    val ne = (range.end - deletedCount).coerceIn(0, newLength)
                    if (ne > ns) shifted.add(AnnotatedString.Range(range.item, ns, ne))
                }
                else -> {
                    val ns = minOf(range.start, deleteStart).coerceIn(0, newLength)
                    val ne = maxOf(deleteStart, range.end - deletedCount).coerceIn(0, newLength)
                    if (ne > ns) shifted.add(AnnotatedString.Range(range.item, ns, ne))
                }
            }
        }
        return shifted
    }

    private fun appendActiveStylesForRange(
        spans: MutableList<AnnotatedString.Range<SpanStyle>>,
        start: Int,
        end: Int,
        isBoldActive: Boolean,
        isItalicActive: Boolean,
        isUnderlineActive: Boolean,
        activeTextColor: Color?,
        activeHighlightColor: Color?,
        activeFontSizeSp: Float?,
        activeFontOption: NoteFontOption?
    ) {
        if (end <= start) return
        if (isBoldActive && !spans.any { it.start <= start && it.end >= end && it.item.fontWeight == FontWeight.Bold }) {
            spans.add(AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), start, end))
        }
        if (isItalicActive && !spans.any { it.start <= start && it.end >= end && it.item.fontStyle == FontStyle.Italic }) {
            spans.add(AnnotatedString.Range(SpanStyle(fontStyle = FontStyle.Italic), start, end))
        }
        if (isUnderlineActive && !spans.any { it.start <= start && it.end >= end && it.item.textDecoration?.contains(TextDecoration.Underline) == true }) {
            spans.add(AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.Underline), start, end))
        }
        if (activeTextColor != null && activeTextColor != Color.Unspecified && !spans.any { it.start <= start && it.end >= end && it.item.color == activeTextColor }) {
            spans.add(AnnotatedString.Range(SpanStyle(color = activeTextColor), start, end))
        }
        if (activeHighlightColor != null && activeHighlightColor != Color.Unspecified && activeHighlightColor != Color.Transparent && !spans.any { it.start <= start && it.end >= end && it.item.background == activeHighlightColor }) {
            val bg = if (activeHighlightColor.alpha >= 0.99f) activeHighlightColor.copy(alpha = 0.55f) else activeHighlightColor
            spans.add(AnnotatedString.Range(SpanStyle(background = bg), start, end))
        }
        if (activeFontSizeSp != null && activeFontSizeSp != DEFAULT_FONT_SIZE_SP) {
            val clamped = activeFontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            if (!spans.any { it.start <= start && it.end >= end && it.item.fontSize == clamped.sp }) {
                spans.add(AnnotatedString.Range(SpanStyle(fontSize = clamped.sp), start, end))
            }
        }
        if (activeFontOption != null && activeFontOption != NoteFontOption.DEFAULT) {
            val fam = activeFontOption.resolveFontFamily()
            if (!spans.any { it.start <= start && it.end >= end && it.item.fontFamily == fam }) {
                spans.add(AnnotatedString.Range(SpanStyle(fontFamily = fam), start, end))
            }
        }
    }

    /**
     * Automatically continues bullet, numbered, or checkbox lists when Enter is pressed,
     * or cancels the list if Enter is pressed on an empty list prefix — while strictly
     * preserving ALL existing note SpanStyles and respecting [PAGE_SENTINEL] on Page 2+.
     */
    fun handleEnterOnListItem(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        isBoldActive: Boolean = false,
        isItalicActive: Boolean = false,
        isUnderlineActive: Boolean = false,
        activeTextColor: Color? = null,
        activeHighlightColor: Color? = null,
        activeFontSizeSp: Float? = null,
        activeFontOption: NoteFontOption? = null
    ): TextFieldValue? {
        val oldText = oldValue.text
        val newText = newValue.text
        if (newText.length != oldText.length + 1) return null

        val insertPos = oldValue.selection.min
        if (insertPos !in 0 until newText.length) return null
        if (newText[insertPos] != '\n') return null

        val rawLineStart = oldText.lastIndexOf('\n', (insertPos - 1).coerceAtLeast(0)).let {
            if (it == -1) 0 else it + 1
        }
        val sentinelOffset = if (rawLineStart < oldText.length && oldText[rawLineStart] == PAGE_SENTINEL_CHAR) 1 else 0
        val effectiveLineStart = (rawLineStart + sentinelOffset).coerceAtMost(insertPos)
        val prevLine = oldText.substring(effectiveLineStart, insertPos)
        val oldSpans = oldValue.annotatedString.spanStyles

        fun buildWithPrefix(prefix: String): TextFieldValue {
            val updated = oldText.substring(0, insertPos) + "\n" + prefix + oldText.substring(insertPos)
            val totalInserted = 1 + prefix.length
            val updatedSpans = shiftSpansForInsertion(oldSpans, insertPos, totalInserted, updated.length)
            val prefixStart = insertPos + 1
            val prefixEnd = prefixStart + prefix.length
            appendActiveStylesForRange(
                spans = updatedSpans,
                start = prefixStart,
                end = prefixEnd,
                isBoldActive = isBoldActive,
                isItalicActive = isItalicActive,
                isUnderlineActive = isUnderlineActive,
                activeTextColor = activeTextColor,
                activeHighlightColor = activeHighlightColor,
                activeFontSizeSp = activeFontSizeSp,
                activeFontOption = activeFontOption
            )
            val coalesced = coalesceSpans(updatedSpans, updated.length)
            val newCursor = (insertPos + totalInserted).coerceIn(0, updated.length)
            return TextFieldValue(AnnotatedString(updated, coalesced), TextRange(newCursor))
        }

        fun buildCancelledPrefix(prefixLen: Int): TextFieldValue {
            val removeEnd = (effectiveLineStart + prefixLen).coerceAtMost(newText.length)
            val updated = newText.removeRange(effectiveLineStart, removeEnd)
            val afterInsertSpans = shiftSpansForInsertion(oldSpans, insertPos, 1, newText.length)
            val updatedSpans = shiftSpansForDeletion(afterInsertSpans, effectiveLineStart, removeEnd, updated.length)
            val coalesced = coalesceSpans(updatedSpans, updated.length)
            val newCursor = effectiveLineStart.coerceIn(0, updated.length)
            return TextFieldValue(AnnotatedString(updated, coalesced), TextRange(newCursor))
        }

        // Bullet list (•)
        if (prevLine == "• ") {
            return buildCancelledPrefix(2)
        } else if (prevLine.startsWith("• ")) {
            return buildWithPrefix("• ")
        }

        // Checkbox list ([ ] or [✓] or [x] or legacy ☐ / ☑)
        val isCheckboxLine = prevLine.startsWith("[ ] ") || prevLine.startsWith("[✓] ") ||
            prevLine.startsWith("[x] ") || prevLine.startsWith("[X] ") ||
            prevLine.startsWith("☐ ") || prevLine.startsWith("☑ ")

        val isCheckboxEmpty = prevLine == "[ ] " || prevLine == "[✓] " ||
            prevLine == "[x] " || prevLine == "[X] " ||
            prevLine == "☐ " || prevLine == "☑ "

        if (isCheckboxEmpty) {
            val prefixLen = if (prevLine.startsWith("[")) 4 else 2
            return buildCancelledPrefix(prefixLen)
        } else if (isCheckboxLine) {
            return buildWithPrefix("[ ] ")
        }

        // Numbered list (1., 2., etc.)
        val numMatch = Regex("""^(\d+)\.\s*(.*)""").find(prevLine)
        if (numMatch != null) {
            val num = numMatch.groupValues[1].toIntOrNull() ?: 1
            val rest = numMatch.groupValues[2]
            return if (rest.isBlank()) {
                buildCancelledPrefix(prevLine.length)
            } else {
                buildWithPrefix("${num + 1}. ")
            }
        }

        return null
    }

    /**
     * Toggles line prefix for bullet (• ), numbering (1. ), or checkbox ([ ] ),
     * preserving any leading [PAGE_SENTINEL] on Page 2+.
     */
    fun toggleLinePrefix(
        value: TextFieldValue,
        prefix: String
    ): TextFieldValue {
        val currentText = value.text
        val cursor = value.selection.start.coerceIn(0, currentText.length)
        val rawLineStart = currentText.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let {
            if (it == -1) 0 else it + 1
        }
        val sentinelOffset = if (rawLineStart < currentText.length && currentText[rawLineStart] == PAGE_SENTINEL_CHAR) 1 else 0
        val lineStart = (rawLineStart + sentinelOffset).coerceAtMost(currentText.length)
        val lineEnd = currentText.indexOf('\n', lineStart).let {
            if (it == -1) currentText.length else it
        }
        val currentLine = currentText.substring(lineStart, lineEnd)

        val knownPrefixes = listOf("• ", "1. ", "[ ] ", "[✓] ", "[x] ", "[X] ", "☐ ", "☑ ")
        val matchedPrefix = knownPrefixes.firstOrNull { currentLine.startsWith(it) }
            ?: if (currentLine.matches(Regex("""^\d+\.\s+.*"""))) {
                currentLine.substring(0, currentLine.indexOf(' ') + 1)
            } else null

        val updatedText: String
        val cursorShift: Int

        if (matchedPrefix == prefix) {
            updatedText = currentText.removeRange(lineStart, lineStart + prefix.length)
            cursorShift = -prefix.length
        } else if (matchedPrefix != null) {
            updatedText = currentText.replaceRange(lineStart, lineStart + matchedPrefix.length, prefix)
            cursorShift = prefix.length - matchedPrefix.length
        } else {
            updatedText = currentText.substring(0, lineStart) + prefix + currentText.substring(lineStart)
            cursorShift = prefix.length
        }

        val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in value.annotatedString.spanStyles) {
            if (range.end <= lineStart) {
                updatedSpans.add(range)
            } else if (range.start >= lineStart) {
                val newStart = (range.start + cursorShift).coerceIn(0, updatedText.length)
                val newEnd = (range.end + cursorShift).coerceIn(0, updatedText.length)
                if (newEnd > newStart) {
                    updatedSpans.add(AnnotatedString.Range(range.item, newStart, newEnd))
                }
            } else {
                val newEnd = (range.end + cursorShift).coerceIn(0, updatedText.length)
                if (newEnd > range.start) {
                    updatedSpans.add(AnnotatedString.Range(range.item, range.start, newEnd))
                }
            }
        }

        val newCursor = (cursor + cursorShift).coerceIn(sentinelOffset, updatedText.length)
        return TextFieldValue(
            annotatedString = AnnotatedString(updatedText, coalesceSpans(updatedSpans, updatedText.length)),
            selection = TextRange(newCursor)
        )
    }

    /**
     * Preserves and expands/contracts existing styles when text is typed or deleted,
     * applying active styles ONLY to newly inserted characters and coalescing adjacent spans in O(N)
     * so typing never degrades on low-RAM devices.
     */
    fun handleTextChange(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        isBoldActive: Boolean,
        isItalicActive: Boolean,
        isUnderlineActive: Boolean,
        activeTextColor: Color?,
        activeHighlightColor: Color?,
        activeFontSizeSp: Float? = null,
        activeFontOption: NoteFontOption? = null
    ): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text

        if (oldText == newText) {
            return newValue.copy(annotatedString = oldValue.annotatedString)
        }

        val oldSpans = oldValue.annotatedString.spanStyles

        if (newText.length > oldText.length) {
            val insertStart = minOf(oldValue.selection.min, oldText.length)
            val addedCount = newText.length - oldText.length
            val insertEnd = (insertStart + addedCount).coerceAtMost(newText.length)

            val updatedSpans = shiftSpansForInsertion(oldSpans, insertStart, addedCount, newText.length)
            appendActiveStylesForRange(
                spans = updatedSpans,
                start = insertStart,
                end = insertEnd,
                isBoldActive = isBoldActive,
                isItalicActive = isItalicActive,
                isUnderlineActive = isUnderlineActive,
                activeTextColor = activeTextColor,
                activeHighlightColor = activeHighlightColor,
                activeFontSizeSp = activeFontSizeSp,
                activeFontOption = activeFontOption
            )

            return newValue.copy(
                annotatedString = AnnotatedString(newText, coalesceSpans(updatedSpans, newText.length))
            )
        }

        // Deletion
        val deleteStart = newValue.selection.min
        val deletedCount = oldText.length - newText.length
        val deleteEnd = deleteStart + deletedCount

        val updatedSpans = shiftSpansForDeletion(oldSpans, deleteStart, deleteEnd, newText.length)

        return newValue.copy(
            annotatedString = AnnotatedString(newText, coalesceSpans(updatedSpans, newText.length))
        )
    }

    data class CursorStyles(
        val isBold: Boolean,
        val isItalic: Boolean,
        val isUnderline: Boolean,
        val textColor: Color?,
        val highlightColor: Color?,
        val isBullet: Boolean,
        val isNumbered: Boolean,
        val isCheckbox: Boolean,
        val isCheckboxChecked: Boolean,
        val fontSizeSp: Float? = null,
        val fontOption: NoteFontOption? = null
    )

    fun getStylesAtCursor(
        value: TextFieldValue
    ): CursorStyles {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        val inspectPos = if (cursor > 0 && (cursor == text.length || !value.selection.collapsed)) cursor - 1 else cursor

        val spans = value.annotatedString.spanStyles
        val activeSpans = spans.filter { it.start <= inspectPos && it.end > inspectPos }

        val isBold = activeSpans.any { it.item.fontWeight == FontWeight.Bold }
        val isItalic = activeSpans.any { it.item.fontStyle == FontStyle.Italic }
        val isUnderline = activeSpans.any { it.item.textDecoration?.contains(TextDecoration.Underline) == true }
        val color = activeSpans.lastOrNull { it.item.color != Color.Unspecified && it.item.color != Color.Transparent }?.item?.color
        val highlight = activeSpans.lastOrNull { it.item.background != Color.Unspecified && it.item.background != Color.Transparent }?.item?.background
        val fontSizeSp = activeSpans.lastOrNull { it.item.fontSize != TextUnit.Unspecified && it.item.fontSize.isSp }?.item?.fontSize?.value
        val fontOption = activeSpans.lastOrNull { it.item.fontFamily != null }?.item?.fontFamily?.let {
            NoteFontOption.fromFontFamily(it)
        }

        val rawLineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', rawLineStart).let { if (it == -1) text.length else it }
        val line = stripPageSentinel(text.substring(rawLineStart, lineEnd))

        val isBullet = line.startsWith("• ")
        val isNumbered = line.matches(Regex("""^\d+\.\s+.*"""))
        val isCheckbox = line.startsWith("[ ] ") || line.startsWith("[✓] ") || line.startsWith("[x] ") || line.startsWith("[X] ") || line.startsWith("☐ ") || line.startsWith("☑ ")
        val isCheckboxChecked = line.startsWith("[✓] ") || line.startsWith("[x] ") || line.startsWith("[X] ") || line.startsWith("☑ ")

        return CursorStyles(
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            textColor = color,
            highlightColor = highlight,
            isBullet = isBullet,
            isNumbered = isNumbered,
            isCheckbox = isCheckbox,
            isCheckboxChecked = isCheckboxChecked,
            fontSizeSp = fontSizeSp,
            fontOption = fontOption
        )
    }

    /**
     * Toggles checkable to-do item on the line containing [offset]:
     * - "[ ] " -> "[✓] " (with strikethrough on line text)
     * - "[✓] " / "[x] " -> "[ ] " (with strikethrough removed)
     * - legacy "☐ " -> "[✓] "
     * - legacy "☑ " -> "[ ] "
     * - otherwise -> prepends "[ ] "
     */
    fun toggleCheckboxAtOffset(
        value: TextFieldValue,
        offset: Int
    ): TextFieldValue {
        val currentText = value.text
        if (currentText.isEmpty()) {
            return TextFieldValue(AnnotatedString("[ ] "), TextRange(4))
        }
        val targetPos = offset.coerceIn(0, currentText.length)
        val rawLineStart = currentText.lastIndexOf('\n', (targetPos - 1).coerceAtLeast(0)).let {
            if (it == -1) 0 else it + 1
        }
        val sentinelOffset = if (rawLineStart < currentText.length && currentText[rawLineStart] == PAGE_SENTINEL_CHAR) 1 else 0
        val lineStart = (rawLineStart + sentinelOffset).coerceAtMost(currentText.length)
        val lineEnd = currentText.indexOf('\n', lineStart).let {
            if (it == -1) currentText.length else it
        }
        val currentLine = currentText.substring(lineStart, lineEnd)

        return when {
            // Checked: [✓] or [x] or [X] -> Uncheck to [ ]
            currentLine.startsWith("[✓] ") || currentLine.startsWith("[x] ") || currentLine.startsWith("[X] ") -> {
                val prefixLen = 4
                val updatedText = currentText.replaceRange(lineStart, lineStart + prefixLen, "[ ] ")
                val textStart = lineStart + 4
                val textEnd = lineEnd
                val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
                for (range in value.annotatedString.spanStyles) {
                    val isLineThrough = range.item.textDecoration?.contains(TextDecoration.LineThrough) == true
                    if (isLineThrough && range.start < textEnd && range.end > textStart) {
                        if (range.start < textStart) {
                            updatedSpans.add(AnnotatedString.Range(range.item, range.start, textStart))
                        }
                        if (range.end > textEnd) {
                            updatedSpans.add(AnnotatedString.Range(range.item, textEnd, range.end))
                        }
                        continue
                    }
                    updatedSpans.add(range)
                }
                TextFieldValue(
                    annotatedString = AnnotatedString(updatedText, updatedSpans),
                    selection = value.selection
                )
            }
            // Unchecked: [ ] -> Check to [✓] and strike through text
            currentLine.startsWith("[ ] ") -> {
                val updatedText = currentText.replaceRange(lineStart, lineStart + 4, "[✓] ")
                val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
                for (range in value.annotatedString.spanStyles) {
                    updatedSpans.add(range)
                }
                val textStart = lineStart + 4
                val textEnd = lineEnd
                if (textEnd > textStart) {
                    updatedSpans.add(AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.LineThrough), textStart, textEnd))
                }
                TextFieldValue(
                    annotatedString = AnnotatedString(updatedText, updatedSpans),
                    selection = value.selection
                )
            }
            // Legacy ☑ -> Uncheck to [ ]
            currentLine.startsWith("☑ ") -> {
                val updatedText = currentText.replaceRange(lineStart, lineStart + 2, "[ ] ")
                val textStart = lineStart + 4
                val textEnd = lineEnd + 2
                val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
                for (range in value.annotatedString.spanStyles) {
                    val isLineThrough = range.item.textDecoration?.contains(TextDecoration.LineThrough) == true
                    if (isLineThrough && range.start >= lineStart && range.end <= textEnd) {
                        continue
                    }
                    updatedSpans.add(range)
                }
                TextFieldValue(
                    annotatedString = AnnotatedString(updatedText, updatedSpans),
                    selection = value.selection
                )
            }
            // Legacy ☐ -> Check to [✓] with strikethrough
            currentLine.startsWith("☐ ") -> {
                val updatedText = currentText.replaceRange(lineStart, lineStart + 2, "[✓] ")
                val updatedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
                for (range in value.annotatedString.spanStyles) {
                    updatedSpans.add(range)
                }
                val textStart = lineStart + 4
                val textEnd = lineEnd + 2
                if (textEnd > textStart) {
                    updatedSpans.add(AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.LineThrough), textStart, textEnd))
                }
                TextFieldValue(
                    annotatedString = AnnotatedString(updatedText, updatedSpans),
                    selection = value.selection
                )
            }
            else -> {
                toggleLinePrefix(value, "[ ] ")
            }
        }
    }
}
