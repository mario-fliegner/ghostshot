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
import androidx.compose.ui.unit.Dp
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
        // rotated dimensions. Compact stays on the vertical stack regardless of height — this
        // test targets that unchanged Compact behavior specifically (see the Medium-Row tests
        // below for the new short-height exception, which is Medium-only).
        setPreviewContent(containerWidth = 700.dp, containerHeight = 260.dp)

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

    // ── Medium short-height Row layout ───────────────────────────────────────

    @Test
    fun mediumShortHeight_usesRowLayout() {
        // Below the 420dp threshold — must switch to the side-by-side Row.
        setPreviewContent(
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
            containerWidth = 700.dp,
            containerHeight = 300.dp
        )

        // Geometric proof of a Row (not a Column), without asserting on internal
        // implementation details: in a side-by-side layout, the Share button's vertical
        // extent overlaps the player area's vertical extent. In a vertical stack, Actions
        // sit entirely below the player area with no vertical overlap at all.
        val playerAreaBounds = composeRule.onNodeWithTag("create_video_preview_player_area")
            .fetchSemanticsNode().boundsInRoot
        val shareBounds = composeRule.onNodeWithTag("create_video_action_share")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected the Share button (top=${shareBounds.top}, bottom=${shareBounds.bottom}) to " +
                "vertically overlap the player area (top=${playerAreaBounds.top}, " +
                "bottom=${playerAreaBounds.bottom}) — evidence of a side-by-side Row layout",
            shareBounds.top < playerAreaBounds.bottom && shareBounds.bottom > playerAreaBounds.top
        )
        // And horizontally: Actions must sit to the right of the player area, not overlapping it.
        assertTrue(
            "Expected the Share button's left edge (${shareBounds.left}) to sit at or right of " +
                "the player area's right edge (${playerAreaBounds.right})",
            shareBounds.left >= playerAreaBounds.right - 1f
        )
    }

    @Test
    fun mediumShortHeight_playerAreaMatchesCardWidth() {
        setPreviewContent(
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
            containerWidth = 700.dp,
            containerHeight = 300.dp
        )

        val playerAreaBounds = composeRule.onNodeWithTag("create_video_preview_player_area")
            .fetchSemanticsNode().boundsInRoot
        val cardBounds = composeRule.onNodeWithTag("create_video_preview_player_card")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected the player area width (${playerAreaBounds.width}) to closely match the " +
                "card width (${cardBounds.width}) instead of being artificially inflated by a " +
                "weight(1f) zone — the gap that caused the reported 'two islands' appearance",
            kotlin.math.abs(playerAreaBounds.width - cardBounds.width) <= 2f
        )
    }

    @Test
    fun mediumShortHeight_contentGroupIsHorizontallyCentered() {
        setPreviewContent(
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
            containerWidth = 700.dp,
            containerHeight = 300.dp
        )

        val containerBounds = composeRule.onNodeWithTag("create_video_preview_test_container")
            .fetchSemanticsNode().boundsInRoot
        val playerAreaBounds = composeRule.onNodeWithTag("create_video_preview_player_area")
            .fetchSemanticsNode().boundsInRoot
        val shareBounds = composeRule.onNodeWithTag("create_video_action_share")
            .fetchSemanticsNode().boundsInRoot

        val leftMargin = playerAreaBounds.left - containerBounds.left
        // Share fillMaxWidth()s the Actions column, so its right edge represents the group's
        // own right edge without asserting on any internal implementation detail.
        val rightMargin = containerBounds.right - shareBounds.right

        // Robust, non-pixel-exact tolerance: compares the group's two outer margins against
        // each other, not against a fixed device-specific pixel value.
        val tolerance = containerBounds.width * 0.05f
        assertTrue(
            "Expected the content group's left margin (${leftMargin}) and right margin " +
                "(${rightMargin}) to be comparable — evidence the group is centered as a whole " +
                "(tolerance=${tolerance})",
            kotlin.math.abs(leftMargin - rightMargin) <= tolerance
        )
    }

    @Test
    fun mediumShortHeight_noHorizontalOverflow() {
        // The fallback 4f/3f ratio is the only one reachable without a real video asset (see
        // class doc). Two different container shapes exercise both binding-constraint regimes
        // the width-cap formula can hit — a wide-short container (width more binding, as a wide
        // 16:9-like video would also produce) and a narrower Medium container (height more
        // binding, as a narrow 9:16-like video would also produce) — without altering
        // production code to inject a specific aspect ratio, which is out of scope here.
        for ((width, height) in listOf(820.dp to 300.dp, 620.dp to 400.dp)) {
            setPreviewContent(
                windowWidthSizeClass = WindowWidthSizeClass.Medium,
                containerWidth = width,
                containerHeight = height
            )

            val containerTag = "create_video_preview_test_container"
            assertFullyContained("create_video_preview_player_area", containerTag)
            assertFullyContained("create_video_preview_player_card", containerTag)
            assertFullyContained("create_video_action_share", containerTag)
            assertFullyContained("create_video_action_done", containerTag)
            assertFullyContained("create_video_action_delete", containerTag)
        }
    }

    @Test
    fun mediumSufficientHeight_staysColumnLayout() {
        // At or above the 420dp threshold — must keep the existing vertical stack.
        setPreviewContent(
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
            containerWidth = 700.dp,
            containerHeight = 700.dp
        )

        val playerAreaBounds = composeRule.onNodeWithTag("create_video_preview_player_area")
            .fetchSemanticsNode().boundsInRoot
        val shareBounds = composeRule.onNodeWithTag("create_video_action_share")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected the Share button (top=${shareBounds.top}) to sit below the player area " +
                "(bottom=${playerAreaBounds.bottom}) — evidence the vertical stack remained active",
            shareBounds.top >= playerAreaBounds.bottom - 1f
        )
    }

    @Test
    fun mediumShortHeight_actionsFullyVisible() {
        setPreviewContent(
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
            containerWidth = 700.dp,
            containerHeight = 300.dp
        )

        val containerTag = "create_video_preview_test_container"
        assertFullyContained("create_video_action_share", containerTag)
        assertFullyContained("create_video_action_done", containerTag)
        assertFullyContained("create_video_action_delete", containerTag)
    }

    @Test
    fun mediumShortHeight_actionsRemainClickable() {
        var doneCount = 0
        var deleteCount = 0
        setPreviewContent(
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
            containerWidth = 700.dp,
            containerHeight = 300.dp,
            onDone = { doneCount++ },
            onDelete = { deleteCount++ }
        )

        composeRule.onNodeWithTag("create_video_action_done").performClick()
        composeRule.waitForIdle()
        assertTrue("Expected onDone to be invoked exactly once in the Row layout", doneCount == 1)

        composeRule.onNodeWithTag("create_video_action_delete").performClick()
        composeRule.waitForIdle()
        assertTrue(
            "onDelete must not fire before the confirmation dialog is confirmed, in the Row layout",
            deleteCount == 0
        )
    }

    @Test
    fun mediumShortHeight_playerGetsMoreUsableSpace() {
        val width = 700.dp
        val height = 300.dp

        // WindowWidthSizeClass.Compact at this exact box size exercises the same vertical-stack
        // branch that Medium used before this change (and that Medium-with-sufficient-height
        // still uses) — a controlled, apples-to-apples "what the old layout would have produced
        // at this exact size" comparison. Not a claim about how Compact windows actually behave.
        val rowCardArea = measureCardArea(WindowWidthSizeClass.Medium, width, height)
        val columnFallbackCardArea = measureCardArea(WindowWidthSizeClass.Compact, width, height)

        assertTrue(
            "Expected the Medium short-height Row card area (${rowCardArea}px²) to be " +
                "substantially larger than the same-size vertical-stack card area " +
                "(${columnFallbackCardArea}px²) — a generous, non-pixel-exact margin, not a " +
                "device-specific ratio",
            rowCardArea > columnFallbackCardArea * 1.2f
        )
    }

    @Test
    fun mediumShortHeight_buttonTextsNotWrapped_touchTargetsPreserved() {
        setPreviewContent(
            windowWidthSizeClass = WindowWidthSizeClass.Medium,
            containerWidth = 700.dp,
            containerHeight = 300.dp
        )

        for (tag in listOf("create_video_action_share", "create_video_action_done", "create_video_action_delete")) {
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            // Single-line M3 buttons stay well under this height; wrapped (2-line) text would
            // push them clearly above it — an indirect, resolution-independent wrap detector.
            assertTrue(
                "$tag height (${bounds.height}) suggests wrapped text in the narrow Actions column",
                bounds.height <= 60.dp.toPx()
            )
            // ButtonDefaults.MinHeight (Material 3) — touch target floor must be preserved.
            assertTrue(
                "$tag height (${bounds.height}) is below the minimum touch target height",
                bounds.height >= 40.dp.toPx()
            )
        }
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

    /** Composes [PreviewContent] with the given size/state, returns the player card's area in px². */
    private fun measureCardArea(windowWidthSizeClass: WindowWidthSizeClass, width: Dp, height: Dp): Float {
        setPreviewContent(windowWidthSizeClass = windowWidthSizeClass, containerWidth = width, containerHeight = height)
        val cardBounds = composeRule.onNodeWithTag("create_video_preview_player_card").fetchSemanticsNode().boundsInRoot
        return cardBounds.width * cardBounds.height
    }

    private fun Dp.toPx(): Float {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        return value * density
    }

    private fun setPreviewContent(
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        onDelete: () -> Unit = {},
        onDone: () -> Unit = {},
        containerWidth: Dp? = null,
        containerHeight: Dp? = null
    ) {
        wakeTestDevice()
        scenario?.close()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    // Deterministic, device-independent sizing (tagged only in this test file,
                    // not in production code) rather than relying on the physical test device's
                    // actual dimensions.
                    val contentModifier = if (containerWidth != null && containerHeight != null) {
                        Modifier
                            .size(width = containerWidth, height = containerHeight)
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
