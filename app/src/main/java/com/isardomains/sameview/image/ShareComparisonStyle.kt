// path: app/src/main/java/com/isardomains/sameview/image/ShareComparisonStyle.kt
package com.isardomains.sameview.image

/** Visual composition style for the exported comparison image. */
enum class ShareComparisonStyle {
    /** Fixed 50/50 slider split with SameView handle at the centre. */
    SLIDER,
    /** Reference image on the left half, capture on the right half. */
    SIDE_BY_SIDE
}
