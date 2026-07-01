package com.isardomains.sameview.ui.compare

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.guide.GuideRepository
import com.isardomains.sameview.guide.GuideTipController
import com.isardomains.sameview.guide.GuideTipId
import com.isardomains.sameview.guide.GuideTopicId
import com.isardomains.sameview.ui.theme.SameViewTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompareGuideTipIntegrationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var dataStoreFile: File? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        dataStoreFile?.delete()
        dataStoreFile = null
    }

    @Test
    fun exportTip_appearsForSavedSessionContext() {
        val controller = GuideTipController(createRepository())

        setCompareContent(controller = controller)

        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()
    }

    @Test
    fun exportTip_gotItMarksTipSeen() = runBlocking {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        setCompareContent(controller = controller)
        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_tip_got_it").performClick()
        waitUntilTipSeen(repository)

        assertTrue(repository.observeSeenTipIds().first().contains(GuideTipId.EXPORT))
    }

    @Test
    fun exportTip_learnMoreOpensShareGuideTopicAndMarksSeen() = runBlocking {
        val repository = createRepository()
        val controller = GuideTipController(repository)
        var openedTopic: GuideTopicId? = null

        setCompareContent(
            controller = controller,
            onOpenGuideTopic = { openedTopic = it }
        )
        composeRule.onNodeWithTag("guide_tip_card").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_tip_learn_more").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { openedTopic != null }
        waitUntilTipSeen(repository)

        assertEquals(GuideTopicId.SHARE_COMPARISON_IMAGE, openedTopic)
        assertTrue(repository.observeSeenTipIds().first().contains(GuideTipId.EXPORT))
    }

    private fun setCompareContent(
        controller: GuideTipController,
        onOpenGuideTopic: (GuideTopicId) -> Unit = {}
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
                    CompareScreen(
                        referenceImageUri = null,
                        captureImageUri = null,
                        onBack = {},
                        sessionId = "session-1",
                        onShareComparisonImage = {},
                        isShareComparisonAvailable = true,
                        onCreateVideo = {},
                        isCreateVideoAvailable = true,
                        windowWidthSizeClass = WindowWidthSizeClass.Compact,
                        guideTipController = controller,
                        onOpenGuideTopic = onOpenGuideTopic
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitUntilTipSeen(repository: GuideRepository) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.observeSeenTipIds().first().contains(GuideTipId.EXPORT) }
        }
    }

    private fun createRepository(): GuideRepository {
        val file = File(context.cacheDir, "guide-tip-${UUID.randomUUID()}.preferences_pb")
        dataStoreFile = file
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file }
        )
        return GuideRepository(dataStore)
    }

    private fun wakeTestDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }
}




