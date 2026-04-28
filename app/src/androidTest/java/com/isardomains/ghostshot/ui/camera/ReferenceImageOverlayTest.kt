package com.isardomains.ghostshot.ui.camera

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceImageOverlayTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    /**
     * Regression for the EXIF-mismatch rendering bug:
     * Landscape-oriented metadata (orientedWidth > orientedHeight) in a portrait viewport
     * must not produce an image box wider than the viewport.
     *
     * Old code used requiredSize(displayedWidth, displayedHeight) + FillBounds.
     * With landscape metadata (100×60) on a portrait viewport, displayedWidth would be
     * ~3–4× the viewport width. getUnclippedBoundsInRoot() reports the pre-clip size,
     * making the regression immediately detectable.
     *
     * New code uses fillMaxSize() + Crop, so the image bounds match the viewport exactly.
     */
    @Test
    fun overlayWithLandscapeMetadata_imageWidthDoesNotExceedViewport() {
        setOverlayContent(
            metadata = ReferenceImageMetadata(
                rawWidth = 100,
                rawHeight = 60,
                orientedWidth = 100,
                orientedHeight = 60,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        )

        val viewportBounds = composeRule.onNodeWithTag("overlay_viewport").getUnclippedBoundsInRoot()
        val imageBounds = composeRule.onNodeWithTag("compare_reference_image").getUnclippedBoundsInRoot()

        val viewportWidth = viewportBounds.right - viewportBounds.left
        val imageWidth = imageBounds.right - imageBounds.left

        assert(imageWidth <= viewportWidth + 2.dp) {
            "Overlay image width ($imageWidth) must not exceed viewport width ($viewportWidth). " +
                "Regression to the old requiredSize/FillBounds behavior would cause the image " +
                "to be ~${(viewportWidth.value * 3).toInt()}dp wide here."
        }
    }

    /**
     * Portrait metadata (orientedWidth < orientedHeight) must also stay within the viewport.
     * Old code: requiredSize would produce a box slightly wider than the viewport due to fill-scale math.
     * New code: fillMaxSize() always matches the viewport exactly.
     */
    @Test
    fun overlayWithPortraitMetadata_imageWidthDoesNotExceedViewport() {
        setOverlayContent(
            metadata = ReferenceImageMetadata(
                rawWidth = 60,
                rawHeight = 100,
                orientedWidth = 60,
                orientedHeight = 100,
                exifOrientation = null
            ),
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        )

        val viewportBounds = composeRule.onNodeWithTag("overlay_viewport").getUnclippedBoundsInRoot()
        val imageBounds = composeRule.onNodeWithTag("compare_reference_image").getUnclippedBoundsInRoot()

        val viewportWidth = viewportBounds.right - viewportBounds.left
        val imageWidth = imageBounds.right - imageBounds.left

        assert(imageWidth <= viewportWidth + 2.dp) {
            "Overlay image width ($imageWidth) must not exceed viewport width ($viewportWidth)."
        }
    }

    /**
     * When metadata is null, the fallback AsyncImage (fillMaxSize + Crop) must be visible.
     * Ensures the null-metadata code path remains functional after any future refactoring.
     */
    @Test
    fun overlayWithNullMetadata_fallbackImageIsDisplayed() {
        setOverlayContent(
            metadata = null,
            displayMode = ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW
        )

        val overlayDescription = context.getString(R.string.overlay_content_description)
        composeRule.onNodeWithContentDescription(overlayDescription).assertIsDisplayed()
    }

    private fun setOverlayContent(
        metadata: ReferenceImageMetadata?,
        displayMode: ReferenceImageDisplayMode
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                GhostShotTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("overlay_viewport")
                    ) {
                        ReferenceImageOverlay(
                            referenceUri = Uri.parse("content://ghostshot/overlay-test"),
                            metadata = metadata,
                            displayMode = displayMode,
                            offsetX = 0f,
                            offsetY = 0f,
                            scale = 1f,
                            alpha = 0.5f,
                            onDragged = { _, _ -> },
                            onScaled = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
