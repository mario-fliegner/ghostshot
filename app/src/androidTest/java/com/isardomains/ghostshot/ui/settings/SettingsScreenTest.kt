package com.isardomains.ghostshot.ui.settings

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.camera.GridType
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    private fun setContent(
        gridType: GridType = GridType.RULE_OF_THIRDS,
        onGridTypeSelected: (GridType) -> Unit = {},
        onBack: () -> Unit = {}
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
                    SettingsScreenContent(
                        gridType = gridType,
                        onGridTypeSelected = onGridTypeSelected,
                        onBack = onBack
                    )
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

    @Test
    fun allThreeOptions_areDisplayed() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_grid_type_none))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_grid_type_rule_of_thirds))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_grid_type_quarters))
            .assertIsDisplayed()
    }

    @Test
    fun gridTypeNone_rowTag_isDisplayed() {
        setContent(gridType = GridType.NONE)

        composeRule.onNodeWithTag("settings_grid_type_none").assertIsDisplayed()
    }

    @Test
    fun gridTypeRuleOfThirds_rowTag_isDisplayed() {
        setContent(gridType = GridType.RULE_OF_THIRDS)

        composeRule.onNodeWithTag("settings_grid_type_rule_of_thirds").assertIsDisplayed()
    }

    @Test
    fun gridTypeQuarters_rowTag_isDisplayed() {
        setContent(gridType = GridType.QUARTERS)

        composeRule.onNodeWithTag("settings_grid_type_quarters").assertIsDisplayed()
    }

    @Test
    fun tap_none_invokesCallback() {
        var selected: GridType? = null
        setContent(onGridTypeSelected = { selected = it })

        composeRule.onNodeWithTag("settings_grid_type_none").performClick()
        composeRule.waitForIdle()

        assertEquals(GridType.NONE, selected)
    }

    @Test
    fun tap_ruleOfThirds_invokesCallback() {
        var selected: GridType? = null
        setContent(onGridTypeSelected = { selected = it })

        composeRule.onNodeWithTag("settings_grid_type_rule_of_thirds").performClick()
        composeRule.waitForIdle()

        assertEquals(GridType.RULE_OF_THIRDS, selected)
    }

    @Test
    fun tap_quarters_invokesCallback() {
        var selected: GridType? = null
        setContent(onGridTypeSelected = { selected = it })

        composeRule.onNodeWithTag("settings_grid_type_quarters").performClick()
        composeRule.waitForIdle()

        assertEquals(GridType.QUARTERS, selected)
    }
}
