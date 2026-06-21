// path: app/src/main/java/com/isardomains/sameview/image/ShareQuality.kt
package com.isardomains.sameview.image

/** Output resolution tier for the exported comparison image. */
enum class ShareQuality {
    /** Comparison area longest edge capped at 2048 px. */
    STANDARD,
    /** Session viewport resolution; no upscaling beyond source dimensions. */
    ORIGINAL
}
