package com.example.sharkmentor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ACTION_BLOCK_REGEX = Regex("#ACTION:(\\{.*?\\})#", RegexOption.DOT_MATCHES_ALL)
private val ACTION_TAG_REGEX = Regex("#ACTION:[A-Z_]+")

private val HEADING_REGEX = Regex("^(#{1,3})\\s+(.+)$")
private val FINAL_ANSWER_REGEX = Regex(
    "^(?:\\*\\*)?((?:Doğru\\s+Cevap|Doğru\\s+Seçenek|Cevap\\s+Şıkkı|Sonuç|Cevap|Yanıt)\\s*[:\\-])(?:\\*\\*)?\\s*(.*)$",
    RegexOption.IGNORE_CASE
)
private val STEP_HEADER_REGEX = Regex(
    "^(?:\\*\\*)?((?:\\d+\\.\\s*Adım|Adım\\s*\\d+|Çözüm\\s*Yolu|Çözüm|Verilenler|İstenen|Formül|Kural|Özet|Not|Dikkat|Önemli)\\s*[:.]?)(?:\\*\\*)?\\s*(.*)$",
    RegexOption.IGNORE_CASE
)
private val NUMBERED_BOLD_STEP_REGEX = Regex("^(\\d+\\.)\\s+\\*\\*([^*]+?)\\*\\*\\s*[:\\-]?\\s*(.*)$")
private val OPTION_PREFIX_REGEX = Regex("^([A-E][\\).])\\s+(.*)$")
private val BULLET_REGEX = Regex("^\\s*(?:[-*•])\\s+(.+)$")

private val DISPLAY_MATH_REGEX = Regex(
    "(?:\\$\\$([\\s\\S]+?)\\$\\$|\\\\\\[([\\s\\S]+?)\\\\\\])"
)
private val INLINE_TOKEN_REGEX = Regex(
    "(\\*\\*([^*\\n]+?)\\*\\*)|(`([^`\\n]+?)`)|((?<!\\*)\\*([^\\s*][^*\\n]*?[^\\s*]|[^\\s*])\\*(?!\\*))"
)
private val MULTI_BLANK_LINE_REGEX = Regex("\\n{3,}")
private val ENV_BEGIN_END_REGEX = Regex(
    "\\\\begin\\{(aligned|align\\*?|gather\\*?|eqnarray\\*?|equation\\*?|split)\\}|\\\\end\\{(aligned|align\\*?|gather\\*?|eqnarray\\*?|equation\\*?|split)\\}"
)
private val CASES_ENV_REGEX = Regex("\\\\begin\\{cases\\}([\\s\\S]*?)\\\\end\\{cases\\}")
private val MATRIX_ENV_REGEX = Regex("\\\\begin\\{(p|b|v|V)?matrix\\}([\\s\\S]*?)\\\\end\\{(?:p|b|v|V)?matrix\\}")
private val ESCAPED_DOLLAR_SENTINEL = "\uE000"

/**
 * Pure JVM structured block model for AI study solutions and explanations.
 */
sealed interface SolutionBlock {
    data class Heading(val text: String, val level: Int) : SolutionBlock
    data class Step(val label: String, val body: String, val isImportantNote: Boolean = false) : SolutionBlock
    data class DisplayFormula(val formula: String) : SolutionBlock
    data class FinalAnswer(val label: String, val content: String) : SolutionBlock
    data class Bullet(val text: String) : SolutionBlock
    data class Paragraph(val text: String) : SolutionBlock
}

/**
 * Theme-aware inline rich chat text composable used by Shark AI chat bubbles.
 * Automatically adapts contrast to Light, Dark, all 8 palettes, Zen Monochrome,
 * and primary-filled user chat bubbles.
 */
@Composable
fun RichChatText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 24.sp
) {
    val colorScheme = MaterialTheme.colorScheme
    val isOnPrimarySurface = textColor == colorScheme.onPrimary
    val accentColor = if (isOnPrimarySurface) textColor else colorScheme.primary
    val secondaryAccent = if (isOnPrimarySurface) textColor else colorScheme.tertiary
    val codeBgColor = textColor.copy(alpha = if (isOnPrimarySurface) 0.16f else 0.09f)

    val annotated = remember(text, textColor, accentColor, secondaryAccent, codeBgColor) {
        val cleaned = cleanAndFormatMath(text)
        buildRichChatAnnotatedString(
            cleanedText = cleaned,
            baseColor = textColor,
            accentColor = accentColor,
            secondaryAccent = secondaryAccent,
            codeBgColor = codeBgColor
        )
    }

    Text(
        text = annotated,
        modifier = modifier,
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = 0.15.sp
    )
}

/**
 * Structured, theme-aware math & study solution renderer for Lasso AI, PDF AI, Note AI,
 * and Question Solve screens. Renders headings, step labels, tinted display formula cards,
 * clean bullets, inline Unicode math, and highlighted final-answer callouts.
 */
@Composable
fun RichSolutionContent(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    formulaContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    answerContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    answerContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    fontSize: TextUnit = 14.5.sp,
    lineHeight: TextUnit = 22.sp,
    showWatermark: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val secondaryAccent = colorScheme.tertiary
    val codeBgColor = textColor.copy(alpha = 0.09f)
    val blocks = remember(text) { parseSolutionBlocks(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is SolutionBlock.Heading -> {
                    val headingSize = when (block.level) {
                        1 -> (fontSize.value + 2.5f).sp
                        2 -> (fontSize.value + 1.5f).sp
                        else -> (fontSize.value + 0.5f).sp
                    }
                    val styled = remember(block.text, textColor, accentColor, codeBgColor) {
                        buildInlineAnnotatedString(block.text, textColor, accentColor, codeBgColor)
                    }
                    Text(
                        text = styled,
                        color = textColor,
                        fontSize = headingSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (headingSize.value + 6f).sp
                    )
                }

                is SolutionBlock.Step -> {
                    val labelColor = if (block.isImportantNote) secondaryAccent else accentColor
                    val styled = remember(block.label, block.body, textColor, labelColor, accentColor, codeBgColor) {
                        buildAnnotatedString {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = labelColor))
                            append(block.label)
                            pop()
                            if (block.body.isNotBlank()) {
                                append(" ")
                                appendInlineMarkdown(block.body, textColor, accentColor, codeBgColor)
                            }
                        }
                    }
                    Text(
                        text = styled,
                        color = textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }

                is SolutionBlock.DisplayFormula -> {
                    Surface(
                        color = formulaContainerColor,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .fillMaxHeight()
                                    .background(accentColor)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = block.formula,
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = fontSize,
                                    lineHeight = lineHeight
                                )
                            }
                        }
                    }
                }

                is SolutionBlock.FinalAnswer -> {
                    Surface(
                        color = answerContainerColor,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val styled = remember(block.label, block.content, answerContentColor) {
                            buildAnnotatedString {
                                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = answerContentColor))
                                append(block.label)
                                pop()
                                if (block.content.isNotBlank()) {
                                    append(" ")
                                    appendInlineMarkdown(
                                        text = block.content,
                                        baseColor = answerContentColor,
                                        accentColor = answerContentColor,
                                        codeBgColor = answerContentColor.copy(alpha = 0.12f),
                                        defaultWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Text(
                            text = styled,
                            color = answerContentColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }

                is SolutionBlock.Bullet -> {
                    val styled = remember(block.text, textColor, accentColor, codeBgColor) {
                        buildInlineAnnotatedString(block.text, textColor, accentColor, codeBgColor)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.padding(end = 7.dp)
                        )
                        Text(
                            text = styled,
                            color = textColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is SolutionBlock.Paragraph -> {
                    val styled = remember(block.text, textColor, accentColor, codeBgColor) {
                        buildParagraphAnnotatedString(block.text, textColor, accentColor, codeBgColor)
                    }
                    Text(
                        text = styled,
                        color = textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
            }
        }
        if (showWatermark && text.isNotBlank()) {
            SharkCoreLabWatermark("SharkStudio CoreLab")
        }
    }
}

private fun buildRichChatAnnotatedString(
    cleanedText: String,
    baseColor: Color,
    accentColor: Color,
    secondaryAccent: Color,
    codeBgColor: Color
): AnnotatedString = buildAnnotatedString {
    val lines = cleanedText.split("\n")
    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trim()
        val headingMatch = HEADING_REGEX.find(trimmed)
        val finalMatch = FINAL_ANSWER_REGEX.find(trimmed)
        val stepMatch = STEP_HEADER_REGEX.find(trimmed)
        val optionMatch = OPTION_PREFIX_REGEX.find(trimmed)

        when {
            headingMatch != null -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor, fontSize = 16.sp))
                appendInlineMarkdown(headingMatch.groupValues[2], baseColor, accentColor, codeBgColor, FontWeight.Bold)
                pop()
            }

            finalMatch != null -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor))
                append(finalMatch.groupValues[1])
                pop()
                val rest = finalMatch.groupValues[2].trim()
                if (rest.isNotEmpty()) {
                    append(" ")
                    appendInlineMarkdown(rest, baseColor, accentColor, codeBgColor, FontWeight.SemiBold)
                }
            }

            stepMatch != null -> {
                val label = stepMatch.groupValues[1]
                val isNote = label.contains("Dikkat", ignoreCase = true) ||
                    label.contains("Önemli", ignoreCase = true) ||
                    label.contains("Not", ignoreCase = true)
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (isNote) secondaryAccent else accentColor))
                append(label)
                pop()
                val rest = stepMatch.groupValues[2].trim()
                if (rest.isNotEmpty()) {
                    append(" ")
                    appendInlineMarkdown(rest, baseColor, accentColor, codeBgColor)
                }
            }

            optionMatch != null -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor))
                append(optionMatch.groupValues[1])
                pop()
                append(" ")
                appendInlineMarkdown(optionMatch.groupValues[2], baseColor, accentColor, codeBgColor)
            }

            BULLET_REGEX.matches(rawLine) -> {
                val content = BULLET_REGEX.find(rawLine)!!.groupValues[1]
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor))
                append("• ")
                pop()
                appendInlineMarkdown(content, baseColor, accentColor, codeBgColor)
            }

            else -> {
                appendInlineMarkdown(rawLine, baseColor, accentColor, codeBgColor)
            }
        }

        if (index < lines.lastIndex) {
            append("\n")
        }
    }
}

private fun buildParagraphAnnotatedString(
    text: String,
    baseColor: Color,
    accentColor: Color,
    codeBgColor: Color
): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { idx, line ->
        val trimmed = line.trim()
        val optionMatch = OPTION_PREFIX_REGEX.find(trimmed)
        if (optionMatch != null) {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor))
            append(optionMatch.groupValues[1])
            pop()
            append(" ")
            appendInlineMarkdown(optionMatch.groupValues[2], baseColor, accentColor, codeBgColor)
        } else {
            appendInlineMarkdown(line, baseColor, accentColor, codeBgColor)
        }
        if (idx < lines.lastIndex) append("\n")
}
}

private fun buildInlineAnnotatedString(
    text: String,
    baseColor: Color,
    accentColor: Color,
    codeBgColor: Color
): AnnotatedString = buildAnnotatedString {
    appendInlineMarkdown(text, baseColor, accentColor, codeBgColor)
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    baseColor: Color,
    accentColor: Color,
    codeBgColor: Color,
    defaultWeight: FontWeight? = null
) {
    if (defaultWeight != null) {
        pushStyle(SpanStyle(fontWeight = defaultWeight, color = baseColor))
    }
    var cursor = 0
    for (match in INLINE_TOKEN_REGEX.findAll(text)) {
        val range = match.range
        if (range.first > cursor) {
            append(text.substring(cursor, range.first))
        }
        when {
            match.groups[2] != null -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                append(match.groupValues[2])
                pop()
            }

            match.groups[4] != null -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        background = codeBgColor
                    )
                )
                append(" ${match.groupValues[4]} ")
                pop()
            }

            match.groups[6] != null -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                append(match.groupValues[6])
                pop()
            }
        }
        cursor = range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
    if (defaultWeight != null) {
        pop()
    }
}

/**
 * Parses an AI solution into clean structured blocks ([SolutionBlock]) while preserving
 * display math blocks (`$$...$$` and `\[...\]`) as dedicated formula cards and formatting
 * all LaTeX expressions into crisp Unicode math.
 */
fun parseSolutionBlocks(raw: String): List<SolutionBlock> {
    if (raw.isBlank()) return emptyList()

    val sanitized = raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(ACTION_BLOCK_REGEX, "")
        .replace(ACTION_TAG_REGEX, "")
        .trim()

    if (sanitized.isEmpty()) return emptyList()

    val result = mutableListOf<SolutionBlock>()
    var cursor = 0

    for (match in DISPLAY_MATH_REGEX.findAll(sanitized)) {
        val range = match.range
        if (range.first > cursor) {
            parseTextSegmentIntoBlocks(sanitized.substring(cursor, range.first), result)
        }
        val rawFormula = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
        val formattedFormula = formatMathAsPlainText(rawFormula)
        if (formattedFormula.isNotBlank()) {
            result.add(SolutionBlock.DisplayFormula(formattedFormula))
        }
        cursor = range.last + 1
    }

    if (cursor < sanitized.length) {
        parseTextSegmentIntoBlocks(sanitized.substring(cursor), result)
    }

    return result
}

private fun parseTextSegmentIntoBlocks(segment: String, out: MutableList<SolutionBlock>) {
    val cleaned = cleanAndFormatMath(segment)
    if (cleaned.isBlank()) return

    val paragraphLines = mutableListOf<String>()
    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            val joined = paragraphLines.joinToString("\n").trim()
            if (joined.isNotEmpty()) {
                out.add(SolutionBlock.Paragraph(joined))
            }
            paragraphLines.clear()
        }
    }

    cleaned.split("\n").forEach { rawLine ->
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            flushParagraph()
            return@forEach
        }

        val headingMatch = HEADING_REGEX.find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            out.add(
                SolutionBlock.Heading(
                    text = headingMatch.groupValues[2].trim(),
                    level = headingMatch.groupValues[1].length
                )
            )
            return@forEach
        }

        val finalMatch = FINAL_ANSWER_REGEX.find(trimmed)
        if (finalMatch != null) {
            flushParagraph()
            out.add(
                SolutionBlock.FinalAnswer(
                    label = finalMatch.groupValues[1].trim(),
                    content = cleanSplitBoldRemainder(trimmed, finalMatch.groupValues[2].trim())
                )
            )
            return@forEach
        }

        val stepMatch = STEP_HEADER_REGEX.find(trimmed)
        if (stepMatch != null) {
            flushParagraph()
            val label = stepMatch.groupValues[1].trim()
            val isNote = label.contains("Dikkat", ignoreCase = true) ||
                label.contains("Önemli", ignoreCase = true) ||
                label.contains("Not", ignoreCase = true)
            out.add(
                SolutionBlock.Step(
                    label = label,
                    body = cleanSplitBoldRemainder(trimmed, stepMatch.groupValues[2].trim()),
                    isImportantNote = isNote
                )
            )
            return@forEach
        }

        val numberedBoldMatch = NUMBERED_BOLD_STEP_REGEX.find(trimmed)
        if (numberedBoldMatch != null) {
            flushParagraph()
            val label = "${numberedBoldMatch.groupValues[1]} ${numberedBoldMatch.groupValues[2].trim()}:"
            out.add(
                SolutionBlock.Step(
                    label = label,
                    body = numberedBoldMatch.groupValues[3].trim(),
                    isImportantNote = false
                )
            )
            return@forEach
        }

        val bulletMatch = BULLET_REGEX.find(rawLine)
        if (bulletMatch != null) {
            flushParagraph()
            out.add(SolutionBlock.Bullet(bulletMatch.groupValues[1].trim()))
            return@forEach
        }

        paragraphLines.add(trimmed)
    }

    flushParagraph()
}

private fun cleanSplitBoldRemainder(fullTrimmedLine: String, remainder: String): String {
    if (!fullTrimmedLine.startsWith("**") || !remainder.endsWith("**")) return remainder
    val starPairs = Regex("\\*\\*").findAll(remainder).count()
    return if (starPairs % 2 == 1) {
        remainder.removeSuffix("**").trimEnd()
    } else {
        remainder
    }
}

/**
 * Formats raw AI/LaTeX output as clean plain Unicode text (suitable for clipboard copy,
 * Note saving, and display-formula cards) with Markdown markers stripped.
 */
fun formatMathAsPlainText(raw: String): String {
    var s = cleanAndFormatMath(raw)
    s = s.replace(Regex("\\*\\*([^*\\n]+?)\\*\\*"), "$1")
    s = s.replace(Regex("`([^`\\n]+?)`"), "$1")
    s = s.replace(Regex("(?m)^#{1,3}\\s+"), "")
    return s.trim()
}

/**
 * Transforms raw LaTeX math formulas and symbols into clean, universal Unicode characters
 * using a balanced-brace recursive parser so nested fractions, roots, superscripts,
 * subscripts, geometry symbols, and calculus expressions render accurately without
 * leaking raw LaTeX backslashes or corrupting Turkish text.
 */
fun cleanAndFormatMath(raw: String): String {
    if (raw.isEmpty()) return ""

    var s = raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(ACTION_BLOCK_REGEX, "")
        .replace(ACTION_TAG_REGEX, "")
        // Preserve explicitly escaped currency dollar signs (\$)
        .replace("\\$", ESCAPED_DOLLAR_SENTINEL)

    // Convert LaTeX cases & matrix environments before general command expansion
    s = CASES_ENV_REGEX.replace(s) { match ->
        val rows = match.groupValues[1]
            .split("\\\\")
            .map { row -> row.replace("&", ", ").trim() }
            .filter { it.isNotEmpty() }
        rows.joinToString("\n") { "{ $it" }
    }
    s = MATRIX_ENV_REGEX.replace(s) { match ->
        val kind = match.groupValues[1]
        val open = if (kind == "p") "(" else "["
        val close = if (kind == "p") ")" else "]"
        val rows = match.groupValues[2]
            .split("\\\\")
            .map { row -> row.split("&").joinToString(", ") { it.trim() }.trim() }
            .filter { it.isNotEmpty() }
        "$open${rows.joinToString(" ; ")}$close"
    }

    // Strip aligned/equation environment wrappers and clean alignment tabs
    val hadAlignedEnv = ENV_BEGIN_END_REGEX.containsMatchIn(s)
    s = s.replace(ENV_BEGIN_END_REGEX, "\n")
    if (hadAlignedEnv) {
        s = s.replace(" & ", " ").replace("&=", "=").replace("&", " ")
    }

    // Strip display and inline LaTeX delimiters
    s = s.replace("\\[", "")
        .replace("\\]", "")
        .replace("\\(", "")
        .replace("\\)", "")
        .replace("$$", "")

    // Strip paired inline $...$ delimiters while preserving unpaired currency like "$50 ile $100"
    s = stripPairedInlineDollars(s)

    // Recursively expand balanced-brace LaTeX commands, symbols, superscripts, and subscripts
    s = processLatexExpression(s)

    // Clean any residual LaTeX line breaks (\\) inside formulas
    s = s.replace("\\\\", "\n")

    // Collapse accidental quadruple asterisks when bold wraps \boxed or \mathbf
    s = s.replace("****", "**")

    // Normalize excessive spaces per line while preserving indentation/newlines
    s = s.split("\n").joinToString("\n") { line ->
        line.replace(Regex("[ \\t]{2,}"), " ").trimEnd()
    }
    s = s.replace(MULTI_BLANK_LINE_REGEX, "\n\n")

    // Restore explicitly escaped currency dollar signs
    s = s.replace(ESCAPED_DOLLAR_SENTINEL, "$")

    return s.trim()
}

private fun stripPairedInlineDollars(input: String): String {
    if (!input.contains('$')) return input
    val out = StringBuilder(input.length)
    var i = 0
    val n = input.length

    while (i < n) {
        val ch = input[i]
        if (ch != '$') {
            out.append(ch)
            i++
            continue
        }

        // Standard Pandoc/KaTeX rule: opening $ cannot be immediately followed by whitespace
        if (i + 1 >= n || input[i + 1].isWhitespace()) {
            out.append('$')
            i++
            continue
        }

        // Search for matching closing $ on the same paragraph (no blank line crossing)
        var closeIdx = -1
        var j = i + 1
        while (j < n) {
            if (input[j] == '\n' && j + 1 < n && input[j + 1] == '\n') break
            if (input[j] == '$') {
                // Closing $ cannot be preceded by whitespace
                if (!input[j - 1].isWhitespace()) {
                    closeIdx = j
                    break
                }
            }
            j++
        }

        if (closeIdx > i + 1) {
            out.append(input.substring(i + 1, closeIdx))
            i = closeIdx + 1
        } else {
            // Unclosed $ — strip only if immediately followed by a LaTeX command/streaming math token
            if (i + 1 < n && input[i + 1] == '\\') {
                i++
            } else {
                out.append('$')
                i++
            }
        }
    }
    return out.toString()
}

private fun processLatexExpression(input: String): String {
    val sb = StringBuilder(input.length)
    var i = 0
    val n = input.length

    while (i < n) {
        val ch = input[i]
        when {
            ch == '\\' -> {
                if (i + 1 >= n) {
                    i++
                    continue
                }
                val next = input[i + 1]
                when {
                    next == '\\' -> {
                        sb.append('\n')
                        i += 2
                    }

                    next.isLetter() -> {
                        var end = i + 1
                        while (end < n && input[end].isLetter()) end++
                        val cmd = input.substring(i + 1, end)
                        i = handleLatexCommand(cmd, input, end, sb)
                    }

                    else -> {
                        when (next) {
                            '{' -> sb.append('{')
                            '}' -> sb.append('}')
                            '%' -> sb.append('%')
                            '_' -> sb.append('_')
                            '&' -> sb.append('&')
                            '#' -> sb.append('#')
                            '|' -> sb.append('‖')
                            ',', ';', ':', ' ' -> sb.append(' ')
                            '!' -> Unit
                            else -> sb.append(next)
                        }
                        i += 2
                    }
                }
            }

            ch == '^' -> {
                i = handleSuperscript(input, i + 1, sb)
            }

            ch == '_' -> {
                i = handleSubscript(input, i + 1, sb)
            }

            else -> {
                sb.append(ch)
                i++
            }
        }
    }

    return sb.toString()
}

private fun handleLatexCommand(
    cmd: String,
    input: String,
    afterCmd: Int,
    out: StringBuilder
): Int {
    val n = input.length

    when (cmd) {
        "frac", "dfrac", "tfrac", "cfrac" -> {
            val arg1 = readLatexArg(input, afterCmd) ?: return afterCmd
            val arg2 = readLatexArg(input, arg1.second) ?: return arg1.second
            val num = processLatexExpression(arg1.first).trim()
            val den = processLatexExpression(arg2.first).trim()
            out.append(formatFraction(num, den))
            return arg2.second
        }

        "sqrt" -> {
            val optIndex = readBracketGroup(input, afterCmd)
            val afterOpt = optIndex?.second ?: afterCmd
            val arg = readLatexArg(input, afterOpt) ?: return afterOpt
            val inner = processLatexExpression(arg.first).trim()
            val rootPrefix = if (optIndex != null) {
                val deg = processLatexExpression(optIndex.first).trim()
                "${formatSuperText(deg)}√"
            } else {
                "√"
            }
            if (isSimpleRootAtom(inner)) {
                out.append(rootPrefix).append(inner)
            } else {
                out.append(rootPrefix).append('(').append(inner).append(')')
            }
            return arg.second
        }

        "binom", "dbinom", "tbinom" -> {
            val arg1 = readLatexArg(input, afterCmd) ?: return afterCmd
            val arg2 = readLatexArg(input, arg1.second) ?: return arg1.second
            val top = processLatexExpression(arg1.first).trim()
            val bot = processLatexExpression(arg2.first).trim()
            out.append("C(").append(top).append(", ").append(bot).append(')')
            return arg2.second
        }

        "boxed" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            out.append("**").append(inner).append("**")
            return arg.second
        }

        "overline", "bar" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            if (inner.length == 1) {
                out.append(inner).append('\u0304')
            } else {
                inner.forEach { c ->
                    out.append(c)
                    if (!c.isWhitespace()) out.append('\u0305')
                }
            }
            return arg.second
        }

        "vec", "overrightarrow" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            out.append(inner).append('\u20D7')
            return arg.second
        }

        "widehat", "hat" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            if (inner.length >= 2 && inner.all { it.isUpperCase() }) {
                out.append('∠').append(inner)
            } else if (inner.length == 1) {
                out.append(inner).append('\u0302')
            } else {
                out.append(inner)
            }
            return arg.second
        }

        "tilde", "widetilde" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            out.append(inner).append('\u0303')
            return arg.second
        }

        "dot" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            out.append(inner).append('\u0307')
            return arg.second
        }

        "ddot" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            out.append(inner).append('\u0308')
            return arg.second
        }

        "mathbb" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            val mapped = inner.map { c ->
                when (c) {
                    'R' -> "ℝ"
                    'N' -> "ℕ"
                    'Z' -> "ℤ"
                    'Q' -> "ℚ"
                    'C' -> "ℂ"
                    'P' -> "ℙ"
                    'E' -> "𝔼"
                    else -> c.toString()
                }
            }.joinToString("")
            out.append(mapped)
            return arg.second
        }

        "mathbf", "textbf", "boldsymbol", "bm" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            val inner = processLatexExpression(arg.first).trim()
            out.append("**").append(inner).append("**")
            return arg.second
        }

        "text", "textrm", "mathrm", "mathit", "mathsf", "mathtt", "mathcal",
        "mathfrak", "operatorname", "textit", "emph", "underline", "cancel" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            out.append(processLatexExpression(arg.first))
            return arg.second
        }

        "textcolor" -> {
            val colorArg = readLatexArg(input, afterCmd) ?: return afterCmd
            val contentArg = readLatexArg(input, colorArg.second) ?: return colorArg.second
            out.append(processLatexExpression(contentArg.first))
            return contentArg.second
        }

        "color" -> {
            val colorArg = readLatexArg(input, afterCmd) ?: return afterCmd
            return colorArg.second
        }

        "tag" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            out.append(" (").append(processLatexExpression(arg.first).trim()).append(')')
            return arg.second
        }

        "hspace", "vspace", "phantom", "hphantom", "vphantom", "label" -> {
            val arg = readLatexArg(input, afterCmd) ?: return afterCmd
            return arg.second
        }

        "left", "right", "middle" -> {
            var idx = afterCmd
            while (idx < n && input[idx] == ' ') idx++
            if (idx < n) {
                when {
                    input[idx] == '.' -> return idx + 1
                    input[idx] == '\\' && idx + 1 < n && input[idx + 1] in charArrayOf('{', '}', '|') -> {
                        val sym = input[idx + 1]
                        out.append(if (sym == '|') '‖' else sym)
                        return idx + 2
                    }
                    input[idx] in charArrayOf('(', ')', '[', ']', '|', '/', '<', '>') -> {
                        out.append(input[idx])
                        return idx + 1
                    }
                }
            }
            return afterCmd
        }

        "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
        "limits", "nolimits", "notag", "nonumber", "centering", "noindent",
        "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr",
        "biggl", "biggr", "Biggl", "Biggr" -> {
            return afterCmd
        }

        "newline" -> {
            out.append('\n')
            return afterCmd
        }
    }

    LATEX_SYMBOL_MAP[cmd]?.let { sym ->
        out.append(sym)
        if (cmd in GEOMETRY_PREFIX_SYMBOLS &&
            afterCmd + 1 < n &&
            input[afterCmd] == ' ' &&
            input[afterCmd + 1].isLetter()
        ) {
            return afterCmd + 1
        }
        return afterCmd
    }

    if (cmd in LATEX_FUNCTION_NAMES) {
        out.append(cmd)
        return afterCmd
    }

    // Fallback: emit bare command name cleanly so no raw backslash leaks into user text
    out.append(cmd)
    return afterCmd
}

private fun handleSuperscript(input: String, startIdx: Int, out: StringBuilder): Int {
    val n = input.length
    if (startIdx >= n) {
        out.append('^')
        return startIdx
    }

    // Braced superscript: ^{...}
    val braced = readBracedGroup(input, startIdx)
    if (braced != null) {
        val rawInner = braced.first.trim()
        if (rawInner == "\\circ" || rawInner == "°") {
            out.append('°')
            return braced.second
        }
        if (rawInner == "\\prime" || rawInner == "'") {
            out.append('′')
            return braced.second
        }
        if (rawInner == "\\prime\\prime" || rawInner == "''") {
            out.append('″')
            return braced.second
        }
        val processed = processLatexExpression(rawInner).trim()
        out.append(formatSuperText(processed))
        return braced.second
    }

    // Shorthand ^\circ or ^\prime
    if (input.startsWith("\\circ", startIdx)) {
        out.append('°')
        return startIdx + 5
    }
    if (input.startsWith("\\prime", startIdx)) {
        out.append('′')
        return startIdx + 6
    }

    // Shorthand ^(-1) or ^-1 / ^-2
    if (input.startsWith("(-1)", startIdx)) {
        out.append("⁻¹")
        return startIdx + 4
    }
    if (input[startIdx] == '-' && startIdx + 1 < n && input[startIdx + 1].isDigit()) {
        var end = startIdx + 1
        while (end < n && input[end].isDigit()) end++
        out.append(formatSuperText(input.substring(startIdx, end)))
        return end
    }

    // Single unbraced character superscript: ^2, ^3, ^n, ^x, etc.
    val single = input[startIdx]
    val mapped = SUPER_CHAR_MAP[single]
    if (mapped != null) {
        out.append(mapped)
        return startIdx + 1
    }

    out.append('^')
    return startIdx
}

private fun handleSubscript(input: String, startIdx: Int, out: StringBuilder): Int {
    val n = input.length
    if (startIdx >= n) {
        out.append('_')
        return startIdx
    }

    // Braced subscript: _{...}
    val braced = readBracedGroup(input, startIdx)
    if (braced != null) {
        val processed = processLatexExpression(braced.first).trim()
        out.append(formatSubText(processed))
        return braced.second
    }

    // Single unbraced digit subscript: x_0, a_1, d_2 (leave snake_case words untouched!)
    val single = input[startIdx]
    val afterNextIsIdent = startIdx + 1 < n && (input[startIdx + 1].isLetterOrDigit() || input[startIdx + 1] == '_')
    if (single.isDigit() && !afterNextIsIdent) {
        out.append(SUB_CHAR_MAP[single] ?: single)
        return startIdx + 1
    }
    if (single in charArrayOf('n', 'k', 'i', 'j', 'x') && !afterNextIsIdent) {
        val prevIsMathIdent = out.isNotEmpty() && (out.last().isLetter() || out.last() == ')')
        if (prevIsMathIdent && (out.length == 1 || !out[out.length - 2].isLetter())) {
            val mapped = SUB_CHAR_MAP[single]
            if (mapped != null) {
                out.append(mapped)
                return startIdx + 1
            }
        }
    }

    out.append('_')
    return startIdx
}

private fun readBracedGroup(input: String, startIndex: Int): Pair<String, Int>? {
    var i = startIndex
    val n = input.length
    while (i < n && input[i] == ' ') i++
    if (i >= n || input[i] != '{') return null

    var depth = 0
    val contentStart = i + 1
    var cursor = i
    while (cursor < n) {
        val c = input[cursor]
        if (c == '\\' && cursor + 1 < n) {
            cursor += 2
            continue
        }
        if (c == '{') {
            depth++
        } else if (c == '}') {
            depth--
            if (depth == 0) {
                return input.substring(contentStart, cursor) to (cursor + 1)
            }
        }
        cursor++
    }
    // Unclosed brace (e.g. mid-stream) — gracefully return remainder
    return input.substring(contentStart) to n
}

private fun readBracketGroup(input: String, startIndex: Int): Pair<String, Int>? {
    var i = startIndex
    val n = input.length
    while (i < n && input[i] == ' ') i++
    if (i >= n || input[i] != '[') return null

    var depth = 0
    val contentStart = i + 1
    var cursor = i
    while (cursor < n) {
        val c = input[cursor]
        if (c == '[') {
            depth++
        } else if (c == ']') {
            depth--
            if (depth == 0) {
                return input.substring(contentStart, cursor) to (cursor + 1)
            }
        }
        cursor++
    }
    return null
}

private fun readLatexArg(input: String, startIndex: Int): Pair<String, Int>? {
    var i = startIndex
    val n = input.length
    while (i < n && input[i] == ' ') i++
    if (i >= n) return null

    if (input[i] == '{') {
        return readBracedGroup(input, i)
    }
    if (input[i] == '\\') {
        var end = i + 1
        while (end < n && input[end].isLetter()) end++
        if (end == i + 1 && end < n) end++
        return input.substring(i, end) to end
    }
    return input[i].toString() to (i + 1)
}

private fun formatFraction(num: String, den: String): String {
    if (num.isEmpty() && den.isEmpty()) return ""
    val numNeedsParens = needsParensForFraction(num, allowLeadingMinus = true)
    val denNeedsParens = needsParensForFraction(den, allowLeadingMinus = false)
    val nStr = if (numNeedsParens) "($num)" else num
    val dStr = if (denNeedsParens) "($den)" else den
    return if (!numNeedsParens && !denNeedsParens && num.length <= 4 && den.length <= 4) {
        "$nStr/$dStr"
    } else {
        "$nStr / $dStr"
    }
}

private fun needsParensForFraction(expr: String, allowLeadingMinus: Boolean): Boolean {
    if (expr.isEmpty()) return false
    if (isWrappedInSingleOuterParens(expr)) return false

    var parenDepth = 0
    for (idx in expr.indices) {
        val c = expr[idx]
        when (c) {
            '(', '[', '{' -> parenDepth++
            ')', ']', '}' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
            else -> if (parenDepth == 0) {
                if (c.isWhitespace()) return true
                if (c in charArrayOf('+', '±', '∓', '=', '/', '÷', '×', '·')) return true
                if (c == '-' && (idx > 0 || !allowLeadingMinus)) return true
            }
        }
    }
    return false
}

private fun isWrappedInSingleOuterParens(expr: String): Boolean {
    if (expr.length < 2 || expr.first() != '(' || expr.last() != ')') return false
    var depth = 0
    for (idx in expr.indices) {
        when (expr[idx]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0 && idx < expr.lastIndex) return false
            }
        }
    }
    return depth == 0
}

private fun isSimpleRootAtom(expr: String): Boolean {
    if (expr.isEmpty()) return true
    if (expr.all { it.isDigit() }) return true
    if (expr.length == 1 && expr[0].isLetter()) return true
    return false
}

private fun formatSuperText(str: String): String {
    if (str.isEmpty()) return ""
    if (str.all { it == ' ' || SUPER_CHAR_MAP.containsKey(it) }) {
        return buildString(str.length) {
            str.forEach { ch ->
                append(if (ch == ' ') ' ' else SUPER_CHAR_MAP.getValue(ch))
            }
        }
    }
    return if (str.length == 1) "^$str" else "^($str)"
}

private fun formatSubText(str: String): String {
    if (str.isEmpty()) return ""
    if (str.all { it == ' ' || SUB_CHAR_MAP.containsKey(it) }) {
        return buildString(str.length) {
            str.forEach { ch ->
                append(if (ch == ' ') ' ' else SUB_CHAR_MAP.getValue(ch))
            }
        }
    }
    return if (str.length == 1) "_$str" else "_($str)"
}

private val SUPER_CHAR_MAP = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'k' to 'ᵏ', 't' to 'ᵗ'
)

private val SUB_CHAR_MAP = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
    'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
    'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
    'v' to 'ᵥ', 'x' to 'ₓ'
)

private val GEOMETRY_PREFIX_SYMBOLS = setOf(
    "angle", "measuredangle", "triangle", "bigtriangleup"
)

private val LATEX_FUNCTION_NAMES = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc",
    "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
    "log", "ln", "lg", "exp", "lim", "sup", "inf",
    "max", "min", "det", "gcd", "lcm", "mod", "bmod", "pmod",
    "arg", "deg", "dim", "ker", "hom", "Pr", "sgn", "rank"
)

private val LATEX_SYMBOL_MAP = mapOf(
    // Operators & dots
    "times" to "×",
    "cdot" to "·",
    "cdots" to "⋯",
    "ldots" to "…",
    "dots" to "…",
    "vdots" to "⋮",
    "ddots" to "⋱",
    "div" to "÷",
    "pm" to "±",
    "mp" to "∓",
    "ast" to "*",
    "star" to "★",
    "circ" to "∘",
    "bullet" to "•",
    "oplus" to "⊕",
    "otimes" to "⊗",
    // Relations
    "approx" to "≈",
    "equiv" to "≡",
    "neq" to "≠",
    "ne" to "≠",
    "leq" to "≤",
    "le" to "≤",
    "leqslant" to "≤",
    "geq" to "≥",
    "ge" to "≥",
    "geqslant" to "≥",
    "ll" to "≪",
    "gg" to "≫",
    "propto" to "∝",
    "simeq" to "≃",
    "sim" to "∼",
    "cong" to "≅",
    "doteq" to "≐",
    // Calculus & big operators
    "infty" to "∞",
    "partial" to "∂",
    "nabla" to "∇",
    "int" to "∫",
    "iint" to "∬",
    "iiint" to "∭",
    "oint" to "∮",
    "sum" to "∑",
    "prod" to "∏",
    // Arrows
    "rightarrow" to "→",
    "to" to "→",
    "longrightarrow" to "→",
    "leftarrow" to "←",
    "longleftarrow" to "←",
    "leftrightarrow" to "↔",
    "longleftrightarrow" to "↔",
    "Rightarrow" to "⇒",
    "implies" to "⇒",
    "Leftarrow" to "⇐",
    "Leftrightarrow" to "⇔",
    "iff" to "⇔",
    "mapsto" to "↦",
    "rightleftharpoons" to "⇌",
    "uparrow" to "↑",
    "downarrow" to "↓",
    // Geometry & proof
    "degree" to "°",
    "prime" to "′",
    "angle" to "∠",
    "measuredangle" to "∡",
    "triangle" to "△",
    "bigtriangleup" to "△",
    "perp" to "⊥",
    "parallel" to "∥",
    "square" to "□",
    "blacksquare" to "■",
    "therefore" to "∴",
    "because" to "∵",
    // Sets & logic
    "in" to "∈",
    "notin" to "∉",
    "ni" to "∋",
    "subset" to "⊂",
    "subseteq" to "⊆",
    "supset" to "⊃",
    "supseteq" to "⊇",
    "cup" to "∪",
    "cap" to "∩",
    "setminus" to "∖",
    "emptyset" to "∅",
    "varnothing" to "∅",
    "forall" to "∀",
    "exists" to "∃",
    "nexists" to "∄",
    "neg" to "¬",
    "lnot" to "¬",
    "land" to "∧",
    "wedge" to "∧",
    "lor" to "∨",
    "vee" to "∨",
    "top" to "⊤",
    "bot" to "⊥",
    // Greek lowercase
    "alpha" to "α",
    "beta" to "β",
    "gamma" to "γ",
    "delta" to "δ",
    "epsilon" to "ε",
    "varepsilon" to "ε",
    "zeta" to "ζ",
    "eta" to "η",
    "theta" to "θ",
    "vartheta" to "ϑ",
    "iota" to "ι",
    "kappa" to "κ",
    "lambda" to "λ",
    "mu" to "μ",
    "nu" to "ν",
    "xi" to "ξ",
    "pi" to "π",
    "varpi" to "ϖ",
    "rho" to "ρ",
    "varrho" to "ϱ",
    "sigma" to "σ",
    "varsigma" to "ς",
    "tau" to "τ",
    "upsilon" to "υ",
    "phi" to "φ",
    "varphi" to "φ",
    "chi" to "χ",
    "psi" to "ψ",
    "omega" to "ω",
    // Greek uppercase
    "Gamma" to "Γ",
    "Delta" to "Δ",
    "Theta" to "Θ",
    "Lambda" to "Λ",
    "Xi" to "Ξ",
    "Pi" to "Π",
    "Sigma" to "Σ",
    "Upsilon" to "Υ",
    "Phi" to "Φ",
    "Psi" to "Ψ",
    "Omega" to "Ω",
    // Spacing
    "quad" to "  ",
    "qquad" to "   ",
    "enspace" to " ",
    "thinspace" to " ",
    "space" to " "
)
