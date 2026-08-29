// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/WackelbildHandoffOrchestratorTest.kt
package com.isardomains.sameview.ui.wackelbild

import com.isardomains.sameview.image.wackelbild.WackelbildDateOverlay
import com.isardomains.sameview.image.wackelbild.WackelbildPrintFailureReason
import com.isardomains.sameview.image.wackelbild.WackelbildPrintPair
import com.isardomains.sameview.image.wackelbild.WackelbildPrintResult
import com.isardomains.sameview.net.deinwackelbild.CreateHandoffRequest
import com.isardomains.sameview.net.deinwackelbild.CreateHandoffResponse
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildApiClient
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildApiError
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildErrorClassification
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildResult
import com.isardomains.sameview.net.deinwackelbild.DeinWackelbildSlot
import com.isardomains.sameview.net.deinwackelbild.UploadResponse
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WackelbildHandoffOrchestratorTest {

    private lateinit var cacheDir: File
    private lateinit var tempFileManager: WackelbildTempFileManager
    private lateinit var sessionDir: File

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("wb-orch-cache-").toFile()
        tempFileManager = WackelbildTempFileManager(cacheDir)
        sessionDir = Files.createTempDirectory("wb-orch-session-").toFile()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
        sessionDir.deleteRecursively()
    }

    // ── Fakes / fixtures ─────────────────────────────────────────────────────

    private class FakeApiClient : DeinWackelbildApiClient {
        val createResponses = ArrayDeque<DeinWackelbildResult<CreateHandoffResponse>>()
        val uploadResponses = ArrayDeque<DeinWackelbildResult<UploadResponse>>()
        val createCalls = mutableListOf<String>() // idempotency keys, in call order
        val uploadCalls = mutableListOf<Triple<String, DeinWackelbildSlot, File>>() // (uploadUrl, slot, file)

        override suspend fun createHandoff(
            request: CreateHandoffRequest,
            idempotencyKey: String
        ): DeinWackelbildResult<CreateHandoffResponse> {
            createCalls.add(idempotencyKey)
            return createResponses.removeFirstOrNull() ?: error("FakeApiClient: no more scripted create responses")
        }

        override suspend fun uploadImage(
            uploadUrl: String,
            handoffToken: String,
            slot: DeinWackelbildSlot,
            file: File
        ): DeinWackelbildResult<UploadResponse> {
            uploadCalls.add(Triple(uploadUrl, slot, file))
            return uploadResponses.removeFirstOrNull() ?: error("FakeApiClient: no more scripted upload responses")
        }
    }

    /** Hangs (via [awaitCancellation]) on the configured call instead of returning, so a test can
     * deterministically catch the orchestrator mid-flight and cancel it there. */
    private class HangingApiClient(
        private val hangOnCreate: Boolean = false,
        private val hangOnUploadSlot: DeinWackelbildSlot? = null,
        private val createResponse: DeinWackelbildResult<CreateHandoffResponse>? = null,
        private val uploadOneResponse: DeinWackelbildResult<UploadResponse>? = null
    ) : DeinWackelbildApiClient {
        val reached = CompletableDeferred<Unit>()

        override suspend fun createHandoff(
            request: CreateHandoffRequest,
            idempotencyKey: String
        ): DeinWackelbildResult<CreateHandoffResponse> {
            if (hangOnCreate) {
                reached.complete(Unit)
                awaitCancellation()
            }
            return createResponse ?: error("HangingApiClient: no create response configured")
        }

        override suspend fun uploadImage(
            uploadUrl: String,
            handoffToken: String,
            slot: DeinWackelbildSlot,
            file: File
        ): DeinWackelbildResult<UploadResponse> {
            if (slot == hangOnUploadSlot) {
                reached.complete(Unit)
                awaitCancellation()
            }
            return uploadOneResponse ?: error("HangingApiClient: no upload response configured for $slot")
        }
    }

    private fun createSuccess(
        uploadUrl: String = "https://deinwackelbild.de/upload/x",
        handoffToken: String = "tok",
        status: String = "awaiting_files"
    ): DeinWackelbildResult<CreateHandoffResponse> = DeinWackelbildResult.Success(
        CreateHandoffResponse(
            handoffId = "h1", handoffToken = handoffToken, partner = "sameview", status = status,
            expiresAt = "2026-08-30T12:00:00Z", maxFileBytes = 20_971_520L, acceptedTypes = listOf("image/jpeg"),
            uploadedSlots = emptyList(), uploadUrl = uploadUrl, checkoutUrl = null
        )
    )

    private fun uploadOneSuccess(
        status: String = "awaiting_files",
        slots: List<String> = listOf("one"),
        checkoutUrl: String? = null
    ): DeinWackelbildResult<UploadResponse> = DeinWackelbildResult.Success(UploadResponse("h1", status, slots, checkoutUrl))

    private fun uploadTwoSuccess(
        checkoutUrl: String? = "https://deinwackelbild.de/checkout/h1",
        status: String = "ready",
        slots: List<String> = listOf("one", "two")
    ): DeinWackelbildResult<UploadResponse> = DeinWackelbildResult.Success(UploadResponse("h1", status, slots, checkoutUrl))

    private fun <T> failure(classification: DeinWackelbildErrorClassification): DeinWackelbildResult<T> =
        DeinWackelbildResult.Failure(DeinWackelbildApiError(classification))

    private class RecordingRenderer(private val usedFallback: Boolean = false, private val fail: Boolean = false) {
        var callCount = 0
            private set

        val fn: suspend (File, File, WackelbildDateOverlay?) -> WackelbildPrintResult = { _, outputDir, _ ->
            callCount++
            if (fail) {
                WackelbildPrintResult.Failure(WackelbildPrintFailureReason.PERMANENT_NO_VALID_SOURCE)
            } else {
                val ref = File(outputDir, "image_one.jpg").also { it.parentFile?.mkdirs(); it.writeBytes(byteArrayOf(1)) }
                val cap = File(outputDir, "image_two.jpg").also { it.parentFile?.mkdirs(); it.writeBytes(byteArrayOf(2)) }
                WackelbildPrintResult.Success(WackelbildPrintPair(ref, cap), usedFallback)
            }
        }
    }

    private fun orchestrator(keys: MutableList<String>? = null) = WackelbildHandoffOrchestrator(
        idempotencyKeyFactory = {
            val key = "key-${(keys?.size ?: 0) + 1}"
            keys?.add(key)
            key
        }
    )

    private suspend fun execute(
        orchestrator: WackelbildHandoffOrchestrator,
        apiClient: DeinWackelbildApiClient,
        renderer: RecordingRenderer,
        confirmFallback: suspend () -> Unit = {},
        phases: MutableList<WackelbildOperationState> = mutableListOf()
    ): WackelbildOperationState = orchestrator.execute(
        sessionDir = sessionDir,
        tempFileManager = tempFileManager,
        apiClient = apiClient,
        dateOverlay = null,
        renderPrintPair = renderer.fn,
        awaitFallbackConfirmation = confirmFallback,
        onPhaseChange = { phases.add(it) }
    )

    private fun assertNoLeftoverOperationDirs() {
        val wackelbildRoot = File(cacheDir, "wackelbild")
        assertTrue(wackelbildRoot.listFiles()?.isEmpty() ?: true)
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun happyPath_endsInReady_withCorrectSlotMapping_andCleanup() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess(checkoutUrl = "https://deinwackelbild.de/checkout/h1"))
        }
        val renderer = RecordingRenderer(usedFallback = false)
        val phases = mutableListOf<WackelbildOperationState>()

        val result = execute(orchestrator(), apiClient, renderer, phases = phases)

        assertEquals(WackelbildOperationState.Ready("https://deinwackelbild.de/checkout/h1", usedFallback = false), result)
        assertEquals(1, renderer.callCount)
        assertEquals(DeinWackelbildSlot.ONE, apiClient.uploadCalls[0].second)
        assertEquals(DeinWackelbildSlot.TWO, apiClient.uploadCalls[1].second)
        assertEquals("image_one.jpg", apiClient.uploadCalls[0].third.name) // Reference -> slot one
        assertEquals("image_two.jpg", apiClient.uploadCalls[1].third.name) // Capture -> slot two
        assertTrue(phases.contains(WackelbildOperationState.Preparing))
        assertTrue(phases.contains(WackelbildOperationState.CreatingHandoff))
        assertTrue(phases.contains(WackelbildOperationState.UploadingSlot(DeinWackelbildSlot.ONE)))
        assertTrue(phases.contains(WackelbildOperationState.UploadingSlot(DeinWackelbildSlot.TWO)))
        assertNoLeftoverOperationDirs()
    }

    // ── Fallback confirmation ────────────────────────────────────────────────

    @Test
    fun nonFallback_neverEntersAwaitingFallbackConfirmation() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        val phases = mutableListOf<WackelbildOperationState>()
        execute(orchestrator(), apiClient, RecordingRenderer(usedFallback = false), phases = phases)
        assertFalse(phases.contains(WackelbildOperationState.AwaitingFallbackConfirmation))
    }

    @Test
    fun fallback_entersAwaitingConfirmation_zeroApiCallsBeforeConfirm() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        val phases = mutableListOf<WackelbildOperationState>()
        var apiCallsBeforeConfirm = -1
        execute(
            orchestrator(), apiClient, RecordingRenderer(usedFallback = true),
            confirmFallback = { apiCallsBeforeConfirm = apiClient.createCalls.size + apiClient.uploadCalls.size },
            phases = phases
        )
        assertEquals(0, apiCallsBeforeConfirm)
        assertTrue(phases.contains(WackelbildOperationState.AwaitingFallbackConfirmation))
    }

    @Test
    fun fallback_confirmResumesSameOperation_noRerender_usedFallbackPropagates() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess(checkoutUrl = "https://deinwackelbild.de/checkout/h1"))
        }
        val renderer = RecordingRenderer(usedFallback = true)
        val result = execute(orchestrator(), apiClient, renderer, confirmFallback = { /* returns immediately: confirmed */ })
        assertEquals(1, renderer.callCount)
        assertEquals(WackelbildOperationState.Ready("https://deinwackelbild.de/checkout/h1", usedFallback = true), result)
    }

    @Test
    fun fallback_cancelWhileAwaiting_zeroNetworkCalls_cleanupRuns() = runTest {
        val apiClient = FakeApiClient() // no responses scripted -- must never be called
        var caught = false
        val job = launch {
            try {
                execute(
                    orchestrator(), apiClient, RecordingRenderer(usedFallback = true),
                    confirmFallback = { awaitCancellation() }
                )
                fail("expected cancellation")
            } catch (e: CancellationException) {
                caught = true
            }
        }
        yield() // let it reach the suspended confirmFallback
        job.cancel()
        job.join()
        assertTrue(caught)
        assertEquals(0, apiClient.createCalls.size)
        assertEquals(0, apiClient.uploadCalls.size)
        assertNoLeftoverOperationDirs()
    }

    // ── Renderer failure ─────────────────────────────────────────────────────

    @Test
    fun rendererFailure_noNetworkCall_correctFailureCategory_cleanupRuns() = runTest {
        val apiClient = FakeApiClient()
        val result = execute(orchestrator(), apiClient, RecordingRenderer(fail = true))
        assertEquals(
            WackelbildOperationState.Failed(WackelbildOperationFailure(WackelbildOperationFailureCategory.PREPARATION_FAILED)),
            result
        )
        assertEquals(0, apiClient.createCalls.size)
        assertNoLeftoverOperationDirs()
    }

    // ── Idempotency ──────────────────────────────────────────────────────────

    @Test
    fun sameKeyReusedAcrossCreateTransientRetries() = runTest {
        val keys = mutableListOf<String>()
        val apiClient = FakeApiClient().apply {
            createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK))
            createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_SERVER))
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        execute(orchestrator(keys), apiClient, RecordingRenderer())
        assertEquals(3, apiClient.createCalls.size)
        assertEquals(1, apiClient.createCalls.toSet().size) // all 3 attempts used the identical key
        assertEquals(1, keys.size)
    }

    @Test
    fun expiredHandoff_403Or410_triggersRestart_newKey_filesReused_noRerender() = runTest {
        val keys = mutableListOf<String>()
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(failure(DeinWackelbildErrorClassification.EXPIRED_HANDOFF)) // covers both 403 and 410
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess(checkoutUrl = "https://deinwackelbild.de/checkout/h2"))
        }
        val renderer = RecordingRenderer()
        val result = execute(orchestrator(keys), apiClient, renderer)
        assertTrue(result is WackelbildOperationState.Ready)
        assertEquals(2, keys.size)
        assertEquals(keys[0], apiClient.createCalls[0])
        assertEquals(keys[1], apiClient.createCalls[1])
        assertEquals(1, renderer.callCount)
        assertEquals(apiClient.uploadCalls[0].third, apiClient.uploadCalls[2].third) // same Reference file reused
    }

    @Test
    fun incompleteHandoff409_triggersRestart_newKey() = runTest {
        val keys = mutableListOf<String>()
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(failure(DeinWackelbildErrorClassification.INCOMPLETE_HANDOFF))
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        val result = execute(orchestrator(keys), apiClient, RecordingRenderer())
        assertTrue(result is WackelbildOperationState.Ready)
        assertEquals(2, apiClient.createCalls.size)
        assertEquals(2, keys.size)
    }

    @Test
    fun newExecuteCall_getsNewKey() = runTest {
        val keys = mutableListOf<String>()
        val orch = orchestrator(keys)
        val apiClient1 = FakeApiClient().apply {
            createResponses.add(createSuccess()); uploadResponses.add(uploadOneSuccess()); uploadResponses.add(uploadTwoSuccess())
        }
        execute(orch, apiClient1, RecordingRenderer())
        val apiClient2 = FakeApiClient().apply {
            createResponses.add(createSuccess()); uploadResponses.add(uploadOneSuccess()); uploadResponses.add(uploadTwoSuccess())
        }
        execute(orch, apiClient2, RecordingRenderer())
        assertEquals(2, keys.size)
        assertFalse(apiClient1.createCalls[0] == apiClient2.createCalls[0])
    }

    // ── Retry ────────────────────────────────────────────────────────────────

    @Test
    fun networkFailureThenSuccess() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK))
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertTrue(result is WackelbildOperationState.Ready)
        assertEquals(2, apiClient.createCalls.size)
    }

    @Test
    fun server5xxThenSuccess() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_SERVER))
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertTrue(result is WackelbildOperationState.Ready)
    }

    @Test
    fun rateLimitThenSuccess() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(failure(DeinWackelbildErrorClassification.RATE_LIMITED))
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertTrue(result is WackelbildOperationState.Ready)
    }

    @Test
    fun exactDelaySequence_1sThen2s() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK))
            createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_SERVER))
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess())
        }
        execute(orchestrator(), apiClient, RecordingRenderer())
        assertEquals(3000L, testScheduler.currentTime)
    }

    @Test
    fun exactlyThreeMaxAttempts_thenTerminalFailure() = runTest {
        val apiClient = FakeApiClient().apply {
            repeat(3) { createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK)) }
        }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertEquals(3, apiClient.createCalls.size)
        assertEquals(
            WackelbildOperationState.Failed(
                WackelbildOperationFailure(WackelbildOperationFailureCategory.NETWORK_UNAVAILABLE, DeinWackelbildErrorClassification.RETRYABLE_NETWORK)
            ),
            result
        )
    }

    @Test
    fun nonRetryable400_noRetry_immediateFailure() = runTest {
        val apiClient = FakeApiClient().apply { createResponses.add(failure(DeinWackelbildErrorClassification.INVALID_REQUEST)) }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertEquals(1, apiClient.createCalls.size)
        assertEquals(WackelbildOperationFailureCategory.HANDOFF_FAILED, (result as WackelbildOperationState.Failed).failure.category)
    }

    // ── Handoff generation bound / 27-request worst case ────────────────────

    @Test
    fun generation3RestartTrigger_terminatesWithHandoffFailed_noFourthGeneration() = runTest {
        val apiClient = FakeApiClient().apply {
            repeat(3) {
                createResponses.add(createSuccess())
                uploadResponses.add(uploadOneSuccess())
                uploadResponses.add(failure(DeinWackelbildErrorClassification.EXPIRED_HANDOFF))
            }
        }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertEquals(
            WackelbildOperationState.Failed(
                WackelbildOperationFailure(WackelbildOperationFailureCategory.HANDOFF_FAILED, DeinWackelbildErrorClassification.EXPIRED_HANDOFF)
            ),
            result
        )
        assertEquals(3, apiClient.createCalls.size)
    }

    @Test
    fun worstCaseSequence_reachesExactly27Requests_thenTerminates() = runTest {
        val apiClient = FakeApiClient().apply {
            repeat(3) {
                createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK))
                createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_SERVER))
                createResponses.add(createSuccess())
                uploadResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK))
                uploadResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_SERVER))
                uploadResponses.add(uploadOneSuccess())
                uploadResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK))
                uploadResponses.add(failure(DeinWackelbildErrorClassification.RATE_LIMITED))
                uploadResponses.add(failure(DeinWackelbildErrorClassification.EXPIRED_HANDOFF))
            }
        }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertEquals(9, apiClient.createCalls.size)
        assertEquals(18, apiClient.uploadCalls.size)
        assertEquals(27, apiClient.createCalls.size + apiClient.uploadCalls.size)
        assertEquals(WackelbildOperationFailureCategory.HANDOFF_FAILED, (result as WackelbildOperationState.Failed).failure.category)
    }

    // ── Semantic validation ──────────────────────────────────────────────────

    @Test
    fun createWrongStatus_terminalFailure_noUpload() = runTest {
        val apiClient = FakeApiClient().apply { createResponses.add(createSuccess(status = "something_else")) }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertTrue(result is WackelbildOperationState.Failed)
        assertEquals(0, apiClient.uploadCalls.size)
    }

    @Test
    fun uploadOneWrongStatus_terminalFailure_noSlotTwo() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess(status = "ready"))
        }
        val result = execute(orchestrator(), apiClient, RecordingRenderer())
        assertTrue(result is WackelbildOperationState.Failed)
        assertEquals(1, apiClient.uploadCalls.size)
    }

    @Test
    fun uploadOneMissingSlotOne_terminalFailure() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess(slots = emptyList()))
        }
        assertTrue(execute(orchestrator(), apiClient, RecordingRenderer()) is WackelbildOperationState.Failed)
    }

    @Test
    fun uploadOnePrematureCheckout_terminalFailure() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess(checkoutUrl = "https://deinwackelbild.de/checkout/h1"))
        }
        assertTrue(execute(orchestrator(), apiClient, RecordingRenderer()) is WackelbildOperationState.Failed)
    }

    @Test
    fun uploadTwoNotReady_terminalFailure() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess(status = "awaiting_files"))
        }
        assertTrue(execute(orchestrator(), apiClient, RecordingRenderer()) is WackelbildOperationState.Failed)
    }

    @Test
    fun uploadTwoMissingSlotOne_terminalFailure() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess(slots = listOf("two")))
        }
        assertTrue(execute(orchestrator(), apiClient, RecordingRenderer()) is WackelbildOperationState.Failed)
    }

    @Test
    fun uploadTwoMissingSlotTwo_terminalFailure() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess(slots = listOf("one")))
        }
        assertTrue(execute(orchestrator(), apiClient, RecordingRenderer()) is WackelbildOperationState.Failed)
    }

    @Test
    fun uploadTwoReadyWithoutCheckout_terminalFailure() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(createSuccess())
            uploadResponses.add(uploadOneSuccess())
            uploadResponses.add(uploadTwoSuccess(checkoutUrl = null))
        }
        assertTrue(execute(orchestrator(), apiClient, RecordingRenderer()) is WackelbildOperationState.Failed)
    }

    // ── Cancellation ─────────────────────────────────────────────────────────

    @Test
    fun cancellation_duringPreparation_cleanupRuns_noReady() = runTest {
        val apiClient = FakeApiClient()
        val reached = CompletableDeferred<Unit>()
        val hangingRenderer: suspend (File, File, WackelbildDateOverlay?) -> WackelbildPrintResult = { _, _, _ ->
            reached.complete(Unit)
            awaitCancellation()
        }
        var caught = false
        val job = launch {
            try {
                orchestrator().execute(sessionDir, tempFileManager, apiClient, null, hangingRenderer, {}, {})
                fail("expected cancellation")
            } catch (e: CancellationException) {
                caught = true
            }
        }
        reached.await()
        job.cancel()
        job.join()
        assertTrue(caught)
        assertNoLeftoverOperationDirs()
    }

    @Test
    fun cancellation_duringCreate_cleanupRuns_noReady() = runTest {
        val apiClient = HangingApiClient(hangOnCreate = true)
        var caught = false
        val job = launch {
            try {
                orchestrator().execute(sessionDir, tempFileManager, apiClient, null, RecordingRenderer().fn, {}, {})
                fail("expected cancellation")
            } catch (e: CancellationException) {
                caught = true
            }
        }
        apiClient.reached.await()
        job.cancel()
        job.join()
        assertTrue(caught)
        assertNoLeftoverOperationDirs()
    }

    @Test
    fun cancellation_duringRetryDelay_cleanupRuns_onlyOneAttemptMade() = runTest {
        val apiClient = FakeApiClient().apply {
            createResponses.add(failure(DeinWackelbildErrorClassification.RETRYABLE_NETWORK))
        }
        var caught = false
        val job = launch {
            try {
                execute(orchestrator(), apiClient, RecordingRenderer())
                fail("expected cancellation")
            } catch (e: CancellationException) {
                caught = true
            }
        }
        yield() // let the first attempt run and fail, then enter delay(1000)
        job.cancel()
        job.join()
        assertTrue(caught)
        assertEquals(1, apiClient.createCalls.size)
        assertNoLeftoverOperationDirs()
    }

    @Test
    fun cancellation_duringUploadOne_cleanupRuns_noReady() = runTest {
        val apiClient = HangingApiClient(hangOnUploadSlot = DeinWackelbildSlot.ONE, createResponse = createSuccess())
        var caught = false
        val job = launch {
            try {
                orchestrator().execute(sessionDir, tempFileManager, apiClient, null, RecordingRenderer().fn, {}, {})
                fail("expected cancellation")
            } catch (e: CancellationException) {
                caught = true
            }
        }
        apiClient.reached.await()
        job.cancel()
        job.join()
        assertTrue(caught)
        assertNoLeftoverOperationDirs()
    }

    @Test
    fun cancellation_duringUploadTwo_cleanupRuns_noReady() = runTest {
        val apiClient = HangingApiClient(
            hangOnUploadSlot = DeinWackelbildSlot.TWO,
            createResponse = createSuccess(),
            uploadOneResponse = uploadOneSuccess()
        )
        var caught = false
        val job = launch {
            try {
                orchestrator().execute(sessionDir, tempFileManager, apiClient, null, RecordingRenderer().fn, {}, {})
                fail("expected cancellation")
            } catch (e: CancellationException) {
                caught = true
            }
        }
        apiClient.reached.await()
        job.cancel()
        job.join()
        assertTrue(caught)
        assertNoLeftoverOperationDirs()
    }
}
