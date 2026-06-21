// path: app/src/main/java/com/isardomains/sameview/image/ShareCaptionData.kt
package com.isardomains.sameview.image

/**
 * Pre-computed text lines for the caption area below the comparison image.
 *
 * All fields are nullable. A null field means that line is not rendered.
 * When all three fields are null (or blank), pass [ShareRenderConfig.captionData] as
 * null to skip the caption area entirely.
 */
data class ShareCaptionData(
    /** User-authored session title. Null when toggle is off or title is absent/blank. */
    val titleLine: String?,
    /** Formatted date pair, e.g. "2008 → 2026". Null when toggle is off or Level 5. */
    val dateLine: String?,
    /** Formatted location string. Null when toggle is off or no location fields are set. */
    val locationLine: String?
) {
    /** True when at least one line produces visible content. */
    val hasContent: Boolean
        get() = !titleLine.isNullOrBlank() ||
                !dateLine.isNullOrBlank() ||
                !locationLine.isNullOrBlank()

    /** Number of non-blank lines (0–3). */
    val lineCount: Int
        get() = listOf(titleLine, dateLine, locationLine).count { !it.isNullOrBlank() }
}
