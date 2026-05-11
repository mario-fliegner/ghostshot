package com.isardomains.ghostshot.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isardomains.ghostshot.ui.camera.GridType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
}
