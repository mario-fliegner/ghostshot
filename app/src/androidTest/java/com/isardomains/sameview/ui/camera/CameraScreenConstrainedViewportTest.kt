package com.isardomains.sameview.ui.camera

import android.Manifest
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.isardomains.sameview.ui.settings.SettingsRepository
import com.isardomains.sameview.ui.theme.SameViewTheme
import java.io.File
import java.util.UUID
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Authoritative production-wiring proof for the shared constrained viewport fix (Block 2).
 *
 * Unlike [ReferenceMarkersOverlayUITest], which exercises [MarkerEditBorder] in an isolated
 * harness with its own explicit viewport size, this class mounts the real [CameraScreen] and
 * asserts against [CameraViewModel.uiState]'s live-published [CameraUiState.viewportWidth] /
 * [CameraUiState.viewportHeight] — the actual production ground truth Box A computes and
 * [ReferenceImageOverlay], [ReferenceMarkerOverlay], and [MarkerEditBorder] must all consume
 * identically. No test re-derives [computeVisibleImageRect]'s formula or hardcodes any
 * device-specific dimension.
 */
@RunWith(AndroidJUnit4::class)
class CameraScreenConstrainedViewportTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @get:Rule
    val cameraPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val tempFiles = mutableListOf<File>()
    private val settingsDataStoreFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
        settingsDataStoreFiles.forEach { it.delete() }
        settingsDataStoreFiles.clear()
    }

    // ── 1. MarkerEditBorder uses the constrained viewport ──────────────────────────────

    @Test
    fun markerEditBorder_usesConstrainedViewport() {
        val viewModel = mountCameraScreenForViewportTest()
        loadReferenceImageAndWait(viewModel)
        viewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        viewModel.enterMarkerEditMode()
        composeRule.waitForIdle()

        val borderBounds = composeRule.onNodeWithTag("marker_edit_border").fetchSemanticsNode().boundsInRoot
        val viewportWidth = viewModel.uiState.value.viewportWidth.toFloat()
        val viewportHeight = viewModel.uiState.value.viewportHeight.toFloat()

        assertTrue(
            "Border width (${borderBounds.width}) must match constrained viewport width ($viewportWidth)",
            abs(borderBounds.width - viewportWidth) <= GEOMETRY_TOLERANCE_PX
        )
        assertTrue(
            "Border height (${borderBounds.height}) must match constrained viewport height ($viewportHeight)",
            abs(borderBounds.height - viewportHeight) <= GEOMETRY_TOLERANCE_PX
        )
    }

    // ── 2. Non-zero vertical offset uses the constrained height ────────────────────────

    @Test
    fun markerEditBorder_nonZeroVerticalOffset_usesConstrainedHeight() {
        val viewModel = mountCameraScreenForViewportTest()
        loadReferenceImageAndWait(viewModel)
        // SHOW_FULL_IMAGE (baseScale = min) leaves letterbox margin at zero offset, so a
        // modest offset shift is observable rather than clamped away at the viewport edge
        // (COMPARE_WITH_PREVIEW's baseScale = max already touches the top/bottom bound).
        viewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.SHOW_FULL_IMAGE)
        viewModel.enterMarkerEditMode()
        composeRule.waitForIdle()

        val baselineTop = composeRule.onNodeWithTag("marker_edit_border").fetchSemanticsNode().boundsInRoot.top

        val offsetDelta = 0.05f
        viewModel.onOverlayDragged(0f, offsetDelta)
        composeRule.waitForIdle()

        val offsetTop = composeRule.onNodeWithTag("marker_edit_border").fetchSemanticsNode().boundsInRoot.top
        val viewportHeight = viewModel.uiState.value.viewportHeight.toFloat()
        val expectedShift = offsetDelta * viewportHeight
        val actualShift = offsetTop - baselineTop

        assertTrue(
            "Border top shift ($actualShift) must match offsetDelta * constrained viewport height " +
                "($expectedShift) — not an idealized aspect-ratio height",
            abs(actualShift - expectedShift) <= GEOMETRY_TOLERANCE_PX
        )
    }

    // ── 3. ReferenceMarkerOverlay shares the same viewport as MarkerEditBorder ─────────

    @Test
    fun referenceMarkerOverlay_projectsMarkerAtBorderCenter() {
        val viewModel = mountCameraScreenForViewportTest()
        loadReferenceImageAndWait(viewModel)
        viewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        viewModel.enterMarkerEditMode()
        composeRule.waitForIdle()

        // Ground truth is read directly from MarkerEditBorder's own rendered bounds — not
        // recomputed from computeVisibleImageRect/normalizedToScreen. In the fill/un-offset
        // COMPARE_WITH_PREVIEW case, the image center coincides with the border's center.
        val borderBounds = composeRule.onNodeWithTag("marker_edit_border").fetchSemanticsNode().boundsInRoot
        val expectedMarkerScreenX = (borderBounds.left + borderBounds.right) / 2f
        val expectedMarkerScreenY = (borderBounds.top + borderBounds.bottom) / 2f

        viewModel.addMarker(normalizedX = 0.5f, normalizedY = 0.5f)
        composeRule.waitForIdle()

        // A short drag starting exactly at the border-derived center: if ReferenceMarkerOverlay
        // hit-tests the marker at that same point, its own viewport agrees with the border's.
        composeRule.onRoot().performTouchInput {
            down(Offset(expectedMarkerScreenX, expectedMarkerScreenY))
            moveTo(Offset(expectedMarkerScreenX, expectedMarkerScreenY + 40f))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("marker_drag_loupe").assertIsDisplayed()

        composeRule.onRoot().performTouchInput { up() }
    }

    // ── 4. ReferenceImageOverlay's live drag path uses the constrained height ──────────

    @Test
    fun overlayDrag_convertsPixelDistanceUsingConstrainedHeight() {
        val viewModel = mountCameraScreenForViewportTest()
        loadReferenceImageAndWait(viewModel)
        viewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        viewModel.enterMarkerEditMode()
        composeRule.waitForIdle()

        // MarkerEditBorder's own bounds (production-observable, not a new tag) supply a
        // reliable on-screen coordinate inside the shared viewport region. No marker exists
        // yet, so the gesture is classified as an overlay pan, not a marker drag.
        val borderBounds = composeRule.onNodeWithTag("marker_edit_border").fetchSemanticsNode().boundsInRoot
        val centerX = (borderBounds.left + borderBounds.right) / 2f
        val centerY = (borderBounds.top + borderBounds.bottom) / 2f
        val viewportHeight = viewModel.uiState.value.viewportHeight.toFloat()
        val dragDistancePx = 600f
        val baselineOffsetY = viewModel.uiState.value.overlayOffsetY

        // ReferenceMarkerOverlay's gesture classifier (awaitEachGesture) consumes the first,
        // slop-crossing move event purely to decide drag-vs-long-press-vs-tap; only movement
        // reported by *subsequent* events reaches the pan-forwarding loop that calls
        // calculatePan() and invokes onOverlayDragged. A single down->moveTo->up gesture would
        // leave nothing after that first move for the forwarding loop to observe (the closing
        // up() event only breaks its loop). A slop-crossing step followed by a second,
        // separately-dispatched move of exactly dragDistancePx is required so the forwarding
        // loop's calculatePan() delta equals dragDistancePx.
        val slopCrossingStepPx = 60f
        composeRule.onRoot().performTouchInput {
            down(Offset(centerX, centerY))
            moveTo(Offset(centerX, centerY + slopCrossingStepPx))
            moveTo(Offset(centerX, centerY + slopCrossingStepPx + dragDistancePx))
            up()
        }
        composeRule.waitForIdle()

        val expectedOffsetYDelta = dragDistancePx / viewportHeight
        val actualOffsetYDelta = viewModel.uiState.value.overlayOffsetY - baselineOffsetY

        assertTrue(
            "Resulting overlayOffsetY delta ($actualOffsetYDelta) must match dragDistancePx / " +
                "constrained viewport height ($expectedOffsetYDelta) — proves whichever real " +
                "composable handled the gesture used the shared constrained height, not an " +
                "idealized aspect-ratio height",
            abs(actualOffsetYDelta - expectedOffsetYDelta) <= OFFSET_TOLERANCE
        )
    }

    // ── 5. Old idealized 9:16 sizing is rejected ───────────────────────────────────────

    @Test
    fun markerEditBorder_rejectsIdealizedAspectRatioHeight() {
        val viewModel = mountCameraScreenForViewportTest()
        loadReferenceImageAndWait(viewModel)
        viewModel.onReferenceImageDisplayModeChanged(ReferenceImageDisplayMode.COMPARE_WITH_PREVIEW)
        viewModel.enterMarkerEditMode()
        composeRule.waitForIdle()

        val viewportWidth = viewModel.uiState.value.viewportWidth.toFloat()
        val viewportHeight = viewModel.uiState.value.viewportHeight.toFloat()
        val idealizedHeight = viewportWidth * 16f / 9f

        // Precondition: this run must actually be in the height-constrained scenario this fix
        // targets, derived only from live production state (no hardcoded device dimensions).
        // On a device/config where the idealized and actual heights coincide (e.g. a genuinely
        // 16:9 viewport with no system-bar-driven mismatch), there is nothing for this specific
        // check to distinguish — skip (not fail) rather than report a false production failure
        // for a device geometry this check doesn't apply to. The width/height-match assertions
        // in markerEditBorder_usesConstrainedViewport still cover this device unconditionally.
        Assume.assumeTrue(
            "Skipping: idealized 16:9 height ($idealizedHeight) is not meaningfully taller than " +
                "the actual constrained viewport height ($viewportHeight) on this device/config " +
                "— this device does not exhibit the height-constrained scenario this check targets",
            (idealizedHeight - viewportHeight) > MIN_MEANINGFUL_GAP_PX
        )

        val borderBounds = composeRule.onNodeWithTag("marker_edit_border").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Border height (${borderBounds.height}) must match the true constrained viewport " +
                "height ($viewportHeight)",
            abs(borderBounds.height - viewportHeight) <= GEOMETRY_TOLERANCE_PX
        )
        assertTrue(
            "Border height (${borderBounds.height}) must NOT match the idealized 16:9 height " +
                "($idealizedHeight)",
            abs(borderBounds.height - idealizedHeight) > GEOMETRY_TOLERANCE_PX
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────

    private fun mountCameraScreenForViewportTest(): CameraViewModel {
        wakeTestDevice()
        val settingsFile = File(context.cacheDir, "camera_settings_${UUID.randomUUID()}.preferences_pb")
        settingsDataStoreFiles += settingsFile
        val settingsPrefs = PreferenceDataStoreFactory.create { settingsFile }
        val viewModel = CameraViewModel(context, SettingsRepository(settingsPrefs))

        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    CameraScreen(viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.viewportWidth > 0 && viewModel.uiState.value.viewportHeight > 0
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun loadReferenceImageAndWait(viewModel: CameraViewModel) {
        val uri = createReferenceImageUri()
        viewModel.onReferenceImageSelected(uri)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.referenceImageMetadata != null
        }
        composeRule.waitForIdle()
    }

    private fun createReferenceImageUri(): Uri {
        val file = File(context.cacheDir, "viewport_test_reference_${UUID.randomUUID()}.jpg")
        tempFiles += file
        InstrumentationRegistry.getInstrumentation().context.assets.open("portrait_tall.jpg").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(file)
    }

    private fun wakeTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }

    private companion object {
        const val GEOMETRY_TOLERANCE_PX = 4f
        const val OFFSET_TOLERANCE = 0.015f
        const val MIN_MEANINGFUL_GAP_PX = 20f
    }
}
