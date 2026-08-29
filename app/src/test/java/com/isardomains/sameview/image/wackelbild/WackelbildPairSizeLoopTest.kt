// path: app/src/test/java/com/isardomains/sameview/image/wackelbild/WackelbildPairSizeLoopTest.kt
package com.isardomains.sameview.image.wackelbild

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Exercises [WackelbildPairSizeLoop]'s retry/bound/sequencing orchestration in isolation, using
 * injected fake render lambdas that write controllably-sized dummy files -- no real Bitmap/JPEG
 * work, no giant 20 MiB fixtures, deterministic and fast. This is the seam the production
 * [WackelbildPrintRenderer] also uses; the actual badge-before-encode Bitmap work is covered by
 * the instrumented test.
 */
class WackelbildPairSizeLoopTest {

    private val tempDir = Files.createTempDirectory("wackelbild-loop-test-").toFile()

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun fakeFile(name: String, sizeBytes: Long): File {
        val file = File(tempDir, name)
        file.writeBytes(ByteArray(sizeBytes.coerceAtMost(1024).toInt())) // content is irrelevant; we override length via truncate below
        if (sizeBytes > 1024) {
            java.io.RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
        }
        return file
    }

    // ── First attempt fits ────────────────────────────────────────────────────

    @Test
    fun run_bothFilesFitOnFirstAttempt_succeedsImmediately() = runBlocking {
        var refCalls = 0
        var capCalls = 0
        val loop = WackelbildPairSizeLoop()
        val result = loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, _ -> refCalls++; fakeFile("ref.jpg", 5_000_000) },
            renderCapture = { _, _ -> capCalls++; fakeFile("cap.jpg", 5_000_000) },
            deleteCandidate = { it.delete() }
        )
        assertNotNull(result)
        assertEquals(1, refCalls)
        assertEquals(1, capCalls)
    }

    // ── Pair-quality order: 92 then 85 ────────────────────────────────────────

    @Test
    fun run_qualityOrder_is92Then85() = runBlocking {
        val observedQualities = mutableListOf<Int>()
        val loop = WackelbildPairSizeLoop(maxDimensionSteps = 0)
        loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, quality -> observedQualities.add(quality); fakeFile("ref.jpg", 21 * 1024 * 1024) },
            renderCapture = { _, _ -> fakeFile("cap.jpg", 21 * 1024 * 1024) },
            deleteCandidate = { it.delete() }
        )
        assertEquals(listOf(92, 85), observedQualities)
    }

    // ── Dimension reduction factor ────────────────────────────────────────────

    @Test
    fun run_dimensionStepDown_appliesFactorOncePerDimensionLevel() = runBlocking {
        val observedDims = mutableListOf<WackelbildTargetDimensions>()
        val loop = WackelbildPairSizeLoop(maxDimensionSteps = 2, dimensionStepFactor = 0.85f)
        loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { dims, quality ->
                if (quality == 92) observedDims.add(dims)
                fakeFile("ref.jpg", 21 * 1024 * 1024) // always oversized -> exhausts all attempts
            },
            renderCapture = { _, _ -> fakeFile("cap.jpg", 1_000) },
            deleteCandidate = { it.delete() }
        )
        assertEquals(3, observedDims.size) // levels 0, 1, 2
        assertEquals(1000, observedDims[0].width)
        assertTrue("dimension level 1 must be smaller than level 0", observedDims[1].width < observedDims[0].width)
        assertTrue("dimension level 2 must be smaller than level 1", observedDims[2].width < observedDims[1].width)
    }

    // ── Max 8 attempts (2 quality x 4 dimension levels) ──────────────────────

    @Test
    fun run_boundedAt8Attempts_thenGivesUp() = runBlocking {
        var attempts = 0
        val loop = WackelbildPairSizeLoop() // default maxDimensionSteps=3 -> 4 levels x 2 quality = 8
        val result = loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, _ -> attempts++; fakeFile("ref.jpg", 21 * 1024 * 1024) }, // always oversized
            renderCapture = { _, _ -> fakeFile("cap.jpg", 1_000) },
            deleteCandidate = { it.delete() }
        )
        assertNull("must give up, never loop unbounded", result)
        assertEquals(8, attempts)
    }

    // ── Both sides regenerated together; no per-side independent acceptance ──

    @Test
    fun run_eitherFileOversized_neitherAccepted_bothRegeneratedNextAttempt() = runBlocking {
        var refCalls = 0
        var capCalls = 0
        val loop = WackelbildPairSizeLoop(maxDimensionSteps = 0)
        val result = loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, quality ->
                refCalls++
                // Reference is always small -- but the pair must still fail at quality 92
                // because Capture is oversized at that step, proving no per-side acceptance.
                fakeFile("ref_$quality.jpg", 1_000)
            },
            renderCapture = { _, quality ->
                capCalls++
                if (quality == 92) fakeFile("cap_92.jpg", 21 * 1024 * 1024) else fakeFile("cap_85.jpg", 1_000)
            },
            deleteCandidate = { it.delete() }
        )
        assertNotNull("must eventually succeed at quality 85", result)
        assertEquals("both sides must regenerate on retry, not just the oversized one", 2, refCalls)
        assertEquals(2, capCalls)
    }

    @Test
    fun run_deletesBothCandidates_whenAttemptFails() = runBlocking {
        val deleted = mutableListOf<String>()
        val loop = WackelbildPairSizeLoop(maxDimensionSteps = 0)
        loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, quality -> fakeFile("ref_$quality.jpg", 21 * 1024 * 1024) },
            renderCapture = { _, quality -> fakeFile("cap_$quality.jpg", 21 * 1024 * 1024) },
            deleteCandidate = { deleted.add(it.name) }
        )
        // Both quality attempts fail -> both files deleted each time (2 attempts x 2 files).
        assertEquals(4, deleted.size)
    }

    // ── Sequential order: Reference fully completes before Capture starts ───

    @Test
    fun run_referenceCompletesBeforeCaptureStarts_sequentialNotConcurrent() = runBlocking {
        val order = mutableListOf<String>()
        val loop = WackelbildPairSizeLoop()
        loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, _ -> order.add("ref-start"); order.add("ref-end"); fakeFile("ref.jpg", 1_000) },
            renderCapture = { _, _ -> order.add("cap-start"); order.add("cap-end"); fakeFile("cap.jpg", 1_000) },
            deleteCandidate = { it.delete() }
        )
        assertEquals(listOf("ref-start", "ref-end", "cap-start", "cap-end"), order)
    }

    // ── Reference/Capture pair mapping never reversed ─────────────────────────

    @Test
    fun run_pairMapping_referenceFirstCaptureSecond_neverReversed() = runBlocking {
        val loop = WackelbildPairSizeLoop()
        val result = loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, _ -> fakeFile("distinctly_reference.jpg", 1_000) },
            renderCapture = { _, _ -> fakeFile("distinctly_capture.jpg", 1_000) },
            deleteCandidate = { it.delete() }
        )
        assertNotNull(result)
        assertTrue("first element must be the Reference-produced file", result!!.first.name.contains("reference"))
        assertTrue("second element must be the Capture-produced file", result.second.name.contains("capture"))
    }

    // ── Badge-induced overage triggers retry (simulated via fake size) ───────

    @Test
    fun run_badgeInducedOverageAtQuality92_triggersRetryAtQuality85() = runBlocking {
        // Simulates a badge pushing the quality-92 candidate over 20 MiB; quality-85 fits.
        val loop = WackelbildPairSizeLoop(maxDimensionSteps = 0)
        var attemptCount = 0
        val result = loop.run(
            initialDims = WackelbildTargetDimensions(1000, 1000),
            renderReference = { _, quality ->
                attemptCount++
                if (quality == 92) fakeFile("ref_92.jpg", 21 * 1024 * 1024) else fakeFile("ref_85.jpg", 15 * 1024 * 1024)
            },
            renderCapture = { _, quality ->
                if (quality == 92) fakeFile("cap_92.jpg", 21 * 1024 * 1024) else fakeFile("cap_85.jpg", 15 * 1024 * 1024)
            },
            deleteCandidate = { it.delete() }
        )
        assertNotNull(result)
        assertEquals(2, attemptCount)
    }
}
