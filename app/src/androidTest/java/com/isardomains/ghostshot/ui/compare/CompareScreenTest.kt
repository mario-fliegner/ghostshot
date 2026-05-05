package com.isardomains.ghostshot.ui.compare

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.DateFormat
import java.util.Date

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

    // --- Timestamp tests ---

    @Test
    fun timestamp_notDisplayedWhenNull() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, timestamp = null)

        composeRule.onNodeWithTag("compare_screen_timestamp").assertDoesNotExist()
    }

    @Test
    fun timestamp_tagDisplayedWhenProvided() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, timestamp = fakeTimestamp)

        composeRule.onNodeWithTag("compare_screen_timestamp").assertIsDisplayed()
    }

    @Test
    fun timestamp_formattedTextMatchesExpected() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, timestamp = fakeTimestamp)

        val expected = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fakeTimestamp))
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun compareScreen_belowContent_isDisplayedWithViewport() {
        val compareInput = createCompareInput()
        setCompareContent(
            referenceImageUri = compareInput.referenceUri,
            captureImageUri = compareInput.captureUri,
            timestamp = fakeTimestamp
        )

        waitForSliderViewport()
        composeRule.onNodeWithTag("compare_screen_shell_content").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_timestamp").assertIsDisplayed()
    }

    // --- Session title tests ---

    @Test
    fun sessionTitle_displayedWhenSet() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = "My Shot"
        )

        composeRule.onNodeWithTag("compare_screen_session_title").assertIsDisplayed()
        composeRule.onNodeWithText("My Shot").assertIsDisplayed()
    }

    @Test
    fun sessionTitle_timestampStillDisplayedWithTitle() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = "My Shot"
        )

        composeRule.onNodeWithTag("compare_screen_timestamp").assertIsDisplayed()
    }

    @Test
    fun sessionTitle_updatedAfterEditTitleDialogSave() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = null,
            onSaveTitle = {}
        )

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_screen_title_input").performTextInput("New Title")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title_save)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_session_title").assertIsDisplayed()
        composeRule.onNodeWithText("New Title").assertIsDisplayed()
    }

    @Test
    fun sessionTitle_notDisplayedWhenEmpty() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = null
        )

        composeRule.onNodeWithTag("compare_screen_session_title").assertDoesNotExist()
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
            referenceImageUri = Uri.parse("content://ghostshot/missing-reference"),
            captureImageUri = Uri.parse("content://ghostshot/missing-capture")
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(context.getString(R.string.compare_error_load_failed))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("compare_load_failed_fallback").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.compare_error_load_failed))
            .assertIsDisplayed()
    }

    @Test
    fun fullscreen_isNotDefaultMode() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp
        )

        composeRule.onNodeWithTag("compare_screen_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_timestamp").assertIsDisplayed()
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
        composeRule.onNodeWithTag("compare_screen_timestamp").assertDoesNotExist()
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
    fun moreMenuButton_hiddenWhenOnSaveTitleNull() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onSaveTitle = null)

        composeRule.onNodeWithTag("compare_screen_more_menu_button").assertDoesNotExist()
    }

    @Test
    fun moreMenuButton_visibleWhenOnSaveTitleProvided() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onSaveTitle = {})

        composeRule.onNodeWithTag("compare_screen_more_menu_button").assertIsDisplayed()
    }

    @Test
    fun moreMenu_opensOnClick() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onSaveTitle = {})

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title))
            .assertIsDisplayed()
    }

    @Test
    fun editTitleDialog_opensOnMenuItemClick() {
        setCompareContent(referenceImageUri = null, captureImageUri = null, onSaveTitle = {})

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title_dialog_title))
            .assertIsDisplayed()
    }

    @Test
    fun editTitleDialog_prefillsCurrentTitle() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            sessionTitle = "My Title",
            onSaveTitle = {}
        )

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("My Title").assertIsDisplayed()
    }

    @Test
    fun editTitleDialog_save_invokesCallback() {
        var savedTitle: String? = "SENTINEL"
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            onSaveTitle = { savedTitle = it }
        )

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title_save))
            .performClick()
        composeRule.waitForIdle()

        // empty input → trimmed → null
        assert(savedTitle != "SENTINEL")
    }

    @Test
    fun editTitleDialog_cancel_doesNotInvokeCallback() {
        var callCount = 0
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            onSaveTitle = { callCount++ }
        )

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title_cancel))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(0, callCount)
    }

    @Test
    fun removeTitle_visibleAndWorksWhenTitleIsSet() {
        var savedTitle: String? = "SENTINEL"
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            timestamp = fakeTimestamp,
            sessionTitle = "My Shot",
            onSaveTitle = { savedTitle = it }
        )

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compare_screen_remove_title_item").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_remove_title_item").performClick()
        composeRule.waitForIdle()

        assertEquals(null, savedTitle)
        composeRule.onNodeWithTag("compare_screen_session_title").assertDoesNotExist()
        composeRule.onNodeWithTag("compare_screen_timestamp").assertIsDisplayed()
    }

    @Test
    fun removeTitle_notVisibleWhenNoTitleSet() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            sessionTitle = null,
            onSaveTitle = {}
        )

        composeRule.onNodeWithTag("compare_screen_more_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compare_screen_remove_title_item").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.compare_screen_edit_title)).assertIsDisplayed()
    }

    @Test
    fun deleteButton_independentOfTitleFeature() {
        setCompareContent(
            referenceImageUri = null,
            captureImageUri = null,
            onDelete = {},
            onSaveTitle = {}
        )

        composeRule.onNodeWithTag("compare_screen_delete_button").assertIsDisplayed()
        composeRule.onNodeWithTag("compare_screen_more_menu_button").assertIsDisplayed()
    }

    private fun setCompareContent(
        referenceImageUri: Uri?,
        captureImageUri: Uri?,
        onBack: () -> Unit = {},
        timestamp: Long? = null,
        onDelete: (() -> Unit)? = null,
        sessionTitle: String? = null,
        onSaveTitle: ((String?) -> Unit)? = null
    ) {
        setHostContent {
            CompareScreen(
                referenceImageUri = referenceImageUri,
                captureImageUri = captureImageUri,
                onBack = onBack,
                timestamp = timestamp,
                onDelete = onDelete,
                sessionTitle = sessionTitle,
                onSaveTitle = onSaveTitle
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
                GhostShotTheme {
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
                GhostShotTheme {
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

    private fun createImageUri(fileNamePrefix: String, color: Int): Uri {
        val file = File.createTempFile(fileNamePrefix, ".png", context.cacheDir)
        tempFiles += file
        val bitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
        return Uri.fromFile(file)
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

    private data class CompareInput(
        val referenceUri: Uri,
        val captureUri: Uri
    )
}
