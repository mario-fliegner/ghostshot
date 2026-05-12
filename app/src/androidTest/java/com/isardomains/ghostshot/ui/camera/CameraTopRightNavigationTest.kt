package com.isardomains.ghostshot.ui.camera

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

@RunWith(AndroidJUnit4::class)
class CameraTopRightNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val landscapeMenuMinGap = 4.dp
    private val landscapeActionsExpectedTopDistance = 20.dp

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun historyButton_isAlwaysVisible() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_history_button")
            .assertIsDisplayed()
    }

    @Test
    fun overflowButton_isAlwaysVisible() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button")
            .assertIsDisplayed()
    }

    @Test
    fun topRightActions_buttonsStayWithinRootBounds() {
        setTopRightContent()

        assertNodeWithinRoot("camera_history_button")
        assertNodeWithinRoot("camera_overflow_button")
    }

    @Test
    fun topRightActions_touchTargetsRemainAtLeast48Dp() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_history_button")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("camera_overflow_button")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun historyButton_invokesCallback() {
        var historyOpenCount = 0
        setTopRightContent(onOpenHistory = { historyOpenCount++ })

        composeRule.onNodeWithTag("camera_history_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, historyOpenCount)
    }

    @Test
    fun topRightActions_defaultLayout_keepsHistoryAndOverflowClickable() {
        var historyOpenCount = 0
        setTopRightContent(onOpenHistory = { historyOpenCount++ })

        composeRule.onNodeWithTag("camera_history_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, historyOpenCount)
        composeRule.onNodeWithTag("camera_overflow_menu").assertIsDisplayed()
    }

    @Test
    fun topRightActions_defaultLayout_keepsPortraitActionsHorizontal() {
        setTopRightContent()

        val historyBounds = composeRule.onNodeWithTag("camera_history_button")
            .getUnclippedBoundsInRoot()
        val overflowBounds = composeRule.onNodeWithTag("camera_overflow_button")
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Portrait/default actions should keep the horizontal top-action layout",
            historyBounds.right <= overflowBounds.left
        )
    }

    @Test
    fun landscapeTopActions_areVisibleClickableAndAtLeast48Dp() {
        var historyOpenCount = 0
        setLandscapeTopRightContent(onOpenHistory = { historyOpenCount++ })

        composeRule.onNodeWithTag("camera_history_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_overflow_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, historyOpenCount)
        composeRule.onNodeWithTag("camera_overflow_menu").assertIsDisplayed()
    }

    @Test
    fun landscapeTopActions_whenNavigationIsRight_useLeftSideArea() {
        setLandscapeTopRightContent(
            navigationLeftInset = 0.dp,
            navigationRightInset = 42.dp,
            frameLeft = 96.dp
        )

        assertLandscapeActionsWithinRoot()
        assertActionsWithinStartSideArea(96.dp)
        assertActionsDoNotOverlapEndNavigation(42.dp)
    }

    @Test
    fun landscapeTopActions_whenOnLeftSide_openDropdownToRightOfOverflow() {
        setLandscapeTopRightContent(
            navigationLeftInset = 0.dp,
            navigationRightInset = 42.dp,
            frameLeft = 96.dp
        )

        openOverflowMenu()

        assertDropdownWithinRoot()
        assertSideAwareMenuPositionOpensBesideAnchor(opensToEnd = true)
    }

    @Test
    fun landscapeTopActions_whenNavigationIsLeft_useRightSideArea() {
        setLandscapeTopRightContent(
            navigationLeftInset = 42.dp,
            navigationRightInset = 0.dp,
            frameLeft = 96.dp
        )

        assertLandscapeActionsWithinRoot()
        assertActionsWithinEndSideArea(96.dp)
        assertActionsDoNotOverlapStartNavigation(42.dp)
    }

    @Test
    fun landscapeTopActions_whenOnRightSide_openDropdownToLeftOfOverflow() {
        setLandscapeTopRightContent(
            navigationLeftInset = 42.dp,
            navigationRightInset = 0.dp,
            frameLeft = 96.dp
        )

        openOverflowMenu()

        assertDropdownWithinRoot()
        assertSideAwareMenuPositionOpensBesideAnchor(opensToEnd = false)
    }

    @Test
    fun landscapeTopActions_whenNavigationInsetsAreEqual_useStableRightFallback() {
        setLandscapeTopRightContent(
            navigationLeftInset = 0.dp,
            navigationRightInset = 0.dp,
            frameLeft = 96.dp
        )

        assertLandscapeActionsWithinRoot()
        assertActionsWithinEndSideArea(96.dp)
    }

    @Test
    fun landscapeTopActions_useFixedTopDistanceInUpperRailZone() {
        setLandscapeTopRightContent()

        val railBounds = composeRule.onNodeWithTag("camera_landscape_top_actions_rail")
            .getUnclippedBoundsInRoot()
        val actionsBounds = composeRule.onNodeWithTag("camera_landscape_top_actions")
            .getUnclippedBoundsInRoot()
        val actualTopDistance = actionsBounds.top - railBounds.top
        val allowedTopDistance = landscapeActionsExpectedTopDistance + 2.dp
        val upperThirdLimit = railBounds.top + (railBounds.bottom - railBounds.top) / 3

        assertTrue(
            "Landscape actions should use the fixed top distance",
            actualTopDistance <= allowedTopDistance
        )
        assertTrue(
            "Landscape actions should sit in the upper third of the rail",
            actionsBounds.bottom <= upperThirdLimit
        )
    }

    @Test
    fun overflowButton_opensMenu() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_overflow_menu").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_containsSettings() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_settings))
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_containsAbout() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_about))
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_settingsTap_closesMenu() {
        setTopRightContent()

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_settings))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_overflow_button").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_history_button").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_aboutTap_invokesAboutCallback() {
        var aboutOpenCount = 0
        setTopRightContent(onOpenAbout = { aboutOpenCount++ })

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_about))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, aboutOpenCount)
    }

    @Test
    fun historyButton_navigatesViaNavController() {
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
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "camera") {
                        composable("camera") {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CameraTopRightActions(
                                    onOpenHistory = { navController.navigate("history") }
                                )
                            }
                        }
                        composable("history") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("history_screen_stub")
                            ) {}
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("camera_history_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history_screen_stub").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_settingsTap_invokesSettingsCallback() {
        var settingsOpenCount = 0
        setTopRightContent(onOpenSettings = { settingsOpenCount++ })

        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.camera_overflow_settings))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, settingsOpenCount)
    }

    private fun setTopRightContent(
        onOpenHistory: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenAbout: () -> Unit = {}
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
                            .testTag("camera_top_right_root")
                    ) {
                        CameraTopRightActions(
                            onOpenHistory = onOpenHistory,
                            onOpenSettings = onOpenSettings,
                            onOpenAbout = onOpenAbout
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setLandscapeTopRightContent(
        onOpenHistory: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenAbout: () -> Unit = {},
        navigationLeftInset: androidx.compose.ui.unit.Dp = 0.dp,
        navigationRightInset: androidx.compose.ui.unit.Dp = 0.dp,
        frameLeft: androidx.compose.ui.unit.Dp = 96.dp
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
                    CameraLandscapeTopActions(
                        onOpenHistory = onOpenHistory,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                        frameLeft = frameLeft,
                        navigationLeftInset = navigationLeftInset,
                        navigationRightInset = navigationRightInset,
                        safeTopInset = 0.dp,
                        safeBottomInset = 0.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_top_right_root")
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertNodeWithinRoot(testTag: String) {
        val rootBounds = composeRule.onNodeWithTag("camera_top_right_root")
            .getUnclippedBoundsInRoot()
        val nodeBounds = composeRule.onNodeWithTag(testTag)
            .getUnclippedBoundsInRoot()

        assertTrue("$testTag should not start before root", nodeBounds.left >= rootBounds.left)
        assertTrue("$testTag should not be above root", nodeBounds.top >= rootBounds.top)
        assertTrue("$testTag should not end after root", nodeBounds.right <= rootBounds.right)
        assertTrue("$testTag should not be below root", nodeBounds.bottom <= rootBounds.bottom)
    }

    private fun assertLandscapeActionsWithinRoot() {
        assertNodeWithinRoot("camera_history_button")
        assertNodeWithinRoot("camera_overflow_button")
    }

    private fun openOverflowMenu() {
        composeRule.onNodeWithTag("camera_overflow_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_overflow_menu").assertIsDisplayed()
    }

    private fun assertDropdownWithinRoot() {
        val rootBounds = composeRule.onNodeWithTag("camera_top_right_root")
            .getUnclippedBoundsInRoot()
        val menuBounds = composeRule.onNodeWithTag("camera_overflow_menu")
            .getUnclippedBoundsInRoot()

        assertTrue("Dropdown should not start before root", menuBounds.left >= rootBounds.left)
        assertTrue("Dropdown should not be above root", menuBounds.top >= rootBounds.top)
        assertTrue("Dropdown should not end after root", menuBounds.right <= rootBounds.right)
        assertTrue("Dropdown should not be below root", menuBounds.bottom <= rootBounds.bottom)
    }

    private fun assertSideAwareMenuPositionOpensBesideAnchor(opensToEnd: Boolean) {
        val gapPx = 24
        val anchorBounds = if (opensToEnd) {
            IntRect(left = 72, top = 525, right = 216, bottom = 669)
        } else {
            IntRect(left = 864, top = 525, right = 1008, bottom = 669)
        }
        val menuSize = IntSize(width = 528, height = 288)
        val windowSize = IntSize(width = 1080, height = 2400)
        val position = SideAwareOverflowMenuPositionProvider(
            opensToEnd = opensToEnd,
            gapPx = gapPx,
            verticalMarginPx = 24
        ).calculatePosition(
            anchorBounds = anchorBounds,
            windowSize = windowSize,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = menuSize
        )
        val menuBounds = IntRect(offset = position, size = menuSize)

        if (opensToEnd) {
            assertTrue(
                "Left rail menu should be placed right of overflow",
                menuBounds.left >= anchorBounds.right + gapPx
            )
        } else {
            assertTrue(
                "Right rail menu should be placed left of overflow",
                menuBounds.right <= anchorBounds.left - gapPx
            )
        }
        assertTrue("Menu should stay inside window left", menuBounds.left >= 0)
        assertTrue("Menu should stay inside window right", menuBounds.right <= windowSize.width)
        assertTrue("Menu should not overlap anchor horizontally", !rangesOverlap(
            menuBounds.left,
            menuBounds.right,
            anchorBounds.left,
            anchorBounds.right
        ))
    }

    private fun rangesOverlap(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean =
        firstStart < secondEnd && firstEnd > secondStart

    private fun assertActionsWithinStartSideArea(sideAreaWidth: androidx.compose.ui.unit.Dp) {
        val rootBounds = composeRule.onNodeWithTag("camera_top_right_root")
            .getUnclippedBoundsInRoot()
        val maxRight = rootBounds.left + sideAreaWidth

        listOf("camera_history_button", "camera_overflow_button").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag should stay inside start side area", bounds.right <= maxRight)
        }
    }

    private fun assertActionsWithinEndSideArea(sideAreaWidth: androidx.compose.ui.unit.Dp) {
        val rootBounds = composeRule.onNodeWithTag("camera_top_right_root")
            .getUnclippedBoundsInRoot()
        val minLeft = rootBounds.right - sideAreaWidth

        listOf("camera_history_button", "camera_overflow_button").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag should stay inside end side area", bounds.left >= minLeft)
        }
    }

    private fun assertActionsDoNotOverlapStartNavigation(navigationInset: androidx.compose.ui.unit.Dp) {
        val rootBounds = composeRule.onNodeWithTag("camera_top_right_root")
            .getUnclippedBoundsInRoot()
        val minLeft = rootBounds.left + navigationInset

        listOf("camera_history_button", "camera_overflow_button").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag should not overlap start navigation", bounds.left >= minLeft)
        }
    }

    private fun assertActionsDoNotOverlapEndNavigation(navigationInset: androidx.compose.ui.unit.Dp) {
        val rootBounds = composeRule.onNodeWithTag("camera_top_right_root")
            .getUnclippedBoundsInRoot()
        val maxRight = rootBounds.right - navigationInset

        listOf("camera_history_button", "camera_overflow_button").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag should not overlap end navigation", bounds.right <= maxRight)
        }
    }

    private fun wakeTestDevice() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            .close()
    }
}
