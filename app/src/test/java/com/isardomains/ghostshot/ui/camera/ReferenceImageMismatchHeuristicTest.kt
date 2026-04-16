package com.isardomains.ghostshot.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceImageMismatchHeuristicTest {

    @Test
    fun identicalAspectRatio_recommendsCompareMode() {
        val result = ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = 1080,
            orientedImageHeight = 1920,
            viewportWidth = 1080,
            viewportHeight = 1920
        )

        assertEquals(0f, result.cropLoss, 0.0001f)
        assertFalse(result.hasStrongMismatch)
        assertEquals(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, result.startMode)
    }

    @Test
    fun similarPortraitAspectRatio_recommendsCompareMode() {
        val result = ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = 1000,
            orientedImageHeight = 1700,
            viewportWidth = 1080,
            viewportHeight = 1920
        )

        assertFalse(result.hasStrongMismatch)
        assertEquals(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, result.startMode)
    }

    @Test
    fun landscapeImageAgainstPortraitViewport_recommendsFullImageMode() {
        val result = ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = 1920,
            orientedImageHeight = 1080,
            viewportWidth = 1080,
            viewportHeight = 1920
        )

        assertTrue(result.hasStrongMismatch)
        assertEquals(ReferenceImageDisplayMode.SHOW_FULL_IMAGE, result.startMode)
    }

    @Test
    fun portraitImageAgainstLandscapeViewport_recommendsFullImageMode() {
        val result = ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = 1080,
            orientedImageHeight = 1920,
            viewportWidth = 1920,
            viewportHeight = 1080
        )

        assertTrue(result.hasStrongMismatch)
        assertEquals(ReferenceImageDisplayMode.SHOW_FULL_IMAGE, result.startMode)
    }

    @Test
    fun squareImageAgainstPortraitViewport_recommendsFullImageMode() {
        val result = ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = 1200,
            orientedImageHeight = 1200,
            viewportWidth = 1080,
            viewportHeight = 1920
        )

        assertTrue(result.hasStrongMismatch)
        assertEquals(ReferenceImageDisplayMode.SHOW_FULL_IMAGE, result.startMode)
    }

    @Test
    fun cropLossBoundary_belowThresholdUsesCompare_atThresholdUsesFullImage() {
        val belowThreshold = ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = 3999,
            orientedImageHeight = 3000,
            viewportWidth = 3000,
            viewportHeight = 3000
        )
        val atThreshold = ReferenceImageMismatchHeuristic.evaluate(
            orientedImageWidth = 4000,
            orientedImageHeight = 3000,
            viewportWidth = 3000,
            viewportHeight = 3000
        )

        assertEquals(0.2498f, belowThreshold.cropLoss, 0.0001f)
        assertFalse(belowThreshold.hasStrongMismatch)
        assertEquals(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW, belowThreshold.startMode)
        assertEquals(0.25f, atThreshold.cropLoss, 0.0001f)
        assertTrue(atThreshold.hasStrongMismatch)
        assertEquals(ReferenceImageDisplayMode.SHOW_FULL_IMAGE, atThreshold.startMode)
    }
}
