package com.isardomains.sameview.guide

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WalkthroughCompletionState {
    object Loading : WalkthroughCompletionState
    data class Loaded(val isCompleted: Boolean) : WalkthroughCompletionState
}

@Singleton
class GuideRepository @Inject constructor(
    @param:GuideDataStore private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val WALKTHROUGH_COMPLETED = booleanPreferencesKey("walkthrough_completed")
        val SEEN_TIP_IDS = stringSetPreferencesKey("seen_tip_ids")
    }

    private val preferences: Flow<Preferences> = dataStore.data
        .catch {
            emit(emptyPreferences())
        }

    fun observeWalkthroughCompleted(): Flow<Boolean> =
        preferences.map { prefs -> prefs[Keys.WALKTHROUGH_COMPLETED] ?: false }

    fun observeWalkthroughCompletionState(): Flow<WalkthroughCompletionState> =
        observeWalkthroughCompleted().map { completed ->
            WalkthroughCompletionState.Loaded(completed)
        }

    suspend fun markWalkthroughComplete() {
        dataStore.edit { prefs ->
            prefs[Keys.WALKTHROUGH_COMPLETED] = true
        }
    }

    fun observeSeenTipIds(): Flow<Set<GuideTipId>> =
        preferences.map { prefs ->
            prefs[Keys.SEEN_TIP_IDS]
                .orEmpty()
                .mapNotNull(GuideTipId::fromStoredValue)
                .toSet()
        }

    fun observeTipSeen(tipId: GuideTipId): Flow<Boolean> =
        observeSeenTipIds().map { seenTipIds -> tipId in seenTipIds }

    suspend fun markTipSeen(tipId: GuideTipId) {
        dataStore.edit { prefs ->
            val seenTipIds = prefs[Keys.SEEN_TIP_IDS].orEmpty() + tipId.storedValue
            prefs[Keys.SEEN_TIP_IDS] = seenTipIds
        }
    }

    suspend fun resetContextualTips() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SEEN_TIP_IDS)
        }
    }
}
