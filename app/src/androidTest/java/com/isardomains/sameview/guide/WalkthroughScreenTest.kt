package com.isardomains.sameview.guide

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

@RunWith(AndroidJUnit4::class)
class WalkthroughScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    // ── Existing tests updated for renamed strings / new illustration tags ────

    @Test
    fun firstPage_rendersApprovedContentAndDots() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_screen_root").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_mockup_then_and_now").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_progress_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
    }

    @Test
    fun nextAdvancesThroughPagesAndPageFourShowsBackAndStart() {
        setWalkthroughContent()

        // Page 1 → page 2
        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_step2_image").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()

        // Page 2 → page 3
        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_take_shot_title))
            .assertIsDisplayed()

        // Page 3 → page 4
        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_start").assertIsDisplayed()
    }

    @Test
    fun backOnLastPageReturnsToCapturePage() {
        setWalkthroughContent()
        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_take_shot_title))
            .assertIsDisplayed()
    }

    @Test
    fun skipAndStartInvokeCallbacks() {
        var skipCount = 0
        var startCount = 0
        setWalkthroughContent(
            onSkip = { skipCount++ },
            onStart = { startCount++ }
        )

        composeRule.onNodeWithTag("walkthrough_skip").performClick()
        composeRule.waitForIdle()
        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("walkthrough_start").performClick()
        composeRule.waitForIdle()

        assertEquals(1, skipCount)
        assertEquals(1, startCount)
    }

    @Test
    fun expandedLayoutUsesTwoColumns() {
        setWalkthroughContent(windowWidthSizeClass = WindowWidthSizeClass.Expanded)

        composeRule.onNodeWithTag("walkthrough_two_column_layout").assertIsDisplayed()
    }

    // ── Button model ──────────────────────────────────────────────────────────

    @Test
    fun page1_hasSkipAndNextNoBack() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
        composeRule.onAllNodesWithTag("walkthrough_back").assertCountEquals(0)
    }

    @Test
    fun page2_hasSkipBackAndNext() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
    }

    @Test
    fun page3_hasSkipBackAndNext() {
        setWalkthroughContent()

        repeat(2) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()
    }

    @Test
    fun page4_hasBackAndStartNoSkip() {
        setWalkthroughContent()

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_start").assertIsDisplayed()
        composeRule.onAllNodesWithTag("walkthrough_skip").assertCountEquals(0)
    }

    @Test
    fun backOnPage2ReturnsToPage1() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun backOnPage3ReturnsToPage2() {
        setWalkthroughContent()

        repeat(2) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    // ── Swipe navigation ──────────────────────────────────────────────────────

    @Test
    fun swipeLeftOnPage1AdvancesToPage2() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeRightOnPage2ReturnsToPage1() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeDoesNotAdvancePastPage4() {
        setWalkthroughContent()

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeDoesNotGoBeforePage1() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun progressDotsUpdateOnSwipe() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        // Page title change confirms pager advanced; dots are always visible
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_progress_dots").assertIsDisplayed()
    }

    // ── Swipe area expansion regression tests ───────────────────────────────────
    //
    // These target coordinates derived from measured node bounds (walkthrough_pager vs.
    // the shared container) rather than hardcoded pixel values, so they stay valid across
    // screen sizes. Each test asserts its geometry assumption (margin exists, point falls
    // outside the pager) before acting, so a future layout change that removes the margin
    // fails loudly here instead of silently testing the wrong region.

    @Test
    fun swipeInPortraitSideMarginOutsidePagerAdvancesToPage2() {
        setWalkthroughContent()

        val pagerBounds = composeRule.onNodeWithTag("walkthrough_pager").fetchSemanticsNode().boundsInRoot
        val containerBounds = composeRule.onNodeWithTag("walkthrough_single_column_layout")
            .fetchSemanticsNode().boundsInRoot

        val marginWidth = pagerBounds.left - containerBounds.left
        assertTrue(
            "Portrait layout must leave a horizontal margin beside the pager for this test to be meaningful",
            marginWidth > 0f
        )

        val y = pagerBounds.center.y
        val startX = pagerBounds.left - marginWidth * 0.25f
        // The drag must travel far enough for PagerState's fling behavior to snap forward —
        // a distance confined to the (narrow) margin itself is too short to cross that
        // threshold, even though the start point is what this test needs to verify. Extend
        // the end point well past the margin, matching the distance used by the sibling
        // "space above image" tests below, which use a comparable fraction of pagerBounds.
        val endX = startX - pagerBounds.width * 0.8f
        assertTrue("Swipe start must fall outside the pager's horizontal bounds", startX < pagerBounds.left)

        dragHorizontally(startX, endX, y)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeInPortraitSpaceAboveImageAdvancesToPage2() {
        setWalkthroughContent()

        // Derived from onRoot() and the pager (not walkthrough_single_column_layout /
        // walkthrough_content), so this test no longer merely re-confirms the capped gesture
        // host that used to carry .scrollable() directly — it now proves the relocated
        // root-level handler covers this same region.
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val pagerBounds = composeRule.onNodeWithTag("walkthrough_pager").fetchSemanticsNode().boundsInRoot

        val marginHeight = pagerBounds.top - rootBounds.top
        assertTrue(
            "Portrait layout must leave leading slack space above the pager for this test to be meaningful",
            marginHeight > 0f
        )

        // Stay close to the pager's own top edge — a point already proven safe by the existing
        // on-image swipe tests — rather than interpolating toward the literal root top, which
        // may fall within a system status bar region on edge-to-edge windows.
        val y = pagerBounds.top - marginHeight * 0.15f
        assertTrue("Swipe origin Y must fall outside the pager's vertical bounds", y < pagerBounds.top)
        assertTrue("Swipe origin Y must fall within root bounds", y > rootBounds.top)

        val startX = pagerBounds.right - pagerBounds.width * 0.1f
        val endX = pagerBounds.left + pagerBounds.width * 0.1f

        dragHorizontally(startX, endX, y)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeInLandscapeSpaceAboveImageAdvancesToPage2() {
        setWalkthroughContent(windowWidthSizeClass = WindowWidthSizeClass.Expanded)

        // Derived from onRoot() and the pager (not walkthrough_two_column_layout /
        // walkthrough_content) — see the Portrait counterpart above for the same rationale.
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val pagerBounds = composeRule.onNodeWithTag("walkthrough_pager").fetchSemanticsNode().boundsInRoot

        val marginHeight = pagerBounds.top - rootBounds.top
        assertTrue(
            "Landscape layout must leave a vertical margin above the pager for this test to be meaningful",
            marginHeight > 0f
        )

        val y = pagerBounds.top - marginHeight * 0.15f
        assertTrue("Swipe origin Y must fall outside the pager's vertical bounds", y < pagerBounds.top)
        assertTrue("Swipe origin Y must fall within root bounds", y > rootBounds.top)

        val startX = pagerBounds.right - pagerBounds.width * 0.1f
        val endX = pagerBounds.left + pagerBounds.width * 0.1f

        dragHorizontally(startX, endX, y)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    @Test
    fun tapInPortraitSideMarginDoesNotChangePage() {
        setWalkthroughContent()

        val pagerBounds = composeRule.onNodeWithTag("walkthrough_pager").fetchSemanticsNode().boundsInRoot
        val containerBounds = composeRule.onNodeWithTag("walkthrough_single_column_layout")
            .fetchSemanticsNode().boundsInRoot

        val marginWidth = pagerBounds.left - containerBounds.left
        assertTrue(
            "Portrait layout must leave a horizontal margin beside the pager for this test to be meaningful",
            marginWidth > 0f
        )

        val x = containerBounds.left + marginWidth * 0.5f
        val y = pagerBounds.center.y
        assertTrue("Tap origin must fall outside the pager's horizontal bounds", x < pagerBounds.left)

        composeRule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    // ── Root outer padding gutter (root-level gesture host boundary) ────────────
    //
    // On the real instrumentation device (SM-S911B, 360dp portrait / 780dp landscape available
    // width), walkthrough_content's 420dp/880dp width cap can never bind — the physical screen
    // is narrower than the cap plus the root Box's own horizontal padding in both orientations
    // (confirmed by measurement: content fills the padded area edge-to-edge, margin = padding
    // only). A Box(Modifier.size(700.dp, 900.dp)) test wrapper cannot force a wider virtual
    // window either: Modifier.size() coerces to the incoming constraints of the real Activity
    // window and cannot exceed the physical display, so the wrapper measured at exactly the
    // device's native resolution regardless of the requested size (verified via boundsInRoot
    // logging: root == 1080x2340px, the S23's real resolution, not 700x900dp).
    //
    // Consequently, the specific scenario this task's Scope Confirmation targeted — a swipeable
    // margin caused by walkthrough_content's width cap — is not reproducible via
    // connectedDebugAndroidTest on this hardware; it only occurs on tablets, foldables, or
    // windowed/desktop targets wider than 468dp (portrait) / 928dp (landscape). That gap is
    // reported in the Abschlussbericht rather than silently glossed over.
    //
    // What IS real and provable on this device: the root Box's own .padding(horizontal = 24dp,
    // vertical = 16dp) is applied BEFORE .scrollable() in the modifier chain (see
    // WalkthroughScreen.kt), so .scrollable()'s pointer-input region is the padded interior —
    // it deliberately does not extend into that outer padding gutter. These tests verify that
    // boundary: gestures starting in the root's outer padding do not change the page, in both
    // orientations, at both page-sequence boundaries, for both tap and drag. This guards against
    // a future modifier-order regression accidentally consuming touches in the decorative
    // padding, without overclaiming coverage of the width-cap scenario.

    @Test
    fun swipeInLeftRootPaddingDoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            containerWidth = 700.dp,
            containerHeight = 900.dp
        )

        // walkthrough_screen_root (WalkthroughScreen's own outermost Surface), not onRoot():
        // onRoot() reflects the full, unconstrained test-Activity window; walkthrough_screen_root
        // is the node .scrollable() is measured relative to — see dragHorizontally's kdoc.
        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = contentBounds.left - rootBounds.left
        assertTrue(
            "Root Box must have a measurable left padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val startX = contentBounds.left - marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Start point must fall within root bounds", startX > rootBounds.left)
        assertTrue(
            "Start point must fall outside walkthrough_content — this test targets exactly the " +
                "root Box's own outer padding gutter, not the (on this device unreachable) " +
                "content-cap margin",
            startX < contentBounds.left
        )

        // Leftward drag (decreasing X) would advance to the next page if it registered at all.
        val endX = startX - rootBounds.width * 0.3f

        dragHorizontally(startX, endX, y, targetTag = "walkthrough_screen_root")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeInRightRootPaddingDoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            containerWidth = 700.dp,
            containerHeight = 900.dp
        )

        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = rootBounds.right - contentBounds.right
        assertTrue(
            "Root Box must have a measurable right padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val startX = contentBounds.right + marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Start point must fall within root bounds", startX < rootBounds.right)
        assertTrue(
            "Start point must fall outside walkthrough_content — this test targets exactly the " +
                "root Box's own outer padding gutter, not the (on this device unreachable) " +
                "content-cap margin",
            startX > contentBounds.right
        )

        // Leftward drag (decreasing X) would advance to the next page if it registered at all.
        val endX = startX - rootBounds.width * 0.5f

        dragHorizontally(startX, endX, y, targetTag = "walkthrough_screen_root")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun tapInLeftRootPaddingDoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            containerWidth = 700.dp,
            containerHeight = 900.dp
        )

        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = contentBounds.left - rootBounds.left
        assertTrue(
            "Root Box must have a measurable left padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val x = contentBounds.left - marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Tap origin must fall within root bounds", x > rootBounds.left)
        assertTrue("Tap origin must fall outside walkthrough_content", x < contentBounds.left)

        // performTouchInput coordinates are local to walkthrough_screen_root's own bounds, not
        // root-space — rootBounds here is that node's own boundsInRoot, so its top-left is the
        // conversion offset (see dragHorizontally's kdoc for the same root cause).
        composeRule.onNodeWithTag("walkthrough_screen_root").performTouchInput {
            down(Offset(x, y) - rootBounds.topLeft)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun tapInRightRootPaddingDoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            containerWidth = 700.dp,
            containerHeight = 900.dp
        )

        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = rootBounds.right - contentBounds.right
        assertTrue(
            "Root Box must have a measurable right padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val x = contentBounds.right + marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Tap origin must fall within root bounds", x < rootBounds.right)
        assertTrue("Tap origin must fall outside walkthrough_content", x > contentBounds.right)

        composeRule.onNodeWithTag("walkthrough_screen_root").performTouchInput {
            down(Offset(x, y) - rootBounds.topLeft)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeInLeftRootPaddingInLandscapeDoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Expanded,
            containerWidth = 1200.dp,
            containerHeight = 700.dp
        )

        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = contentBounds.left - rootBounds.left
        assertTrue(
            "Root Box must have a measurable left padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val startX = contentBounds.left - marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Start point must fall within root bounds", startX > rootBounds.left)
        assertTrue("Start point must fall outside walkthrough_content", startX < contentBounds.left)

        val endX = startX - rootBounds.width * 0.3f

        dragHorizontally(startX, endX, y, targetTag = "walkthrough_screen_root")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeInRightRootPaddingInLandscapeDoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Expanded,
            containerWidth = 1200.dp,
            containerHeight = 700.dp
        )

        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = rootBounds.right - contentBounds.right
        assertTrue(
            "Root Box must have a measurable right padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val startX = contentBounds.right + marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Start point must fall within root bounds", startX < rootBounds.right)
        assertTrue("Start point must fall outside walkthrough_content", startX > contentBounds.right)

        val endX = startX - rootBounds.width * 0.5f

        dragHorizontally(startX, endX, y, targetTag = "walkthrough_screen_root")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeInRootPaddingOnPage1DoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            containerWidth = 700.dp,
            containerHeight = 900.dp
        )

        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = contentBounds.left - rootBounds.left
        assertTrue(
            "Root Box must have a measurable left padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val startX = contentBounds.left - marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Start point must fall outside walkthrough_content", startX < contentBounds.left)

        // Rightward drag (increasing X) would request the previous page if it registered at all
        // — doubly guarded here since page 1 has no previous page either way.
        val endX = startX + rootBounds.width * 0.3f

        dragHorizontally(startX, endX, y, targetTag = "walkthrough_screen_root")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeInRootPaddingOnPage4DoesNotChangePage() {
        setWalkthroughContent(
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            containerWidth = 700.dp,
            containerHeight = 900.dp
        )

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()

        val rootBounds = composeRule.onNodeWithTag("walkthrough_screen_root").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("walkthrough_content").fetchSemanticsNode().boundsInRoot

        val marginWidth = rootBounds.right - contentBounds.right
        assertTrue(
            "Root Box must have a measurable right padding gutter for this test to be meaningful",
            marginWidth > 0f
        )

        val startX = contentBounds.right + marginWidth * 0.5f
        val y = rootBounds.top + rootBounds.height * 0.5f
        assertTrue("Start point must fall outside walkthrough_content", startX > contentBounds.right)

        // Leftward drag (decreasing X) would request the next page if it registered at all —
        // doubly guarded here since page 4 has no next page either way.
        val endX = startX - rootBounds.width * 0.5f

        dragHorizontally(startX, endX, y, targetTag = "walkthrough_screen_root")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()
    }

    @Test
    fun swipeOnTextSlotAdvancesToPage2() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_body").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()
    }

    @Test
    fun buttonTapsStillWorkAfterSwipeAreaExpansion() {
        var skipCount = 0
        var startCount = 0
        setWalkthroughContent(
            onSkip = { skipCount++ },
            onStart = { startCount++ }
        )

        // Skip sits inside the now-swipeable single-column container on page 1; a discrete
        // tap must still register as a click, not be swallowed by the ancestor swipe area.
        composeRule.onNodeWithTag("walkthrough_skip").performClick()
        composeRule.waitForIdle()
        assertEquals(1, skipCount)

        // Next: page 1 -> 2
        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_align_overlay_title))
            .assertIsDisplayed()

        // Back: page 2 -> 1
        composeRule.onNodeWithTag("walkthrough_back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_then_and_now_title))
            .assertIsDisplayed()

        // Next through to page 4, then Start.
        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_start").performClick()
        composeRule.waitForIdle()
        assertEquals(1, startCount)
    }

    // ── Responsive ────────────────────────────────────────────────────────────

    @Test
    fun mediumLayoutUsesTwoColumns() {
        setWalkthroughContent(windowWidthSizeClass = WindowWidthSizeClass.Medium)

        composeRule.onNodeWithTag("walkthrough_two_column_layout").assertIsDisplayed()
    }

    // ── Illustration tags ─────────────────────────────────────────────────────

    @Test
    fun page1IllustrationHasStep1Image() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_step1_image").assertIsDisplayed()
    }

    @Test
    fun page2IllustrationHasStep2Image() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_step2_image").assertIsDisplayed()
    }

    @Test
    fun page2IllustrationShowsStep2Image() {
        setWalkthroughContent()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("walkthrough_step2_image").assertIsDisplayed()
    }

    @Test
    fun page3IllustrationHasStep3Image() {
        setWalkthroughContent()

        repeat(2) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_step3_image").assertIsDisplayed()
    }

    @Test
    fun page4IllustrationHasStep4Image() {
        setWalkthroughContent()

        repeat(3) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("walkthrough_step4_image").assertIsDisplayed()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    fun pageThreeRemainsUsableAfterRecreate() {
        setWalkthroughContent()

        repeat(2) {
            composeRule.onNodeWithTag("walkthrough_next").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_take_shot_title))
            .assertIsDisplayed()

        scenario?.recreate()
        setWalkthroughScreenContent()

        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_take_shot_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_back").assertIsDisplayed()
        composeRule.onNodeWithTag("walkthrough_next").assertIsDisplayed()

        composeRule.onNodeWithTag("walkthrough_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.walkthrough_page_see_what_changed_title))
            .assertIsDisplayed()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setWalkthroughContent(
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        onSkip: () -> Unit = {},
        onStart: () -> Unit = {},
        containerWidth: Dp? = null,
        containerHeight: Dp? = null
    ) {
        wakeTestDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        setWalkthroughScreenContent(windowWidthSizeClass, onSkip, onStart, containerWidth, containerHeight)
    }

    /**
     * Re-invokable content setter, split out from [setWalkthroughContent] so recreate-survival
     * tests can call it again after [ActivityScenario.recreate] — a bare [ComponentActivity] does
     * not automatically re-run a previous [android.app.Activity.setContent] call on its new
     * instance.
     *
     * [containerWidth]/[containerHeight], when both provided, wrap [WalkthroughScreen] in a
     * fixed-size [Box] — deterministic, device-independent sizing (test-file-only; no change to
     * [WalkthroughScreen]'s own signature) for tests that need to guarantee the width-capped
     * walkthrough_content column is narrower than the available area. Real portrait devices are
     * often narrower than the 420dp cap plus padding, so the cap would otherwise rarely trigger.
     */
    private fun setWalkthroughScreenContent(
        windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        onSkip: () -> Unit = {},
        onStart: () -> Unit = {},
        containerWidth: Dp? = null,
        containerHeight: Dp? = null
    ) {
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                SameViewTheme {
                    if (containerWidth != null && containerHeight != null) {
                        Box(Modifier.size(width = containerWidth, height = containerHeight)) {
                            WalkthroughScreen(
                                entryMode = WalkthroughEntryMode.FIRST_RUN,
                                windowWidthSizeClass = windowWidthSizeClass,
                                onSkip = onSkip,
                                onStart = onStart
                            )
                        }
                    } else {
                        WalkthroughScreen(
                            entryMode = WalkthroughEntryMode.FIRST_RUN,
                            windowWidthSizeClass = windowWidthSizeClass,
                            onSkip = onSkip,
                            onStart = onStart
                        )
                    }
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

    /**
     * Performs a multi-step horizontal drag between two root-space points, mirroring the
     * manual down/moveTo/up drag technique already used for marker gestures elsewhere in
     * the instrumentation suite (see ReferenceMarkersOverlayUITest). Used by the swipe-area
     * expansion tests, which need explicit start/end X coordinates derived from measured
     * node bounds rather than the default node-relative swipeLeft()/swipeRight().
     *
     * [targetTag], when provided, injects the gesture on that tagged node instead of
     * [androidx.compose.ui.test.onRoot]. Needed for tests that wrap [WalkthroughScreen] in a
     * fixed-size test container: onRoot() reflects the full composition root (the real,
     * unconstrained test-Activity window), not that container, so coordinates computed
     * relative to the container would otherwise land outside the actually-rendered area.
     * "walkthrough_screen_root" (WalkthroughScreen's own outermost Surface, already tagged in
     * production code) is used for exactly this — it is not one of the capped
     * walkthrough_single_column_layout / walkthrough_two_column_layout / walkthrough_content
     * hosts this fix targets.
     */
    private fun dragHorizontally(startX: Float, endX: Float, y: Float, targetTag: String? = null) {
        val target = if (targetTag != null) composeRule.onNodeWithTag(targetTag) else composeRule.onRoot()
        // TouchInjectionScope coordinates are local to the target node's own bounds (its
        // top-left = Offset.Zero), not root-space. onRoot()'s own boundsInRoot.topLeft is
        // (0, 0), so root-space and local coordinates coincided there — but walkthrough_screen_root
        // (used when a fixed-size test wrapper offsets it within the real window) generally does
        // not sit at the root origin, so its own root-space offset must be subtracted first.
        val originOffset = if (targetTag != null) {
            composeRule.onNodeWithTag(targetTag).fetchSemanticsNode().boundsInRoot.topLeft
        } else {
            Offset.Zero
        }
        target.performTouchInput {
            down(Offset(startX, y) - originOffset)
            val steps = 5
            repeat(steps) { step ->
                val fraction = (step + 1f) / steps
                moveTo(Offset(startX + (endX - startX) * fraction, y) - originOffset)
            }
            up()
        }
    }
}
