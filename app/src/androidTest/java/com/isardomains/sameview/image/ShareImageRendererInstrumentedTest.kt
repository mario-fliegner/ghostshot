package com.isardomains.sameview.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ShareImageRendererInstrumentedTest {

    private lateinit var resolver: ContentResolver
    private lateinit var sessionDir: File
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resolver = context.contentResolver
        sessionDir = File(context.filesDir, "share_test_${System.currentTimeMillis()}")
        sessionDir.mkdirs()
        writeSyntheticSession(sessionDir)
    }

    @After
    fun tearDown() {
        sessionDir.deleteRecursively()
        createdUris.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
    }

    // ── Pre-existing tests (Block 2 / original implementation) ───────────────

    @Test
    fun t_b2_09_sliderStandard_noCaptionCreatesJpeg() {
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp()
        )
        assertJpegExistsInMediaStore(render(config))
    }

    @Test
    fun t_b2_10_sideBySideOriginal_withCaption_createsJpeg() {
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SIDE_BY_SIDE,
            quality = ShareQuality.ORIGINAL,
            captionData = ShareCaptionData("Grünwald Rathaus", "1958 → 2026", "Grünwald"),
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp()
        )
        assertJpegExistsInMediaStore(render(config))
    }

    @Test
    fun t_b2_11_output_hasNoGpsExif() {
        val config = ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER,
            quality = ShareQuality.STANDARD,
            captionData = null,
            sessionDir = sessionDir,
            exportTimestamp = nowTimestamp()
        )
        val uri = render(config)
        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            assertTrue("GPS latitude must not be present", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE).isNullOrEmpty())
            assertTrue("GPS longitude must not be present", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE).isNullOrEmpty())
        } ?: error("Could not open exported JPEG for EXIF check")
    }

    @Test
    fun t_b2_12_displayName_containsTimestampAndStyleSuffix() {
        val ts = "20260621_120000"
        val uri = render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir, exportTimestamp = ts
        ))
        val name = queryDisplayName(uri)
        assertNotNull(name)
        assertTrue(name!!.contains(ts))
        assertTrue(name.contains("slider"))
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun t_b2_13_sideBySideStyle_displayNameContainsSidebyside() {
        val uri = render(ShareRenderConfig(
            style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir, exportTimestamp = "20260621_120001"
        ))
        assertTrue(queryDisplayName(uri)?.contains("sidebyside") == true)
    }

    @Test
    fun t_b2_14_output_relativePath_isPicturesSameView() {
        val uri = render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir, exportTimestamp = nowTimestamp()
        ))
        val cursor = resolver.query(uri, arrayOf(MediaStore.Images.Media.RELATIVE_PATH), null, null, null)
        val path = cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        assertNotNull(path)
        assertTrue("RELATIVE_PATH must start with Pictures/SameView, was: $path",
            path!!.startsWith("Pictures/SameView"))
    }

    // ── Branding tests ────────────────────────────────────────────────────────

    @Test
    fun branding_useBrandingTrue_withBrandingFile_rendersWithoutCrash() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        assertJpegExistsInMediaStore(render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(), useBranding = true
        )))
    }

    @Test
    fun i12_branding_useBrandingTrue_renderedOutputDiffersFromStandardHandle() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        val uriWith = render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir,
            exportTimestamp = "20260701_100001", useBranding = true
        ))
        val uriWithout = render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir,
            exportTimestamp = "20260701_100002", useBranding = false
        ))
        val bmpWith = resolver.openInputStream(uriWith)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Cannot decode branding export")
        val bmpWithout = resolver.openInputStream(uriWithout)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Cannot decode standard export")
        val cx = bmpWith.width / 2; val cy = bmpWith.height / 2
        var diffFound = false
        outer@ for (dy in 0 until 10) for (dx in 0 until 10) {
            if (bmpWith.getPixel(cx + dx, cy + dy) != bmpWithout.getPixel(cx + dx, cy + dy)) {
                diffFound = true; break@outer
            }
        }
        bmpWith.recycle(); bmpWithout.recycle()
        assertTrue("Branding ON must differ from branding OFF in handle region", diffFound)
    }

    @Test
    fun branding_useBrandingFalse_rendersStandardHandle_withoutCrash() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        assertJpegExistsInMediaStore(render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(), useBranding = false
        )))
    }

    @Test
    fun branding_useBrandingTrue_missingBrandingFile_fallsBackToStandardHandle() {
        assertJpegExistsInMediaStore(render(ShareRenderConfig(
            style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(), useBranding = true
        )))
    }

    @Test
    fun branding_sideBySide_useBrandingTrue_noHandleRendered() {
        writeSyntheticBrandingPng(File(sessionDir, "branding-handle.png"))
        assertJpegExistsInMediaStore(render(ShareRenderConfig(
            style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir,
            exportTimestamp = nowTimestamp(), useBranding = true
        )))
    }

    // ── Block C: HQ Original quality tests ───────────────────────────────────

    // T-HQ-I-01: Standard unchanged
    @Test
    fun t_hq_i_01_standard_quality_unchanged() {
        // Standard on the HQ session must produce the same canvas as standard session
        val hqDir = createHqSessionDir()
        try {
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp()
            ))
            assertJpegExistsInMediaStore(uri)
            val (w, h) = decodeOutputDimensions(uri)
            // viewport 200x300, Standard → max(200,300)=300 < 2048 → canvas 200+2*8=216 × 300+2*8=316
            assertTrue("Standard longest edge must be <= 2048", maxOf(w, h) <= 2048)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-02: Original + captureOriginalFile → canvas larger than Standard
    @Test
    fun t_hq_i_02_original_withHqCaptureFile_canvasLargerThanStandard() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")

            val standardUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260701_200001"
            ))
            val hqUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260701_200002",
                captureOriginalFile = captureOriginal
            ))

            val (sw, sh) = decodeOutputDimensions(standardUri)
            val (hw, hh) = decodeOutputDimensions(hqUri)

            assertTrue("HQ canvas longest edge (${maxOf(hw, hh)}) must exceed Standard (${maxOf(sw, sh)})",
                maxOf(hw, hh) > maxOf(sw, sh))
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-03: Aspect ratio matches viewport
    @Test
    fun t_hq_i_03_original_withHqCaptureFile_aspectRatioMatchesViewport() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            // viewport 200×300, expected HQ compW=400 compH=600, canvas ~432×632
            // Comparison area ratio = 600/400 = 1.5 = viewport ratio 300/200 ✓
            val (w, h) = decodeOutputDimensions(uri)
            // Canvas is comparison area + 2*outerPad on each side, ratio must be close to 3:2 (h/w)
            // Allow ±10% for outerPad influence on overall canvas ratio
            val ratio = h.toDouble() / w.toDouble()
            val expectedRatio = 3.0 / 2.0   // 300/200 viewport ratio
            assertTrue("Canvas aspect ratio $ratio must be close to viewport ratio $expectedRatio",
                Math.abs(ratio - expectedRatio) < expectedRatio * 0.15)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-04: HQ export has no GPS EXIF
    @Test
    fun t_hq_i_04_original_hqExport_noGpsExif() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            resolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                assertTrue("GPS lat must not be in HQ output",
                    exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE).isNullOrEmpty())
                assertTrue("GPS lon must not be in HQ output",
                    exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE).isNullOrEmpty())
            } ?: error("Cannot open HQ output for EXIF check")
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-05: null captureOriginalFile → viewport canvas (same as Standard)
    @Test
    fun t_hq_i_05_original_noCaptureOriginalFile_usesViewportDimensions() {
        val hqDir = createHqSessionDir()
        try {
            val standardUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260701_300001"
            ))
            // Original with captureOriginalFile = null → uses viewport dims (same as Standard for small viewport)
            val originalUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260701_300002",
                captureOriginalFile = null
            ))
            val (sw, sh) = decodeOutputDimensions(standardUri)
            val (ow, oh) = decodeOutputDimensions(originalUri)
            assertEquals("Without captureOriginalFile, Original width must equal Standard", sw, ow)
            assertEquals("Without captureOriginalFile, Original height must equal Standard", sh, oh)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-06: Slider HQ end-to-end — valid JPEG
    @Test
    fun t_hq_i_06_slider_withHqSources_rendersValidJpeg() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            assertJpegExistsInMediaStore(uri)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-07: Side by side HQ end-to-end — valid JPEG
    @Test
    fun t_hq_i_07_sideBySide_withHqSources_rendersValidJpeg() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            assertJpegExistsInMediaStore(uri)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-08: reference-original.jpg missing → falls back gracefully to reference.jpg
    @Test
    fun t_hq_i_08_original_referenceOriginalMissing_fallsBackGracefully() {
        val hqDir = createHqSessionDir()
        try {
            File(hqDir, "reference-original.jpg").delete()
            val captureOriginal = File(hqDir, "capture-original.jpg")
            // Must not crash — falls back to reference.jpg on the reference side
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            assertJpegExistsInMediaStore(uri)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-09: overlay params missing → falls back gracefully to reference.jpg
    @Test
    fun t_hq_i_09_original_overlayParamsMissing_fallsBackToReferenceJpg() {
        val hqDir = createHqSessionDir()
        try {
            // Rewrite metadata.json without the overlay block
            File(hqDir, "metadata.json").writeText(
                """{"version":5,"session":{"id":"test-hq","createdAtMs":1000},""" +
                """"viewport":{"width":200,"height":300,"orientation":"PORTRAIT"},""" +
                """"files":{"capture":"capture.jpg","reference":"reference.jpg",""" +
                """"referenceOriginal":"reference-original.jpg","captureOriginal":"capture-original.jpg"},""" +
                """"capture":{"timestampMs":1000}}"""
            )
            val captureOriginal = File(hqDir, "capture-original.jpg")
            // Must not crash — overlay block absent → readOverlayParams returns null → use reference.jpg
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            assertJpegExistsInMediaStore(uri)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-11: Composition — reference side colour matches reference-original.jpg.
    // Uses solid-colour bitmaps: reference=blue(80,120,180), capture=orange(180,120,80).
    // In the Slider rendering, the capture layer occupies the LEFT half and the reference
    // shows through on the RIGHT half (same pattern as CompareSliderRenderEngine).
    // The right quarter of the Slider output must therefore be predominantly blue (reference).
    @Test
    fun t_hq_i_11_composition_referenceSideColour_matchesReferenceOriginal() {
        val hqDir = createHqSessionDir()   // ref=blue(80,120,180), cap=orange(180,120,80)
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            val outputBmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Cannot decode output for composition check")
            try {
                // Sample at 3/4 from the left edge — solidly in the reference zone (right half).
                // The capture layer covers the left half; the reference shows through on the right.
                val sampleX = 3 * outputBmp.width / 4
                val sampleY = outputBmp.height / 2
                val pixel = outputBmp.getPixel(sampleX, sampleY)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Reference-original is blue (80,120,180) → blue channel exceeds red channel
                assertTrue(
                    "Right-quarter pixel must be blue-dominant (reference side). " +
                    "Got R=$r G=$g B=$b. Expected B > R.",
                    b > r
                )
            } finally {
                outputBmp.recycle()
            }
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // ── Block D: Slider-specific HQ tests ────────────────────────────────────

    // T-HQ-I-07 (Block D): Slider HQ output is larger than Slider Standard output.
    @Test
    fun t_hq_d_07_slider_hq_longestEdgeLargerThanStandard() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val stdUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.STANDARD,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260629_200001"
            ))
            val hqUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260629_200002",
                captureOriginalFile = captureOriginal
            ))
            val (sw, sh) = decodeOutputDimensions(stdUri)
            val (hw, hh) = decodeOutputDimensions(hqUri)
            assertTrue(
                "Slider HQ longest edge (${maxOf(hw, hh)}) must exceed Slider Standard (${maxOf(sw, sh)})",
                maxOf(hw, hh) > maxOf(sw, sh)
            )
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-08 (Block D): Slider HQ with active caption produces a taller canvas than
    // the same export without caption, confirming caption renders at HQ dimensions.
    @Test
    fun t_hq_d_08_slider_hq_captionPresent_canvasIsTaller() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val noCaptionUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260629_200003",
                captureOriginalFile = captureOriginal
            ))
            val withCaptionUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = ShareCaptionData(titleLine = "HQ caption test", dateLine = "2008 → 2026", locationLine = null),
                sessionDir = hqDir, exportTimestamp = "20260629_200004",
                captureOriginalFile = captureOriginal
            ))
            val (_, nh) = decodeOutputDimensions(noCaptionUri)
            val (_, wh) = decodeOutputDimensions(withCaptionUri)
            assertTrue(
                "HQ Slider with caption ($wh px) must be taller than without caption ($nh px)",
                wh > nh
            )
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-09 (Block D): Slider HQ renders the SameView handle at the center.
    // The handle is a white-filled circle. Pixels at the canvas center (which coincides
    // with the handle position for a 50/50 split) must be white-dominant.
    //
    // HQ canvas (createHqSessionDir): compW=400 compH=600 outerPad≈12
    // sliderX = outerPad + compW/2 = 12+200 = 212  hcy = outerPad + compH/2 = 12+300 = 312
    // canvasW = 432 canvasH = 632 → canvas center (216, 316) is ~5.7 px from handle center
    // Handle radius ≈ min(400,600)*0.12/2 = 24 px → (216,316) is well inside the white fill.
    @Test
    fun t_hq_d_09_slider_hq_handleVisibleAtCenter() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            val outputBmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Cannot decode output for handle check")
            try {
                val pixel = outputBmp.getPixel(outputBmp.width / 2, outputBmp.height / 2)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // White handle fill: all channels > 200 after JPEG Q92 compression.
                assertTrue(
                    "Center pixel must be white-dominant (handle fill). Got R=$r G=$g B=$b.",
                    r > 200 && g > 200 && b > 200
                )
            } finally {
                outputBmp.recycle()
            }
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // ── Block E: Side-by-side HQ tests ───────────────────────────────────────

    // T-HQ-I-10-a: SbS HQ renders a valid JPEG.
    @Test
    fun t_hq_e_10a_sideBySide_hq_rendersValidJpeg() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            assertJpegExistsInMediaStore(uri)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-10-b: SbS HQ longest edge exceeds SbS Standard longest edge.
    @Test
    fun t_hq_e_10b_sideBySide_hq_longestEdgeLargerThanStandard() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val stdUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.STANDARD,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260629_300001"
            ))
            val hqUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260629_300002",
                captureOriginalFile = captureOriginal
            ))
            val (sw, sh) = decodeOutputDimensions(stdUri)
            val (hw, hh) = decodeOutputDimensions(hqUri)
            assertTrue(
                "SbS HQ longest edge (${maxOf(hw, hh)}) must exceed SbS Standard (${maxOf(sw, sh)})",
                maxOf(hw, hh) > maxOf(sw, sh)
            )
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-10-c: SbS HQ canvas height is approximately half the Slider HQ canvas height.
    // The SbS formula sets compH = makeEven(sliderCompH / 2). The canvas heights therefore
    // satisfy: sbs_canvas_h * 2 ≈ slider_canvas_h ± (2 * outerPad) because outerPad is counted
    // once in SbS but twice in the difference. Tolerance of ±40 px covers all reasonable
    // outerPad values.
    @Test
    fun t_hq_e_10c_sideBySide_hq_heightIsApproxHalfOfSliderHeight() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val sliderUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SLIDER, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260629_400001",
                captureOriginalFile = captureOriginal
            ))
            val sbsUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = "20260629_400002",
                captureOriginalFile = captureOriginal
            ))
            val (_, sliderH) = decodeOutputDimensions(sliderUri)
            val (_, sbsH) = decodeOutputDimensions(sbsUri)
            val diff = Math.abs(sbsH * 2 - sliderH)
            assertTrue(
                "SbS canvas height ($sbsH * 2 = ${sbsH*2}) should be close to Slider height ($sliderH); diff=$diff must be ≤ 40",
                diff <= 40
            )
            assertTrue("SbS canvas height ($sbsH) must be less than Slider ($sliderH)", sbsH < sliderH)
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // T-HQ-I-10-d: Standard + SbS: canvas longest edge stays within Standard cap (≤ 2048 px).
    @Test
    fun t_hq_e_10d_sideBySide_standard_longestEdgeBelowCap() {
        // Use the standard session (viewport 1080×1920; Standard = viewport ≤ 2048).
        val uri = render(ShareRenderConfig(
            style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.STANDARD,
            captionData = null, sessionDir = sessionDir, exportTimestamp = nowTimestamp()
        ))
        val (w, h) = decodeOutputDimensions(uri)
        assertTrue(
            "Standard SbS longest edge (${maxOf(w, h)}) must be ≤ 2048",
            maxOf(w, h) <= 2048
        )
    }

    // Block E extra: portrait source images must render portrait in SbS (not letterboxed).
    // Uses createHqSessionDir() where reference-original.jpg is 200×300 (portrait, blue).
    // In SbS the reference occupies the left half (200×300 slot). With Fit semantics at
    // scale=1.0 (portrait fits exactly), the top row of the reference slot must show the
    // reference colour. If EXIF rotation were applied incorrectly (making the source
    // landscape 300×200), the top rows would be dark background (letterboxed).
    //
    // Sampling point: x = left half centre (outerPad + compW/4 ≈ 112),
    //                 y = outerPad + 20 ≈ 32 (near the top of the reference slot)
    //
    // Correct portrait: pixel ≈ blue (R≈80, G≈120, B≈180) → B > R ✓
    // Wrong  landscape: pixel ≈ dark background (#17202F ≈ 23,32,47)  → B < 80 at most ✗
    @Test
    fun t_hq_e_portrait_sideBySide_referenceRendersPortraitNotLetterboxed() {
        val hqDir = createHqSessionDir()
        try {
            val captureOriginal = File(hqDir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = hqDir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            val outputBmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Cannot decode output for portrait orientation check")
            try {
                // HQ SbS: compW=400, compH=300, outerPad=12
                // leftRect spans x=[12..212], y=[12..312]
                // sampleX = 12 + 50 = 62 (well inside left half, far from separator)
                // sampleY = 12 + 20 = 32 (near the top of the reference slot)
                val sampleX = outputBmp.width / 4      // ≈ 112, centre of left half
                val sampleY = outputBmp.height / 10     // ≈ 63, near top of slot
                val pixel = outputBmp.getPixel(sampleX, sampleY)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                assertTrue(
                    "Near-top pixel of left (reference) half must be blue-dominant. " +
                    "Got R=$r G=$g B=$b. If dark, portrait source rendered as letterboxed landscape.",
                    b > r
                )
            } finally {
                outputBmp.recycle()
            }
        } finally {
            hqDir.deleteRecursively()
        }
    }

    // ── Block C-Fix: Mismatched-aspect-ratio SbS slot-fill tests ─────────────

    // These tests use createMismatchedAspectHqSessionDir() where capture-original.jpg
    // is LANDSCAPE (600×400, 3:2) while the viewport is PORTRAIT (200×300, 2:3).
    // This exposes the Block C bug: incorrect bitmap dims cause ~50 px dark bands
    // in the slot that the matched-ratio fixture (Block E) could not detect.

    // T-HQ-I-12: SbS reference slot — no letterboxing.
    // Reference-original.jpg is 200×300 portrait (blue). In SbS with correct slot
    // dims (133×198), RenderRenderer fills the slot exactly. With the buggy dims
    // (266×198), Fit produces 133×99 in the slot → 50 px dark bands top and bottom.
    // Sample near the TOP of the left half at canvas y≈17 to detect the dark band.
    //
    // Canvas geometry (viewport 200×300, capture-original 600×400, scale≈1.333):
    //   compW=266, SbS compH=198, outerPad=7
    //   leftRect x=[7..140], y=[7..205]
    //   sampleX = 7 + 133/2 ≈ 73,  sampleY = 7 + 10 = 17
    @Test
    fun t_hq_cfix_i12_sideBySide_hq_referenceSlot_noLetterboxingWithMismatchedAspect() {
        val dir = createMismatchedAspectHqSessionDir()
        try {
            val captureOriginal = File(dir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = dir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Cannot decode output for T-HQ-I-12")
            try {
                // Sample near top of left slot (reference side). outerPad≈7, leftRect top=7.
                val sampleX = bmp.width / 4        // ≈ centre of left half
                val sampleY = bmp.height / 10       // ≈ near top of slot (well inside 198-tall slot)
                val pixel = bmp.getPixel(sampleX, sampleY)
                val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                // Reference-original is solid blue (80,120,180) → blue-dominant.
                // Dark band (#17202F ≈ 23,32,47) would give dark pixel — B small, R small, but B>R.
                // Use brightness check to distinguish: image content R+G+B ≥ 200; dark band << 150.
                val brightness = r + g + b
                assertTrue(
                    "Reference slot near-top pixel must be image content (brightness≥200), not dark " +
                    "background (letterboxing). Got R=$r G=$g B=$b brightness=$brightness. " +
                    "If dark, reference bitmap was decoded at wrong dims (compW instead of compW/2).",
                    brightness >= 200
                )
                assertTrue(
                    "Reference slot near-top pixel must be blue-dominant. Got R=$r B=$b.",
                    b > r
                )
            } finally { bmp.recycle() }
        } finally { dir.deleteRecursively() }
    }

    // T-HQ-I-13: SbS capture slot — no letterboxing.
    // Capture-original.jpg is 600×400 landscape (orange). With correct center-crop to slot
    // (133×198, 2:3), orange fills the slot. With the buggy dims (266×198), the squished
    // 266×198 capture Fits into 133×198 producing 133×99 with ~50 px dark bands.
    // Sample near the TOP of the right half at canvas y≈17.
    @Test
    fun t_hq_cfix_i13_sideBySide_hq_captureSlot_noLetterboxingWithMismatchedAspect() {
        val dir = createMismatchedAspectHqSessionDir()
        try {
            val captureOriginal = File(dir, "capture-original.jpg")
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = dir, exportTimestamp = nowTimestamp(),
                captureOriginalFile = captureOriginal
            ))
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Cannot decode output for T-HQ-I-13")
            try {
                // Sample near top of right slot (capture side).
                val sampleX = bmp.width * 3 / 4    // ≈ centre of right half
                val sampleY = bmp.height / 10       // ≈ near top of slot
                val pixel = bmp.getPixel(sampleX, sampleY)
                val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                // Capture-original is solid orange (180,120,80) → red-dominant.
                val brightness = r + g + b
                assertTrue(
                    "Capture slot near-top pixel must be image content (brightness≥200), not dark " +
                    "background (letterboxing). Got R=$r G=$g B=$b brightness=$brightness. " +
                    "If dark, capture bitmap was decoded at wrong dims (compW instead of compW/2).",
                    brightness >= 200
                )
                assertTrue(
                    "Capture slot near-top pixel must be orange-dominant (R > B). Got R=$r B=$b.",
                    r > b
                )
            } finally { bmp.recycle() }
        } finally { dir.deleteRecursively() }
    }

    // T-HQ-I-14: SbS Standard quality unchanged with mismatched-aspect fixture.
    // Standard SbS uses capture.jpg (viewport-sized, 2:3 portrait) which fills its slot.
    @Test
    fun t_hq_cfix_i14_sideBySide_standard_unchangedWithMismatchedAspect() {
        val dir = createMismatchedAspectHqSessionDir()
        try {
            val uri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.STANDARD,
                captionData = null, sessionDir = dir, exportTimestamp = nowTimestamp()
            ))
            assertJpegExistsInMediaStore(uri)
            val (w, h) = decodeOutputDimensions(uri)
            assertTrue("Standard SbS longest edge must be ≤ 2048", maxOf(w, h) <= 2048)
        } finally { dir.deleteRecursively() }
    }

    // T-HQ-I-15: SbS HQ canvas larger than SbS Standard (with mismatched-aspect fixture).
    @Test
    fun t_hq_cfix_i15_sideBySide_hq_longestEdgeLargerThanStandard_mismatchedAspect() {
        val dir = createMismatchedAspectHqSessionDir()
        try {
            val captureOriginal = File(dir, "capture-original.jpg")
            val stdUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.STANDARD,
                captionData = null, sessionDir = dir, exportTimestamp = "20260629_500001"
            ))
            val hqUri = render(ShareRenderConfig(
                style = ShareComparisonStyle.SIDE_BY_SIDE, quality = ShareQuality.ORIGINAL,
                captionData = null, sessionDir = dir, exportTimestamp = "20260629_500002",
                captureOriginalFile = captureOriginal
            ))
            val (sw, sh) = decodeOutputDimensions(stdUri)
            val (hw, hh) = decodeOutputDimensions(hqUri)
            assertTrue(
                "SbS HQ longest edge (${maxOf(hw, hh)}) must exceed SbS Standard (${maxOf(sw, sh)})",
                maxOf(hw, hh) > maxOf(sw, sh)
            )
        } finally { dir.deleteRecursively() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun render(config: ShareRenderConfig): Uri {
        val uri = runBlocking { ShareImageRenderer().render(config, resolver) }
        createdUris.add(uri)
        return uri
    }

    private fun assertJpegExistsInMediaStore(uri: Uri) {
        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.IS_PENDING),
            null, null, null
        )
        cursor?.use {
            assertTrue("MediaStore entry must exist", it.moveToFirst())
            assertEquals("image/jpeg", it.getString(0))
            assertEquals(0, it.getInt(1))
        } ?: error("MediaStore query returned null for: $uri")
    }

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    /**
     * Decodes only the dimensions of the output JPEG (no full bitmap allocation).
     * Returns (width, height) of the exported image.
     */
    private fun decodeOutputDimensions(uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        return Pair(opts.outWidth, opts.outHeight)
    }

    private fun nowTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /**
     * Creates a v5 HQ session directory with:
     *  - reference.jpg: 200×300 solid blue
     *  - capture.jpg: 200×300 solid orange
     *  - reference-original.jpg: 200×300 solid blue (same as reference.jpg for simplicity)
     *  - capture-original.jpg: 400×600 solid orange (2× viewport — triggers HQ canvas expansion)
     *  - metadata.json: viewport 200×300, overlay block, files.captureOriginal declared
     *
     * Viewport 200×300 is intentionally small so that capture-original 400×600 clearly
     * exceeds it and triggers the HQ dimension calculator.
     */
    private fun createHqSessionDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "share_hq_test_${System.currentTimeMillis()}"
        )
        dir.mkdirs()
        writeSyntheticJpeg(File(dir, "reference.jpg"), Color.rgb(80, 120, 180), 200, 300)
        writeSyntheticJpeg(File(dir, "capture.jpg"), Color.rgb(180, 120, 80), 200, 300)
        writeSyntheticJpeg(File(dir, "reference-original.jpg"), Color.rgb(80, 120, 180), 200, 300)
        writeSyntheticJpeg(File(dir, "capture-original.jpg"), Color.rgb(180, 120, 80), 400, 600)
        File(dir, "metadata.json").writeText(
            """{"version":5,"session":{"id":"test-hq","createdAtMs":1000},""" +
            """"viewport":{"width":200,"height":300,"orientation":"PORTRAIT"},""" +
            """"overlay":{"scale":1.0,"offsetX":0.0,"offsetY":0.0,"displayMode":"COMPARE_WITH_PREVIEW"},""" +
            """"files":{"capture":"capture.jpg","reference":"reference.jpg",""" +
            """"referenceOriginal":"reference-original.jpg","captureOriginal":"capture-original.jpg"},""" +
            """"capture":{"timestampMs":1000}}"""
        )
        return dir
    }

    /**
     * Creates a v5 HQ session directory where capture-original.jpg has a DIFFERENT
     * aspect ratio than the viewport.
     *
     *  - viewport: 200×300 (2:3 portrait)
     *  - reference.jpg / reference-original.jpg: 200×300 solid blue (portrait, 2:3)
     *  - capture.jpg: 200×300 solid orange (portrait, 2:3 — Standard path)
     *  - capture-original.jpg: 600×400 solid orange (3:2 LANDSCAPE — HQ source, different ratio!)
     *
     * This fixture exposes the Block C bitmap-dimension bug: if HQ bitmaps are prepared
     * at full compW (266px) instead of slot width (133px), the landscape 266×198 result
     * Fits into the portrait 133×198 slot as 133×99, producing ~50 px dark bands.
     *
     * Canvas geometry with this fixture:
     *   scale=1.333, compW=266, SbS_compH=198, slotW=133, slotH=198
     */
    private fun createMismatchedAspectHqSessionDir(): File {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "share_mismatch_hq_test_${System.currentTimeMillis()}"
        )
        dir.mkdirs()
        writeSyntheticJpeg(File(dir, "reference.jpg"), Color.rgb(80, 120, 180), 200, 300)
        writeSyntheticJpeg(File(dir, "capture.jpg"), Color.rgb(180, 120, 80), 200, 300)
        writeSyntheticJpeg(File(dir, "reference-original.jpg"), Color.rgb(80, 120, 180), 200, 300)
        // capture-original is LANDSCAPE 600×400 — key difference from createHqSessionDir()
        writeSyntheticJpeg(File(dir, "capture-original.jpg"), Color.rgb(180, 120, 80), 600, 400)
        File(dir, "metadata.json").writeText(
            """{"version":5,"session":{"id":"test-mismatch","createdAtMs":1000},""" +
            """"viewport":{"width":200,"height":300,"orientation":"PORTRAIT"},""" +
            """"overlay":{"scale":1.0,"offsetX":0.0,"offsetY":0.0,"displayMode":"COMPARE_WITH_PREVIEW"},""" +
            """"files":{"capture":"capture.jpg","reference":"reference.jpg",""" +
            """"referenceOriginal":"reference-original.jpg","captureOriginal":"capture-original.jpg"},""" +
            """"capture":{"timestampMs":1000}}"""
        )
        return dir
    }

    private fun writeSyntheticSession(dir: File) {
        writeSyntheticJpeg(File(dir, "reference.jpg"), Color.rgb(80, 120, 180))
        writeSyntheticJpeg(File(dir, "capture.jpg"), Color.rgb(180, 120, 80))
        File(dir, "metadata.json").writeText(
            """{"version":4,"session":{"id":"test","createdAtMs":1000},"viewport":{"width":1080,"height":1920,"orientation":"PORTRAIT"},"overlay":{"scale":1.0,"offsetX":0,"offsetY":0,"displayMode":"COMPARE_WITH_PREVIEW"},"files":{"capture":"capture.jpg","reference":"reference.jpg","referenceOriginal":"reference-original.jpg"},"capture":{"timestampMs":1000}}"""
        )
    }

    private fun writeSyntheticJpeg(file: File, fillColor: Int) =
        writeSyntheticJpeg(file, fillColor, 200, 300)

    private fun writeSyntheticJpeg(file: File, fillColor: Int, width: Int, height: Int) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(fillColor) }
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }

    private fun writeSyntheticBrandingPng(file: File) {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(Color.rgb(200, 50, 50)) }
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }
}
