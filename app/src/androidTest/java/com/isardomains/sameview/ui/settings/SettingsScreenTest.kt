package com.isardomains.sameview.ui.settings

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.ui.camera.GridType
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
        keepScreenOn: Boolean = true,
        onKeepScreenOnChanged: (Boolean) -> Unit = {},
        resetOverlayAfterCapture: Boolean = false,
        onResetOverlayAfterCaptureChanged: (Boolean) -> Unit = {},
        autoOpenCompareAfterCapture: Boolean = false,
        onAutoOpenCompareAfterCaptureChanged: (Boolean) -> Unit = {},
        recreationGuidance: Boolean = false,
        onRecreationGuidanceChanged: (Boolean) -> Unit = {},
        liveDirectionArrow: Boolean = false,
        onLiveDirectionArrowChanged: (Boolean) -> Unit = {},
        showLocationPermissionDeniedHint: Boolean = false,
        stripOriginalsMetadata: Boolean = false,
        onStripOriginalsMetadataChanged: (Boolean) -> Unit = {},
        hasBranding: Boolean = false,
        onChooseImage: () -> Unit = {},
        onChooseSymbol: (BuiltinBrandingSymbol) -> Unit = {},
        onRemoveBranding: () -> Unit = {},
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
                SameViewTheme {
                    SettingsScreenContent(
                        gridType = gridType,
                        onGridTypeSelected = onGridTypeSelected,
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChanged = onKeepScreenOnChanged,
                        resetOverlayAfterCapture = resetOverlayAfterCapture,
                        onResetOverlayAfterCaptureChanged = onResetOverlayAfterCaptureChanged,
                        autoOpenCompareAfterCapture = autoOpenCompareAfterCapture,
                        onAutoOpenCompareAfterCaptureChanged = onAutoOpenCompareAfterCaptureChanged,
                        recreationGuidance = recreationGuidance,
                        onRecreationGuidanceChanged = onRecreationGuidanceChanged,
                        liveDirectionArrow = liveDirectionArrow,
                        onLiveDirectionArrowChanged = onLiveDirectionArrowChanged,
                        showLocationPermissionDeniedHint = showLocationPermissionDeniedHint,
                        stripOriginalsMetadata = stripOriginalsMetadata,
                        onStripOriginalsMetadataChanged = onStripOriginalsMetadataChanged,
                        hasBranding = hasBranding,
                        onChooseImage = onChooseImage,
                        onChooseSymbol = onChooseSymbol,
                        onRemoveBranding = onRemoveBranding,
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
    fun cameraCardTitle_isDisplayed() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_camera_title))
            .assertIsDisplayed()
    }

    @Test
    fun overlayCompareCardTitle_isDisplayed() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_overlay_compare_title))
            .assertIsDisplayed()
    }

    @Test
    fun allThreeGridSegments_areDisplayed() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_grid_type_none_short))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_grid_type_rule_of_thirds_short))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_grid_type_quarters_short))
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
    fun selectedGridTypeSegment_isSelected() {
        setContent(gridType = GridType.QUARTERS)

        composeRule.onNodeWithTag("settings_grid_type_quarters").assertIsSelected()
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

    @Test
    fun keepScreenAwake_switch_isDisplayed() {
        setContent()

        composeRule.onNodeWithTag("settings_keep_screen_on").assertIsDisplayed()
    }

    @Test
    fun tap_keepScreenAwake_whenTrue_invokesCallback_withFalse() {
        var received: Boolean? = null
        setContent(keepScreenOn = true, onKeepScreenOnChanged = { received = it })

        composeRule.onNodeWithTag("settings_keep_screen_on").performClick()
        composeRule.waitForIdle()

        assertEquals(false, received)
    }

    @Test
    fun tap_keepScreenAwake_whenFalse_invokesCallback_withTrue() {
        var received: Boolean? = null
        setContent(keepScreenOn = false, onKeepScreenOnChanged = { received = it })

        composeRule.onNodeWithTag("settings_keep_screen_on").performClick()
        composeRule.waitForIdle()

        assertEquals(true, received)
    }

    @Test
    fun resetOverlayAfterCapture_switch_isDisplayed() {
        setContent()

        composeRule.onNodeWithTag("settings_reset_overlay_after_capture").assertIsDisplayed()
    }

    @Test
    fun tap_resetOverlayAfterCapture_whenFalse_invokesCallbackWithTrue() {
        var received: Boolean? = null
        setContent(resetOverlayAfterCapture = false, onResetOverlayAfterCaptureChanged = { received = it })

        composeRule.onNodeWithTag("settings_reset_overlay_after_capture").performClick()
        composeRule.waitForIdle()

        assertEquals(true, received)
    }

    @Test
    fun tap_resetOverlayAfterCapture_whenTrue_invokesCallbackWithFalse() {
        var received: Boolean? = null
        setContent(resetOverlayAfterCapture = true, onResetOverlayAfterCaptureChanged = { received = it })

        composeRule.onNodeWithTag("settings_reset_overlay_after_capture").performClick()
        composeRule.waitForIdle()

        assertEquals(false, received)
    }

    @Test
    fun autoOpenCompareAfterCapture_switch_isDisplayed() {
        setContent()

        composeRule.onNodeWithTag("settings_auto_open_compare_after_capture").assertIsDisplayed()
    }

    @Test
    fun autoOpenCompareAfterCapture_switch_toggle_callsCallback() {
        var received: Boolean? = null
        setContent(
            autoOpenCompareAfterCapture = false,
            onAutoOpenCompareAfterCaptureChanged = { received = it }
        )

        composeRule.onNodeWithTag("settings_auto_open_compare_after_capture").performClick()
        composeRule.waitForIdle()

        assertEquals(true, received)
    }

    @Test
    fun recreationGuidanceToggle_isVisible_inCategory4() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_gps_guidance_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_recreation_guidance").assertIsDisplayed()
    }

    @Test
    fun recreationGuidanceToggle_defaultIsOff() {
        var received: Boolean? = null
        setContent(recreationGuidance = false, onRecreationGuidanceChanged = { received = it })

        composeRule.onNodeWithTag("settings_recreation_guidance").performClick()
        composeRule.waitForIdle()

        assertEquals(true, received)
    }

    @Test
    fun liveDirectionArrowToggle_isVisible_inGpsGuidanceCard() {
        setContent()

        composeRule.onNodeWithTag("settings_live_direction_arrow").assertIsDisplayed()
    }

    @Test
    fun liveDirectionArrowToggle_defaultIsOff_tapInvokesCallbackWithTrue() {
        var received: Boolean? = null
        setContent(
            recreationGuidance = true,
            liveDirectionArrow = false,
            onLiveDirectionArrowChanged = { received = it }
        )

        composeRule.onNodeWithTag("settings_live_direction_arrow").performClick()
        composeRule.waitForIdle()

        assertEquals(true, received)
    }

    @Test
    fun liveDirectionArrowToggle_whenOn_tapInvokesCallbackWithFalse() {
        var received: Boolean? = null
        setContent(
            recreationGuidance = true,
            liveDirectionArrow = true,
            onLiveDirectionArrowChanged = { received = it }
        )

        composeRule.onNodeWithTag("settings_live_direction_arrow").performClick()
        composeRule.waitForIdle()

        assertEquals(false, received)
    }

    @Test
    fun liveDirectionArrowToggle_isDisabled_whenRecreationGuidanceOff_doesNotInvokeCallback() {
        var received: Boolean? = null
        setContent(
            recreationGuidance = false,
            liveDirectionArrow = false,
            onLiveDirectionArrowChanged = { received = it }
        )

        composeRule.onNodeWithTag("settings_live_direction_arrow").performClick()
        composeRule.waitForIdle()

        assertEquals(null, received)
    }

    @Test
    fun liveDirectionArrowToggle_isEnabled_whenRecreationGuidanceOn() {
        var received: Boolean? = null
        setContent(
            recreationGuidance = true,
            liveDirectionArrow = false,
            onLiveDirectionArrowChanged = { received = it }
        )

        composeRule.onNodeWithTag("settings_live_direction_arrow").performClick()
        composeRule.waitForIdle()

        assertEquals(true, received)
    }

    @Test
    fun privacyCardTitle_isDisplayed() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_privacy_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun stripOriginalsMetadataToggle_callsCallback_whenClicked() {
        var received: Boolean? = null
        setContent(
            stripOriginalsMetadata = false,
            onStripOriginalsMetadataChanged = { received = it }
        )

        composeRule.onNodeWithTag("settings_strip_originals_metadata")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(true, received)
    }

    // ── Branding section tests ─────────────────────────────────────────────────

    @Test
    fun branding_sectionTitle_isDisplayed() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_branding_section_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun branding_descriptionText_isDisplayed() {
        setContent()

        composeRule.onNodeWithText(context.getString(R.string.settings_branding_description))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun branding_chooseImageButton_isDisplayed() {
        setContent()

        composeRule.onNodeWithTag("settings_branding_choose_image")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun branding_chooseSymbolButton_isDisplayed() {
        setContent()

        composeRule.onNodeWithTag("settings_branding_choose_symbol")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun branding_removeButton_notVisible_whenNoBranding() {
        setContent(hasBranding = false)

        // The remove button must not exist in the composition when hasBranding = false
        composeRule.onNodeWithTag("settings_branding_remove").assertDoesNotExist()
    }

    @Test
    fun branding_removeButton_isVisible_whenBrandingSet() {
        setContent(hasBranding = true)

        composeRule.onNodeWithTag("settings_branding_remove")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun branding_chooseImage_callsCallback() {
        var called = false
        setContent(onChooseImage = { called = true })

        composeRule.onNodeWithTag("settings_branding_choose_image")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(true, called)
    }

    @Test
    fun branding_removeButton_callsCallback_whenClicked() {
        var called = false
        setContent(hasBranding = true, onRemoveBranding = { called = true })

        composeRule.onNodeWithTag("settings_branding_remove")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(true, called)
    }
}
