// path: app/src/main/java/com/isardomains/sameview/image/wackelbild/WackelbildPrintRenderer.kt
package com.isardomains.sameview.image.wackelbild

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.isardomains.sameview.image.ShareImageRenderer
import com.isardomains.sameview.image.readExifOrientedDimensions
import com.isardomains.sameview.image.readOverlayParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Reference/Capture output pair produced by [WackelbildPrintRenderer]. Semantic, typed fields —
 * never exposed as generic `one`/`two`; the eventual DeinWackelbild API slot mapping is decided
 * once, explicitly, at the network integration call site (Block 7+), not here. */
data class WackelbildPrintPair(val referenceFile: File, val captureFile: File)

/** Pre-formatted date strings for the optional print-output date badge. Formatting/metadata
 * reading happens elsewhere (Block 4's `DateBadgeFormatter`) — this renderer only draws whatever
 * strings it is given, and draws nothing for a null side. Pass `null` (not an instance) to
 * represent the date overlay being fully OFF for both sides. */
data class WackelbildDateOverlay(val referenceText: String?, val captureText: String?)

/** Reasons [WackelbildPrintRenderer] cannot produce a print pair at all. A successful fallback is
 * never represented here — see [WackelbildPrintResult.Success.usedFallback] instead. */
enum class WackelbildPrintFailureReason { PERMANENT_NO_VALID_SOURCE }

sealed class WackelbildPrintResult {
    data class Success(val pair: WackelbildPrintPair, val usedFallback: Boolean) : WackelbildPrintResult()
    data class Failure(val reason: WackelbildPrintFailureReason) : WackelbildPrintResult()
}

private const val TWENTY_MIB = 20L * 1024 * 1024
private const val JPEG_QUALITY_HIGH = 92
private const val JPEG_QUALITY_LOW = 85

/**
 * Produces the two-file HQ/fallback Wackelbild print pair for one session.
 *
 * Architecture per `docs/deinwackelbild/DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` §9/§10 (as
 * corrected by Gates 5B/5C): HQ Capture is a direct, uncropped downsample of
 * `capture-original.jpg` (never `prepareHqCaptureForSbs`, never a crop), gated by a named
 * ratio-tolerance guard; HQ Reference reuses `ShareImageRenderer`'s already-proven
 * `renderHqReference`; both sides render, badge, encode, and recycle sequentially — never two
 * full-size output bitmaps held at once; the date badge is always drawn before JPEG encoding, so
 * `File.length()` always measures the true final visual output; the frozen-pair fallback never
 * crops/stretches/letterboxes an incompatible-ratio pair and instead hard-fails.
 */
class WackelbildPrintRenderer(
    private val shareRenderer: ShareImageRenderer = ShareImageRenderer()
) {

    /**
     * @param sessionDir Directory containing `reference.jpg`, `capture.jpg`, `metadata.json`, and
     *   (when present) `reference-original.jpg`/`capture-original.jpg`. Never opened for write.
     * @param outputDir Directory to write `image_one.jpg`/`image_two.jpg` candidates into — the
     *   caller (Block 6+) owns its lifecycle; this renderer only ever writes inside it.
     * @param dateOverlay Pre-formatted date strings, or null to draw no badge on either side.
     */
    suspend fun renderPrintPair(
        sessionDir: File,
        outputDir: File,
        dateOverlay: WackelbildDateOverlay?
    ): WackelbildPrintResult {
        val referenceCandidateFile = File(outputDir, "image_one.jpg")
        val captureCandidateFile = File(outputDir, "image_two.jpg")

        val hqPair = tryRenderHq(sessionDir, referenceCandidateFile, captureCandidateFile, dateOverlay)
        if (hqPair != null) {
            return WackelbildPrintResult.Success(hqPair, usedFallback = false)
        }
        return renderFallback(sessionDir, referenceCandidateFile, captureCandidateFile, dateOverlay)
    }

    // ── HQ path ──────────────────────────────────────────────────────────────

    private suspend fun tryRenderHq(
        sessionDir: File,
        referenceCandidateFile: File,
        captureCandidateFile: File,
        dateOverlay: WackelbildDateOverlay?
    ): WackelbildPrintPair? {
        return try {
            val viewport = shareRenderer.readSessionViewport(sessionDir)
            val captureOriginalFile = shareRenderer.resolveHqCaptureFile(sessionDir) ?: return null
            val overlayParams = readOverlayParams(sessionDir) ?: return null
            val captureOriginalDims = readExifOrientedDimensions(captureOriginalFile) ?: return null

            val ratioAcceptable = WackelbildDimensionResolver.isRatioWithinTolerance(
                actualW = captureOriginalDims.first,
                actualH = captureOriginalDims.second,
                expectedW = viewport.first,
                expectedH = viewport.second,
                tolerance = WackelbildDimensionResolver.roundingToleranceFor(viewport.first, viewport.second)
            )
            if (!ratioAcceptable) return null

            val referenceOriginalFile = File(sessionDir, "reference-original.jpg")
            if (!referenceOriginalFile.isFile) return null
            val referenceOriginalDims = readExifOrientedDimensions(referenceOriginalFile) ?: return null

            val resolvedDims = try {
                WackelbildDimensionResolver.resolve(
                    viewportW = viewport.first,
                    viewportH = viewport.second,
                    captureOriginalDims = captureOriginalDims,
                    referenceOriginalDims = referenceOriginalDims,
                    overlayScale = overlayParams.scale,
                    displayMode = overlayParams.displayMode
                )
            } catch (_: WackelbildHqUnusableException) {
                return null
            }

            val loop = WackelbildPairSizeLoop()
            val pair = loop.run(
                initialDims = resolvedDims,
                renderReference = { dims, quality ->
                    renderBadgeEncodeRecycle(referenceCandidateFile, quality, dateOverlay?.referenceText) {
                        shareRenderer.renderHqReference(sessionDir, dims.width, dims.height, overlayParams)
                    }
                },
                renderCapture = { dims, quality ->
                    renderBadgeEncodeRecycle(captureCandidateFile, quality, dateOverlay?.captureText) {
                        shareRenderer.decodeHqCapture(captureOriginalFile, dims.width, dims.height)
                            ?: throw IOException("Cannot decode HQ capture source in ${sessionDir.name}")
                    }
                },
                deleteCandidate = { it.delete() }
            )
            pair?.let { (refFile, capFile) -> WackelbildPrintPair(refFile, capFile) }
        } catch (_: Exception) {
            // Any HQ decode/render exception (IO, decode, unexpected) routes to fallback (§13).
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    // ── Frozen-pair fallback ─────────────────────────────────────────────────

    private suspend fun renderFallback(
        sessionDir: File,
        referenceCandidateFile: File,
        captureCandidateFile: File,
        dateOverlay: WackelbildDateOverlay?
    ): WackelbildPrintResult {
        return try {
            val referenceFile = File(sessionDir, "reference.jpg")
            val captureFile = File(sessionDir, "capture.jpg")

            val referenceDims = readExifOrientedDimensions(referenceFile)
                ?: return WackelbildPrintResult.Failure(WackelbildPrintFailureReason.PERMANENT_NO_VALID_SOURCE)
            val captureDims = readExifOrientedDimensions(captureFile)
                ?: return WackelbildPrintResult.Failure(WackelbildPrintFailureReason.PERMANENT_NO_VALID_SOURCE)

            val fallbackDims = WackelbildDimensionResolver.resolveFallbackDimensions(referenceDims, captureDims)
                ?: return WackelbildPrintResult.Failure(WackelbildPrintFailureReason.PERMANENT_NO_VALID_SOURCE)

            // No dimension step-down for the fallback: Correction J's dimension algorithm is
            // already deterministic (Case A/B), so only the quality ladder (92 then 85) applies.
            val loop = WackelbildPairSizeLoop(maxDimensionSteps = 0)
            val pair = loop.run(
                initialDims = fallbackDims,
                renderReference = { dims, quality ->
                    renderBadgeEncodeRecycle(referenceCandidateFile, quality, dateOverlay?.referenceText) {
                        scaleToDims(shareRenderer.decodeReferenceFallback(sessionDir), dims)
                    }
                },
                renderCapture = { dims, quality ->
                    renderBadgeEncodeRecycle(captureCandidateFile, quality, dateOverlay?.captureText) {
                        val raw = BitmapFactory.decodeFile(captureFile.absolutePath)
                            ?: throw IOException("Cannot decode capture.jpg in ${sessionDir.name}")
                        scaleToDims(raw, dims)
                    }
                },
                deleteCandidate = { it.delete() }
            )

            if (pair != null) {
                WackelbildPrintResult.Success(WackelbildPrintPair(pair.first, pair.second), usedFallback = true)
            } else {
                WackelbildPrintResult.Failure(WackelbildPrintFailureReason.PERMANENT_NO_VALID_SOURCE)
            }
        } catch (_: Exception) {
            WackelbildPrintResult.Failure(WackelbildPrintFailureReason.PERMANENT_NO_VALID_SOURCE)
        } catch (_: OutOfMemoryError) {
            WackelbildPrintResult.Failure(WackelbildPrintFailureReason.PERMANENT_NO_VALID_SOURCE)
        }
    }

    // ── Shared render primitives ─────────────────────────────────────────────

    /**
     * Renders/decodes one side's bitmap, draws the badge into it (if [dateText] is non-null,
     * before compression — Block 5B/C Correction A), compresses to [outFile] at [quality], then
     * recycles. Never holds more than one bitmap beyond this call, satisfying the sequential,
     * single-bitmap-at-a-time discipline (§9.4/§10.3 Correction F).
     *
     * [produceBitmap]'s result may be immutable (both `ImageDecoder`- and `BitmapFactory`-decoded
     * bitmaps are immutable by default, unlike `Bitmap.createBitmap`/`createScaledBitmap`
     * results) — `Canvas` requires a mutable bitmap, so when a badge is actually being drawn onto
     * an immutable source, a mutable copy is made first and the immutable original is recycled
     * immediately. No copy is made on the (far more common) no-badge path.
     */
    private suspend fun renderBadgeEncodeRecycle(
        outFile: File,
        quality: Int,
        dateText: String?,
        produceBitmap: () -> Bitmap
    ): File = withContext(Dispatchers.Default) {
        var bitmap = produceBitmap()
        try {
            if (!dateText.isNullOrBlank() && !bitmap.isMutable) {
                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                bitmap.recycle()
                bitmap = mutableBitmap
            }
            DateBadgeRenderer.draw(bitmap, dateText)
            FileOutputStream(outFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
        } finally {
            bitmap.recycle()
        }
        outFile
    }

    /** Downscale-only resize to [dims]; never upscales, never crops. Returns [bitmap] unchanged
     * when it already matches [dims] exactly. Recycles the original when a new bitmap is created. */
    private fun scaleToDims(bitmap: Bitmap, dims: WackelbildTargetDimensions): Bitmap {
        if (bitmap.width == dims.width && bitmap.height == dims.height) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, dims.width, dims.height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}

/**
 * Sequential, badge-before-encode, bounded pair-level ≤20 MiB size loop
 * (`DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md` §10.3, Block 5B/C Corrections A/F).
 *
 * [renderReference]/[renderCapture] must each fully render, badge, encode, and recycle their own
 * bitmap before returning the resulting [File] — this loop never touches `Bitmap`/`Canvas` APIs
 * directly, which is what makes it independently unit-testable with injected fakes, without any
 * real 20 MiB fixture.
 */
internal class WackelbildPairSizeLoop(
    private val qualityLadder: List<Int> = listOf(JPEG_QUALITY_HIGH, JPEG_QUALITY_LOW),
    private val maxDimensionSteps: Int = 3,
    private val dimensionStepFactor: Float = 0.85f,
    private val maxFileBytes: Long = TWENTY_MIB
) {
    suspend fun run(
        initialDims: WackelbildTargetDimensions,
        renderReference: suspend (dims: WackelbildTargetDimensions, quality: Int) -> File,
        renderCapture: suspend (dims: WackelbildTargetDimensions, quality: Int) -> File,
        deleteCandidate: (File) -> Unit
    ): Pair<File, File>? {
        var currentDims = initialDims
        for (dimStep in 0..maxDimensionSteps) {
            for (quality in qualityLadder) {
                // Sequential: renderCapture only starts after renderReference has fully
                // completed (rendered, badged, encoded, recycled) — never overlapping.
                val referenceFile = renderReference(currentDims, quality)
                val captureFile = renderCapture(currentDims, quality)
                if (referenceFile.length() <= maxFileBytes && captureFile.length() <= maxFileBytes) {
                    return referenceFile to captureFile
                }
                // Neither candidate is accepted when either exceeds the limit — both are
                // regenerated together on the next attempt, never accepted/resized independently.
                deleteCandidate(referenceFile)
                deleteCandidate(captureFile)
            }
            if (dimStep < maxDimensionSteps) {
                currentDims = WackelbildDimensionResolver.stepDownDimensions(currentDims, dimensionStepFactor)
            }
        }
        return null
    }
}
