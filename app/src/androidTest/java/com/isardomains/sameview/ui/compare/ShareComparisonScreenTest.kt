package com.isardomains.sameview.ui.compare

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.R
import com.isardomains.sameview.image.ShareComparisonStyle
import com.isardomains.sameview.image.ShareQuality
import com.isardomains.sameview.ui.settings.SameViewSegmentControl
import com.isardomains.sameview.ui.settings.SameViewSegmentItem
import com.isardomains.sameview.ui.settings.SettingsCard
import com.isardomains.sameview.ui.settings.SettingsSwitchRow
import com.isardomains.sameview.ui.theme.SameViewTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ShareComparisonScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    // ── T-B3-07: TopAppBar title "Share comparison" visible ───────────────────

    @Test
    fun t_b3_07_topAppBarTitleVisible() {
        launch()
        val expectedTitle = context.getString(R.string.share_comparison_screen_title)
        composeRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    // ── T-B3-08: Style segment control shows Slider and Side by side ──────────

    @Test
    fun t_b3_08_styleControlShowsBothOptions() {
        launch()
        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_style_slider)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_style_side_by_side)
        ).assertIsDisplayed()
    }

    // ── T-B3-09: Extras card shows title+date and location toggles ───────────

    @Test
    fun t_b3_09_extrasCardShowsTwoToggles() {
        launch()
        composeRule.onNodeWithTag("share_comparison_toggle_title_date").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_toggle_location").assertIsDisplayed()
    }

    // ── T-B3-10: Share button present ─────────────────────────────────────────

    @Test
    fun t_b3_10_shareButtonPresent() {
        launch()
        composeRule.onNodeWithTag("share_comparison_share_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    // ── T-B3-11: Back button invokes callback ─────────────────────────────────

    @Test
    fun t_b3_11_backButtonInvokesCallback() {
        var backInvoked = false
        launch(onBack = { backInvoked = true })
        composeRule.onNodeWithTag("share_comparison_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Back callback should be invoked", backInvoked)
    }

    // ── Quality card ──────────────────────────────────────────────────────────

    @Test
    fun qualityCard_showsStandardAndOriginal() {
        launch()
        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_quality_standard)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_quality_original)
        ).assertIsDisplayed()
    }

    // ── Style switch reflects immediately ────────────────────────────────────

    @Test
    fun styleSwitch_sideBySideSelectable() {
        launch()
        composeRule.onNodeWithTag("share_comparison_style_control").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_style_side_by_side)
        ).performClick()
        composeRule.waitForIdle()
        // Control should still be displayed after switch
        composeRule.onNodeWithTag("share_comparison_style_control").assertIsDisplayed()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun launch(
        onBack: () -> Unit = {},
        hasBranding: Boolean = false,
        hasGlobalBranding: Boolean = false,
        isUsingGlobalDefault: Boolean = false,
        initialStyle: ShareComparisonStyle = ShareComparisonStyle.SLIDER
    ) {
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
                    ShareComparisonScreenStub(
                        onBack = onBack,
                        hasBranding = hasBranding,
                        hasGlobalBranding = hasGlobalBranding,
                        isUsingGlobalDefault = isUsingGlobalDefault,
                        initialStyle = initialStyle
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    // ── Logo card tests (V2) ─────────────────────────────────────────────────

    @Test
    fun logoCard_visible_whenSliderSelected() {
        launch(hasBranding = false)

        composeRule.onNodeWithText(context.getString(R.string.share_comparison_logo_card_title))
            .assertIsDisplayed()
    }

    @Test
    fun logoCard_absent_whenSideBySideSelected() {
        launch(initialStyle = ShareComparisonStyle.SIDE_BY_SIDE)

        composeRule.onNodeWithText(context.getString(R.string.share_comparison_logo_card_title))
            .assertDoesNotExist()
    }

    @Test
    fun logoCard_emptyState_whenNoBranding() {
        launch(hasBranding = false)

        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.share_comparison_logo_none))
            .assertIsDisplayed()
        // Toggle absent in empty state
        composeRule.onNodeWithTag("share_comparison_toggle_logo").assertDoesNotExist()
    }

    @Test
    fun logoCard_emptyState_showsActionButtons() {
        launch(hasBranding = false)

        composeRule.onNodeWithTag("share_comparison_logo_choose_photo").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_use_symbol").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_remove").assertDoesNotExist()
    }

    @Test
    fun logoCard_emptyState_useDefaultLogo_visible_whenGlobalExists() {
        launch(hasBranding = false, hasGlobalBranding = true)

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()
    }

    @Test
    fun logoCard_emptyState_useDefaultLogo_absent_whenNoGlobal() {
        launch(hasBranding = false, hasGlobalBranding = false)

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()
    }

    @Test
    fun logoCard_populated_showsAllManagementElements() {
        launch(hasBranding = true)

        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_toggle_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_choose_photo").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_use_symbol").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_remove").assertIsDisplayed()
    }

    @Test
    fun logoCard_populated_useDefaultLogo_visible_whenGlobalExists() {
        launch(hasBranding = true, hasGlobalBranding = true)

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()
    }

    @Test
    fun logoCard_populated_useDefaultLogo_absent_whenNoGlobal() {
        launch(hasBranding = true, hasGlobalBranding = false)

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()
    }

    @Test
    fun logoCard_noOldEditSessionHintText() {
        launch(hasBranding = false)

        composeRule.onNodeWithText("Add one in Edit session.").assertDoesNotExist()
    }

    @Test
    fun logoCard_populatedState_whenBrandingSet() {
        launch(hasBranding = true)

        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_toggle_logo").assertIsDisplayed()
    }

    @Test
    fun logoCard_toggle_showsLogo_label() {
        launch(hasBranding = true)

        composeRule.onNodeWithText(context.getString(R.string.share_comparison_logo_show))
            .assertIsDisplayed()
    }

    @Test
    fun logoCard_noBrandingToggle_insideStyleCard() {
        launch()

        composeRule.onNodeWithTag("share_comparison_toggle_branding").assertDoesNotExist()
    }

    @Test
    fun logoCard_noSliderOnlyHint_visible() {
        launch()

        composeRule.onNodeWithText("Only applied to slider style").assertDoesNotExist()
    }

    @Test
    fun logoCard_disappears_onSwitchToSideBySide() {
        launch(hasBranding = true)

        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_style_side_by_side)
        ).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.share_comparison_logo_card_title))
            .assertDoesNotExist()
    }

    @Test
    fun logoCard_reappears_onSwitchBackToSlider() {
        launch(hasBranding = true)

        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_style_side_by_side)
        ).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.share_comparison_style_slider)
        ).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_toggle_logo").assertIsDisplayed()
    }

    // ── In-screen branding state refresh — single source of truth ──────────────
    // These tests verify that the ACTIVE Share Comparison screen updates immediately
    // when branding state changes, without closing or reopening the screen.
    // Strategy: liveBrandingState is a MutableState<Boolean?> (null=no logo, non-null=logo).
    // Changing it on the UI thread causes the stub to recompose, proving that production
    // code driven by previewBrandingBitmap StateFlow would also refresh correctly.
    //
    // The stub renders the populated state (preview node) when hasBranding=true and the
    // empty state (placeholder node) when hasBranding=false — a direct mapping to the
    // real ViewModel's hasBranding = (previewBrandingBitmap != null).

    private var liveBrandingState = mutableStateOf(false)

    private fun launchWithLiveState(initialHasBranding: Boolean = true) {
        liveBrandingState = mutableStateOf(initialHasBranding)
        wakeDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                val h by liveBrandingState
                SameViewTheme {
                    ShareComparisonScreenStub(
                        onBack = {},
                        hasBranding = h
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun overrideFromGlobalDefault_updatesLogoCard_withoutReopeningScreen() {
        // Starts with no logo (empty state). Simulates auto-copy from global setting hasBranding=true.
        launchWithLiveState(initialHasBranding = false)
        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_preview").assertDoesNotExist()

        // Simulate previewBrandingBitmap becoming non-null → hasBranding=true.
        composeRule.runOnUiThread { liveBrandingState.value = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertDoesNotExist()
    }

    @Test
    fun overrideWithSymbol_updatesLogoCard_withoutReopeningScreen() {
        // Starts with logo. Override sets a new bitmap — hasBranding stays true but the
        // preview node remains displayed (content replaced by new Bitmap in production).
        launchWithLiveState(initialHasBranding = true)
        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()

        // Simulate remove then re-add (clear then set new bitmap)
        composeRule.runOnUiThread { liveBrandingState.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()

        composeRule.runOnUiThread { liveBrandingState.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
    }

    @Test
    fun overrideWithPhoto_updatesLogoCard_withoutReopeningScreen() {
        launchWithLiveState(initialHasBranding = false)
        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()

        composeRule.runOnUiThread { liveBrandingState.value = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
    }

    @Test
    fun removeLogo_updatesLogoCard_toEmptyState_immediately() {
        launchWithLiveState(initialHasBranding = true)
        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()

        // Simulate previewBrandingBitmap = null → hasBranding = false
        composeRule.runOnUiThread { liveBrandingState.value = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()
        composeRule.onNodeWithTag("share_comparison_logo_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("share_comparison_toggle_logo").assertDoesNotExist()
    }

    @Test
    fun multipleChanges_eachUpdatesLogoCard_withoutReopeningScreen() {
        launchWithLiveState(initialHasBranding = false)
        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()

        // logo set → populated
        composeRule.runOnUiThread { liveBrandingState.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()

        // remove → empty
        composeRule.runOnUiThread { liveBrandingState.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_comparison_logo_placeholder").assertIsDisplayed()

        // new logo set → populated again
        composeRule.runOnUiThread { liveBrandingState.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_comparison_logo_preview").assertIsDisplayed()
    }

    // ── Issue 1: "Use default logo" meaningful visibility ─────────────────────
    // Button must be absent when it cannot perform a meaningful change.

    @Test
    fun useDefaultLogo_hidden_whenAlreadyUsingDefault() {
        // Global exists, but session already uses the global default — button must be hidden.
        launch(hasBranding = true, hasGlobalBranding = true, isUsingGlobalDefault = true)

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()
    }

    @Test
    fun useDefaultLogo_visible_whenGlobalExistsAndSessionDiffers() {
        // Global exists and session is NOT using it — button must be visible.
        launch(hasBranding = true, hasGlobalBranding = true, isUsingGlobalDefault = false)

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()
    }

    @Test
    fun useDefaultLogo_absent_inEmptyState_whenAlreadyUsingDefault() {
        // Empty state + using global default (e.g. after auto-copy that failed to decode).
        launch(hasBranding = false, hasGlobalBranding = true, isUsingGlobalDefault = true)

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()
    }

    // ── Issue 3: Remove logo available when Show logo is OFF (regression guard) ─

    @Test
    fun removeLogoStillAvailable_whenShowLogoOff() {
        // populated state with Show logo OFF — Remove logo must still be present.
        launch(hasBranding = true, initialStyle = ShareComparisonStyle.SLIDER)
        // initialUseBranding defaults to hasBranding=true; toggle it off via click.
        composeRule.onNodeWithTag("share_comparison_toggle_logo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_logo_remove").assertIsDisplayed()
    }

    // ── Issue 1 live: button visibility updates immediately without screen reopen ─

    private var liveBrandingState2 = mutableStateOf(false)
    private var liveIsUsingGlobalDefault = mutableStateOf(false)

    private fun launchWithLiveStates(
        initialHasBranding: Boolean,
        initialIsUsingGlobalDefault: Boolean = false
    ) {
        liveBrandingState2 = mutableStateOf(initialHasBranding)
        liveIsUsingGlobalDefault = mutableStateOf(initialIsUsingGlobalDefault)
        wakeDevice()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario?.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.setContent {
                val h by liveBrandingState2
                val u by liveIsUsingGlobalDefault
                SameViewTheme {
                    ShareComparisonScreenStub(
                        onBack = {},
                        hasBranding = h,
                        hasGlobalBranding = true,
                        isUsingGlobalDefault = u
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun useDefaultLogo_hiddenImmediately_afterUseDefault() {
        // Start: session differs from global → button visible. Use default → button disappears.
        launchWithLiveStates(initialHasBranding = true, initialIsUsingGlobalDefault = false)
        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()

        composeRule.runOnUiThread { liveIsUsingGlobalDefault.value = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()
    }

    @Test
    fun useDefaultLogo_visible_afterChoosingPhoto() {
        // Start: isUsingGlobalDefault=true → button hidden. Choose photo → button appears.
        launchWithLiveStates(initialHasBranding = true, initialIsUsingGlobalDefault = true)
        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()

        composeRule.runOnUiThread { liveIsUsingGlobalDefault.value = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()
    }

    @Test
    fun useDefaultLogo_visible_afterChoosingSymbol() {
        // Same pattern as photo — symbol selection also clears isUsingGlobalDefault.
        launchWithLiveStates(initialHasBranding = true, initialIsUsingGlobalDefault = true)
        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()

        composeRule.runOnUiThread { liveIsUsingGlobalDefault.value = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()
    }

    @Test
    fun useDefaultLogo_visibility_updatesWithoutScreenReopen() {
        // Cycle: not-using → button visible; use default → button hidden; choose photo → button visible.
        launchWithLiveStates(initialHasBranding = true, initialIsUsingGlobalDefault = false)
        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()

        composeRule.runOnUiThread { liveIsUsingGlobalDefault.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertDoesNotExist()

        composeRule.runOnUiThread { liveIsUsingGlobalDefault.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_comparison_logo_use_default").assertIsDisplayed()
    }

    private fun wakeDevice() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP")
    }
}


// ── Structural test stub (V2) ───────────────────────────────────────────────────
// Renders the same card/control structure as ShareComparisonScreen without Hilt.
// Tests verify that the correct nodes, labels, and test tags are present and
// interactive. ViewModel-level behavior is covered by ShareComparisonViewModelTest.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareComparisonScreenStub(
    onBack: () -> Unit,
    hasBranding: Boolean = false,
    hasGlobalBranding: Boolean = false,
    isUsingGlobalDefault: Boolean = false,
    initialUseBranding: Boolean = hasBranding,
    initialStyle: ShareComparisonStyle = ShareComparisonStyle.SLIDER
) {
    var style by remember { mutableStateOf(initialStyle) }
    var quality by remember { mutableStateOf(ShareQuality.STANDARD) }
    var useBranding by remember { mutableStateOf(initialUseBranding) }

    val styles = listOf(ShareComparisonStyle.SLIDER, ShareComparisonStyle.SIDE_BY_SIDE)
    val qualities = listOf(ShareQuality.STANDARD, ShareQuality.ORIGINAL)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_comparison_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("share_comparison_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Style card
            SettingsCard(title = stringResource(R.string.share_comparison_style_label)) {
                SameViewSegmentControl(
                    items = styles.map {
                        SameViewSegmentItem(
                            label = when (it) {
                                ShareComparisonStyle.SLIDER -> stringResource(R.string.share_comparison_style_slider)
                                ShareComparisonStyle.SIDE_BY_SIDE -> stringResource(R.string.share_comparison_style_side_by_side)
                            }
                        )
                    },
                    selectedIndex = styles.indexOf(style),
                    onItemSelected = { style = styles[it] },
                    modifier = Modifier.testTag("share_comparison_style_control")
                )
            }
            // Comparison logo card (Slider only) — mirrors production 3-zone layout
            if (style == ShareComparisonStyle.SLIDER) {
                SettingsCard(title = stringResource(R.string.share_comparison_logo_card_title)) {
                    if (!hasBranding) {
                        // ZONE 1: Empty state
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("share_comparison_logo_placeholder"),
                                contentAlignment = Alignment.Center
                            ) {}
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.share_comparison_logo_none),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // ZONE 2: Primary source actions
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_comparison_logo_choose_photo")
                            ) { Text(stringResource(R.string.share_comparison_logo_choose_photo)) }
                            androidx.compose.material3.OutlinedButton(
                                onClick = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_comparison_logo_use_symbol")
                            ) { Text(stringResource(R.string.share_comparison_logo_use_symbol)) }
                        }
                        if (hasGlobalBranding && !isUsingGlobalDefault) {
                            androidx.compose.material3.TextButton(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("share_comparison_logo_use_default")
                            ) { Text(stringResource(R.string.share_comparison_logo_use_default)) }
                        }
                    } else {
                        // ZONE 1: Preview + visibility toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("share_comparison_logo_preview")
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                SettingsSwitchRow(
                                    label = stringResource(R.string.share_comparison_logo_show),
                                    checked = useBranding,
                                    onCheckedChange = { useBranding = it },
                                    testTag = "share_comparison_toggle_logo"
                                )
                            }
                        }
                        // ZONE 2: Primary source actions
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_comparison_logo_choose_photo")
                            ) { Text(stringResource(R.string.share_comparison_logo_choose_photo)) }
                            androidx.compose.material3.OutlinedButton(
                                onClick = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_comparison_logo_use_symbol")
                            ) { Text(stringResource(R.string.share_comparison_logo_use_symbol)) }
                        }
                        if (hasGlobalBranding && !isUsingGlobalDefault) {
                            androidx.compose.material3.TextButton(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("share_comparison_logo_use_default")
                            ) { Text(stringResource(R.string.share_comparison_logo_use_default)) }
                        }
                        // ZONE 3: Destructive
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_comparison_logo_remove")
                        ) { Text(stringResource(R.string.share_comparison_logo_remove)) }
                    }
                }
            }
            // Extras card
            SettingsCard(title = stringResource(R.string.share_comparison_extras_label)) {
                SettingsSwitchRow(
                    label = stringResource(R.string.create_video_overlay_title_date_label),
                    checked = true,
                    onCheckedChange = {},
                    testTag = "share_comparison_toggle_title_date"
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.create_video_overlay_location_label),
                    checked = false,
                    onCheckedChange = {},
                    testTag = "share_comparison_toggle_location"
                )
            }
            // Quality card
            SettingsCard(title = stringResource(R.string.share_comparison_quality_label)) {
                SameViewSegmentControl(
                    items = qualities.map {
                        SameViewSegmentItem(
                            label = when (it) {
                                ShareQuality.STANDARD -> stringResource(R.string.share_comparison_quality_standard)
                                ShareQuality.ORIGINAL -> stringResource(R.string.share_comparison_quality_original)
                            }
                        )
                    },
                    selectedIndex = qualities.indexOf(quality),
                    onItemSelected = { quality = qualities[it] },
                    modifier = Modifier.testTag("share_comparison_quality_control")
                )
            }
            // Share button
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_comparison_share_button")
            ) {
                Text(stringResource(R.string.share_comparison_action_share))
            }
        }
    }
}
