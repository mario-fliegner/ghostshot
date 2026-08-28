package com.isardomains.sameview.ui.wackelbild

import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Block 2 instrumentation coverage for [WackelbildScreenContent].
 *
 * Exercises the exact production composable directly (no Hilt/ViewModel involved — the
 * `internal` [WackelbildScreenContent] takes a plain [File], matching the split already used
 * elsewhere in this codebase to keep screen content testable without a Hilt harness) so there is
 * no risk of a test-only stub drifting from the real screen.
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

    private fun launch(
        referenceFile: File,
        onBack: () -> Unit = {},
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
    ) {
        // A screen-off/locked device leaves the Activity below RESUMED, which in turn defers
        // Coil's lifecycle-aware image request — see the established pattern in
        // ShareComparisonScreenTest/CompareScreenTest.
        wakeDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    WackelbildScreenContent(
                        referenceFile = referenceFile,
                        onBack = onBack,
                        windowWidthSizeClass = windowWidthSizeClass
                    )
                }
            }
        }
        composeRule.waitForIdle()
        waitForPreviewResolved()
    }

    /**
     * Coil decodes [referenceFile] asynchronously. Poll (with real, non-Compose-clock delays)
     * until the preview has settled into either its success or fallback state before assertions
     * run, bounded well above the near-instant local-file decode time.
     */
    private fun waitForPreviewResolved() {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            val resolved = composeRule
                .onAllNodesWithTag("wackelbild_reference_image")
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
        val file = File.createTempFile("wackelbild_ref", ".jpg", context.cacheDir)
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
        val file = File.createTempFile("wackelbild_ref_corrupt", ".jpg", context.cacheDir)
        file.writeBytes("not a real image".toByteArray())
        tempFiles.add(file)
        return file
    }

    private fun missingFile(): File =
        File(context.cacheDir, "wackelbild_does_not_exist_${System.nanoTime()}.jpg")

    // --- Title ---

    @Test
    fun screenTitle_isDisplayed() {
        launch(referenceFile = createJpeg(320, 400))
        val expectedTitle = context.getString(R.string.wackelbild_screen_title)
        composeRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    // --- Back ---

    @Test
    fun backButton_invokesCallback() {
        var backInvoked = false
        launch(referenceFile = createJpeg(320, 400), onBack = { backInvoked = true })
        composeRule.onNodeWithTag("wackelbild_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Back callback should be invoked", backInvoked)
    }

    // --- Valid reference preview ---

    @Test
    fun validReference_displaysImage_noFallback() {
        launch(referenceFile = createJpeg(320, 400))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_preview_fallback").assertDoesNotExist()
    }

    // --- Intrinsic aspect ratio: Portrait ---

    @Test
    fun portraitReference_previewContainerMatchesIntrinsicRatio() {
        // 320x400 => ratio 0.8, chosen to stay under the 500dp preview height cap on
        // standard test-device widths so the assertion below is exact, not cap-affected.
        launch(referenceFile = createJpeg(320, 400))
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("wackelbild_reference_preview_container")
            .getUnclippedBoundsInRoot()
        val widthDp = (bounds.right - bounds.left).value
        val heightDp = (bounds.bottom - bounds.top).value
        val actualRatio = widthDp / heightDp
        assertEquals(0.8f, actualRatio, 0.05f)
        // Portrait: container must be taller than it is wide.
        assertTrue("Portrait preview must be taller than wide", heightDp > widthDp)
    }

    // --- Intrinsic aspect ratio: Landscape ---

    @Test
    fun landscapeReference_previewContainerMatchesIntrinsicRatio() {
        // 400x300 => ratio 1.333, safely under the height cap.
        launch(referenceFile = createJpeg(400, 300))
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("wackelbild_reference_preview_container")
            .getUnclippedBoundsInRoot()
        val widthDp = (bounds.right - bounds.left).value
        val heightDp = (bounds.bottom - bounds.top).value
        val actualRatio = widthDp / heightDp
        assertEquals(400f / 300f, actualRatio, 0.05f)
        // Landscape: container must be wider than it is tall.
        assertTrue("Landscape preview must be wider than tall", widthDp > heightDp)
    }

    // --- No additional crop: image fills its (aspect-correct) container exactly ---

    @Test
    fun referenceImage_fillsPreviewContainer_noAdditionalCrop() {
        launch(referenceFile = createJpeg(400, 300))
        composeRule.waitForIdle()

        val containerBounds = composeRule.onNodeWithTag("wackelbild_reference_preview_container")
            .getUnclippedBoundsInRoot()
        val imageBounds = composeRule.onNodeWithTag("wackelbild_reference_image")
            .getUnclippedBoundsInRoot()

        // ContentScale.Fit inside a container already sized to the image's own aspect ratio
        // means the image occupies the full container — no letterboxing, no cropping.
        val containerW = (containerBounds.right - containerBounds.left).value
        val containerH = (containerBounds.bottom - containerBounds.top).value
        val imageW = (imageBounds.right - imageBounds.left).value
        val imageH = (imageBounds.bottom - imageBounds.top).value
        assertEquals(containerW, imageW, 1f)
        assertEquals(containerH, imageH, 1f)
    }

    // --- Missing reference.jpg ---

    @Test
    fun missingReferenceFile_showsFallback() {
        launch(referenceFile = missingFile())
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wackelbild_preview_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertDoesNotExist()
        val expectedTitle = context.getString(R.string.wackelbild_preview_error_title)
        composeRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    // --- Undecodable/corrupt existing reference.jpg ---

    @Test
    fun corruptReferenceFile_showsSameFallback() {
        launch(referenceFile = createCorruptFile())
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wackelbild_preview_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertDoesNotExist()
    }

    // --- Back/TopAppBar remain available even in the fallback state ---

    @Test
    fun fallbackState_backButtonStillAvailable() {
        var backInvoked = false
        launch(referenceFile = missingFile(), onBack = { backInvoked = true })
        composeRule.onNodeWithTag("wackelbild_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Back callback should still work in the fallback state", backInvoked)
    }

    // --- No later-block UI exists yet ---

    @Test
    fun noLaterBlockUi_existsYet() {
        launch(referenceFile = createJpeg(320, 400))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wackelbild_capture_image").assertDoesNotExist()
        composeRule.onNodeWithTag("wackelbild_date_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("wackelbild_order_button").assertDoesNotExist()
        composeRule.onNodeWithTag("wackelbild_loading_spinner").assertDoesNotExist()
    }

    // --- Responsive: Compact and Expanded remain usable ---

    @Test
    fun compactWidth_screenRendersAndReferenceIsDisplayed() {
        launch(referenceFile = createJpeg(320, 400), windowWidthSizeClass = WindowWidthSizeClass.Compact)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertIsDisplayed()
    }

    @Test
    fun expandedWidth_screenRendersAndReferenceIsDisplayed() {
        launch(referenceFile = createJpeg(320, 400), windowWidthSizeClass = WindowWidthSizeClass.Expanded)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wackelbild_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("wackelbild_reference_image").assertIsDisplayed()
    }
}
