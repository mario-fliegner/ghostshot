// path: app/src/main/java/com/isardomains/sameview/ui/camera/SessionBranding.kt
package com.isardomains.sameview.ui.camera

/**
 * Represents the branding block in a session's metadata.json (schema v6).
 *
 * [handleFile] is the filename of the normalized 512×512 RGBA PNG inside the session directory.
 * [type] is "image" for a user-provided image or "builtin" for a built-in symbol.
 * [builtinId] identifies the built-in symbol (e.g. "heart", "fire"); null when [type] is "image".
 * [updatedAtMs] is the timestamp of the last branding update in milliseconds since Unix epoch.
 *
 * The PNG file referenced by [handleFile] is always metadata-clean (no EXIF, GPS, XMP, IPTC).
 * This data class is purely informational and is used only for reading stored branding state.
 * Absent branding block in metadata.json is represented as null at the call site.
 */
data class SessionBranding(
    val handleFile: String,
    val type: String,
    val builtinId: String?,
    val updatedAtMs: Long
)
