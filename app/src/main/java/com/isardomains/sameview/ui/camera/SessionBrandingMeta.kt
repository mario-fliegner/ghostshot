// path: app/src/main/java/com/isardomains/sameview/ui/camera/SessionBrandingMeta.kt
package com.isardomains.sameview.ui.camera

/**
 * Lightweight companion that stores global-branding provenance alongside
 * filesDir/branding/handle.png.
 *
 * This is intentionally separate from [SessionBranding] so that the global-branding
 * storage layer (Block 2) does not depend on the full session metadata model.
 *
 * [type] is "image" or "builtin". [builtinId] identifies the built-in symbol when applicable.
 * Both values are written into new sessions when global branding is auto-copied at creation
 * time (Block 3).
 */
data class SessionBrandingMeta(
    val type: String,
    val builtinId: String?
)
