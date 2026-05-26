package com.isardomains.sameview.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.isardomains.sameview.ui.camera.GridType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val GRID_TYPE = stringPreferencesKey("grid_type")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val RESET_OVERLAY_AFTER_CAPTURE = booleanPreferencesKey("reset_overlay_after_capture")
        val AUTO_OPEN_COMPARE_AFTER_CAPTURE = booleanPreferencesKey("auto_open_compare_after_capture")
    }

    private val preferences: Flow<Preferences> = dataStore.data
        .catch {
            emit(emptyPreferences())
        }

    val gridType: Flow<GridType> = preferences.map { prefs ->
        when (prefs[Keys.GRID_TYPE]) {
            GridType.NONE.name -> GridType.NONE
            GridType.QUARTERS.name -> GridType.QUARTERS
            else -> GridType.RULE_OF_THIRDS
        }
    }

    suspend fun setGridType(type: GridType) {
        dataStore.edit { prefs ->
            prefs[Keys.GRID_TYPE] = type.name
        }
    }

    val keepScreenOn: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }

    val resetOverlayAfterCapture: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.RESET_OVERLAY_AFTER_CAPTURE] ?: false
    }

    suspend fun setResetOverlayAfterCapture(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.RESET_OVERLAY_AFTER_CAPTURE] = enabled
        }
    }

    val autoOpenCompareAfterCapture: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.AUTO_OPEN_COMPARE_AFTER_CAPTURE] ?: false
    }

    suspend fun setAutoOpenCompareAfterCapture(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_OPEN_COMPARE_AFTER_CAPTURE] = enabled
        }
    }
}
