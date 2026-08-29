// path: app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildDtos.kt
package com.isardomains.sameview.net.deinwackelbild

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which of the two SameView print images an upload request targets. Wire values (`"one"`/`"two"`)
 * are the DeinWackelbild API's own slot names; the SameView-side semantic mapping (Reference →
 * `one`, Capture → `two`) is a caller-level decision (`DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md`
 * §9.3 Correction N) — this raw client only ever sends whichever slot/file pair it is given.
 */
enum class DeinWackelbildSlot(val wireValue: String) {
    ONE("one"),
    TWO("two")
}

/** Create-handoff request body. `external_reference` is deliberately never a field here — SameView
 * V1 omits it (spec §27) even though the partner API supports it. */
data class CreateHandoffRequest(val partner: String = "sameview", val locale: String? = null)

data class CreateHandoffResponse(
    val handoffId: String,
    val handoffToken: String,
    val partner: String,
    val status: String,               // raw wire value, e.g. "awaiting_files" -- Block 8 owns meaning
    val expiresAt: String,            // raw ISO-8601 string, not parsed -- no date/time library added
    val maxFileBytes: Long,
    val acceptedTypes: List<String>,
    val uploadedSlots: List<String>,
    val uploadUrl: String,
    val checkoutUrl: String?
)

data class UploadResponse(
    val handoffId: String,
    val status: String,               // raw wire value, e.g. "ready"
    val uploadedSlots: List<String>,
    val checkoutUrl: String?
)

/** Nested `data` object of the confirmed WordPress REST error envelope. */
data class DeinWackelbildErrorData(val status: Int?)

/** Parsed best-effort from a non-2xx response body. Never shown to the user verbatim -- `code`
 * and `message` are for internal technical logging/classification only. */
data class DeinWackelbildErrorEnvelope(
    val code: String?,
    val message: String?,
    val data: DeinWackelbildErrorData?
)

/** Raw HTTP/transport/protocol-level outcome. Business interpretation (e.g. "403 and 410 both mean
 * start a new handoff") is Block 8's job -- this enum only records the distinguishable facts a
 * later block needs to make that decision. */
enum class DeinWackelbildErrorClassification {
    RETRYABLE_NETWORK,
    RETRYABLE_SERVER,
    RATE_LIMITED,
    INVALID_REQUEST,
    EXPIRED_HANDOFF,
    INTEGRATION_UNAVAILABLE,
    FILE_TOO_LARGE,
    INVALID_IMAGE,
    DIMENSION_MISMATCH,
    PERMANENT_LOCAL,
    INCOMPLETE_HANDOFF,
    MALFORMED_RESPONSE,
    UNEXPECTED_HTTP_STATUS
}

/** Never contains the partner key, handoff token, upload URL, checkout URL, image file path, or
 * raw response body -- only the classification, the HTTP status (if any), and the server's own
 * technical `code` (if the error body parsed). */
data class DeinWackelbildApiError(
    val classification: DeinWackelbildErrorClassification,
    val httpStatus: Int? = null,
    val serverCode: String? = null
)

sealed class DeinWackelbildResult<out T> {
    data class Success<T>(val value: T) : DeinWackelbildResult<T>()
    data class Failure(val error: DeinWackelbildApiError) : DeinWackelbildResult<Nothing>()
}

// ── Request serialization ───────────────────────────────────────────────────

internal fun CreateHandoffRequest.toJson(): JSONObject = JSONObject().apply {
    put("partner", partner)
    locale?.let { put("locale", it) }
}

/** `Idempotency-Key` format confirmed by the pilot contract: required, 8-100 characters, only
 * letters/digits/`.`/`_`/`-`. Generation/retention/regeneration is Block 8's job; this only
 * validates a caller-supplied value before it is ever placed on the wire. */
private val IDEMPOTENCY_KEY_PATTERN = Regex("^[A-Za-z0-9._-]{8,100}$")

internal fun isValidIdempotencyKey(key: String): Boolean = IDEMPOTENCY_KEY_PATTERN.matches(key)

// ── Response parsing ────────────────────────────────────────────────────────

/** Returns `null` on any malformed/missing-required-field/invalid-URL condition -- the caller maps
 * a `null` result to `DeinWackelbildErrorClassification.MALFORMED_RESPONSE`. */
internal fun parseCreateHandoffResponse(json: JSONObject): CreateHandoffResponse? {
    val handoffId = json.optNullableString("handoff_id") ?: return null
    val handoffToken = json.optNullableString("handoff_token") ?: return null
    val partner = json.optNullableString("partner") ?: return null
    val status = json.optNullableString("status") ?: return null
    val expiresAt = json.optNullableString("expires_at") ?: return null
    val maxFileBytes = json.optLong("max_file_bytes", -1L)
    if (maxFileBytes <= 0L) return null
    val acceptedTypes = (json.optJSONArray("accepted_types") ?: return null).toStringList()
    if (acceptedTypes.isEmpty()) return null
    val uploadedSlots = (json.optJSONArray("uploaded_slots") ?: JSONArray()).toStringList()
    val uploadUrl = json.optNullableString("upload_url") ?: return null
    if (!isValidHttpsUrl(uploadUrl)) return null
    val checkoutUrl = json.optNullableString("checkout_url")
    if (checkoutUrl != null && !isValidHttpsUrl(checkoutUrl)) return null

    return CreateHandoffResponse(
        handoffId = handoffId,
        handoffToken = handoffToken,
        partner = partner,
        status = status,
        expiresAt = expiresAt,
        maxFileBytes = maxFileBytes,
        acceptedTypes = acceptedTypes,
        uploadedSlots = uploadedSlots,
        uploadUrl = uploadUrl,
        checkoutUrl = checkoutUrl
    )
}

internal fun parseUploadResponse(json: JSONObject): UploadResponse? {
    val handoffId = json.optNullableString("handoff_id") ?: return null
    val status = json.optNullableString("status") ?: return null
    val uploadedSlots = (json.optJSONArray("uploaded_slots") ?: JSONArray()).toStringList()
    val checkoutUrl = json.optNullableString("checkout_url")
    if (checkoutUrl != null && !isValidHttpsUrl(checkoutUrl)) return null

    return UploadResponse(
        handoffId = handoffId,
        status = status,
        uploadedSlots = uploadedSlots,
        checkoutUrl = checkoutUrl
    )
}

/** Best-effort only -- never throws, never returns null. A malformed/missing error body must not
 * replace an already-known HTTP-status classification with a protocol failure (§13); it just means
 * [DeinWackelbildErrorEnvelope.code]/[DeinWackelbildErrorEnvelope.message] stay null. */
internal fun parseErrorEnvelope(json: JSONObject): DeinWackelbildErrorEnvelope {
    val code = json.optNullableString("code")
    val message = json.optNullableString("message")
    val dataObject = json.optJSONObject("data")
    val status = dataObject?.takeIf { it.has("status") && !it.isNull("status") }?.optInt("status")
    return DeinWackelbildErrorEnvelope(code, message, status?.let(::DeinWackelbildErrorData))
}

// ── Shared parsing helpers ──────────────────────────────────────────────────

/** Absent, JSON `null`, or blank all mean "not present" -- never distinguished from each other. */
private fun JSONObject.optNullableString(key: String): String? {
    if (isNull(key)) return null
    return optString(key, "").ifBlank { null }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) {
        val value = optString(i, "")
        if (value.isNotBlank()) add(value)
    }
}

/** Safe, canonical URL parsing via OkHttp's own `HttpUrl` -- never manual string matching. Requires
 * HTTPS. Used only to validate; the original raw string is always what gets stored/transmitted, so
 * a fragment (e.g. a token embedded in `checkout_url`) is never dropped or reordered by this check. */
internal fun isValidHttpsUrl(raw: String): Boolean = raw.toHttpUrlOrNull()?.scheme == "https"
