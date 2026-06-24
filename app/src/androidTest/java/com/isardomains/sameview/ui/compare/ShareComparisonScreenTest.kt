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
        composeRule.onNodeWithTag("share_comparison_share_button").assertIsDisplayed()
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
        composeRule.onNodeWithText(context.getString(R.string.share_comparison_logo_hint))
            .assertIsDisplayed()
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
            // Style card — segment only (no branding toggle in V2)
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
            // Logo on handle card (V2 — Slider only)
            if (style == ShareComparisonStyle.SLIDER) {
                SettingsCard(title = stringResource(R.string.share_comparison_logo_card_title)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!hasBranding) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("share_comparison_logo_placeholder"),
                                contentAlignment = Alignment.Center
                            ) {}
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.share_comparison_logo_none),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(R.string.share_comparison_logo_hint),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("share_comparison_logo_preview")
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                SettingsSwitchRow(
                                    label = stringResource(R.string.share_comparison_logo_show),
                                    checked = useBranding,
                                    onCheckedChange = { useBranding = it },
                                    testTag = "share_comparison_toggle_logo"
                                )
                            }
                        }
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
