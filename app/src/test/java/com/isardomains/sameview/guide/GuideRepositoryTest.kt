package com.isardomains.sameview.guide

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GuideRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { tempFolder.newFile("guide.preferences_pb") }
        )

    @Test
    fun defaults_areSafeWhenKeysMissing() = runTest {
        val repository = GuideRepository(createDataStore())

        assertFalse(repository.observeWalkthroughCompleted().first())
        assertEquals(emptySet<GuideTipId>(), repository.observeSeenTipIds().first())
        assertEquals(
            WalkthroughCompletionState.Loaded(false),
            repository.observeWalkthroughCompletionState().first()
        )
    }

    @Test
    fun markWalkthroughComplete_persistsCompletion() = runTest {
        val repository = GuideRepository(createDataStore())

        repository.markWalkthroughComplete()

        assertTrue(repository.observeWalkthroughCompleted().first())
    }

    @Test
    fun markTipSeen_persistsSeenTip() = runTest {
        val repository = GuideRepository(createDataStore())

        repository.markTipSeen(GuideTipId.REFERENCE)

        assertEquals(setOf(GuideTipId.REFERENCE), repository.observeSeenTipIds().first())
        assertTrue(repository.observeTipSeen(GuideTipId.REFERENCE).first())
        assertFalse(repository.observeTipSeen(GuideTipId.SHARE).first())
    }

    @Test
    fun resetContextualTips_clearsTipsOnly() = runTest {
        val repository = GuideRepository(createDataStore())

        repository.markWalkthroughComplete()
        repository.markTipSeen(GuideTipId.REFERENCE)
        repository.resetContextualTips()

        assertTrue(repository.observeWalkthroughCompleted().first())
        assertEquals(emptySet<GuideTipId>(), repository.observeSeenTipIds().first())
    }

    @Test
    fun unknownStoredTipIds_areIgnored() = runTest {
        val dataStore = createDataStore()
        val repository = GuideRepository(dataStore)
        val seenTipIdsKey = stringSetPreferencesKey("seen_tip_ids")

        dataStore.edit { prefs ->
            prefs[seenTipIdsKey] = setOf(GuideTipId.REFERENCE.storedValue, "unknown_tip")
        }

        assertEquals(setOf(GuideTipId.REFERENCE), repository.observeSeenTipIds().first())
    }

    @Test
    fun missingWalkthroughKey_defaultsFalseEvenWhenOtherKeysExist() = runTest {
        val dataStore = createDataStore()
        val repository = GuideRepository(dataStore)
        val seenTipIdsKey = stringSetPreferencesKey("seen_tip_ids")

        dataStore.edit { prefs ->
            prefs[seenTipIdsKey] = setOf(GuideTipId.SHARE.storedValue)
        }

        assertFalse(repository.observeWalkthroughCompleted().first())
    }

    @Test
    fun persistedWalkthroughCompletion_survivesRepositoryRecreation() = runTest {
        val dataStore = createDataStore()
        val walkthroughCompletedKey = booleanPreferencesKey("walkthrough_completed")

        dataStore.edit { prefs ->
            prefs[walkthroughCompletedKey] = true
        }

        assertTrue(GuideRepository(dataStore).observeWalkthroughCompleted().first())
    }
}
