// path: app/src/main/java/com/isardomains/sameview/ui/camera/MetadataTextSanitizer.kt
package com.isardomains.sameview.ui.camera

/**
 * Normalizes user-authored metadata text fields on save.
 *
 * Preserves international characters, emojis, and normal punctuation.
 * Removes invisible / control characters that cause rendering artefacts
 * on Canvas and in video exports.
 */
internal object MetadataTextSanitizer {

    // U+200B ZERO WIDTH SPACE, U+200C ZERO WIDTH NON-JOINER, U+200D ZERO WIDTH JOINER,
    // U+FEFF ZERO WIDTH NO-BREAK SPACE (BOM), U+2060 WORD JOINER
    private val ZERO_WIDTH_CHARS: Set<Char> = setOf(
        Char(0x200B),
        Char(0x200C),
        Char(0x200D),
        Char(0xFEFF),
        Char(0x2060)
    )

    // U+202A–U+202E: LEFT-TO-RIGHT EMBEDDING, RIGHT-TO-LEFT EMBEDDING,
    //                POP DIRECTIONAL FORMATTING, LTR/RTL OVERRIDE
    // U+2066–U+2069: LTR/RTL/FIRST STRONG ISOLATE, POP DIRECTIONAL ISOLATE
    private val BIDI_OVERRIDE_CHARS: Set<Char> =
        (0x202A..0x202E).mapTo(HashSet()) { Char(it) }
            .also { set -> (0x2066..0x2069).forEach { set.add(Char(it)) } }

    private fun String.removeAll(chars: Set<Char>): String =
        if (chars.none { it in this }) this else filter { it !in chars }

    /**
     * Sanitizes a single-line metadata field (title, display name, city, country).
     *
     * 1. trim()
     * 2. Replace line breaks (\r\n, \r, \n) with a single space
     * 3. Replace tabs (\t) with a single space
     * 4. Remove Zero Width Characters
     * 5. Remove Bidi / Directional Override Characters
     * 6. trim() again
     *
     * Returns null when the result is empty (field absent).
     */
    fun sanitizeSingleLine(value: String): String? = value
        .trim()
        .replace("\r\n", " ")
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace('\t', ' ')
        .removeAll(ZERO_WIDTH_CHARS)
        .removeAll(BIDI_OVERRIDE_CHARS)
        .trim()
        .ifEmpty { null }

    /**
     * Sanitizes a multi-line metadata field (description). Line breaks are preserved.
     *
     * 1. trim()
     * 2. Replace tabs (\t) with a single space
     * 3. Remove Zero Width Characters
     * 4. Remove Bidi / Directional Override Characters
     * 5. trim() again
     *
     * Returns null when the result is empty (field absent).
     */
    fun sanitizeMultiLine(value: String): String? = value
        .trim()
        .replace('\t', ' ')
        .removeAll(ZERO_WIDTH_CHARS)
        .removeAll(BIDI_OVERRIDE_CHARS)
        .trim()
        .ifEmpty { null }
}
