package com.isardomains.sameview.ui.branding

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrandingSymbolPickerSheetTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    private fun launchSheet(
        onSymbolSelected: (BuiltinBrandingSymbol) -> Unit = {},
        onDismiss: () -> Unit = {}
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
                    BrandingSymbolPickerSheet(
                        onSymbolSelected = onSymbolSelected,
                        onDismiss = onDismiss
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
    fun symbolSheet_titleVisible() {
        launchSheet()

        composeRule.onNodeWithText(context.getString(R.string.branding_symbol_picker_title))
            .assertIsDisplayed()
    }

    @Test
    fun symbolSheet_allSixSymbolCellsVisible() {
        launchSheet()

        composeRule.onNodeWithTag("symbol_cell_heart").assertIsDisplayed()
        composeRule.onNodeWithTag("symbol_cell_star").assertIsDisplayed()
        composeRule.onNodeWithTag("symbol_cell_camera").assertIsDisplayed()
        composeRule.onNodeWithTag("symbol_cell_home").assertIsDisplayed()
        composeRule.onNodeWithTag("symbol_cell_pin").assertIsDisplayed()
        composeRule.onNodeWithTag("symbol_cell_fire").assertIsDisplayed()
    }

    @Test
    fun symbolSheet_tapSymbol_callsCallbackWithCorrectSymbol() {
        var selected: BuiltinBrandingSymbol? = null
        launchSheet(onSymbolSelected = { selected = it })

        composeRule.onNodeWithTag("symbol_cell_heart")
            .performClick()
        composeRule.waitForIdle()

        assertEquals(BuiltinBrandingSymbol.HEART, selected)
    }

    @Test
    fun symbolSheet_tapSymbol_doesNotCallOnDismiss() {
        var dismissCalled = false
        launchSheet(
            onSymbolSelected = {},
            onDismiss = { dismissCalled = true }
        )

        composeRule.onNodeWithTag("symbol_cell_star")
            .performClick()
        composeRule.waitForIdle()

        assertFalse(dismissCalled)
    }

    @Test
    fun symbolSheet_cancelButton_callsOnDismiss() {
        var dismissCalled = false
        var selectedCalled = false
        launchSheet(
            onSymbolSelected = { selectedCalled = true },
            onDismiss = { dismissCalled = true }
        )

        composeRule.onNodeWithText(context.getString(android.R.string.cancel))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(true, dismissCalled)
        assertFalse(selectedCalled)
    }

    @Test
    fun symbolSheet_dragToDismiss_doesNotCallSelectedCallback() {
        var selectedCalled = false
        launchSheet(onSymbolSelected = { selectedCalled = true })

        composeRule.onNodeWithText(context.getString(R.string.branding_symbol_picker_title))
            .performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertFalse(selectedCalled)
    }
}
