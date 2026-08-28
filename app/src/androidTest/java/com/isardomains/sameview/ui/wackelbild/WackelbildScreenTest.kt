package com.isardomains.sameview.ui.wackelbild

import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Block 3 instrumentation coverage for [WackelbildScreenContent].
 *
 * Exercises the exact production composable directly (no Hilt/ViewModel involved — the
 * `internal` [WackelbildScreenContent] takes files/state/callbacks directly, matching the split
 * already used elsewhere in this codebase to keep screen content testable without a Hilt
 * harness) so there is no risk of a test-only stub drifting from the real screen.
 */
@RunWith(AndroidJUnit4::class)
class WackelbildScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun wakeDevice() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
    }

    /**
     * Launches [WackelbildScreenContent] with a small stateful wrapper standing in for the real
     * ViewModel's `visibleImage` StateFlow — [onSwipeDetected]/[onAccessibilityToggle] toggle it
     * exactly like [WackelbildViewModel.onSwipeDetected]/[WackelbildViewModel.onAccessibilityToggle]
     * do, so swipe/accessibility tests observe the real resulting UI. Lifecycle callbacks are
     * pass-through so tests can assert on invocation directly, mirroring [backButton_invokesCallback].
     */
    private fun launch(
        referenceFile: File,
        captureFile: File,
        isSensorAvailable: Boolean = true,
        onBack: () -> Unit = {},
        onScreenActive: () -> Unit = {},
        onScreenInactive: () -> Unit = {},
        onScreenLeft: () -> Unit = {},
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
    ): MutableState<WackelbildImageSide> {
        // A screen-off/locked device leaves the Activity below RESUMED, which in turn defers
        // Coil's lifecycle-aware image request — see the established pattern in
        // ShareComparisonScreenTest/CompareScreenTest.
        wakeDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var visibleImageState: MutableState<WackelbildImageSide>
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    val visibleImage = remember { mutableStateOf(WackelbildImageSide.REFERENCE) }
                    visibleImageState = visibleImage
                    WackelbildScreenContent(
                        referenceFile = referenceFile,
                        captureFile = captureFile,
                        visibleImage = visibleImage.value,
                        isSensorAvailable = isSensorAvailable,
                        onSwipeDetected = { visibleImage.value = visibleImage.value.opposite() },
                        onAccessibilityToggle = { visibleImage.value = visibleImage.value.opposite() },
                        onScreenActive = onScreenActive,
                        onScreenInactive = onScreenInactive,
                        onScreenLeft = onScreenLeft,
                        onBack = onBack,
                        windowWidthSizeClass = windowWidthSizeClass
                    )
                }
            }
        }
        composeRule.waitForIdle()
        waitForPreviewResolved()
        return visibleImageState
    }

    private fun WackelbildImageSide.opposite(): WackelbildImageSide =
        if (this == WackelbildImageSide.REFERENCE) WackelbildImageSide.CAPTURE else WackelbildImageSide.REFERENCE

    /**
     * Coil decodes both files asynchronously. Poll (with real, non-Compose-clock delays) until
     * the preview has settled into either its success or fallback state before assertions run,
     * bounded well above the near-instant local-file decode time.
     */
    private fun waitForPreviewResolved() {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            val resolved = composeRule
                .onAllNodesWithTag("wackelbild_reference_image")
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule
                    .onAllNodesWithTag("wackelbild_capture_image")
                    .fetchSemanticsNodes().isNotEmpty() ||
                composeRule
                    .onAllNodesWithTag("wackelbild_preview_fallback")
                    .fetchSemanticsNodes().isNotEmpty()
            if (resolved) return
            Thread.sleep(100)
        }
        composeRule.waitForIdle()
    }

    private fun createJpeg(width: Int, height: Int): File {
        val file = File.createTempFile("wackelbild_img", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.DKGRAY)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        tempFiles.add(file)
        return file
    }

    private fun createCorruptFile(): File {
        val file = File.createTempFile("wackelbild_img_corrupt", ".jpg", context.cacheDir)
        file.writeBytes("not a real image".toByteArray())
        tempFiles.add(file)
        return file
    }

    private fun missingFile(): File =
        File(context.cacheDir, "wackelbild_does_not_exist_${System.nanoTime()}.jpg")

    private fun validReference(): File = createJpeg(320, 400)
    private fun validCapture(): File = createJpeg(320, 400)

    private fun SemanticsNodeInteraction.performCustomAccessibilityAction(label: String) {
        val node = fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        val match = actions.firstOrNull { it.label == label }
            ?: error("Custom accessibility action '$label' not found")
        match.action?.invoke()
    }

    // --- Title ---

    @Test
    fun screenTitle_isDisplayed() {
        launch(referenceFile = validReference(), captureFile = validCapture())
        val expectedTitle = context.getString(R.string.wackelbild_screen_title)
        composeRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    // --- Back ---

    @Test
    fun backButton_invokesCallback() {
        var backInvoked = false
        launch(referenceFile = validReference(), captureFile = validCapture(), onBack = { backInvoked = true })
        composeRule.onNodeWithTag("wackelbild_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Back callback should be invoked", backInvoked)
    }

    // --- Initial visible image ---

    @Test
    fun initialVisibleImage_isReference() {
        launch(referenceFile = validReference(), captureFile = validCapture())
        composeRule.onNodeWithTag("wackelbild_reference_image").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_capture_image").assertDoesNotExist()
    }

    // --- Valid Capture displayed after toggle ---

    @Test
    fun validCapture_displaysAfterToggle() {
        val visibleImage = launch(referenceFile = validReference(), captureFile = validCapture())
        composeRule.onNodeWithTag("wackelbild_preview_interactive_area")
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(WackelbildImageSide.CAPTURE, visibleImage.value)
        composeRule.onNodeWithTag("wackelbild_capture_image").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertDoesNotExist()
    }

    // --- Missing/corrupt Capture -> same unified fallback ---

    @Test
    fun missingCapture_showsFallback_noInteractionUi() {
        launch(referenceFile = validReference(), captureFile = missingFile())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_preview_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertDoesNotExist()
        composeRule.onNodeWithTag("wackelbild_preview_interactive_area").assertDoesNotExist()
    }

    @Test
    fun corruptCapture_showsSameFallback() {
        launch(referenceFile = validReference(), captureFile = createCorruptFile())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_preview_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertDoesNotExist()
    }

    @Test
    fun missingReferenceFile_showsFallback() {
        launch(referenceFile = missingFile(), captureFile = validCapture())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_preview_fallback").assertIsDisplayed()
        val expectedTitle = context.getString(R.string.wackelbild_preview_error_title)
        composeRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    @Test
    fun corruptReferenceFile_showsSameFallback() {
        launch(referenceFile = createCorruptFile(), captureFile = validCapture())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_preview_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertDoesNotExist()
    }

    @Test
    fun fallbackState_backButtonStillAvailable() {
        var backInvoked = false
        launch(referenceFile = missingFile(), captureFile = validCapture(), onBack = { backInvoked = true })
        composeRule.onNodeWithTag("wackelbild_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Back callback should still work in the fallback state", backInvoked)
    }

    // --- Swipe toggles ---

    @Test
    fun horizontalSwipe_left_togglesOnce() {
        val visibleImage = launch(referenceFile = validReference(), captureFile = validCapture())
        composeRule.onNodeWithTag("wackelbild_preview_interactive_area")
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(WackelbildImageSide.CAPTURE, visibleImage.value)
    }

    @Test
    fun horizontalSwipe_reverseDirection_alsoTogglesOnce() {
        val visibleImage = launch(referenceFile = validReference(), captureFile = validCapture())
        composeRule.onNodeWithTag("wackelbild_preview_interactive_area")
            .performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(WackelbildImageSide.CAPTURE, visibleImage.value)
    }

    @Test
    fun verticalGesture_doesNotToggle() {
        val visibleImage = launch(referenceFile = validReference(), captureFile = validCapture())
        composeRule.onNodeWithTag("wackelbild_preview_interactive_area")
            .performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals(WackelbildImageSide.REFERENCE, visibleImage.value)
    }

    // --- Sensor-availability hint text ---

    @Test
    fun sensorAvailable_showsTiltHint() {
        launch(referenceFile = validReference(), captureFile = validCapture(), isSensorAvailable = true)
        val tiltTitle = context.getString(R.string.wackelbild_hint_tilt_title)
        composeRule.onNodeWithText(tiltTitle).assertIsDisplayed()
    }

    @Test
    fun sensorUnavailable_showsSwipeHint() {
        launch(referenceFile = validReference(), captureFile = validCapture(), isSensorAvailable = false)
        val swipeTitle = context.getString(R.string.wackelbild_hint_swipe_title)
        composeRule.onNodeWithText(swipeTitle).assertIsDisplayed()
    }

    // --- Accessibility action ---

    @Test
    fun accessibilityAction_togglesImage() {
        val visibleImage = launch(referenceFile = validReference(), captureFile = validCapture())
        val toggleLabel = context.getString(R.string.wackelbild_accessibility_toggle_action)
        composeRule.onNodeWithTag("wackelbild_preview_interactive_area")
            .performCustomAccessibilityAction(toggleLabel)
        composeRule.waitForIdle()
        assertEquals(WackelbildImageSide.CAPTURE, visibleImage.value)
    }

    // --- Lifecycle callbacks ---

    @Test
    fun onResume_invokesOnScreenActive() {
        var activeCount = 0
        launch(
            referenceFile = validReference(),
            captureFile = validCapture(),
            onScreenActive = { activeCount++ }
        )
        // ActivityScenario.launch() already drives the Activity to RESUMED, which is what
        // triggers the ON_RESUME lifecycle event this screen observes.
        assertTrue("onScreenActive should be invoked at least once on resume", activeCount >= 1)
    }

    @Test
    fun moveToCreated_invokesOnScreenInactive() {
        var inactiveCount = 0
        launch(
            referenceFile = validReference(),
            captureFile = validCapture(),
            onScreenInactive = { inactiveCount++ }
        )
        scenario?.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        assertTrue("onScreenInactive should be invoked on pause", inactiveCount >= 1)
    }

    @Test
    fun closingScenario_invokesOnScreenLeft() {
        var leftCount = 0
        launch(
            referenceFile = validReference(),
            captureFile = validCapture(),
            onScreenLeft = { leftCount++ }
        )
        scenario?.close()
        scenario = null
        assertEquals(1, leftCount)
    }

    // --- Existing Block 2 aspect-ratio / no-crop coverage, still green ---

    @Test
    fun portraitReference_previewContainerMatchesIntrinsicRatio() {
        // 320x400 => ratio 0.8, chosen to stay under the 500dp preview height cap on
        // standard test-device widths so the assertion below is exact, not cap-affected.
        launch(referenceFile = createJpeg(320, 400), captureFile = createJpeg(320, 400))
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("wackelbild_reference_preview_container")
            .getUnclippedBoundsInRoot()
        val widthDp = (bounds.right - bounds.left).value
        val heightDp = (bounds.bottom - bounds.top).value
        val actualRatio = widthDp / heightDp
        assertEquals(0.8f, actualRatio, 0.05f)
        assertTrue("Portrait preview must be taller than wide", heightDp > widthDp)
    }

    @Test
    fun landscapeReference_previewContainerMatchesIntrinsicRatio() {
        launch(referenceFile = createJpeg(400, 300), captureFile = createJpeg(400, 300))
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("wackelbild_reference_preview_container")
            .getUnclippedBoundsInRoot()
        val widthDp = (bounds.right - bounds.left).value
        val heightDp = (bounds.bottom - bounds.top).value
        val actualRatio = widthDp / heightDp
        assertEquals(400f / 300f, actualRatio, 0.05f)
        assertTrue("Landscape preview must be wider than tall", widthDp > heightDp)
    }

    @Test
    fun referenceImage_fillsPreviewContainer_noAdditionalCrop() {
        launch(referenceFile = createJpeg(400, 300), captureFile = createJpeg(400, 300))
        composeRule.waitForIdle()

        val containerBounds = composeRule.onNodeWithTag("wackelbild_reference_preview_container")
            .getUnclippedBoundsInRoot()
        val imageBounds = composeRule.onNodeWithTag("wackelbild_reference_image")
            .getUnclippedBoundsInRoot()

        val containerW = (containerBounds.right - containerBounds.left).value
        val containerH = (containerBounds.bottom - containerBounds.top).value
        val imageW = (imageBounds.right - imageBounds.left).value
        val imageH = (imageBounds.bottom - imageBounds.top).value
        assertEquals(containerW, imageW, 1f)
        assertEquals(containerH, imageH, 1f)
    }

    // --- No Block 4 (date/order/network) UI exists yet ---

    @Test
    fun noBlock4Ui_existsYet() {
        launch(referenceFile = validReference(), captureFile = validCapture())
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wackelbild_date_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("wackelbild_order_button").assertDoesNotExist()
        composeRule.onNodeWithTag("wackelbild_loading_spinner").assertDoesNotExist()
    }

    // --- Responsive: Compact and Expanded remain usable ---

    @Test
    fun compactWidth_screenRendersAndReferenceIsDisplayed() {
        launch(
            referenceFile = validReference(),
            captureFile = validCapture(),
            windowWidthSizeClass = WindowWidthSizeClass.Compact
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertIsDisplayed()
    }

    @Test
    fun expandedWidth_screenRendersAndReferenceIsDisplayed() {
        launch(
            referenceFile = validReference(),
            captureFile = validCapture(),
            windowWidthSizeClass = WindowWidthSizeClass.Expanded
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertIsDisplayed()
    }
}
