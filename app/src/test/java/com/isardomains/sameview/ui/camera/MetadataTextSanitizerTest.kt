package com.isardomains.sameview.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataTextSanitizerTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private val ZWS = Char(0x200B)    // ZERO WIDTH SPACE
    private val ZWNJ = Char(0x200C)   // ZERO WIDTH NON-JOINER
    private val ZWJ = Char(0x200D)    // ZERO WIDTH JOINER
    private val BOM = Char(0xFEFF)    // ZERO WIDTH NO-BREAK SPACE (BOM)
    private val WJ = Char(0x2060)     // WORD JOINER
    private val LTR_E = Char(0x202A)  // LEFT-TO-RIGHT EMBEDDING
    private val RLO = Char(0x202E)    // RIGHT-TO-LEFT OVERRIDE
    private val LRI = Char(0x2066)    // LEFT-TO-RIGHT ISOLATE
    private val PDI = Char(0x2069)    // POP DIRECTIONAL ISOLATE

    // ── sanitizeSingleLine — line break replacement ──────────────────────────

    @Test fun singleLine_newline_replacedWithSpace() {
        assertEquals("München Hauptstadt", MetadataTextSanitizer.sanitizeSingleLine("München\nHauptstadt"))
    }

    @Test fun singleLine_crLf_replacedWithSpace() {
        assertEquals("Test Wert", MetadataTextSanitizer.sanitizeSingleLine("Test\r\nWert"))
    }

    @Test fun singleLine_cr_replacedWithSpace() {
        assertEquals("Test Wert", MetadataTextSanitizer.sanitizeSingleLine("Test\rWert"))
    }

    @Test fun singleLine_tab_replacedWithSpace() {
        assertEquals("Test Wert", MetadataTextSanitizer.sanitizeSingleLine("Test\tWert"))
    }

    // ── sanitizeSingleLine — zero width character removal ────────────────────

    @Test fun singleLine_zeroWidthSpace_removed() {
        assertEquals("München", MetadataTextSanitizer.sanitizeSingleLine("Mün${ZWS}chen"))
    }

    @Test fun singleLine_zeroWidthNonJoiner_removed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("Te${ZWNJ}st"))
    }

    @Test fun singleLine_zeroWidthJoiner_removed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("Te${ZWJ}st"))
    }

    @Test fun singleLine_bom_removed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("Te${BOM}st"))
    }

    @Test fun singleLine_wordJoiner_removed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("Te${WJ}st"))
    }

    @Test fun singleLine_onlyZeroWidthChars_returnsNull() {
        assertNull(MetadataTextSanitizer.sanitizeSingleLine("${ZWS}${ZWNJ}"))
    }

    @Test fun singleLine_spaceAndZeroWidthChar_returnsNull() {
        assertNull(MetadataTextSanitizer.sanitizeSingleLine(" ${ZWS} "))
    }

    // ── sanitizeSingleLine — bidi override character removal ─────────────────

    @Test fun singleLine_rtlOverride_removed() {
        assertEquals("TestRevers", MetadataTextSanitizer.sanitizeSingleLine("Test${RLO}Revers"))
    }

    @Test fun singleLine_ltrEmbedding_removed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("Te${LTR_E}st"))
    }

    @Test fun singleLine_ltrIsolate_removed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("Te${LRI}st"))
    }

    @Test fun singleLine_popDirectionalIsolate_removed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("Te${PDI}st"))
    }

    // ── sanitizeSingleLine — trim behaviour ──────────────────────────────────

    @Test fun singleLine_leadingAndTrailingWhitespace_trimmed() {
        assertEquals("Test", MetadataTextSanitizer.sanitizeSingleLine("  Test  "))
    }

    @Test fun singleLine_emptyString_returnsNull() {
        assertNull(MetadataTextSanitizer.sanitizeSingleLine(""))
    }

    @Test fun singleLine_blankString_returnsNull() {
        assertNull(MetadataTextSanitizer.sanitizeSingleLine("   "))
    }

    @Test fun singleLine_onlyNewline_returnsNull() {
        assertNull(MetadataTextSanitizer.sanitizeSingleLine("\n"))
    }

    // ── sanitizeSingleLine — international chars and punctuation preserved ───

    @Test fun singleLine_umlauts_preserved() {
        assertEquals("München", MetadataTextSanitizer.sanitizeSingleLine("München"))
    }

    @Test fun singleLine_czechChars_preserved() {
        assertEquals("Český Krumlov", MetadataTextSanitizer.sanitizeSingleLine("Český Krumlov"))
    }

    @Test fun singleLine_brazilianPortuguese_preserved() {
        assertEquals("São Paulo", MetadataTextSanitizer.sanitizeSingleLine("São Paulo"))
    }

    @Test fun singleLine_emoji_preserved() {
        assertEquals("🎞 Session", MetadataTextSanitizer.sanitizeSingleLine("🎞 Session"))
    }

    @Test fun singleLine_specialPunctuation_preserved() {
        assertEquals("Proj & Co. / 2024", MetadataTextSanitizer.sanitizeSingleLine("Proj & Co. / 2024"))
    }

    @Test fun singleLine_middotAndUmlauts_preserved() {
        assertEquals("Am Schwarzsee · Kitzbühel, Österreich",
            MetadataTextSanitizer.sanitizeSingleLine("Am Schwarzsee · Kitzbühel, Österreich"))
    }

    // ── sanitizeMultiLine — line breaks preserved ─────────────────────────────

    @Test fun multiLine_newline_preserved() {
        assertEquals("Line 1\nLine 2", MetadataTextSanitizer.sanitizeMultiLine("Line 1\nLine 2"))
    }

    @Test fun multiLine_crLf_preserved() {
        assertEquals("Line 1\r\nLine 2", MetadataTextSanitizer.sanitizeMultiLine("Line 1\r\nLine 2"))
    }

    // ── sanitizeMultiLine — tab replacement ──────────────────────────────────

    @Test fun multiLine_tab_replacedWithSpace() {
        assertEquals("Line 1", MetadataTextSanitizer.sanitizeMultiLine("Line\t1"))
    }

    // ── sanitizeMultiLine — zero width character removal ─────────────────────

    @Test fun multiLine_zeroWidthChar_removed() {
        assertEquals("Text", MetadataTextSanitizer.sanitizeMultiLine("Text${ZWS}"))
    }

    @Test fun multiLine_bom_removed() {
        assertEquals("Text", MetadataTextSanitizer.sanitizeMultiLine("${BOM}Text"))
    }

    // ── sanitizeMultiLine — bidi override character removal ──────────────────

    @Test fun multiLine_bidiOverride_removed() {
        assertEquals("Text", MetadataTextSanitizer.sanitizeMultiLine("Text${RLO}"))
    }

    @Test fun multiLine_ltrIsolate_removed() {
        assertEquals("Text", MetadataTextSanitizer.sanitizeMultiLine("Text${LRI}"))
    }

    // ── sanitizeMultiLine — trim and empty handling ───────────────────────────

    @Test fun multiLine_leadingTrailingWhitespace_trimmed() {
        assertEquals("Line 1\nLine 2", MetadataTextSanitizer.sanitizeMultiLine("  Line 1\nLine 2  "))
    }

    @Test fun multiLine_emptyString_returnsNull() {
        assertNull(MetadataTextSanitizer.sanitizeMultiLine(""))
    }

    @Test fun multiLine_onlyZeroWidthChars_returnsNull() {
        assertNull(MetadataTextSanitizer.sanitizeMultiLine("${ZWS}${ZWNJ}"))
    }

    // ── sanitizeSingleLine — null input ──────────────────────────────────────

    @Test fun singleLine_nullPassedViaLet_returnsNull() {
        val input: String? = null
        assertNull(input?.let { MetadataTextSanitizer.sanitizeSingleLine(it) })
    }

    @Test fun multiLine_nullPassedViaLet_returnsNull() {
        val input: String? = null
        assertNull(input?.let { MetadataTextSanitizer.sanitizeMultiLine(it) })
    }
}
