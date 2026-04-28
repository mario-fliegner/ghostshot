package com.isardomains.ghostshot.ui.camera

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraSnackbarPaddingTest {

    @Test
    fun landscape_paddingIsTheSameRegardlessOfOverlay() {
        assertEquals(
            cameraSnackbarBottomPadding(isLandscape = true, hasOverlay = false),
            cameraSnackbarBottomPadding(isLandscape = true, hasOverlay = true)
        )
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
