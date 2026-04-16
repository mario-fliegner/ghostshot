package com.isardomains.ghostshot.ui.camera

enum class ReferenceImageDisplayMode {
    COMPARE_WITH_PREVIEW,
    SHOW_FULL_IMAGE,
}

data class ReferenceImageDisplayRecommendation(
    val cropLoss: Float,
    val hasStrongMismatch: Boolean,
    val startMode: ReferenceImageDisplayMode
)

object ReferenceImageMismatchHeuristic {
    private const val STRONG_MISMATCH_CROP_LOSS = 0.25f

    fun evaluate(
        orientedImageWidth: Int,
        orientedImageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): ReferenceImageDisplayRecommendation {
        val cropLoss = calculateCropLoss(
            orientedImageWidth = orientedImageWidth,
            orientedImageHeight = orientedImageHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
        val hasStrongMismatch = cropLoss >= STRONG_MISMATCH_CROP_LOSS
        return ReferenceImageDisplayRecommendation(
            cropLoss = cropLoss,
            hasStrongMismatch = hasStrongMismatch,
            startMode = if (hasStrongMismatch) {
                ReferenceImageDisplayMode.SHOW_FULL_IMAGE
            } else {
                ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
            }
        )
    }

    fun calculateCropLoss(
        orientedImageWidth: Int,
        orientedImageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): Float {
        if (
            orientedImageWidth <= 0 ||
            orientedImageHeight <= 0 ||
            viewportWidth <= 0 ||
            viewportHeight <= 0
        ) {
            return 0f
        }

        val imageRatio = orientedImageWidth.toFloat() / orientedImageHeight.toFloat()
        val viewportRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
        val visibleFraction = minOf(imageRatio, viewportRatio) / maxOf(imageRatio, viewportRatio)
        return 1f - visibleFraction
    }
}
