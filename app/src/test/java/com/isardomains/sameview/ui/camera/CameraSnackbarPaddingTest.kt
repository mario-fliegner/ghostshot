package com.isardomains.sameview.ui.camera

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraSnackbarPaddingTest {

    @Test
    fun landscape_withOverlay_paddingClearsSlider() {
        assertEquals(170.dp, cameraSnackbarBottomPadding(isLandscape = true, hasOverlay = true))
    }

    @Test
    fun landscape_withoutOverlay_paddingClearsButtonRow() {
        assertEquals(122.dp, cameraSnackbarBottomPadding(isLandscape = true, hasOverlay = false))
    }

    @Test
    fun portrait_withOverlay_paddingClearsSlider() {
        assertEquals(192.dp, cameraSnackbarBottomPadding(isLandscape = false, hasOverlay = true))
    }

    @Test
    fun portrait_withoutOverlay_paddingClearsButtonRow() {
        assertEquals(136.dp, cameraSnackbarBottomPadding(isLandscape = false, hasOverlay = false))
    }
}
