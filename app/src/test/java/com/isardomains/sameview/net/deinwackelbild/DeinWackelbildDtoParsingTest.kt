// path: app/src/test/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildDtoParsingTest.kt
package com.isardomains.sameview.net.deinwackelbild

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeinWackelbildDtoParsingTest {

    private fun validCreateResponseJson(checkoutUrl: String? = null): JSONObject = JSONObject().apply {
        put("handoff_id", "h-1")
        put("handoff_token", "tok-1")
        put("partner", "sameview")
        put("status", "awaiting_files")
        put("expires_at", "2026-08-30T12:00:00Z")
        put("max_file_bytes", 20_971_520L)
        put("accepted_types", org.json.JSONArray(listOf("image/jpeg")))
        put("uploaded_slots", org.json.JSONArray(emptyList<String>()))
        put("upload_url", "https://deinwackelbild.de/upload/h-1")
        put("checkout_url", checkoutUrl)
    }

    // --- CreateHandoffResponse ---

    @Test
    fun createHandoffResponse_fullValid_parsesAllFields() {
        val response = parseCreateHandoffResponse(validCreateResponseJson())
        assertEquals(
            CreateHandoffResponse(
                handoffId = "h-1",
                handoffToken = "tok-1",
                partner = "sameview",
                status = "awaiting_files",
                expiresAt = "2026-08-30T12:00:00Z",
                maxFileBytes = 20_971_520L,
                acceptedTypes = listOf("image/jpeg"),
                uploadedSlots = emptyList(),
                uploadUrl = "https://deinwackelbild.de/upload/h-1",
                checkoutUrl = null
            ),
            response
        )
    }

    @Test
    fun createHandoffResponse_checkoutUrlNull_parsesAsNull() {
        val response = parseCreateHandoffResponse(validCreateResponseJson(checkoutUrl = null))
        assertNull(response!!.checkoutUrl)
    }

    @Test
    fun createHandoffResponse_extraUnknownFields_areIgnored() {
        val json = validCreateResponseJson().apply { put("some_future_field", "value") }
        val response = parseCreateHandoffResponse(json)
        assertEquals("h-1", response!!.handoffId)
    }

    @Test
    fun createHandoffResponse_missingHandoffId_returnsNull() {
        val json = validCreateResponseJson().apply { remove("handoff_id") }
        assertNull(parseCreateHandoffResponse(json))
    }

    @Test
    fun createHandoffResponse_missingHandoffToken_returnsNull() {
        val json = validCreateResponseJson().apply { remove("handoff_token") }
        assertNull(parseCreateHandoffResponse(json))
    }

    @Test
    fun createHandoffResponse_missingUploadUrl_returnsNull() {
        val json = validCreateResponseJson().apply { remove("upload_url") }
        assertNull(parseCreateHandoffResponse(json))
    }

    @Test
    fun createHandoffResponse_invalidNonHttpsUploadUrl_returnsNull() {
        val json = validCreateResponseJson().apply { put("upload_url", "http://deinwackelbild.de/upload/h-1") }
        assertNull(parseCreateHandoffResponse(json))
    }

    @Test
    fun createHandoffResponse_invalidCheckoutUrl_returnsNull() {
        val json = validCreateResponseJson().apply { put("checkout_url", "not a url") }
        assertNull(parseCreateHandoffResponse(json))
    }

    @Test
    fun createHandoffResponse_checkoutUrl_exactFragmentPreserved() {
        val urlWithFragment = "https://deinwackelbild.de/checkout/h-1?token=abc#step=payment"
        val response = parseCreateHandoffResponse(validCreateResponseJson(checkoutUrl = urlWithFragment))
        assertEquals(urlWithFragment, response!!.checkoutUrl)
    }

    // --- UploadResponse ---

    private fun uploadOneResponseJson(): JSONObject = JSONObject().apply {
        put("handoff_id", "h-1")
        put("status", "awaiting_files")
        put("uploaded_slots", org.json.JSONArray(listOf("one")))
        put("checkout_url", null)
    }

    private fun uploadTwoReadyResponseJson(checkoutUrl: String): JSONObject = JSONObject().apply {
        put("handoff_id", "h-1")
        put("status", "ready")
        put("uploaded_slots", org.json.JSONArray(listOf("one", "two")))
        put("checkout_url", checkoutUrl)
    }

    @Test
    fun uploadResponse_slotOneSuccess_parsesAwaitingFilesWithNullCheckout() {
        val response = parseUploadResponse(uploadOneResponseJson())
        assertEquals("awaiting_files", response!!.status)
        assertEquals(listOf("one"), response.uploadedSlots)
        assertNull(response.checkoutUrl)
    }

    @Test
    fun uploadResponse_slotTwoReadySuccess_parsesReadyWithCheckoutUrl() {
        val checkoutUrl = "https://deinwackelbild.de/checkout/h-1#token=xyz"
        val response = parseUploadResponse(uploadTwoReadyResponseJson(checkoutUrl))
        assertEquals("ready", response!!.status)
        assertEquals(listOf("one", "two"), response.uploadedSlots)
        assertEquals(checkoutUrl, response.checkoutUrl)
    }

    @Test
    fun uploadResponse_extraUnknownFields_areIgnored() {
        val json = uploadOneResponseJson().apply { put("future_field", 1) }
        assertEquals("h-1", parseUploadResponse(json)!!.handoffId)
    }

    @Test
    fun uploadResponse_missingRequiredField_returnsNull() {
        val json = uploadOneResponseJson().apply { remove("status") }
        assertNull(parseUploadResponse(json))
    }

    @Test
    fun uploadResponse_invalidCheckoutUrl_returnsNull() {
        val json = uploadTwoReadyResponseJson("not-a-url")
        assertNull(parseUploadResponse(json))
    }

    // --- Error envelope ---

    @Test
    fun errorEnvelope_validWordPressEnvelope_parsesAllFields() {
        val json = JSONObject().apply {
            put("code", "dwb_handoff_image_invalid")
            put("message", "SameView darf ausschließlich gültige JPG-Dateien übertragen.")
            put("data", JSONObject().apply { put("status", 415) })
        }
        val envelope = parseErrorEnvelope(json)
        assertEquals("dwb_handoff_image_invalid", envelope.code)
        assertEquals(415, envelope.data!!.status)
    }

    @Test
    fun errorEnvelope_missingData_stillParsesCodeAndMessage() {
        val json = JSONObject().apply {
            put("code", "dwb_error")
            put("message", "Something went wrong.")
        }
        val envelope = parseErrorEnvelope(json)
        assertEquals("dwb_error", envelope.code)
        assertEquals("Something went wrong.", envelope.message)
        assertNull(envelope.data)
    }

    @Test
    fun errorEnvelope_completelyMalformedBody_neverThrows_allFieldsNull() {
        val envelope = parseErrorEnvelope(JSONObject())
        assertNull(envelope.code)
        assertNull(envelope.message)
        assertNull(envelope.data)
    }

    // --- URL / idempotency-key validation ---

    @Test
    fun isValidHttpsUrl_acceptsHttps() {
        assertTrue(isValidHttpsUrl("https://deinwackelbild.de/x"))
    }

    @Test
    fun isValidHttpsUrl_rejectsHttp() {
        assertTrue(!isValidHttpsUrl("http://deinwackelbild.de/x"))
    }

    @Test
    fun isValidIdempotencyKey_acceptsValidFormat() {
        assertTrue(isValidIdempotencyKey("abc12345"))
        assertTrue(isValidIdempotencyKey("a.b_c-1234"))
    }

    @Test
    fun isValidIdempotencyKey_rejectsTooShort() {
        assertTrue(!isValidIdempotencyKey("short"))
    }

    @Test
    fun isValidIdempotencyKey_rejectsInvalidCharacters() {
        assertTrue(!isValidIdempotencyKey("has a space123"))
    }
}
