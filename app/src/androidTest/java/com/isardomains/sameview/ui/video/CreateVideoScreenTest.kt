package com.isardomains.sameview.ui.video

import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Geometry- and semantics-focused tests for [PreviewContent] (CreateVideoScreen's finished-video
 * Preview state, aligned with the Rendering-state loading-preview layout language — see
 * VIDEO_EXPORT_V1.md §7.5 and RESPONSIVE_LAYOUT_SYSTEM_V1.md Addendum §A9). No real MP4 asset is
 * used: the player is pointed at a non-existent content URI, so every test exercises the
 * fallback (4f/3f) aspect ratio path. These tests verify Compose container geometry and button
 * semantics only — real ExoPlayer pixel rendering cannot be asserted from a Compose UI test.
 *
 * Every assertion checks the actual leaf node (player card, or an individual action button)
 * against real available-area bounds directly — never a wrapping container's bounds alone —
 * since Compose can silently clamp a container's own reported size while still placing children
 * beyond it; that exact failure mode is what let an earlier, since-rolled-back regression pass
 * its tests.
 */
@RunWith(AndroidJUnit4::class)
class CreateVideoScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null
    private val fakeVideoUri: Uri = Uri.parse("content://com.isardomains.sameview.test/fake_preview.mp4")

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun playerCard_isFullyWithinPlayerArea_compactPortrait() {
        setPreviewContent()

        assertFullyContained("create_video_preview_player_card", "create_video_preview_player_area")
    }

    @Test
    fun shareButton_isFullyWithinRootBounds() {
        setPreviewContent()

        assertFullyContainedInRoot("create_video_action_share")
    }

    @Test
    fun doneButton_isFullyWithinRootBounds() {
        setPreviewContent()

        assertFullyContainedInRoot("create_video_action_done")
    }

    @Test
    fun deleteButton_isFullyWithinRootBounds() {
        setPreviewContent()

        assertFullyContainedInRoot("create_video_action_delete")
    }

    @Test
    fun playerCard_hasPositiveGapAboveAvailablePlayerArea_whenHeightPermits() {
        setPreviewContent()

        val areaBounds = composeRule.onNodeWithTag("create_video_preview_player_area")
            .fetchSemanticsNode().boundsInRoot
        val cardBounds = composeRule.onNodeWithTag("create_video_preview_player_card")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected the card's top edge (${cardBounds.top}) to sit below the player area's " +
                "top edge (${areaBounds.top}) — i.e. a positive gap above the card",
            cardBounds.top > areaBounds.top + 1f
        )
    }

    @Test
    fun compactPortrait_doesNotOverflowRootBounds() {
        setPreviewContent()

        assertFullyContainedInRoot("create_video_preview_player_card")
        assertFullyContainedInRoot("create_video_action_share")
        assertFullyContainedInRoot("create_video_action_done")
        assertFullyContainedInRoot("create_video_action_delete")
    }

    @Test
    fun shortCompactLandscapeHeight_actionsFullyVisible_cardShrinks_noOverflow() {
        // Deterministic, device-independent short-height scenario: the composable under test
        // is wrapped in an explicit small fixed-size container (tagged only in this test file,
        // not in production code) rather than relying on the physical test device's actual
        // rotated dimensions.
        setPreviewContent(constrainedSize = true)

        val containerTag = "create_video_preview_test_container"
        assertFullyContained("create_video_action_share", containerTag)
        assertFullyContained("create_video_action_done", containerTag)
        assertFullyContained("create_video_action_delete", containerTag)
        assertFullyContained("create_video_preview_player_card", containerTag)

        val areaBounds = composeRule.onNodeWithTag("create_video_preview_player_area")
            .fetchSemanticsNode().boundsInRoot
        val cardBounds = composeRule.onNodeWithTag("create_video_preview_player_card")
            .fetchSemanticsNode().boundsInRoot

        // The card must respect the 90% height cap of the (now small) player area — direct
        // evidence that it shrank rather than overflowing into the Actions' reserved space.
        assertTrue(
            "Card height (${cardBounds.height}) must not exceed 90% of the player area " +
                "height (${areaBounds.height}) plus a small rounding tolerance",
            cardBounds.height <= areaBounds.height * 0.90f + 2f
        )
    }

    @Test
    fun expandedLayout_totalWidthDoesNotExceed800Dp() {
        setPreviewContent(windowWidthSizeClass = WindowWidthSizeClass.Expanded)

        val areaBounds = composeRule.onNodeWithTag("create_video_preview_player_area")
            .fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        val maxWidthPx = 800f * density

        assertTrue(
            "Expanded player area width (${areaBounds.width}px) must not exceed the 800dp " +
                "cap ($maxWidthPx px)",
            areaBounds.width <= maxWidthPx + 1f
        )
    }

    @Test
    fun fallbackRatio_beforeVideoSizeKnown_producesValidNonOverflowingLayout() {
        // The player is pointed at a non-existent URI, so onVideoSizeChanged never fires and
        // the layout stays on the 4f/3f fallback ratio for the test's full duration — this is
        // exactly the "fallback, not yet loaded" scenario this test targets.
        setPreviewContent()

        assertFullyContained("create_video_preview_player_card", "create_video_preview_player_area")
        assertFullyContainedInRoot("create_video_action_share")
        assertFullyContainedInRoot("create_video_action_done")
        assertFullyContainedInRoot("create_video_action_delete")
    }

    @Test
    fun doneAndDeleteCallbacks_remainCorrectlyWired() {
        var doneCount = 0
        var deleteCount = 0
        setPreviewContent(onDone = { doneCount++ }, onDelete = { deleteCount++ })

        composeRule.onNodeWithTag("create_video_action_done").performClick()
        composeRule.waitForIdle()
        assertTrue("Expected onDone to be invoked exactly once", doneCount == 1)

        composeRule.onNodeWithTag("create_video_action_delete").performClick()
        composeRule.waitForIdle()
        // Delete is confirmation-gated (see the AlertDialog in PreviewContent) — the callback
        // must not fire before the dialog is confirmed.
        assertTrue("onDelete must not fire before the confirmation dialog is confirmed", deleteCount == 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertFullyContained(childTag: String, parentTag: String) {
        val parentBounds = composeRule.onNodeWithTag(parentTag).fetchSemanticsNode().boundsInRoot
        val childBounds = composeRule.onNodeWithTag(childTag).fetchSemanticsNode().boundsInRoot

        assertTrue("$childTag left edge must be within $parentTag bounds", childBounds.left >= parentBounds.left - 1f)
        assertTrue("$childTag top edge must be within $parentTag bounds", childBounds.top >= parentBounds.top - 1f)
        assertTrue("$childTag right edge must be within $parentTag bounds", childBounds.right <= parentBounds.right + 1f)
        assertTrue("$childTag bottom edge must be within $parentTag bounds", childBounds.bottom <= parentBounds.bottom + 1f)
    }

    private fun assertFullyContainedInRoot(childTag: String) {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val childBounds = composeRule.onNodeWithTag(childTag).fetchSemanticsNode().boundsInRoot

        assertTrue("$childTag left edge must be within root bounds", childBounds.left >= rootBounds.left - 1f)
        assertTrue("$childTag top edge must be within root bounds", childBounds.top >= rootBounds.top - 1f)
        assertTrue("$childTag right edge must be within root bounds", childBounds.right <= rootBounds.right + 1f)
        assertTrue("$childTag bottom edge must be within root bounds", childBounds.bottom <= rootBounds.bottom + 1f)
    }

    private fun setPreviewContent(
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        onDelete: () -> Unit = {},
        onDone: () -> Unit = {},
        constrainedSize: Boolean = false
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
                SameViewTheme {
                    val contentModifier = if (constrainedSize) {
                        Modifier
                            .size(width = 700.dp, height = 260.dp)
                            .testTag("create_video_preview_test_container")
                    } else {
                        Modifier
                    }
                    PreviewContent(
                        state = CreateVideoState.Preview(videoUri = fakeVideoUri),
                        onDelete = onDelete,
                        onDone = onDone,
                        windowWidthSizeClass = windowWidthSizeClass,
                        modifier = contentModifier
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun wakeTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }
}
