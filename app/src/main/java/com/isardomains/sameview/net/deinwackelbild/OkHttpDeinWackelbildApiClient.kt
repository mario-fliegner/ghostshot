// path: app/src/main/java/com/isardomains/sameview/net/deinwackelbild/OkHttpDeinWackelbildApiClient.kt
package com.isardomains.sameview.net.deinwackelbild

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

/**
 * Raw OkHttp implementation of [DeinWackelbildApiClient]. Owns request construction, response
 * parsing, and HTTP/transport/protocol error classification only -- no retry, no idempotency-key
 * generation, no operation/business state.
 *
 * [callFactory] is deliberately narrower than the full [OkHttpClient] -- only `newCall(Request)`
 * is ever needed, and this is the whole test seam: production wiring supplies
 * [createDefaultCallFactory], JVM tests supply a hand-written fake with no real socket.
 */
class OkHttpDeinWackelbildApiClient(
    private val callFactory: Call.Factory,
    private val partnerKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL
) : DeinWackelbildApiClient {

    override suspend fun createHandoff(
        request: CreateHandoffRequest,
        idempotencyKey: String
    ): DeinWackelbildResult<CreateHandoffResponse> {
        if (!isValidIdempotencyKey(idempotencyKey)) {
            return DeinWackelbildResult.Failure(
                DeinWackelbildApiError(DeinWackelbildErrorClassification.PERMANENT_LOCAL)
            )
        }

        val httpRequest = Request.Builder()
            .url("$baseUrl/partner-handoffs")
            .header("X-DWB-Partner-Key", partnerKey)
            .header("Idempotency-Key", idempotencyKey)
            // Content-Type is derived from this JSON-media-typed body alone -- no separate
            // manually-set Content-Type header, so there is exactly one source of truth for it.
            .post(request.toJson().toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return executeAndParse(httpRequest, expectedSuccessCode = 201, parse = ::parseCreateHandoffResponse)
    }

    override suspend fun uploadImage(
        uploadUrl: String,
        handoffToken: String,
        slot: DeinWackelbildSlot,
        file: File
    ): DeinWackelbildResult<UploadResponse> {
        if (!file.isFile || !file.canRead()) {
            return DeinWackelbildResult.Failure(
                DeinWackelbildApiError(DeinWackelbildErrorClassification.PERMANENT_LOCAL)
            )
        }
        if (!isValidHttpsUrl(uploadUrl)) {
            return DeinWackelbildResult.Failure(
                DeinWackelbildApiError(DeinWackelbildErrorClassification.MALFORMED_RESPONSE)
            )
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("slot", slot.wireValue)
            .addFormDataPart("file", file.name, file.asRequestBody(JPEG_MEDIA_TYPE))
            .build()
        val httpRequest = Request.Builder()
            .url(uploadUrl) // exact server-returned value -- never reconstructed/concatenated
            .header("X-DWB-Handoff-Token", handoffToken)
            .post(multipartBody)
            .build()

        return executeAndParse(httpRequest, expectedSuccessCode = 200, parse = ::parseUploadResponse)
    }

    private suspend fun <T> executeAndParse(
        request: Request,
        expectedSuccessCode: Int,
        parse: (JSONObject) -> T?
    ): DeinWackelbildResult<T> {
        val response = try {
            callFactory.newCall(request).await()
        } catch (e: IOException) {
            // Covers connectivity failure and SocketTimeoutException (a subtype of IOException)
            // identically -- both map to the same RETRYABLE_NETWORK classification (spec §14.6).
            // A genuine coroutine cancellation is not an IOException and is not caught here; it
            // propagates as CancellationException, never becoming a Failure.
            return DeinWackelbildResult.Failure(
                DeinWackelbildApiError(DeinWackelbildErrorClassification.RETRYABLE_NETWORK)
            )
        }

        return response.use { resp ->
            val bodyString = runCatching { resp.body?.string() }.getOrNull()
            if (resp.isSuccessful && resp.code == expectedSuccessCode) {
                val parsed = bodyString?.let(::parseJsonObjectOrNull)?.let(parse)
                if (parsed != null) {
                    DeinWackelbildResult.Success(parsed)
                } else {
                    DeinWackelbildResult.Failure(
                        DeinWackelbildApiError(DeinWackelbildErrorClassification.MALFORMED_RESPONSE, resp.code)
                    )
                }
            } else {
                // HTTP status is the primary, always-available classification signal. Error-body
                // parsing is best-effort enrichment only (§13) -- a malformed/missing error body
                // must never downgrade an already-known HTTP classification to MALFORMED_RESPONSE.
                val serverCode = bodyString?.let(::parseJsonObjectOrNull)?.let(::parseErrorEnvelope)?.code
                DeinWackelbildResult.Failure(
                    DeinWackelbildApiError(classifyHttpStatus(resp.code), resp.code, serverCode)
                )
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://deinwackelbild.de/wp-json/dwb/v1"

        /**
         * Production OkHttp configuration. `retryOnConnectionFailure(false)` and no redirects
         * keep transport behavior fully deterministic for a later block's retry/attempt
         * accounting; timeouts are sized for the largest request (upload, ≥60s connect/write per
         * the pilot contract) since a single shared client is simpler than maintaining a separate
         * short-timeout client for the small create call.
         */
        fun createDefaultCallFactory(): Call.Factory = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

private fun classifyHttpStatus(code: Int): DeinWackelbildErrorClassification = when (code) {
    400 -> DeinWackelbildErrorClassification.INVALID_REQUEST
    401 -> DeinWackelbildErrorClassification.INTEGRATION_UNAVAILABLE
    403 -> DeinWackelbildErrorClassification.EXPIRED_HANDOFF
    409 -> DeinWackelbildErrorClassification.INCOMPLETE_HANDOFF
    410 -> DeinWackelbildErrorClassification.EXPIRED_HANDOFF
    413 -> DeinWackelbildErrorClassification.FILE_TOO_LARGE
    415 -> DeinWackelbildErrorClassification.INVALID_IMAGE
    422 -> DeinWackelbildErrorClassification.DIMENSION_MISMATCH
    429 -> DeinWackelbildErrorClassification.RATE_LIMITED
    in 500..599 -> DeinWackelbildErrorClassification.RETRYABLE_SERVER
    else -> DeinWackelbildErrorClassification.UNEXPECTED_HTTP_STATUS
}

private fun parseJsonObjectOrNull(raw: String): JSONObject? = try {
    JSONObject(raw)
} catch (_: JSONException) {
    null
}

/**
 * Coroutine-cancellation-aware bridge to OkHttp's callback API -- deliberately not blocking
 * `execute()` inside `withContext(Dispatchers.IO)`, which would leave the underlying HTTP call
 * running to completion even after the coroutine is cancelled. `invokeOnCancellation` calls
 * [Call.cancel] directly, which immediately aborts the transfer; per `suspendCancellableCoroutine`
 * convention, [Callback.onFailure] does not resume an already-cancelled continuation, so
 * cancellation always surfaces as [kotlinx.coroutines.CancellationException], never as a
 * [DeinWackelbildResult.Failure].
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWith(Result.failure(e))
        }
    })
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}
