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
            scope = GuideTipScope.CAMERA,
            eligibleTipIds = setOf(GuideTipId.REFERENCE, GuideTipId.ALIGN)
        )

        val firstTip = controller.evaluate(context)
        val secondTip = controller.evaluate(context)

        assertEquals(GuideTipId.REFERENCE, firstTip?.id)
        assertNull(secondTip)
        assertEquals(GuideTipId.REFERENCE, controller.activeTipId.value)
    }

    @Test
    fun evaluate_suppressesSeenTips() = runTest {
        val repository = createRepository()
        val controller = GuideTipController(repository)

        repository.markTipSeen(GuideTipId.REFERENCE)

        val tip = controller.evaluate(
            GuideTipEvaluationContext(
                scope = GuideTipScope.CAMERA,
                eligibleTipIds = setOf(GuideTipId.REFERENCE, GuideTipId.ALIGN)
            )
        )

        assertEquals(GuideTipId.ALIGN, tip?.id)
        assertEquals(GuideTipId.ALIGN, controller.activeTipId.value)
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
    fun dismissActiveTip_marksTipSeenAndClearsActiveTip() = runTest {
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
    fun antiSpam_requiresUserActionAfterDismissalBeforeNextTip() = runTest {
        val controller = GuideTipController(createRepository())
        val context = GuideTipEvaluationContext(
            scope = GuideTipScope.CAMERA,
            eligibleTipIds = setOf(GuideTipId.REFERENCE, GuideTipId.ALIGN)
        )

        controller.evaluate(context)
        controller.dismissActiveTip(GuideTipDismissReason.GOT_IT)

        assertNull(controller.evaluate(context))

        controller.onUserAction()

        assertEquals(GuideTipId.ALIGN, controller.evaluate(context)?.id)
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
}



