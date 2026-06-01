package com.isardomains.sameview.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class SessionBackupExporterInstrumentedTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(appContext.filesDir, "backup-exporter-instrumented-test")

    @Before
    fun setUp() {
        testRoot.deleteRecursively()
        testRoot.mkdirs()
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    // ---- helpers ----

    private fun createSession(sessionsRoot: File, sessionId: String): Map<String, ByteArray> {
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        val content = mapOf(
            "capture.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 10, 20, 30),
            "reference.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 40, 50, 60),
            "reference-original.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 70, 80, 90),
            "metadata.json" to """{"version":3,"session":{"id":"$sessionId"}}""".toByteArray(Charsets.UTF_8)
        )
        content.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        return content
    }

    private fun exportToFile(sessionsRoot: File, sessionIds: List<String>): Pair<File, SessionBackupExporter.BackupResult> {
        val zipFile = File(testRoot, "output_${System.nanoTime()}.zip")
        val result = zipFile.outputStream().use { os ->
            SessionBackupExporter.exportSessions(sessionsRoot, sessionIds, os)
        }
        return zipFile to result
    }

    private fun readZipEntries(zipFile: File): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    // ---- single-session tests ----

    @Test
    fun singleSession_zipFileIsCreatedAndNonEmpty() {
        val sessionsRoot = File(testRoot, "sessions")
        val sessionId = "2024-01-15_10-30-00"
        createSession(sessionsRoot, sessionId)

        val (zipFile, result) = exportToFile(sessionsRoot, listOf(sessionId))

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        assertTrue(zipFile.exists())
        assertTrue(zipFile.length() > 0L)
    }

    @Test
    fun singleSession_zipContainsExactlyFourEntries() {
        val sessionsRoot = File(testRoot, "sessions")
        val sessionId = "2024-01-15_10-30-00"
        createSession(sessionsRoot, sessionId)

        val (zipFile, result) = exportToFile(sessionsRoot, listOf(sessionId))

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = readZipEntries(zipFile)
        assertEquals(4, entries.size)
        assertTrue(entries.containsKey("$sessionId/capture.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference-original.jpg"))
        assertTrue(entries.containsKey("$sessionId/metadata.json"))
    }

    @Test
    fun singleSession_zipContentsAreByteIdenticalToSourceFiles() {
        val sessionsRoot = File(testRoot, "sessions")
        val sessionId = "2024-02-20_09-00-00"
        val expectedContent = createSession(sessionsRoot, sessionId)

        val (zipFile, result) = exportToFile(sessionsRoot, listOf(sessionId))

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = readZipEntries(zipFile)
        expectedContent.forEach { (filename, expectedBytes) ->
            val extracted = entries["$sessionId/$filename"]
                ?: error("Entry $sessionId/$filename missing from ZIP")
            assertArrayEquals("Byte mismatch for $filename", expectedBytes, extracted)
        }
    }

    // ---- multi-session tests ----

    @Test
    fun multiSession_zipContainsAllSessionSubdirectories() {
        val sessionsRoot = File(testRoot, "sessions")
        val ids = listOf("2024-01-15_10-30-00", "2024-01-16_11-45-00")
        ids.forEach { createSession(sessionsRoot, it) }

        val (zipFile, result) = exportToFile(sessionsRoot, ids)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        assertEquals(2, (result as SessionBackupExporter.BackupResult.Success).sessionCount)
        val entries = readZipEntries(zipFile)
        assertEquals(8, entries.size)
        ids.forEach { id ->
            listOf("capture.jpg", "reference.jpg", "reference-original.jpg", "metadata.json")
                .forEach { f -> assertTrue(entries.containsKey("$id/$f")) }
        }
    }

    @Test
    fun multiSession_allFilesAreByteIdenticalToSources() {
        val sessionsRoot = File(testRoot, "sessions")
        val id1 = "2024-01-15_10-30-00"
        val id2 = "2024-01-16_11-45-00"
        val expected1 = createSession(sessionsRoot, id1)
        val expected2 = createSession(sessionsRoot, id2)

        val (zipFile, result) = exportToFile(sessionsRoot, listOf(id1, id2))

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = readZipEntries(zipFile)
        (expected1.map { id1 to it } + expected2.map { id2 to it }).forEach { (id, entry) ->
            val (filename, expectedBytes) = entry
            val extracted = entries["$id/$filename"]
                ?: error("Entry $id/$filename missing from ZIP")
            assertArrayEquals("Byte mismatch for $id/$filename", expectedBytes, extracted)
        }
    }
}
