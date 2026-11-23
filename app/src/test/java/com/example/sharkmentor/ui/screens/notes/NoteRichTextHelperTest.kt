package com.example.sharkmentor.ui.screens.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRichTextHelperTest {

    @Test
    fun testParseTaggedTextAndToTaggedString() {
        val input = "Bugün <b>fizik</b> ve <color hex=\"#E53935\">kimya</color> çalıştım."
        val annotated = NoteRichTextHelper.parseTaggedText(input)

        assertEquals("Bugün fizik ve kimya çalıştım.", annotated.text)
        assertEquals(2, annotated.spanStyles.size)

        val boldSpan = annotated.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Bold }
        assertNotNull(boldSpan)
        assertEquals(6, boldSpan!!.start)
        assertEquals(11, boldSpan.end)

        val colorSpan = annotated.spanStyles.firstOrNull { it.item.color != Color.Unspecified }
        assertNotNull(colorSpan)
        assertEquals(15, colorSpan!!.start)
        assertEquals(20, colorSpan.end)

        val serialized = NoteRichTextHelper.toTaggedString(annotated)
        assertTrue(serialized.contains("<b>fizik</b>"))
        assertTrue(serialized.contains("kimya</color>"))
    }

    @Test
    fun testHighlightTagParsing() {
        val input = "Önemli: <mark hex=\"#FFF176\">Sınav yarın</mark> sabah."
        val annotated = NoteRichTextHelper.parseTaggedText(input)

        assertEquals("Önemli: Sınav yarın sabah.", annotated.text)
        val highlightSpan = annotated.spanStyles.firstOrNull { it.item.background != Color.Unspecified }
        assertNotNull(highlightSpan)
        assertEquals(8, highlightSpan!!.start)
        assertEquals(19, highlightSpan.end)

        val plain = NoteRichTextHelper.toPlainText(input)
        assertEquals("Önemli: Sınav yarın sabah.", plain)
    }

    @Test
    fun testToggleBoldOnSelection() {
        val original = TextFieldValue(
            annotatedString = AnnotatedString("Ders çalışıyorum"),
            selection = TextRange(5, 16) // "çalışıyorum"
        )

        val bolded = NoteRichTextHelper.toggleStyleOnSelection(original, NoteRichTextHelper.StyleType.BOLD)
        assertTrue(bolded.annotatedString.spanStyles.any { it.item.fontWeight == FontWeight.Bold })

        val unbolded = NoteRichTextHelper.toggleStyleOnSelection(bolded, NoteRichTextHelper.StyleType.BOLD)
        assertFalse(unbolded.annotatedString.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun testApplyHighlightToSelection() {
        val original = TextFieldValue(
            annotatedString = AnnotatedString("Matematik formülleri"),
            selection = TextRange(0, 9) // "Matematik"
        )

        val highlightColor = Color(0xFFFFEB3B)
        val highlighted = NoteRichTextHelper.applyHighlightToSelection(original, highlightColor)
        val span = highlighted.annotatedString.spanStyles.firstOrNull { it.item.background != Color.Unspecified }
        assertNotNull(span)
        assertEquals(0, span!!.start)
        assertEquals(9, span.end)

        val cleared = NoteRichTextHelper.applyHighlightToSelection(highlighted, null)
        assertFalse(cleared.annotatedString.spanStyles.any { it.item.background != Color.Unspecified })
    }

    @Test
    fun testToggleLinePrefix() {
        val initial = TextFieldValue(
            annotatedString = AnnotatedString("Elma\nArmut"),
            selection = TextRange(2, 2) // inside "Elma"
        )

        val bulleted = NoteRichTextHelper.toggleLinePrefix(initial, "• ")
        assertEquals("• Elma\nArmut", bulleted.text)

        val unbulleted = NoteRichTextHelper.toggleLinePrefix(bulleted, "• ")
        assertEquals("Elma\nArmut", unbulleted.text)
    }

    @Test
    fun testHandleEnterOnListItem() {
        val beforeEnter = TextFieldValue(
            annotatedString = AnnotatedString("• Birinci madde"),
            selection = TextRange(15, 15)
        )
        val afterEnter = TextFieldValue(
            annotatedString = AnnotatedString("• Birinci madde\n"),
            selection = TextRange(16, 16)
        )

        val autoBullet = NoteRichTextHelper.handleEnterOnListItem(beforeEnter, afterEnter)
        assertNotNull(autoBullet)
        assertEquals("• Birinci madde\n• ", autoBullet!!.text)
        assertEquals(18, autoBullet.selection.start)

        // Enter on empty bullet line cancels bullet
        val emptyBulletBefore = TextFieldValue(
            annotatedString = AnnotatedString("• Birinci madde\n• "),
            selection = TextRange(18, 18)
        )
        val emptyBulletAfter = TextFieldValue(
            annotatedString = AnnotatedString("• Birinci madde\n• \n"),
            selection = TextRange(19, 19)
        )
        val canceled = NoteRichTextHelper.handleEnterOnListItem(emptyBulletBefore, emptyBulletAfter)
        assertNotNull(canceled)
        assertEquals("• Birinci madde\n\n", canceled!!.text)
    }

    @Test
    fun testToggleCheckboxAtOffset() {
        val initial = TextFieldValue(AnnotatedString("[ ] Ders çalış"))
        val checked = NoteRichTextHelper.toggleCheckboxAtOffset(initial, 0)
        assertTrue(checked.text.startsWith("[✓] "))
        assertTrue(checked.annotatedString.spanStyles.any { it.item.textDecoration?.contains(TextDecoration.LineThrough) == true })

        val unchecked = NoteRichTextHelper.toggleCheckboxAtOffset(checked, 0)
        assertTrue(unchecked.text.startsWith("[ ] "))
        assertFalse(unchecked.annotatedString.spanStyles.any { it.item.textDecoration?.contains(TextDecoration.LineThrough) == true })

        // Also verify legacy unicode ballot box conversion
        val legacy = TextFieldValue(AnnotatedString("☐ Eski not"))
        val legacyChecked = NoteRichTextHelper.toggleCheckboxAtOffset(legacy, 0)
        assertTrue(legacyChecked.text.startsWith("[✓] "))
    }

    @Test
    fun testHandleEnterOnBulletPreservesExistingColorsAndAppliesActiveStyle() {
        val red = Color(0xFFE53935)
        val blue = Color(0xFF1E88E5)
        val initialSpans = listOf(
            AnnotatedString.Range(SpanStyle(color = red), 0, 15),
            AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), 2, 15)
        )
        val beforeEnter = TextFieldValue(
            annotatedString = AnnotatedString("• Kırmızı madde", initialSpans),
            selection = TextRange(15, 15)
        )
        val afterEnter = TextFieldValue(
            annotatedString = AnnotatedString("• Kırmızı madde\n"),
            selection = TextRange(16, 16)
        )

        val continued = NoteRichTextHelper.handleEnterOnListItem(
            oldValue = beforeEnter,
            newValue = afterEnter,
            isBoldActive = true,
            activeTextColor = blue
        )
        assertNotNull(continued)
        assertEquals("• Kırmızı madde\n• ", continued!!.text)
        assertEquals(18, continued.selection.start)

        // Line 1 red span must remain completely intact and never reset
        val preservedRed = continued.annotatedString.spanStyles.firstOrNull {
            it.item.color == red && it.start == 0 && it.end == 15
        }
        assertNotNull("Previous bullet's red color span must not be wiped on Enter", preservedRed)

        // New bullet prefix receives active blue color without altering line 1
        val newPrefixBlue = continued.annotatedString.spanStyles.firstOrNull {
            it.item.color == blue && it.start == 16 && it.end == 18
        }
        assertNotNull("New bullet prefix should inherit active color", newPrefixBlue)

        // Typing on the new line applies blue ONLY to newly typed characters
        val typed = NoteRichTextHelper.handleTextChange(
            oldValue = continued,
            newValue = TextFieldValue("• Kırmızı madde\n• Yeni", TextRange(22, 22)),
            isBoldActive = false,
            isItalicActive = false,
            isUnderlineActive = false,
            activeTextColor = blue,
            activeHighlightColor = null
        )
        assertTrue(typed.annotatedString.spanStyles.any { it.item.color == red && it.start == 0 && it.end == 15 })
        assertTrue(typed.annotatedString.spanStyles.any { it.item.color == blue && it.start in 16..18 && it.end == 22 })
    }

    @Test
    fun testHandleEnterOnNumberedCheckboxAndCancelPreservesSpans() {
        val green = Color(0xFF43A047)
        val purple = Color(0xFF8E24AA)

        // Numbered list Enter
        val numBefore = TextFieldValue(
            annotatedString = AnnotatedString(
                "1. İlk",
                listOf(AnnotatedString.Range(SpanStyle(color = green), 0, 6))
            ),
            selection = TextRange(6, 6)
        )
        val numAfter = TextFieldValue(AnnotatedString("1. İlk\n"), TextRange(7, 7))
        val numRes = NoteRichTextHelper.handleEnterOnListItem(numBefore, numAfter, activeTextColor = green)
        assertNotNull(numRes)
        assertEquals("1. İlk\n2. ", numRes!!.text)
        assertTrue(numRes.annotatedString.spanStyles.any { it.item.color == green && it.start == 0 && it.end == 6 })

        // Checkbox list Enter
        val cbBefore = TextFieldValue(
            annotatedString = AnnotatedString(
                "[ ] Görev",
                listOf(AnnotatedString.Range(SpanStyle(color = purple), 0, 9))
            ),
            selection = TextRange(9, 9)
        )
        val cbAfter = TextFieldValue(AnnotatedString("[ ] Görev\n"), TextRange(10, 10))
        val cbRes = NoteRichTextHelper.handleEnterOnListItem(cbBefore, cbAfter, activeTextColor = purple)
        assertNotNull(cbRes)
        assertEquals("[ ] Görev\n[ ] ", cbRes!!.text)
        assertTrue(cbRes.annotatedString.spanStyles.any { it.item.color == purple && it.start == 0 && it.end == 9 })

        // Cancel empty bullet preserves preceding colored line
        val cancelBefore = TextFieldValue(
            annotatedString = AnnotatedString(
                "• Kırmızı\n• ",
                listOf(AnnotatedString.Range(SpanStyle(color = green), 0, 9))
            ),
            selection = TextRange(12, 12)
        )
        val cancelAfter = TextFieldValue(AnnotatedString("• Kırmızı\n• \n"), TextRange(13, 13))
        val cancelRes = NoteRichTextHelper.handleEnterOnListItem(cancelBefore, cancelAfter)
        assertNotNull(cancelRes)
        assertEquals("• Kırmızı\n\n", cancelRes!!.text)
        assertTrue(cancelRes.annotatedString.spanStyles.any { it.item.color == green && it.start == 0 && it.end == 9 })
    }

    @Test
    fun testFontSizeAndFontFamilyTagRoundTrip() {
        val raw = "<font name=\"SERIF\"><size pt=\"22\">Başlık</size></font> normal"
        val parsed = NoteRichTextHelper.parseTaggedText(raw)
        assertEquals("Başlık normal", parsed.text)
        assertEquals("Başlık normal", NoteRichTextHelper.toPlainText(raw))

        val sizeSpan = parsed.spanStyles.firstOrNull { it.item.fontSize.value == 22f }
        assertNotNull(sizeSpan)
        assertEquals(0, sizeSpan!!.start)
        assertEquals(6, sizeSpan.end)

        val fontSpan = parsed.spanStyles.firstOrNull { it.item.fontFamily == NoteFontOption.SERIF.resolveFontFamily() }
        assertNotNull(fontSpan)
        assertEquals(0, fontSpan!!.start)
        assertEquals(6, fontSpan.end)

        val serialized = NoteRichTextHelper.toTaggedString(parsed)
        assertTrue(serialized.contains("<font name=\"SERIF\">"))
        assertTrue(serialized.contains("<size pt=\"22\">"))

        val reparsed = NoteRichTextHelper.parseTaggedText(serialized)
        assertEquals(parsed.text, reparsed.text)
        assertTrue(reparsed.spanStyles.any { it.item.fontSize.value == 22f })
        assertTrue(reparsed.spanStyles.any { it.item.fontFamily == NoteFontOption.SERIF.resolveFontFamily() })
    }

    @Test
    fun testApplyFontSizeAndFontFamilyToSelection() {
        val original = TextFieldValue(
            annotatedString = AnnotatedString("Fizik notları"),
            selection = TextRange(0, 5)
        )
        val sized = NoteRichTextHelper.applyFontSizeToSelection(original, 24f)
        val fonted = NoteRichTextHelper.applyFontFamilyToSelection(sized, NoteFontOption.MONO)

        assertTrue(fonted.annotatedString.spanStyles.any { it.start == 0 && it.end == 5 && it.item.fontSize.value == 24f })
        assertTrue(fonted.annotatedString.spanStyles.any { it.start == 0 && it.end == 5 && it.item.fontFamily == NoteFontOption.MONO.resolveFontFamily() })

        val tagged = NoteRichTextHelper.toTaggedString(fonted.annotatedString)
        assertTrue(tagged.contains("<font name=\"MONO\">"))
        assertTrue(tagged.contains("<size pt=\"24\">"))
    }
}
