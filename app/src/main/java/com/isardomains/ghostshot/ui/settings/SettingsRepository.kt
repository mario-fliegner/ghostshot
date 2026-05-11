package com.isardomains.ghostshot.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.isardomains.ghostshot.ui.camera.GridType
import kotlinx.coroutines.flow.Flow
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
    }

    val gridType: Flow<GridType> = dataStore.data.map { prefs ->
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

    val keepScreenOn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }
}
