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

/** Filter applied to the Compare Library session grid. Default: [ALL]. */
enum class LibraryFilter(val storedValue: String) {
    ALL("all"),
    FAVORITES("favorites")
}

/** Sort order applied to the Compare Library session grid. Default: [NEWEST_FIRST]. */
enum class LibrarySortOrder(val storedValue: String) {
    NEWEST_FIRST("newest_first"),
    OLDEST_FIRST("oldest_first")
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val GRID_TYPE = stringPreferencesKey("grid_type")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val RESET_OVERLAY_AFTER_CAPTURE = booleanPreferencesKey("reset_overlay_after_capture")
        val AUTO_OPEN_COMPARE_AFTER_CAPTURE = booleanPreferencesKey("auto_open_compare_after_capture")
        val RECREATION_GUIDANCE = booleanPreferencesKey("recreation_guidance")
        val LIVE_DIRECTION_ARROW = booleanPreferencesKey("live_direction_arrow")
        val BRANDING_ENABLED = booleanPreferencesKey("branding_enabled")
        val LIBRARY_FILTER = stringPreferencesKey("library_filter")
        val LIBRARY_SORT_ORDER = stringPreferencesKey("library_sort_order")
        val STRIP_ORIGINALS_METADATA = booleanPreferencesKey("strip_originals_metadata")
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

    val recreationGuidance: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.RECREATION_GUIDANCE] ?: false
    }

    suspend fun setRecreationGuidance(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.RECREATION_GUIDANCE] = enabled
        }
    }

    val liveDirectionArrow: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.LIVE_DIRECTION_ARROW] ?: false
    }

    suspend fun setLiveDirectionArrow(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.LIVE_DIRECTION_ARROW] = enabled
        }
    }

    /** Branding endcard toggle; default OFF per VIDEO_EXPORT_V1 §13.3. */
    val brandingEnabled: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.BRANDING_ENABLED] ?: false
    }

    suspend fun setBrandingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BRANDING_ENABLED] = enabled
        }
    }

    /** Library session filter; default [LibraryFilter.ALL]. */
    val libraryFilter: Flow<LibraryFilter> = preferences.map { prefs ->
        when (prefs[Keys.LIBRARY_FILTER]) {
            LibraryFilter.FAVORITES.storedValue -> LibraryFilter.FAVORITES
            else -> LibraryFilter.ALL
        }
    }

    suspend fun setLibraryFilter(filter: LibraryFilter) {
        dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_FILTER] = filter.storedValue
        }
    }

    /** Library session sort order; default [LibrarySortOrder.NEWEST_FIRST]. */
    val librarySortOrder: Flow<LibrarySortOrder> = preferences.map { prefs ->
        when (prefs[Keys.LIBRARY_SORT_ORDER]) {
            LibrarySortOrder.OLDEST_FIRST.storedValue -> LibrarySortOrder.OLDEST_FIRST
            else -> LibrarySortOrder.NEWEST_FIRST
        }
    }

    suspend fun setLibrarySortOrder(order: LibrarySortOrder) {
        dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_SORT_ORDER] = order.storedValue
        }
    }

    /** Whether to strip EXIF/GPS/camera metadata from stored session originals; default false. */
    val stripOriginalsMetadata: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.STRIP_ORIGINALS_METADATA] ?: false
    }

    suspend fun setStripOriginalsMetadata(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.STRIP_ORIGINALS_METADATA] = enabled
        }
    }
}
