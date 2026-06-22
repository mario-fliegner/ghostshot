package com.isardomains.sameview.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class SessionBackupExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ---- Helpers ----

    private fun createFakeSession(sessionsRoot: File, sessionId: String): File {
        val sessionDir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(sessionDir, "capture.jpg").writeBytes(fakeJpeg("capture-$sessionId"))
        File(sessionDir, "reference.jpg").writeBytes(fakeJpeg("ref-$sessionId"))
        File(sessionDir, "reference-original.jpg").writeBytes(fakeJpeg("orig-$sessionId"))
        File(sessionDir, "metadata.json").writeText(
            """{"version":4,"session":{"id":"$sessionId","createdAtMs":1705312200000},"files":{"capture":"capture.jpg","reference":"reference.jpg","referenceOriginal":"reference-original.jpg"}}"""
        )
        return sessionDir
    }

    private fun createFakeV5Session(sessionsRoot: File, sessionId: String, refSourceExt: String = ".jpg"): File {
        val sessionDir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        val refSourceFilename = "reference-source-original$refSourceExt"
        File(sessionDir, "capture.jpg").writeBytes(fakeJpeg("capture-$sessionId"))
        File(sessionDir, "capture-original.jpg").writeBytes(fakeJpeg("capture-orig-$sessionId"))
        File(sessionDir, "reference.jpg").writeBytes(fakeJpeg("ref-$sessionId"))
        File(sessionDir, "reference-original.jpg").writeBytes(fakeJpeg("ref-orig-$sessionId"))
        File(sessionDir, refSourceFilename).writeBytes(fakeJpeg("ref-src-$sessionId"))
        File(sessionDir, "metadata.json").writeText(
            """{"version":5,"session":{"id":"$sessionId","createdAtMs":1705312200000},"files":{"capture":"capture.jpg","captureOriginal":"capture-original.jpg","reference":"reference.jpg","referenceOriginal":"reference-original.jpg","referenceSourceOriginal":"$refSourceFilename"}}"""
        )
        return sessionDir
    }

    private fun metadataWithFiles(sessionId: String, vararg files: Pair<String, String>): String {
        val filesBlock = files.joinToString(",") { (k, v) -> """"$k":"$v"""" }
        return """{"version":4,"session":{"id":"$sessionId","createdAtMs":1705312200000},"files":{$filesBlock}}"""
    }

    private fun fakeJpeg(marker: String): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + marker.toByteArray()

    private fun ByteArrayOutputStream.readZipEntries(): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                result[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }

    // ---- Structure tests ----

    @Test
    fun singleSession_zipContainsExactlyFourEntries() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-01-15_10-30-00"
        createFakeSession(sessionsRoot, sessionId)

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = output.readZipEntries()
        assertEquals(4, entries.size)
        assertTrue(entries.containsKey("$sessionId/capture.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference-original.jpg"))
        assertTrue(entries.containsKey("$sessionId/metadata.json"))
    }

    @Test
    fun multiSession_zipContainsAllSessions() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val id1 = "2024-01-15_10-30-00"
        val id2 = "2024-01-16_11-45-00"
        createFakeSession(sessionsRoot, id1)
        createFakeSession(sessionsRoot, id2)

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(id1, id2), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        assertEquals(2, (result as SessionBackupExporter.BackupResult.Success).sessionCount)

        val entries = output.readZipEntries()
        assertEquals(8, entries.size)
        listOf(id1, id2).forEach { id ->
            assertTrue(entries.containsKey("$id/capture.jpg"))
            assertTrue(entries.containsKey("$id/reference.jpg"))
            assertTrue(entries.containsKey("$id/reference-original.jpg"))
            assertTrue(entries.containsKey("$id/metadata.json"))
        }
    }

    @Test
    fun threeSession_zipContainsCorrectSessionCount() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val ids = listOf("2024-01-15_10-30-00", "2024-01-16_11-45-00", "2024-01-17_12-00-00")
        ids.forEach { createFakeSession(sessionsRoot, it) }

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, ids, output)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        assertEquals(3, (result as SessionBackupExporter.BackupResult.Success).sessionCount)
        assertEquals(12, output.readZipEntries().size)
    }

    // ---- Byte integrity tests ----

    @Test
    fun captureJpeg_bytesAreUnmodified() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-01-15_10-30-00"
        val captureBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5)
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(dir, "capture.jpg").writeBytes(captureBytes)
        File(dir, "reference.jpg").writeBytes(byteArrayOf(10))
        File(dir, "reference-original.jpg").writeBytes(byteArrayOf(20))
        File(dir, "metadata.json").writeText(metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg"))

        val output = ByteArrayOutputStream()
        SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        assertArrayEquals(captureBytes, output.readZipEntries()["$sessionId/capture.jpg"])
    }

    @Test
    fun metadataJson_bytesAreUnmodified() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-01-15_10-30-00"
        val metadataContent = metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg")
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(dir, "capture.jpg").writeBytes(byteArrayOf(1))
        File(dir, "reference.jpg").writeBytes(byteArrayOf(2))
        File(dir, "reference-original.jpg").writeBytes(byteArrayOf(3))
        File(dir, "metadata.json").writeText(metadataContent, Charsets.UTF_8)

        val output = ByteArrayOutputStream()
        SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        val extracted = output.readZipEntries()["$sessionId/metadata.json"]!!.toString(Charsets.UTF_8)
        assertEquals(metadataContent, extracted)
    }

    @Test
    fun allFiles_bytesAreUnmodified() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-02-20_09-00-00"
        val captureBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 10, 11, 12)
        val referenceBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 20, 21, 22)
        val referenceOriginalBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 30, 31, 32)
        val metadataContent = metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg")
        val metadataBytes = metadataContent.toByteArray(Charsets.UTF_8)

        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(dir, "capture.jpg").writeBytes(captureBytes)
        File(dir, "reference.jpg").writeBytes(referenceBytes)
        File(dir, "reference-original.jpg").writeBytes(referenceOriginalBytes)
        File(dir, "metadata.json").writeBytes(metadataBytes)

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = output.readZipEntries()
        assertArrayEquals(captureBytes, entries["$sessionId/capture.jpg"])
        assertArrayEquals(referenceBytes, entries["$sessionId/reference.jpg"])
        assertArrayEquals(referenceOriginalBytes, entries["$sessionId/reference-original.jpg"])
        assertArrayEquals(metadataBytes, entries["$sessionId/metadata.json"])
    }

    // ---- Missing required file tests ----

    @Test
    fun missingCaptureJpeg_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-01-15_10-30-00"
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        // metadata declares capture.jpg but it is intentionally absent from disk
        File(dir, "reference.jpg").writeBytes(byteArrayOf(1))
        File(dir, "reference-original.jpg").writeBytes(byteArrayOf(2))
        File(dir, "metadata.json").writeText(metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg"))

        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun missingReferenceJpeg_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-01-15_10-30-00"
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(dir, "capture.jpg").writeBytes(byteArrayOf(1))
        // metadata declares reference.jpg but it is intentionally absent from disk
        File(dir, "reference-original.jpg").writeBytes(byteArrayOf(2))
        File(dir, "metadata.json").writeText(metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg"))

        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun missingReferenceOriginalJpeg_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-01-15_10-30-00"
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(dir, "capture.jpg").writeBytes(byteArrayOf(1))
        File(dir, "reference.jpg").writeBytes(byteArrayOf(2))
        // metadata declares reference-original.jpg but it is intentionally absent from disk
        File(dir, "metadata.json").writeText(metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg"))

        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun missingMetadataJson_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-01-15_10-30-00"
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(dir, "capture.jpg").writeBytes(byteArrayOf(1))
        File(dir, "reference.jpg").writeBytes(byteArrayOf(2))
        File(dir, "reference-original.jpg").writeBytes(byteArrayOf(3))
        // metadata.json intentionally omitted

        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun nonExistentSessionDirectory_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(
            sessionsRoot, listOf("2024-01-15_10-30-00"), ByteArrayOutputStream()
        )
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    // ---- Security / path-traversal tests ----

    @Test
    fun sessionId_dotDot_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(".."), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun sessionId_dot_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf("."), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun sessionId_dotDotSlash_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf("../other"), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun sessionId_absolutePath_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val outsideDir = tempFolder.newFolder("outside")
        val result = SessionBackupExporter.exportSessions(
            sessionsRoot, listOf(outsideDir.absolutePath), ByteArrayOutputStream()
        )
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun sessionId_nestedForwardSlash_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf("a/b"), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun sessionId_nestedBackslash_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf("a\\b"), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun sessionId_empty_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(""), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    // ---- Empty list test ----

    @Test
    fun emptySessionList_returnsFailure() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val result = SessionBackupExporter.exportSessions(sessionsRoot, emptyList(), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    // ---- All-or-nothing tests ----

    @Test
    fun secondSessionMissing_wholeBatchFails() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val id1 = "2024-01-15_10-30-00"
        val id2 = "2024-01-16_11-45-00"
        createFakeSession(sessionsRoot, id1)
        // id2 not created

        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(id1, id2), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun firstSessionMissing_wholeBatchFails() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val id1 = "2024-01-15_10-30-00"
        val id2 = "2024-01-16_11-45-00"
        // id1 not created
        createFakeSession(sessionsRoot, id2)

        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(id1, id2), ByteArrayOutputStream())
        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
    }

    @Test
    fun secondSessionHasMissingFile_preValidationPreventsAnyWrite() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val id1 = "2024-01-15_10-30-00"
        val id2 = "2024-01-16_11-45-00"
        createFakeSession(sessionsRoot, id1)
        // id2 exists: metadata declares capture.jpg but it is intentionally absent from disk
        val dir2 = File(sessionsRoot, id2).also { it.mkdirs() }
        File(dir2, "reference.jpg").writeBytes(byteArrayOf(1))
        File(dir2, "reference-original.jpg").writeBytes(byteArrayOf(2))
        File(dir2, "metadata.json").writeText(metadataWithFiles(id2,
            "capture" to "capture.jpg", "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg"))

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(id1, id2), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
        // Pre-validation means nothing was written before failure
        assertEquals(0, output.size())
    }

    @Test
    fun invalidIdInBatch_preValidationPreventsAnyWrite() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val id1 = "2024-01-15_10-30-00"
        createFakeSession(sessionsRoot, id1)

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(id1, "../evil"), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
        assertEquals(0, output.size())
    }

    // ---- v5 and metadata-driven tests ----

    @Test
    fun v5Session_zipContainsSixEntries() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-06-01_12-00-00"
        createFakeV5Session(sessionsRoot, sessionId, refSourceExt = ".heic")

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = output.readZipEntries()
        assertEquals(6, entries.size)
        assertTrue(entries.containsKey("$sessionId/capture.jpg"))
        assertTrue(entries.containsKey("$sessionId/capture-original.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference-original.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference-source-original.heic"))
        assertTrue(entries.containsKey("$sessionId/metadata.json"))
    }

    @Test
    fun v4Session_zipContainsFourEntries() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-06-01_12-00-00"
        createFakeSession(sessionsRoot, sessionId)

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = output.readZipEntries()
        assertEquals(4, entries.size)
        assertTrue(entries.containsKey("$sessionId/capture.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference-original.jpg"))
        assertTrue(entries.containsKey("$sessionId/metadata.json"))
    }

    @Test
    fun metadataDriven_zipEntriesMatchDeclaredFiles() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-06-01_12-00-00"
        // Session with a custom file set declared in files.*
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        File(dir, "capture.jpg").writeBytes(fakeJpeg("c"))
        File(dir, "reference.jpg").writeBytes(fakeJpeg("r"))
        File(dir, "metadata.json").writeText(metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "reference" to "reference.jpg"))

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Success)
        val entries = output.readZipEntries()
        // Only declared files + metadata.json are exported (reference-original.jpg not declared)
        assertEquals(3, entries.size)
        assertTrue(entries.containsKey("$sessionId/capture.jpg"))
        assertTrue(entries.containsKey("$sessionId/reference.jpg"))
        assertTrue(entries.containsKey("$sessionId/metadata.json"))
    }

    @Test
    fun missingDeclaredFile_preValidationFails() {
        val sessionsRoot = tempFolder.newFolder("sessions")
        val sessionId = "2024-06-01_12-00-00"
        val dir = File(sessionsRoot, sessionId).also { it.mkdirs() }
        // metadata declares reference-source-original.bin but it is absent from disk
        File(dir, "capture.jpg").writeBytes(fakeJpeg("c"))
        File(dir, "capture-original.jpg").writeBytes(fakeJpeg("co"))
        File(dir, "reference.jpg").writeBytes(fakeJpeg("r"))
        File(dir, "reference-original.jpg").writeBytes(fakeJpeg("ro"))
        // reference-source-original.bin intentionally absent
        File(dir, "metadata.json").writeText(metadataWithFiles(sessionId,
            "capture" to "capture.jpg", "captureOriginal" to "capture-original.jpg",
            "reference" to "reference.jpg", "referenceOriginal" to "reference-original.jpg",
            "referenceSourceOriginal" to "reference-source-original.bin"))

        val output = ByteArrayOutputStream()
        val result = SessionBackupExporter.exportSessions(sessionsRoot, listOf(sessionId), output)

        assertTrue(result is SessionBackupExporter.BackupResult.Failure)
        assertEquals(0, output.size())
    }

    @Test
    fun collectSessionFiles_returnsFilesBlockPlusMetadata() {
        val sessionDir = tempFolder.newFolder("session")
        File(sessionDir, "metadata.json").writeText(metadataWithFiles("test-session",
            "capture" to "capture.jpg", "captureOriginal" to "capture-original.jpg",
            "reference" to "reference.jpg"))

        val files = SessionBackupExporter.collectSessionFiles(sessionDir)

        // Three files from files.* plus metadata.json at the end.
        assertEquals(4, files.size)
        assertTrue(files.contains("capture.jpg"))
        assertTrue(files.contains("capture-original.jpg"))
        assertTrue(files.contains("reference.jpg"))
        // metadata.json is always appended last.
        assertEquals("metadata.json", files.last())
    }
}
