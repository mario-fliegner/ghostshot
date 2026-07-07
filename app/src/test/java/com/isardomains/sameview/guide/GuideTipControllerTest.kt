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
    fun clearActiveTipWithoutMarkingSeen_scopedToWrongTipId_doesNotClearActiveTip() = runTest {
        // Regression guard: a late/stale dispose from a screen that isn't foreground anymore
        // (e.g. CameraScreen disposing well after CompareLibraryScreen has already made
        // OPEN_COMPARISON active) must not wipe out a different screen's active tip.
        val controller = GuideTipController(createRepository())
        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.LIBRARY,
                eligibleTipIds = setOf(GuideTipId.OPEN_COMPARISON)
            )
        )
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)

        controller.clearActiveTipWithoutMarkingSeen(GuideTipId.REFERENCE)
        assertEquals(GuideTipId.OPEN_COMPARISON, controller.activeTipId.value)

        controller.clearActiveTipWithoutMarkingSeen(GuideTipId.OPEN_COMPARISON)
        assertNull(controller.activeTipId.value)
    }

    @Test
    fun clearActiveTipWithoutMarkingSeen_nullExpectedTipId_clearsUnconditionally() = runTest {
        // Preserves the original unconditional-clear behavior for callers that don't track a
        // specific tip.
        val controller = GuideTipController(createRepository())
        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        assertEquals(GuideTipId.REFERENCE, controller.activeTipId.value)

        controller.clearActiveTipWithoutMarkingSeen()

        assertNull(controller.activeTipId.value)
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
    fun resetInMemoryState_clearsActiveTipAndAntiSpamFlag() = runTest {
        val controller = GuideTipController(createRepository())

        // Pre-load state: an active tip plus the anti-spam flag from a prior dismissal,
        // simulating a controller that has already been used earlier in the session.
        controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE)
            )
        )
        controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)
        // dismissActiveTip already clears activeTipId; re-evaluate to prove the anti-spam
        // flag alone blocks a different tip before reset.
        val blockedBeforeReset = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.LIBRARY,
                eligibleTipIds = setOf(GuideTipId.OPEN_COMPARISON)
            )
        )
        assertNull(blockedBeforeReset)

        controller.resetInMemoryState()

        assertNull(controller.activeTipId.value)
        // If waitingForUserActionAfterDismissal were still true, this would also return null.
        val tipAfterReset = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.LIBRARY,
                eligibleTipIds = setOf(GuideTipId.OPEN_COMPARISON)
            )
        )
        assertEquals(GuideTipId.OPEN_COMPARISON, tipAfterReset?.id)
    }
}



