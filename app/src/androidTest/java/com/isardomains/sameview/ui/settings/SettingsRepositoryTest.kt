package com.isardomains.sameview.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.sameview.ui.camera.GridType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository
    private lateinit var testFile: File

    @Before
    fun setUp() {
        val fileName = "test_settings_${UUID.randomUUID()}.preferences_pb"
        testFile = context.preferencesDataStoreFile(fileName)
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { testFile }
        )
        repository = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        testFile.delete()
    }

    @Test
    fun gridType_defaultsToRuleOfThirds() = testScope.runTest {
        assertEquals(GridType.RULE_OF_THIRDS, repository.gridType.first())
    }

    @Test
    fun setGridType_quartersIsPersisted() = testScope.runTest {
        repository.setGridType(GridType.QUARTERS)
        assertEquals(GridType.QUARTERS, repository.gridType.first())
    }

    @Test
    fun setGridType_noneIsPersisted() = testScope.runTest {
        repository.setGridType(GridType.NONE)
        assertEquals(GridType.NONE, repository.gridType.first())
    }

    @Test
    fun setGridType_ruleOfThirdsCanBeSetExplicitly() = testScope.runTest {
        repository.setGridType(GridType.QUARTERS)
        repository.setGridType(GridType.RULE_OF_THIRDS)
        assertEquals(GridType.RULE_OF_THIRDS, repository.gridType.first())
    }

    @Test
    fun gridType_readException_defaultsToRuleOfThirds() = testScope.runTest {
        val failingRepository = SettingsRepository(
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow {
                    throw IOException("read failed")
                }

                override suspend fun updateData(
                    transform: suspend (t: Preferences) -> Preferences
                ): Preferences {
                    error("updateData should not be called")
                }
            }
        )

        assertEquals(GridType.RULE_OF_THIRDS, failingRepository.gridType.first())
    }

    @Test
    fun gridType_invalidStoredValue_defaultsToRuleOfThirds() = testScope.runTest {
        val key = stringPreferencesKey("grid_type")
        dataStore.edit { prefs ->
            prefs[key] = "not-a-grid-type"
        }

        assertEquals(GridType.RULE_OF_THIRDS, repository.gridType.first())
    }

    @Test
    fun keepScreenOn_defaultsToTrue() = testScope.runTest {
        assertEquals(true, repository.keepScreenOn.first())
    }

    @Test
    fun setKeepScreenOn_falseIsPersisted() = testScope.runTest {
        repository.setKeepScreenOn(false)
        assertEquals(false, repository.keepScreenOn.first())
    }

    @Test
    fun setKeepScreenOn_trueCanBeSetBack() = testScope.runTest {
        repository.setKeepScreenOn(false)
        repository.setKeepScreenOn(true)
        assertEquals(true, repository.keepScreenOn.first())
    }

    @Test
    fun resetOverlayAfterCapture_defaultsToFalse() = testScope.runTest {
        assertEquals(false, repository.resetOverlayAfterCapture.first())
    }

    @Test
    fun setResetOverlayAfterCapture_trueIsPersisted() = testScope.runTest {
        repository.setResetOverlayAfterCapture(true)
        assertEquals(true, repository.resetOverlayAfterCapture.first())
    }

    @Test
    fun setResetOverlayAfterCapture_falseCanBeSetBack() = testScope.runTest {
        repository.setResetOverlayAfterCapture(true)
        repository.setResetOverlayAfterCapture(false)
        assertEquals(false, repository.resetOverlayAfterCapture.first())
    }

    @Test
    fun autoOpenCompareAfterCapture_defaultsToFalse() = testScope.runTest {
        assertEquals(false, repository.autoOpenCompareAfterCapture.first())
    }

    @Test
    fun setAutoOpenCompareAfterCapture_trueIsPersisted() = testScope.runTest {
        repository.setAutoOpenCompareAfterCapture(true)
        assertEquals(true, repository.autoOpenCompareAfterCapture.first())
    }

    @Test
    fun setAutoOpenCompareAfterCapture_falseCanBeSetBack() = testScope.runTest {
        repository.setAutoOpenCompareAfterCapture(true)
        repository.setAutoOpenCompareAfterCapture(false)
        assertEquals(false, repository.autoOpenCompareAfterCapture.first())
    }

    @Test
    fun recreationGuidance_defaultIsFalse() = testScope.runTest {
        assertEquals(false, repository.recreationGuidance.first())
    }

    @Test
    fun recreationGuidance_togglePersists() = testScope.runTest {
        repository.setRecreationGuidance(true)
        assertEquals(true, repository.recreationGuidance.first())
        repository.setRecreationGuidance(false)
        assertEquals(false, repository.recreationGuidance.first())
    }

    // ── Block E: libraryFilter ────────────────────────────────────────────────

    @Test
    fun libraryFilter_defaultsToAll() = testScope.runTest {
        assertEquals(LibraryFilter.ALL, repository.libraryFilter.first())
    }

    @Test
    fun setLibraryFilter_favoritesIsPersisted() = testScope.runTest {
        repository.setLibraryFilter(LibraryFilter.FAVORITES)
        assertEquals(LibraryFilter.FAVORITES, repository.libraryFilter.first())
    }

    @Test
    fun setLibraryFilter_allCanBeSetBack() = testScope.runTest {
        repository.setLibraryFilter(LibraryFilter.FAVORITES)
        repository.setLibraryFilter(LibraryFilter.ALL)
        assertEquals(LibraryFilter.ALL, repository.libraryFilter.first())
    }

    @Test
    fun libraryFilter_invalidStoredValue_defaultsToAll() = testScope.runTest {
        val key = stringPreferencesKey("library_filter")
        dataStore.edit { prefs -> prefs[key] = "not-a-filter" }
        assertEquals(LibraryFilter.ALL, repository.libraryFilter.first())
    }

    // ── Block E: librarySortOrder ─────────────────────────────────────────────

    @Test
    fun librarySortOrder_defaultsToNewestFirst() = testScope.runTest {
        assertEquals(LibrarySortOrder.NEWEST_FIRST, repository.librarySortOrder.first())
    }

    @Test
    fun setLibrarySortOrder_oldestFirstIsPersisted() = testScope.runTest {
        repository.setLibrarySortOrder(LibrarySortOrder.OLDEST_FIRST)
        assertEquals(LibrarySortOrder.OLDEST_FIRST, repository.librarySortOrder.first())
    }

    @Test
    fun setLibrarySortOrder_newestFirstCanBeSetBack() = testScope.runTest {
        repository.setLibrarySortOrder(LibrarySortOrder.OLDEST_FIRST)
        repository.setLibrarySortOrder(LibrarySortOrder.NEWEST_FIRST)
        assertEquals(LibrarySortOrder.NEWEST_FIRST, repository.librarySortOrder.first())
    }

    @Test
    fun librarySortOrder_invalidStoredValue_defaultsToNewestFirst() = testScope.runTest {
        val key = stringPreferencesKey("library_sort_order")
        dataStore.edit { prefs -> prefs[key] = "not-a-sort-order" }
        assertEquals(LibrarySortOrder.NEWEST_FIRST, repository.librarySortOrder.first())
    }
}
