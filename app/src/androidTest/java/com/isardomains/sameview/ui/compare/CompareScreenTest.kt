package com.isardomains.sameview.ui.compare

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(AndroidJUnit4::class)
class CompareScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val tempFiles = mutableListOf<File>()
    private val fakeTimestamp = 1705312200000L

    @After
    fun tearDown() {
        composeRule.mainClock.autoAdvance = true
        scenario?.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        scenario?.close()
        scenario = null
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    @Test
    fun compareScreen_rendersDistinctShellWithTitleAndBack() {
        val compareInput = createCompareInput()
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri
        )

        composeRule.onNodeWithTag("compare_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_back_button").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_shell_content").assertIsDisplayed()
    }

    @Test
    fun compareScreen_missingInputsShowsFallback() {
        setCompareContent(referenceImageUri = null, captureImageUri = null)

        composeRule.onNodeWithTag("compare_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_missing_input_fallback").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_error_missing_images))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_error_missing_images_body))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("compare_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun compareScreen_explicitBackInvokesCallback() {
        val compareInput = createCompareInput()
        var backCount = 0
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            onBack = { backCount++ }
        )

        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, backCount)
    }

    @Test
    fun compareScreen_referenceImageIsDisplayed() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_reference_image").assertIsDisplayed()
    }

    @Test
    fun compareScreen_captureImageIsDisplayed() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_capture_image").assertIsDisplayed()
    }

    @Test
    fun compareScreen_handleLabelsShowFallbackLabels() {
        // No referenceDate provided → Level 5: Reference / Current labels on handle
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        // At 50% position both labels are within viewport and must be displayed
        composeRule.onNodeWithTag("compare_handle_label_left").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_handle_label_right").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_label_reference))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_label_current))
            .assertIsDisplayed()
    }

    @Test
    fun compareScreen_imageContentDescriptionsUseLocalizedCompareLabels() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithContentDescription(context.getString(R.string.compare_label_reference))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.compare_label_capture))
            .assertIsDisplayed()
    }

    @Test
    fun originalPeek_badgeAppearsWhenOriginalReferenceExists() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        // Reference label badge is replaced by handle labels; info badge is retained
        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun originalPeek_infoBadgeDisplayedWithinViewport() {
        // Reference label badge removed; verify the info badge is displayed and positioned
        // in the top-start area of the viewport.
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        val viewportBounds = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
        val badgeBounds = composeRule.onNodeWithTag("compare_original_reference_badge").getUnclippedBoundsInRoot()
        val viewportMidX = viewportBounds.left + (viewportBounds.right - viewportBounds.left) / 2f
        val viewportMidY = viewportBounds.top + (viewportBounds.bottom - viewportBounds.top) / 2f
        // Badge must be in the left half and top half of the viewport
        assertTrue("Badge center must be left of viewport center", badgeBounds.left < viewportMidX)
        assertTrue("Badge center must be above viewport center", badgeBounds.top < viewportMidY)
    }

    @Test
    fun originalPeek_badgeDoesNotAppearWithoutSessionReferencePath() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertDoesNotExist()
    }

    @Test
    fun originalPeek_badgeDoesNotAppearWhenOriginalReferenceMissing() {
        val compareInput = createSessionCompareInput(includeOriginalReference = false)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertDoesNotExist()
    }

    @Test
    fun originalPeek_defaultShowsReferenceWithoutOriginalPeek() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_reference_image").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_original_reference_image").assertDoesNotExist()
    }

    @Test
    fun originalPeek_pressAndHoldShowsOriginalAndLabel() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_image").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_original_reference_label").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_original_reference)).assertIsDisplayed()
        assertRectEquals(
            composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot(),
            composeRule.onNodeWithTag("compare_original_reference_image").getUnclippedBoundsInRoot()
        )

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }
    }

    @Test
    fun originalPeek_labelIsPositionedAtBottomStart() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        val viewportBounds = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
        val labelBounds = composeRule.onNodeWithTag("compare_original_reference_label").getUnclippedBoundsInRoot()
        val viewportMidY = viewportBounds.top + (viewportBounds.bottom - viewportBounds.top) / 2f
        assertTrue(labelBounds.top > viewportMidY)

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }
    }

    @Test
    fun originalPeek_staysViewportSizedWhenReferenceRevealIsSmall() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(x = -120f, y = 0f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        assertRectEquals(
            composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot(),
            composeRule.onNodeWithTag("compare_original_reference_image").getUnclippedBoundsInRoot()
        )

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }
    }

    @Test
    fun originalPeek_releaseHidesOriginalPeek() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_image").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_original_reference_label").assertDoesNotExist()
    }

    @Test
    fun originalPeek_sliderRemainsAvailableAfterPeek() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_slider").assertIsDisplayed()
        val before = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(x = 60f, y = 0f))
            up()
        }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()
        assertTrue(after.left > before.left)
    }

    @Test
    fun originalPeek_cancelHidesOriginalPeek() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput {
                down(center)
                cancel()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_image").assertDoesNotExist()
    }

    @Test
    fun originalPeek_badgeHasContentDescription() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.compare_show_original_reference)
        ).assertIsDisplayed()
    }

    @Test
    fun infoBadge_remainsVisibleAfterSliderMovedFarLeft_withOriginalReference() {
        // Info badge must stay visible at fraction > 0 even with slider near left edge
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(x = -120f, y = 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun infoBadge_remainsVisibleAfterSliderMovedFarLeft() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(x = -120f, y = 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun handleLabel_leftLabelHiddenAtSliderFractionZero() {
        // At fraction ≈ 0, the left label is edge-hidden (no room to the left of handle)
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(x = -10000f, y = 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_handle_label_left").assertDoesNotExist()
    }

    @Test
    fun infoBadge_notVisibleAtSliderFractionZero() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(x = -10000f, y = 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertDoesNotExist()
    }

    @Test
    fun handleLabels_notVisibleDuringOriginalPeek() {
        // Divider is hidden during peek, so handle labels are also absent
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_handle_label_right").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_handle_label_left").assertDoesNotExist()

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }
    }

    @Test
    fun divider_notVisibleDuringOriginalPeek() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_slider").assertDoesNotExist()

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }
    }

    @Test
    fun handleLabel_rightLabelVisibleAfterPeekRelease() {
        // After releasing peek, divider reappears; right label visible at center position
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_handle_label_right").assertIsDisplayed()
    }

    @Test
    fun divider_visibleAfterPeekRelease() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_slider").assertIsDisplayed()
    }

    @Test
    fun fullscreen_handleLabels_notVisibleDuringPeek() {
        // In fullscreen, handle labels must also be absent during peek
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content")
            .performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_handle_label_right").assertDoesNotExist()

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }
    }

    @Test
    fun fullscreen_divider_notVisibleDuringPeek() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content")
            .performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_slider").assertDoesNotExist()

        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }
    }

    @Test
    fun compareScreen_bothImagesUseSameViewportSurface() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        val viewportBounds = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
        val referenceBounds = composeRule.onNodeWithTag("compare_reference_surface").getUnclippedBoundsInRoot()
        val captureBounds = composeRule.onNodeWithTag("compare_capture_surface").getUnclippedBoundsInRoot()

        assertRectEquals(viewportBounds, referenceBounds)
        assertRectEquals(viewportBounds, captureBounds)
    }

    @Test
    fun compareScreen_sliderIsVisible() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_divider_handle").assertIsDisplayed()
    }

    @Test
    fun compareScreen_sliderStartsCenteredAtFiftyPercent() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        val viewportBounds = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
        val sliderBounds = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()

        assertCenterXNear(sliderBounds, viewportBounds, 12.dp)
    }

    @Test
    fun compareScreen_horizontalDragMovesTheSplit() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        val before = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(180f, 0f))
            up()
        }
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()
        assertTrue(after.left > before.left)
    }

    @Test
    fun compareScreen_verticalDragDoesNotDestroyTheSplit() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        val before = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 180f))
            up()
        }
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()
        assertCenterXNear(after, before, 8.dp)
    }

    @Test
    fun compareScreen_recompositionKeepsSliderStateValid() {
        val compareInput = createCompareInput()
        var triggerRecompose: (() -> Unit)? = null
        setHostContent {
            var nonce by remember { mutableIntStateOf(0) }
            triggerRecompose = { nonce++ }
            CompareScreen(
                referenceImageUri = compareInput.referenceUri,
                captureImageUri = compareInput.captureUri,
                onBack = {}
            )
            nonce
        }

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(160f, 0f))
            up()
        }
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()

        composeRule.runOnIdle {
            triggerRecompose?.invoke()
        }
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()
        assertCenterXNear(after, before, 8.dp)
        composeRule.onNodeWithTag("compare_reference_image").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_capture_image").assertIsDisplayed()
    }

    @Test
    fun compareScreen_rotationDoesNotCrashAndKeepsRenderingValid() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        recreateCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_reference_image").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_capture_image").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_slider").assertIsDisplayed()
    }

    @Test
    fun compareScreen_sliderRemainsFunctionalAfterRotation() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        recreateCompareContent(compareInput.referenceUri, compareInput.captureUri)
        waitForSliderViewport()
        val before = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(140f, 0f))
            up()
        }
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()
        assertTrue(after.left > before.left)
    }

    // --- Metadata header tests (replaces old timestamp/session-title tests) ---

    @Test
    fun metadataFallback_notDisplayedWhenNoTimestampAndNoMetadata() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, timestamp = null)

        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertDoesNotExist()
    }

    @Test
    fun metadataFallback_displayedWhenTimestampProvidedAndNoOtherMetadata() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, timestamp = fakeTimestamp)

        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertIsDisplayed()
    }

    @Test
    fun metadataFallback_formattedTextMatchesCreatedPrefix() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, timestamp = fakeTimestamp)

        val expectedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(fakeTimestamp))
        // Verify the date-only format is present (no time component); exact prefix tested via tag.
        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertIsDisplayed()
        composeRule.onAllNodesWithText(expectedDate, substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun metadataHeader_aboveViewport_whenTimestampPresent() {
        val compareInput = createCompareInput()
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            timestamp = fakeTimestamp
        )

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_header").assertIsDisplayed()
    }

    @Test
    fun metadataTitle_displayedWhenSet() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = "My Shot"
        )

        composeRule.onNodeWithTag("compare_screen_metadata_title").assertIsDisplayed()
        composeRule.onNodeWithText("My Shot").assertIsDisplayed()
    }

    @Test
    fun metadataTitle_suppressesFallbackDate() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = "My Shot"
        )

        composeRule.onNodeWithTag("compare_screen_metadata_title").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertDoesNotExist()
    }

    @Test
    fun metadataTitle_notDisplayedWhenNull() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = null
        )

        composeRule.onNodeWithTag("compare_screen_metadata_title").assertDoesNotExist()
    }

    // --- Delete button tests ---

    @Test
    fun deleteButton_notDisplayedWhenOnDeleteIsNull() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onDelete = null)

        composeRule.onNodeWithTag("compare_screen_delete_button").assertDoesNotExist()
    }

    @Test
    fun deleteButton_displayedWhenOnDeleteProvided() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onDelete = {})

        composeRule.onNodeWithTag("compare_screen_delete_button").assertIsDisplayed()
    }

    @Test
    fun deleteButton_tapOpensConfirmDialog() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onDelete = {})

        composeRule.onNodeWithTag("compare_screen_delete_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.compare_screen_delete_dialog_title)
        ).assertIsDisplayed()
    }

    @Test
    fun deleteDialog_cancelClosesDialogWithoutCallback() {
        var deleteCount = 0
        setCompareContent(referenceImageUri = null, captureImageUri = null, onDelete = { deleteCount++ })

        composeRule.onNodeWithTag("compare_screen_delete_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.compare_library_delete_cancel)
        ).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.compare_screen_delete_dialog_title)
        ).assertDoesNotExist()
        assertEquals(0, deleteCount)
    }

    @Test
    fun deleteDialog_confirmCallsOnDeleteExactlyOnce() {
        var deleteCount = 0
        setCompareContent(referenceImageUri = null, captureImageUri = null, onDelete = { deleteCount++ })

        composeRule.onNodeWithTag("compare_screen_delete_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.compare_library_delete_confirm)
        ).performClick()
        composeRule.waitForIdle()

        assertEquals(1, deleteCount)
    }

    // --- Load failure ---

    @Test
    fun compareScreen_loadFailureShowsFallback() {
        setCompareContent(
            referenceImageUri = Uri.parse("content://sameview/missing-reference"),
            captureImageUri = Uri.parse("content://sameview/missing-capture")
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(context.getString(R.string.compare_error_load_failed))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("compare_load_failed_fallback").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_error_load_failed))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_error_load_failed_body))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("compare_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_back_button").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun fullscreen_isNotDefaultMode() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp
        )

        composeRule.onNodeWithTag("compare_screen_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_header").assertIsDisplayed()
    }

    @Test
    fun fullscreen_tapOnViewportTogglesIntoFullscreen() {
        val compareInput = createCompareInput()
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            timestamp = fakeTimestamp
        )

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content").performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_top_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_screen_metadata_header").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_screen_shell_content").assertIsDisplayed()
    }

    @Test
    fun fullscreen_backExitsFullscreenNotScreen() {
        val compareInput = createCompareInput()
        var backCount = 0
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            onBack = { backCount++ }
        )

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content").performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        scenario?.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_top_bar").assertIsDisplayed()
        assertEquals(0, backCount)
    }

    @Test
    fun originalPeek_fullscreenKeepsBadgePeekAndBackBehavior() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        var backCount = 0
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            onBack = { backCount++ }
        )

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content").performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_top_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { down(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_original_reference_image").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_original_reference_badge")
            .performTouchInput { up() }

        scenario?.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_top_bar").assertIsDisplayed()
        assertEquals(0, backCount)
    }

    @Test
    fun moreMenuButton_hiddenWhenNoSessionContext() {
        setCompareContent(referenceImageUri = null, captureImageUri = null)

        composeRule.onNodeWithTag("compare_screen_more_menu_button").assertDoesNotExist()
    }

    @Test
    fun moreMenuButton_visibleWhenEditSessionProvided() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onEditSession = {})

        composeRule.onNodeWithTag("compare_screen_more_menu_button").assertIsDisplayed()
    }

    @Test
    fun moreMenu_opensOnClick() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onEditSession = {})

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.edit_session_overflow_item))
            .assertIsDisplayed()
    }

    @Test
    fun moreMenu_editSessionItem_invokesCallback() {
        var editSessionCount = 0
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            onEditSession = { editSessionCount++ }
        )

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_screen_edit_session_item").performClick()
        composeRule.waitForIdle()

        assertEquals(1, editSessionCount)
    }

    @Test
    fun deleteButton_independentOfTitleFeature() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            onDelete = {},
            onEditSession = {}
        )

        composeRule.onNodeWithTag("compare_screen_delete_button").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_more_menu_button").assertIsDisplayed()
    }

    // --- Backup session overflow tests ---

    @Test
    fun backupSessionItem_visibleInOverflowWhenSessionIdProvided() {
        setHostContent {
            CompareScreen(
                referenceImageUri = null,
                captureImageUri = null,
                onBack = {},
                sessionId = "2024-01-15_10-30-00",
                onBackupSession = {}
            )
        }

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_backup_session_item").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_overflow_backup_session))
            .assertIsDisplayed()
    }

    @Test
    fun backupSessionItem_notVisibleInOverflowWhenSessionIdIsNull() {
        setHostContent {
            CompareScreen(
                referenceImageUri = null,
                captureImageUri = null,
                onBack = {},
                sessionId = null,
                onEditSession = {}
            )
        }

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_backup_session_item").assertDoesNotExist()
    }

    @Test
    fun backupSessionItem_disabledWhenIsBackupInProgressTrue() {
        setHostContent {
            CompareScreen(
                referenceImageUri = null,
                captureImageUri = null,
                onBack = {},
                sessionId = "2024-01-15_10-30-00",
                onBackupSession = {},
                isBackupInProgress = true
            )
        }

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_backup_session_item").assertIsNotEnabled()
    }

    // --- Info badge eligibility tests ---

    @Test
    fun infoBadge_hiddenWhenIdentityTransformAndMatchingAspect() {
        val compareInput = createSessionCompareInputWithMetadata(
            scale = 1.0f, offsetX = 0.0f, offsetY = 0.0f,
            displayMode = "COMPARE_WITH_PREVIEW",
            orientedWidth = 1080, orientedHeight = 1920,
            viewportWidth = 1080, viewportHeight = 1920
        )
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertDoesNotExist()
    }

    @Test
    fun infoBadge_hiddenWhenIdentityTransformShowFullImage() {
        val compareInput = createSessionCompareInputWithMetadata(
            scale = 1.0f, offsetX = 0.0f, offsetY = 0.0f,
            displayMode = "SHOW_FULL_IMAGE",
            orientedWidth = 1080, orientedHeight = 1920,
            viewportWidth = 1080, viewportHeight = 1920
        )
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertDoesNotExist()
    }

    @Test
    fun infoBadge_shownWhenScaleIsNotOne() {
        val compareInput = createSessionCompareInputWithMetadata(
            scale = 1.5f, offsetX = 0.0f, offsetY = 0.0f,
            displayMode = "COMPARE_WITH_PREVIEW",
            orientedWidth = 1080, orientedHeight = 1920,
            viewportWidth = 1080, viewportHeight = 1920
        )
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun infoBadge_shownWhenOffsetXIsNonZero() {
        val compareInput = createSessionCompareInputWithMetadata(
            scale = 1.0f, offsetX = 0.2f, offsetY = 0.0f,
            displayMode = "COMPARE_WITH_PREVIEW",
            orientedWidth = 1080, orientedHeight = 1920,
            viewportWidth = 1080, viewportHeight = 1920
        )
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun infoBadge_shownWhenOffsetYIsNonZero() {
        val compareInput = createSessionCompareInputWithMetadata(
            scale = 1.0f, offsetX = 0.0f, offsetY = 0.2f,
            displayMode = "COMPARE_WITH_PREVIEW",
            orientedWidth = 1080, orientedHeight = 1920,
            viewportWidth = 1080, viewportHeight = 1920
        )
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun infoBadge_shownWhenAspectRatioMismatchInCropMode() {
        // 4:3 landscape reference (1440x1080) vs 9:16 portrait viewport (1080x1920)
        val compareInput = createSessionCompareInputWithMetadata(
            scale = 1.0f, offsetX = 0.0f, offsetY = 0.0f,
            displayMode = "COMPARE_WITH_PREVIEW",
            orientedWidth = 1440, orientedHeight = 1080,
            viewportWidth = 1080, viewportHeight = 1920
        )
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun infoBadge_hiddenWhenAspectRatioMatchesInCropMode() {
        val compareInput = createSessionCompareInputWithMetadata(
            scale = 1.0f, offsetX = 0.0f, offsetY = 0.0f,
            displayMode = "COMPARE_WITH_PREVIEW",
            orientedWidth = 1080, orientedHeight = 1920,
            viewportWidth = 1080, viewportHeight = 1920
        )
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertDoesNotExist()
    }

    @Test
    fun infoBadge_hiddenWhenNoOriginalReferenceFile() {
        val compareInput = createSessionCompareInput(includeOriginalReference = false)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertDoesNotExist()
    }

    @Test
    fun infoBadge_shownWhenMetadataMissing() {
        // createSessionCompareInput writes no metadata.json → safe fallback shows badge
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    @Test
    fun infoBadge_shownWhenMetadataCorrupt() {
        val compareInput = createSessionCompareInputWithCorruptMetadata()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_original_reference_badge").assertIsDisplayed()
    }

    // --- T-I-05: Create Video button visible and enabled when session has valid files ---

    @Test
    fun t_i_05_createVideoButton_visibleAndEnabledWhenSessionHasValidFiles() {
        setHostContent {
            CompareScreen(
                referenceImageUri = null,
                captureImageUri = null,
                onBack = {},
                sessionId = "2026-01-01_12-00-00",
                onCreateVideo = {},
                isCreateVideoAvailable = true
            )
        }

        composeRule.onNodeWithTag("compare_screen_create_video_button").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_create_video_button").assertIsEnabled()
    }

    // --- T-I-06: Create Video button visible but disabled when files are not available ---

    @Test
    fun t_i_06_createVideoButton_visibleButDisabledWhenFilesNotAvailable() {
        setHostContent {
            CompareScreen(
                referenceImageUri = null,
                captureImageUri = null,
                onBack = {},
                sessionId = "2026-01-01_12-00-00",
                onCreateVideo = {},
                isCreateVideoAvailable = false
            )
        }

        composeRule.onNodeWithTag("compare_screen_create_video_button").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_create_video_button").assertIsNotEnabled()
    }

    // --- T-I-07: Tap on Create Video invokes the callback ---

    @Test
    fun t_i_07_createVideoButton_tapInvokesCallback() {
        var createVideoCount = 0
        setHostContent {
            CompareScreen(
                referenceImageUri = null,
                captureImageUri = null,
                onBack = {},
                sessionId = "2026-01-01_12-00-00",
                onCreateVideo = { createVideoCount++ },
                isCreateVideoAvailable = true
            )
        }

        composeRule.onNodeWithTag("compare_screen_create_video_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, createVideoCount)
    }

    // --- T-I-08: Back from CreateVideoScreen returns to CompareScreen with unchanged state ---
    // Verified by checking that tapping Create Video does not alter CompareScreen's slider state.
    // The slider's rememberSaveable ensures state is preserved when navigation pops back.

    @Test
    fun t_i_08_createVideoTap_doesNotAlterCompareScreenState() {
        val compareInput = createCompareInput()
        var createVideoInvoked = false

        setHostContent {
            CompareScreen(
                referenceImageUri = compareInput.referenceUri,
                captureImageUri = compareInput.captureUri,
                onBack = {},
                sessionId = "2026-01-01_12-00-00",
                onCreateVideo = { createVideoInvoked = true },
                isCreateVideoAvailable = true
            )
        }

        waitForSliderViewport()

        // Move slider to a non-default position
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(200f, 0f))
            up()
        }
        composeRule.waitForIdle()
        val sliderBefore = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()

        // Tap Create Video (simulates navigation trigger — doesn't change CompareScreen state)
        composeRule.onNodeWithTag("compare_screen_create_video_button").performClick()
        composeRule.waitForIdle()

        assertTrue("onCreateVideo callback must have been invoked", createVideoInvoked)

        // Slider state is unchanged — same position guaranteed by rememberSaveable on back stack
        val sliderAfter = composeRule.onNodeWithTag("compare_slider").getUnclippedBoundsInRoot()
        assertCenterXNear(sliderBefore, sliderAfter, 8.dp)
    }

    // --- Create Video button absent when onCreateVideo is null (no session context) ---

    @Test
    fun createVideoButton_notVisibleWhenOnCreateVideoIsNull() {
        setCompareContent(referenceImageUri = null, captureImageUri = null)

        composeRule.onNodeWithTag("compare_screen_create_video_button").assertDoesNotExist()
    }

    // --- FitBounds tests ---

    @Test
    fun viewport_landscapeImage_inPortraitDevice_viewportIsLandscapeShaped() {
        val refFile = File.createTempFile("vp_ls_ref", ".png", context.cacheDir)
        val capFile = File.createTempFile("vp_ls_cap", ".png", context.cacheDir)
        createImageFileWithSize(refFile, Color.rgb(200, 100, 100), 800, 450)
        createImageFileWithSize(capFile, Color.rgb(100, 100, 200), 800, 450)

        setCompareContent(Uri.fromFile(refFile), Uri.fromFile(capFile))
        waitForSliderViewport()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                val b = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
                (b.right - b.left) > (b.bottom - b.top)
            }.getOrDefault(false)
        }

        val vpBounds = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
        val vpW = vpBounds.right - vpBounds.left
        val vpH = vpBounds.bottom - vpBounds.top
        assertTrue("Landscape image in portrait device: viewport width ($vpW) must exceed height ($vpH)", vpW > vpH)
    }

    @Test
    fun viewport_portraitImage_inPortraitDevice_viewportIsPortraitShaped() {
        // Landscape-device cross-orientation case not automated: requestedOrientation triggers
        // activity recreation, making reliable content re-injection non-trivial with createEmptyComposeRule.
        // Manual verification covers spec cases 3 & 4. This test exercises the same viewportAspect
        // derivation path for the symmetrical (portrait-in-portrait) case as a regression guard.
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)
        waitForSliderViewport()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                val b = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
                (b.bottom - b.top) > (b.right - b.left)
            }.getOrDefault(false)
        }

        val vpBounds = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
        val vpW = vpBounds.right - vpBounds.left
        val vpH = vpBounds.bottom - vpBounds.top
        assertTrue("Portrait image in portrait device: viewport height ($vpH) must exceed width ($vpW)", vpH > vpW)
    }

    // --- Handle label UI tests ---

    @Test
    fun handleLabels_bothVisibleAtCenterPosition() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_handle_label_left").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_handle_label_right").assertIsDisplayed()
    }

    @Test
    fun handleLabels_showDateLabels_levelOne_differentYears() {
        val compareInput = createCompareInput()
        // 2026-06-11 ~00:00 UTC
        val timestamp2026 = 1_781_136_000_000L
        setHostContent {
            CompareScreen(
                referenceImageUri = compareInput.referenceUri,
                captureImageUri = compareInput.captureUri,
                onBack = {},
                referenceDate = "2008",
                timestamp = timestamp2026
            )
        }

        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_handle_label_left").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_handle_label_right").assertIsDisplayed()
        composeRule.onNodeWithText("2008").assertIsDisplayed()
    }

    @Test
    fun handleLabel_leftHiddenWhenSliderNearLeftEdge() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        // Drag slider far to the left so the left label would overflow the viewport
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-width * 0.45f, 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_handle_label_left").assertDoesNotExist()
    }

    @Test
    fun handleLabel_rightHiddenWhenSliderNearRightEdge() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        // Drag slider far to the right so the right label would overflow the viewport
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.45f, 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_handle_label_right").assertDoesNotExist()
    }

    @Test
    fun handleAlwaysVisible_atExtremeSides() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        // Far left
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-width * 0.45f, 0f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_divider_handle").assertIsDisplayed()

        // Far right
        composeRule.onNodeWithTag("compare_viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.9f, 0f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_divider_handle").assertIsDisplayed()
    }

    @Test
    fun handleLabels_visibleInFullscreen() {
        val compareInput = createCompareInput()
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content").performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_handle_label_left").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_handle_label_right").assertIsDisplayed()
    }

    @Test
    fun originalPeek_sliderAndLabelsRestoredAfterPeekRelease() {
        val compareInput = createSessionCompareInput(includeOriginalReference = true)
        setCompareContent(compareInput.referenceUri, compareInput.captureUri)

        waitForSliderViewport()

        // Start peek
        composeRule.onNodeWithTag("compare_original_reference_badge").performTouchInput { down(center) }
        composeRule.waitForIdle()

        // End peek
        composeRule.onNodeWithTag("compare_original_reference_badge").performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_handle_label_left").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_handle_label_right").assertIsDisplayed()
    }

    // --- New metadata header tests ---

    @Test
    fun metadataHeader_showsTitleAboveSlider() {
        val compareInput = createCompareInput()
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            timestamp = fakeTimestamp,
            sessionTitle = "Zugspitze"
        )
        waitForSliderViewport()

        val headerBounds = composeRule.onNodeWithTag("compare_screen_metadata_header").getUnclippedBoundsInRoot()
        val viewportBounds = composeRule.onNodeWithTag("compare_viewport").getUnclippedBoundsInRoot()
        assertTrue("Metadata header must be above the compare viewport", headerBounds.bottom <= viewportBounds.top)
        composeRule.onNodeWithTag("compare_screen_metadata_title").assertIsDisplayed()
    }

    @Test
    fun metadataHeader_showsLocationWhenProvided() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            locationCity = "München",
            locationCountry = "Deutschland"
        )

        composeRule.onNodeWithTag("compare_screen_metadata_location").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertDoesNotExist()
    }

    @Test
    fun metadataHeader_showsCreatedFallback_whenNoTitleAndNoLocation() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp
        )

        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_title").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_screen_metadata_location").assertDoesNotExist()
    }

    @Test
    fun metadataHeader_locationSuppressesFallback() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            locationCity = "Berlin"
        )

        composeRule.onNodeWithTag("compare_screen_metadata_location").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertDoesNotExist()
    }

    @Test
    fun metadataHeader_noContentWhenNoTimestampAndNoMetadata() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = null
        )

        composeRule.onNodeWithTag("compare_screen_metadata_header").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_screen_metadata_title").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_screen_metadata_location").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertDoesNotExist()
    }

    @Test
    fun metadataHeader_hiddenInFullscreen() {
        val compareInput = createCompareInput()
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            timestamp = fakeTimestamp,
            sessionTitle = "My Session"
        )
        waitForSliderViewport()

        composeRule.onNodeWithTag("compare_screen_shell_content").performTouchInput { down(center); up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_metadata_header").assertDoesNotExist()
    }

    @Test
    fun metadataHeader_longTitleDoesNotOverflow() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = "My grandparents in front of their first house before moving away to another city"
        )

        val headerBounds = composeRule.onNodeWithTag("compare_screen_metadata_header").getUnclippedBoundsInRoot()
        val rootBounds = composeRule.onNodeWithTag("compare_screen_root").getUnclippedBoundsInRoot()
        assertTrue("Title must not overflow screen right edge", headerBounds.right <= rootBounds.right)
    }

    @Test
    fun metadataHeader_locationLineDoesNotOverflow() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            locationDisplayName = "Am Zugspitzgipfel",
            locationCity = "Garmisch-Partenkirchen",
            locationCountry = "Deutschland"
        )

        val headerBounds = composeRule.onNodeWithTag("compare_screen_metadata_header").getUnclippedBoundsInRoot()
        val rootBounds = composeRule.onNodeWithTag("compare_screen_root").getUnclippedBoundsInRoot()
        assertTrue("Location line must not overflow screen right edge", headerBounds.right <= rootBounds.right)
    }

    @Test
    fun metadataHeader_landscape_showsTitleAndLocation() {
        val landscapeConfig = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        setHostContent {
            CompositionLocalProvider(LocalConfiguration provides landscapeConfig) {
                CompareScreen(
                    referenceImageUri = null,
                    captureImageUri = null,
                    onBack = {},
                    timestamp = fakeTimestamp,
                    sessionTitle = "Zugspitze",
                    locationCity = "Garmisch-Partenkirchen"
                )
            }
        }

        composeRule.onNodeWithTag("compare_screen_metadata_title").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_location").assertIsDisplayed()
    }

    @Test
    fun metadataHeader_landscape_showsLocationWhenNoTitle() {
        val landscapeConfig = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        setHostContent {
            CompositionLocalProvider(LocalConfiguration provides landscapeConfig) {
                CompareScreen(
                    referenceImageUri = null,
                    captureImageUri = null,
                    onBack = {},
                    timestamp = fakeTimestamp,
                    locationDisplayName = "Zugspitzgipfel"
                )
            }
        }

        composeRule.onNodeWithTag("compare_screen_metadata_location").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertDoesNotExist()
    }

    @Test
    fun metadataHeader_landscape_showsCreatedFallback_whenNoMetadata() {
        val landscapeConfig = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        setHostContent {
            CompositionLocalProvider(LocalConfiguration provides landscapeConfig) {
                CompareScreen(
                    referenceImageUri = null,
                    captureImageUri = null,
                    onBack = {},
                    timestamp = fakeTimestamp
                )
            }
        }

        composeRule.onNodeWithTag("compare_screen_metadata_fallback").assertIsDisplayed()
    }

    private fun setCompareContent(
        referenceImageUri: Uri?,
        captureImageUri: Uri?,
        onBack: () -> Unit = {},
        timestamp: Long? = null,
        onDelete: (() -> Unit)? = null,
        sessionTitle: String? = null,
        onEditSession: (() -> Unit)? = null,
        locationDisplayName: String? = null,
        locationCity: String? = null,
        locationCountry: String? = null
    ) {
        setHostContent {
            CompareScreen(
                referenceImageUri = referenceImageUri,
                captureImageUri = captureImageUri,
                onBack = onBack,
                timestamp = timestamp,
                onDelete = onDelete,
                sessionTitle = sessionTitle,
                onEditSession = onEditSession,
                locationDisplayName = locationDisplayName,
                locationCity = locationCity,
                locationCountry = locationCountry
            )
        }
    }

    private fun setHostContent(content: @Composable () -> Unit) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitForSliderViewport() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onAllNodesWithTag("compare_slider").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun recreateCompareContent(
        referenceImageUri: Uri,
        captureImageUri: Uri
    ) {
        scenario?.recreate()
        scenario?.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.setContent {
                SameViewTheme {
                    CompareScreen(
                        referenceImageUri = referenceImageUri,
                        captureImageUri = captureImageUri,
                        onBack = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun createCompareInput(): CompareInput {
        return CompareInput(
            referenceUri = createImageUri("compare_reference", Color.rgb(220, 40, 40)),
            captureUri = createImageUri("compare_capture", Color.rgb(40, 120, 220))
        )
    }

    private fun createSessionCompareInput(includeOriginalReference: Boolean): CompareInput {
        val sessionsRoot = File(context.filesDir, "sessions")
        val sessionDir = File(sessionsRoot, "original_peek_${System.nanoTime()}")
        sessionDir.mkdirs()
        val referenceFile = File(sessionDir, "reference.jpg")
        val captureFile = File(sessionDir, "capture.jpg")
        createImageFile(referenceFile, Color.rgb(220, 40, 40))
        createImageFile(captureFile, Color.rgb(40, 120, 220))
        if (includeOriginalReference) {
            createImageFile(File(sessionDir, "reference-original.jpg"), Color.rgb(40, 220, 120))
        }
        return CompareInput(
            referenceUri = Uri.fromFile(referenceFile),
            captureUri = Uri.fromFile(captureFile)
        )
    }

    private fun createImageUri(fileNamePrefix: String, color: Int): Uri {
        val file = File.createTempFile(fileNamePrefix, ".png", context.cacheDir)
        createImageFile(file, color)
        return Uri.fromFile(file)
    }

    private fun createImageFile(file: File, color: Int) {
        tempFiles += file
        val bitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
    }

    private fun createImageFileWithSize(file: File, color: Int, width: Int, height: Int) {
        tempFiles += file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }

    private fun assertRectEquals(expected: DpRect, actual: DpRect) {
        assertEquals(expected.left.value, actual.left.value, 0.5f)
        assertEquals(expected.top.value, actual.top.value, 0.5f)
        assertEquals(expected.right.value, actual.right.value, 0.5f)
        assertEquals(expected.bottom.value, actual.bottom.value, 0.5f)
    }

    private fun assertCenterXNear(actual: DpRect, expected: DpRect, tolerance: Dp) {
        val actualCenter = actual.left + (actual.right - actual.left) / 2f
        val expectedCenter = expected.left + (expected.right - expected.left) / 2f
        val delta = if (actualCenter > expectedCenter) {
            actualCenter - expectedCenter
        } else {
            expectedCenter - actualCenter
        }
        assertTrue(delta <= tolerance)
    }

    private fun assertCenterYNear(actual: DpRect, expected: DpRect, tolerance: Dp) {
        val actualCenter = actual.top + (actual.bottom - actual.top) / 2f
        val expectedCenter = expected.top + (expected.bottom - expected.top) / 2f
        val delta = if (actualCenter > expectedCenter) {
            actualCenter - expectedCenter
        } else {
            expectedCenter - actualCenter
        }
        assertTrue(delta <= tolerance)
    }

    private fun assertDpNear(actual: Dp, expected: Dp, tolerance: Dp) {
        val delta = if (actual > expected) {
            actual - expected
        } else {
            expected - actual
        }
        assertTrue(delta <= tolerance)
    }

    private fun createSessionCompareInputWithMetadata(
        scale: Float = 1.0f,
        offsetX: Float = 0.0f,
        offsetY: Float = 0.0f,
        displayMode: String = "COMPARE_WITH_PREVIEW",
        orientedWidth: Int = 1080,
        orientedHeight: Int = 1920,
        viewportWidth: Int = 1080,
        viewportHeight: Int = 1920
    ): CompareInput {
        val sessionsRoot = File(context.filesDir, "sessions")
        val sessionDir = File(sessionsRoot, "meta_peek_${System.nanoTime()}")
        sessionDir.mkdirs()
        val referenceFile = File(sessionDir, "reference.jpg")
        val captureFile = File(sessionDir, "capture.jpg")
        createImageFile(referenceFile, Color.rgb(220, 40, 40))
        createImageFile(captureFile, Color.rgb(40, 120, 220))
        createImageFile(File(sessionDir, "reference-original.jpg"), Color.rgb(40, 220, 120))
        val metadataFile = File(sessionDir, "metadata.json")
        metadataFile.writeText(
            """{"version":2,"overlay":{"scale":$scale,"offsetX":$offsetX,"offsetY":$offsetY,"displayMode":"$displayMode"},"reference":{"orientedWidth":$orientedWidth,"orientedHeight":$orientedHeight},"viewport":{"width":$viewportWidth,"height":$viewportHeight}}"""
        )
        tempFiles += metadataFile
        return CompareInput(
            referenceUri = android.net.Uri.fromFile(referenceFile),
            captureUri = android.net.Uri.fromFile(captureFile)
        )
    }

    private fun createSessionCompareInputWithCorruptMetadata(): CompareInput {
        val sessionsRoot = File(context.filesDir, "sessions")
        val sessionDir = File(sessionsRoot, "corrupt_meta_${System.nanoTime()}")
        sessionDir.mkdirs()
        val referenceFile = File(sessionDir, "reference.jpg")
        val captureFile = File(sessionDir, "capture.jpg")
        createImageFile(referenceFile, Color.rgb(220, 40, 40))
        createImageFile(captureFile, Color.rgb(40, 120, 220))
        createImageFile(File(sessionDir, "reference-original.jpg"), Color.rgb(40, 220, 120))
        val metadataFile = File(sessionDir, "metadata.json")
        metadataFile.writeText("this is not valid json {{{{")
        tempFiles += metadataFile
        return CompareInput(
            referenceUri = android.net.Uri.fromFile(referenceFile),
            captureUri = android.net.Uri.fromFile(captureFile)
        )
    }

    private data class CompareInput(
        val referenceUri: Uri,
        val captureUri: Uri
    )
}
