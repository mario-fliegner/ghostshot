package com.isardomains.sameview.guide

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GuideTipControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): GuideRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tempFolder.newFile("guide.preferences_pb") }
        )
        return GuideRepository(dataStore)
    }

    @Test
    fun evaluate_activatesOnlyOneTipAtATime() = runTest {
        val controller = GuideTipController(createRepository())
        val context = GuideTipEvaluationContext(
            scope = GuideTipScope.COMPARE,
            eligibleTipIds = setOf(GuideTipId.SHARE, GuideTipId.EDIT_SESSION)
        )

        val firstTip = controller.evaluate(context)
        val secondTip = controller.evaluate(context)

        assertEquals(GuideTipId.SHARE, firstTip?.id)
        assertNull(secondTip)
        assertEquals(GuideTipId.SHARE, controller.activeTipId.value)
    }

    @Test
    fun evaluate_suppressesSeenTips() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        repository.markTipSeen(GuideTipId.SHARE)

        val tip = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.COMPARE,
                eligibleTipIds = setOf(GuideTipId.SHARE, GuideTipId.EDIT_SESSION)
            )
        )

        assertEquals(GuideTipId.EDIT_SESSION, tip?.id)
        assertEquals(GuideTipId.EDIT_SESSION, controller.activeTipId.value)
    }

    @Test
    fun evaluate_returnsNullWhenTransientUiBlocksTips() = runTest {
        val controller = GuideTipController(createRepository())

        val tip = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE),
                isBlockedByTransientUi = true
            )
        )

        assertNull(tip)
        assertNull(controller.activeTipId.value)
    }

    @Test
    fun dismissActiveTip_gotIt_marksTipSeen() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)

        assertNull(controller.activeTipId.value)
        assertEquals(setOf(GuideTipId.REFERENCE), repository.observeSeenTipIds().first())
    }

    @Test
    fun dismissActiveTip_gotIt_tipNeverReappearsEvenAfterUserAction() = runTest {
        val controller = GuideTipController(createRepository())
        val context = GuideTipEvaluationContext(
            scope = GuideTipScope.CAMERA,
            eligibleTipIds = setOf(GuideTipId.REFERENCE)
        )

        controller.evaluate(context)
        controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)
        controller.onUserAction()

        assertNull(controller.evaluate(context))
    }

    @Test
    fun antiSpam_blocksADifferentTipUntilUserActionAfterDismissal() = runTest {
        val controller = GuideTipController(createRepository())

        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)

        val blockedTip = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.COMPARE,
                eligibleTipIds = setOf(GuideTipId.SHARE)
            )
        )
        assertNull(blockedTip)

        controller.onUserAction()

        val nextTip = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.COMPARE,
                eligibleTipIds = setOf(GuideTipId.SHARE)
            )
        )
        assertEquals(GuideTipId.SHARE, nextTip?.id)
    }

    @Test
    fun clearActiveTipWithoutMarkingSeen_allowsSameTipLaterAfterUserAction() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)
        val context = GuideTipEvaluationContext(
            scope = GuideTipScope.CAMERA,
            eligibleTipIds = setOf(GuideTipId.REFERENCE)
        )

        controller.evaluate(context)
        controller.clearActiveTipWithoutMarkingSeen()
        controller.onUserAction()

        assertEquals(emptySet<GuideTipId>(), repository.observeSeenTipIds().first())
        assertEquals(GuideTipId.REFERENCE, controller.evaluate(context)?.id)
    }

    @Test
    fun dismissActiveTip_withLearnMore_marksTipSeenAndClearsActiveTip() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        controller.dismissActiveTip(GuideTipDismissReason.LEARN_MORE)

        assertNull(controller.activeTipId.value)
        assertEquals(setOf(GuideTipId.REFERENCE), repository.observeSeenTipIds().first())
    }

    @Test
    fun completeTip_marksSeenInRepository() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        controller.completeTip(GuideTipId.REFERENCE)

        assertEquals(setOf(GuideTipId.REFERENCE), repository.observeSeenTipIds().first())
    }

    @Test
    fun completeTip_clearsActiveTipIfActive() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        controller.completeTip(GuideTipId.REFERENCE)

        assertNull(controller.activeTipId.value)
    }

    @Test
    fun completeTip_doesNotClearActiveTipIfDifferentTipIsActive() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        controller.completeTip(GuideTipId.SHARE)

        assertEquals(GuideTipId.REFERENCE, controller.activeTipId.value)
    }

    @Test
    fun completeTip_setsWaitingForUserAction() = runTest {
        val controller = GuideTipController(createRepository())

        controller.completeTip(GuideTipId.REFERENCE)

        assertNull(
            controller.evaluate(
                GuideTipEvaluationContext(
                    scope = GuideTipScope.CAMERA,
                    eligibleTipIds = setOf(GuideTipId.REFERENCE)
                )
            )
        )
    }

    @Test
    fun evaluate_editSession_notEligibleWithoutShareCompleted() = runTest {
        val controller = GuideTipController(createRepository())

        val tip = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.COMPARE,
                eligibleTipIds = setOf(GuideTipId.EDIT_SESSION)
            )
        )

        assertNull(tip)
        assertNull(controller.activeTipId.value)
    }

    @Test
    fun evaluate_multiSelect_notEligibleWithoutOpenComparisonCompleted() = runTest {
        val controller = GuideTipController(createRepository())

        val tip = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.LIBRARY,
                eligibleTipIds = setOf(GuideTipId.MULTI_SELECT)
            )
        )

        assertNull(tip)
        assertNull(controller.activeTipId.value)
    }
}



