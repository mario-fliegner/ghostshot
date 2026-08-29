// path: app/src/main/java/com/isardomains/sameview/net/deinwackelbild/DeinWackelbildApiClient.kt
package com.isardomains.sameview.net.deinwackelbild

import java.io.File

/**
 * Raw DeinWackelbild partner-handoff API boundary. Mirrors the partner's two operations directly
 * -- it owns no operation/business state (no stored handoff, no retry count, no idempotency-key
 * generation) and is stateless across calls, so a single instance can be reused for any number of
 * unrelated handoffs. Orchestration (when to call which method, retries, backoff, the operation
 * state machine) belongs to a later block; this interface only has to be fakeable in a JVM test
 * with no socket, which is why it exposes no OkHttp type.
 */
interface DeinWackelbildApiClient {

    /**
     * Creates a new partner handoff. [idempotencyKey] must already be a valid, caller-generated
     * value (format: `DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` §14.2) -- this client neither
     * generates nor retains it; the caller is responsible for reusing the same key across retries
     * of the same user operation.
     */
    suspend fun createHandoff(
        request: CreateHandoffRequest,
        idempotencyKey: String
    ): DeinWackelbildResult<CreateHandoffResponse>

    /**
     * Uploads one JPEG to the given [slot]. [uploadUrl] and [handoffToken] must be the exact
     * values returned by the preceding [createHandoff] call -- this client never reconstructs or
     * derives the upload URL itself. [file] is streamed from disk, never loaded into memory.
     */
    suspend fun uploadImage(
        uploadUrl: String,
        handoffToken: String,
        slot: DeinWackelbildSlot,
        file: File
    ): DeinWackelbildResult<UploadResponse>
}
