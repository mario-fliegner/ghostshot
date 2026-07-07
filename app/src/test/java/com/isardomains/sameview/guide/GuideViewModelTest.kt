package com.isardomains.sameview.guide

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRepository(): GuideRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tempFolder.newFile("guide.preferences_pb") }
        )
        return GuideRepository(dataStore)
    }

    @Test
    fun onResetTipsConfirmed_clearsSeenTipIdsAndControllerInMemoryState() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val viewModel = GuideViewModel(repository, controller)

        // Pre-load state as if the user had already used the app earlier this session.
        repository.markTipSeen(GuideTipId.REFERENCE)
        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)
        viewModel.onShowTipsAgainClick()

        viewModel.onResetTipsConfirmed()

        assertEquals(emptySet<GuideTipId>(), repository.observeSeenTipIds().first())
        assertNull(controller.activeTipId.value)
        assertFalse(viewModel.uiState.value.showResetTipsConfirmation)
        // If waitingForUserActionAfterDismissal were still true from the dismissal above,
        // this would return null instead of the tip.
        val tipAfterReset = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.LIBRARY,
                eligibleTipIds = setOf(GuideTipId.OPEN_COMPARISON)
            )
        )
        assertEquals(GuideTipId.OPEN_COMPARISON, tipAfterReset?.id)
    }
}
