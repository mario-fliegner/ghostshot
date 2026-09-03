// path: app/src/test/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClientTest.kt
package com.isardomains.sameview.net.deinwackelbild

import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OkHttpDeinWackelbildApiClientTest {

    private val partnerKey = "sv_test_REPLACE_ME"
    private val validIdempotencyKey = "op-2026-08-30-abcdef"

    // ── Test doubles ─────────────────────────────────────────────────────────

    private class FakeCall(
        private val requestRef: Request,
        private val response: Response? = null,
        private val failure: IOException? = null,
        private val pending: Boolean = false
    ) : Call {
        var cancelled = false
            private set
        var storedCallback: Callback? = null
            private set

        override fun request(): Request = requestRef
        override fun execute(): Response = throw UnsupportedOperationException("production code uses enqueue() only")
        override fun enqueue(responseCallback: Callback) {
            if (pending) {
                storedCallback = responseCallback
                return
            }
            if (response != null) responseCallback.onResponse(this, response)
            else responseCallback.onFailure(this, failure ?: IOException("fake failure"))
        }
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = this
    }

    private class RecordingCallFactory(private val makeCall: (Request) -> FakeCall) : Call.Factory {
        lateinit var lastRequest: Request
            private set
        lateinit var lastCall: FakeCall
            private set

        override fun newCall(request: Request): Call {
            lastRequest = request
            val call = makeCall(request)
            lastCall = call
            return call
        }
    }

    private class TrackingResponseBody(private val delegate: ResponseBody) : ResponseBody() {
        var closed = false
            private set
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun source(): BufferedSource = delegate.source()
        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private fun fakeResponse(request: Request, code: Int, jsonBody: String, body: ResponseBody? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body ?: jsonBody.toResponseBody("application/json".toMediaType()))
            .build()

    private fun Request.bodyAsString(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun tempJpegFile(name: String = "image_one.jpg"): File {
        val dir = Files.createTempDirectory("wb-net-test-").toFile()
        val file = File(dir, name)
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) // JPEG magic bytes, dummy content
        return file
    }

    private fun validCreateResponseJson(): String = JSONObject().apply {
        put("handoff_id", "h-1")
        put("handoff_token", "tok-1")
        put("partner", "sameview")
        put("status", "awaiting_files")
        put("expires_at", "2026-08-30T12:00:00Z")
        put("max_file_bytes", 20_971_520L)
        put("accepted_types", org.json.JSONArray(listOf("image/jpeg")))
        put("uploaded_slots", org.json.JSONArray(emptyList<String>()))
        put("upload_url", "https://deinwackelbild.de/upload/h-1")
    }.toString()

    // ── Create request construction ─────────────────────────────────────────

    @Test
    fun createHandoff_buildsExactMethodUrlAndHeaders() = runTest {
        val factory = RecordingCallFactory { req -> FakeCall(req, response = fakeResponse(req, 201, validCreateResponseJson())) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.createHandoff(CreateHandoffRequest(locale = "de-DE"), validIdempotencyKey)

        val request = factory.lastRequest
        assertEquals("POST", request.method)
        assertEquals("https://deinwackelbild.de/wp-json/dwb/v1/partner-handoffs", request.url.toString())
        assertEquals("application/json; charset=utf-8", request.body?.contentType().toString())
        assertEquals(partnerKey, request.header("X-DWB-Partner-Key"))
        assertEquals(validIdempotencyKey, request.header("Idempotency-Key"))
    }

    @Test
    fun createHandoff_bodyContainsPartnerAndLocale_noExternalReference_noSessionId() = runTest {
        val factory = RecordingCallFactory { req -> FakeCall(req, response = fakeResponse(req, 201, validCreateResponseJson())) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.createHandoff(CreateHandoffRequest(locale = "de-DE"), validIdempotencyKey)

        val body = JSONObject(factory.lastRequest.bodyAsString())
        assertEquals("sameview", body.getString("partner"))
        assertEquals("de-DE", body.getString("locale"))
        assertFalse(body.has("external_reference"))
        assertFalse(body.has("sessionId"))
        assertFalse(body.has("comparisonId"))
    }

    @Test
    fun createHandoff_nullLocale_omittedFromBody() = runTest {
        val factory = RecordingCallFactory { req -> FakeCall(req, response = fakeResponse(req, 201, validCreateResponseJson())) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.createHandoff(CreateHandoffRequest(locale = null), validIdempotencyKey)

        val body = JSONObject(factory.lastRequest.bodyAsString())
        assertFalse(body.has("locale"))
    }

    @Test
    fun createHandoff_idempotencyKeyPassedThroughUnchanged() = runTest {
        val factory = RecordingCallFactory { req -> FakeCall(req, response = fakeResponse(req, 201, validCreateResponseJson())) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)
        val key = "exact-key-value-123"

        client.createHandoff(CreateHandoffRequest(), key)

        assertEquals(key, factory.lastRequest.header("Idempotency-Key"))
    }

    @Test
    fun createHandoff_partnerKeyNeverInUrl() = runTest {
        val factory = RecordingCallFactory { req -> FakeCall(req, response = fakeResponse(req, 201, validCreateResponseJson())) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        assertFalse(factory.lastRequest.url.toString().contains(partnerKey))
    }

    @Test
    fun createHandoff_blankPartnerKey_integrationUnavailable_noRequestMade() = runTest {
        var called = false
        val factory = Call.Factory { req -> called = true; FakeCall(req, response = fakeResponse(req, 201, validCreateResponseJson())) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey = "")

        val result = client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        assertFalse(called)
        assertTrue(result is DeinWackelbildResult.Failure)
        assertEquals(
            DeinWackelbildErrorClassification.INTEGRATION_UNAVAILABLE,
            (result as DeinWackelbildResult.Failure).error.classification
        )
    }

    @Test
    fun createHandoff_invalidIdempotencyKey_noRequestMade() = runTest {
        var called = false
        val factory = Call.Factory { req -> called = true; FakeCall(req, response = fakeResponse(req, 201, validCreateResponseJson())) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.createHandoff(CreateHandoffRequest(), "short")

        assertFalse(called)
        assertTrue(result is DeinWackelbildResult.Failure)
        assertEquals(
            DeinWackelbildErrorClassification.PERMANENT_LOCAL,
            (result as DeinWackelbildResult.Failure).error.classification
        )
    }

    // ── Upload request construction ─────────────────────────────────────────

    private fun uploadResponseJson(status: String, slots: List<String>, checkoutUrl: String?): String =
        JSONObject().apply {
            put("handoff_id", "h-1")
            put("status", status)
            put("uploaded_slots", org.json.JSONArray(slots))
            put("checkout_url", checkoutUrl)
        }.toString()

    @Test
    fun uploadImage_slotOne_buildsExactUrlHeadersAndMultipart() = runTest {
        val file = tempJpegFile("image_one.jpg")
        val factory = RecordingCallFactory { req ->
            FakeCall(req, response = fakeResponse(req, 200, uploadResponseJson("awaiting_files", listOf("one"), null)))
        }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.uploadImage("https://deinwackelbild.de/upload/h-1", "tok-1", DeinWackelbildSlot.ONE, file)

        val request = factory.lastRequest
        assertEquals("POST", request.method)
        assertEquals("https://deinwackelbild.de/upload/h-1", request.url.toString())
        assertEquals("tok-1", request.header("X-DWB-Handoff-Token"))
        assertNull(request.header("X-DWB-Partner-Key"))
        assertNull(request.header("Idempotency-Key"))
        assertTrue(request.body is MultipartBody)
        val bodyText = request.bodyAsString()
        assertTrue(bodyText.contains("name=\"slot\""))
        assertTrue(bodyText.contains("\r\none\r\n") || bodyText.contains("one"))
        assertTrue(bodyText.contains("name=\"file\""))
        assertTrue(bodyText.contains("Content-Type: image/jpeg"))
    }

    @Test
    fun uploadImage_slotTwo_multipartContainsSlotTwo() = runTest {
        val file = tempJpegFile("image_two.jpg")
        val factory = RecordingCallFactory { req ->
            FakeCall(req, response = fakeResponse(req, 200, uploadResponseJson("ready", listOf("one", "two"), "https://deinwackelbild.de/checkout/h-1")))
        }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.uploadImage("https://deinwackelbild.de/upload/h-1", "tok-1", DeinWackelbildSlot.TWO, file)

        val bodyText = factory.lastRequest.bodyAsString()
        assertTrue(bodyText.contains("name=\"slot\""))
        assertTrue(bodyText.contains("two"))
    }

    @Test
    fun uploadImage_missingFile_permanentLocalFailure_noRequestMade() = runTest {
        var called = false
        val factory = Call.Factory { req -> called = true; FakeCall(req, response = fakeResponse(req, 200, uploadResponseJson("awaiting_files", listOf("one"), null))) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)
        val missingFile = File(Files.createTempDirectory("wb-net-test-").toFile(), "does-not-exist.jpg")

        val result = client.uploadImage("https://deinwackelbild.de/upload/h-1", "tok-1", DeinWackelbildSlot.ONE, missingFile)

        assertFalse(called)
        assertEquals(
            DeinWackelbildErrorClassification.PERMANENT_LOCAL,
            (result as DeinWackelbildResult.Failure).error.classification
        )
    }

    @Test
    fun uploadImage_readyResponse_checkoutUrlRetainedExactly() = runTest {
        val checkoutUrl = "https://deinwackelbild.de/checkout/h-1?token=abc#step=payment"
        val file = tempJpegFile()
        val factory = RecordingCallFactory { req ->
            FakeCall(req, response = fakeResponse(req, 200, uploadResponseJson("ready", listOf("one", "two"), checkoutUrl)))
        }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.uploadImage("https://deinwackelbild.de/upload/h-1", "tok-1", DeinWackelbildSlot.TWO, file)

        assertEquals(checkoutUrl, (result as DeinWackelbildResult.Success).value.checkoutUrl)
    }

    // ── HTTP status classification ───────────────────────────────────────────

    @Test
    fun httpStatus_400_classifiesInvalidRequest() = runTest { assertClassification(400, DeinWackelbildErrorClassification.INVALID_REQUEST) }

    @Test
    fun httpStatus_401_classifiesIntegrationUnavailable() = runTest { assertClassification(401, DeinWackelbildErrorClassification.INTEGRATION_UNAVAILABLE) }

    @Test
    fun httpStatus_403_classifiesExpiredHandoff() = runTest { assertClassification(403, DeinWackelbildErrorClassification.EXPIRED_HANDOFF) }

    @Test
    fun httpStatus_409_classifiesIncompleteHandoff() = runTest { assertClassification(409, DeinWackelbildErrorClassification.INCOMPLETE_HANDOFF) }

    @Test
    fun httpStatus_410_classifiesExpiredHandoff() = runTest { assertClassification(410, DeinWackelbildErrorClassification.EXPIRED_HANDOFF) }

    @Test
    fun httpStatus_413_classifiesFileTooLarge() = runTest { assertClassification(413, DeinWackelbildErrorClassification.FILE_TOO_LARGE) }

    @Test
    fun httpStatus_415_classifiesInvalidImage() = runTest { assertClassification(415, DeinWackelbildErrorClassification.INVALID_IMAGE) }

    @Test
    fun httpStatus_422_classifiesDimensionMismatch() = runTest { assertClassification(422, DeinWackelbildErrorClassification.DIMENSION_MISMATCH) }

    @Test
    fun httpStatus_429_classifiesRateLimited() = runTest { assertClassification(429, DeinWackelbildErrorClassification.RATE_LIMITED) }

    @Test
    fun httpStatus_500_classifiesRetryableServer() = runTest { assertClassification(500, DeinWackelbildErrorClassification.RETRYABLE_SERVER) }

    @Test
    fun httpStatus_unexpected_classifiesUnexpectedHttpStatus() = runTest { assertClassification(451, DeinWackelbildErrorClassification.UNEXPECTED_HTTP_STATUS) }

    private suspend fun assertClassification(code: Int, expected: DeinWackelbildErrorClassification) {
        val errorBody = JSONObject().apply {
            put("code", "dwb_test_error")
            put("message", "test message")
            put("data", JSONObject().apply { put("status", code) })
        }.toString()
        val factory = Call.Factory { req -> FakeCall(req, response = fakeResponse(req, code, errorBody)) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        val failure = result as DeinWackelbildResult.Failure
        assertEquals(expected, failure.error.classification)
        assertEquals(code, failure.error.httpStatus)
        assertEquals("dwb_test_error", failure.error.serverCode)
    }

    @Test
    fun errorStatus_malformedErrorBody_stillPreservesHttpClassification() = runTest {
        val factory = Call.Factory { req -> FakeCall(req, response = fakeResponse(req, 415, "{not valid json")) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        val failure = result as DeinWackelbildResult.Failure
        assertEquals(DeinWackelbildErrorClassification.INVALID_IMAGE, failure.error.classification)
        assertEquals(415, failure.error.httpStatus)
        assertNull(failure.error.serverCode)
    }

    @Test
    fun successResponse_malformedJson_classifiesMalformedResponse() = runTest {
        val factory = Call.Factory { req -> FakeCall(req, response = fakeResponse(req, 201, "{not valid json")) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        assertEquals(
            DeinWackelbildErrorClassification.MALFORMED_RESPONSE,
            (result as DeinWackelbildResult.Failure).error.classification
        )
    }

    // ── Transport / cancellation ─────────────────────────────────────────────

    @Test
    fun ioException_classifiesRetryableNetwork() = runTest {
        val factory = Call.Factory { req -> FakeCall(req, failure = IOException("connection reset")) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        assertEquals(
            DeinWackelbildErrorClassification.RETRYABLE_NETWORK,
            (result as DeinWackelbildResult.Failure).error.classification
        )
    }

    @Test
    fun socketTimeoutException_classifiesRetryableNetwork() = runTest {
        val factory = Call.Factory { req -> FakeCall(req, failure = SocketTimeoutException("timeout")) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        assertEquals(
            DeinWackelbildErrorClassification.RETRYABLE_NETWORK,
            (result as DeinWackelbildResult.Failure).error.classification
        )
    }

    @Test
    fun cancellation_propagatesAsCancellationException_andCancelsUnderlyingCall() = runTest {
        lateinit var fakeCall: FakeCall
        val factory = Call.Factory { req -> FakeCall(req, pending = true).also { fakeCall = it } }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        var caughtCancellation = false
        val job = launch {
            try {
                client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)
                fail("expected cancellation, got a normal result")
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
        }
        yield() // let the coroutine reach suspendCancellableCoroutine (enqueue() already called)
        job.cancel()
        job.join()

        assertTrue(caughtCancellation)
        assertTrue(fakeCall.cancelled)
    }

    // ── Response body resource safety ────────────────────────────────────────

    @Test
    fun responseBody_closedOnSuccessPath() = runTest {
        var trackingBody: TrackingResponseBody? = null
        val factory = Call.Factory { req ->
            val body = TrackingResponseBody(validCreateResponseJson().toResponseBody("application/json".toMediaType()))
            trackingBody = body
            FakeCall(req, response = fakeResponse(req, 201, "", body = body))
        }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        assertTrue(trackingBody!!.closed)
    }

    @Test
    fun responseBody_closedOnHttpErrorPath() = runTest {
        var trackingBody: TrackingResponseBody? = null
        val factory = Call.Factory { req ->
            val body = TrackingResponseBody("{}".toResponseBody("application/json".toMediaType()))
            trackingBody = body
            FakeCall(req, response = fakeResponse(req, 401, "", body = body))
        }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        assertTrue(trackingBody!!.closed)
    }

    // ── Privacy / security ────────────────────────────────────────────────────

    @Test
    fun partnerKey_neverAppearsInResultOrError() = runTest {
        val errorBody = JSONObject().apply {
            put("code", "dwb_test_error")
            put("message", "test message")
        }.toString()
        val factory = Call.Factory { req -> FakeCall(req, response = fakeResponse(req, 401, errorBody)) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        val result = client.createHandoff(CreateHandoffRequest(), validIdempotencyKey)

        val failure = result as DeinWackelbildResult.Failure
        assertFalse(failure.toString().contains(partnerKey))
        assertFalse(failure.error.toString().contains(partnerKey))
    }

    @Test
    fun uploadImage_handoffTokenNeverAppearsInUrl() = runTest {
        val file = tempJpegFile()
        val factory = RecordingCallFactory { req -> FakeCall(req, response = fakeResponse(req, 200, uploadResponseJson("awaiting_files", listOf("one"), null))) }
        val client = OkHttpDeinWackelbildApiClient(factory, partnerKey)

        client.uploadImage("https://deinwackelbild.de/upload/h-1", "secret-handoff-token", DeinWackelbildSlot.ONE, file)

        assertFalse(factory.lastRequest.url.toString().contains("secret-handoff-token"))
    }
}
