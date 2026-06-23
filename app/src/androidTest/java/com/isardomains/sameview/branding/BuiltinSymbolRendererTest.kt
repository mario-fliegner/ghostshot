package com.isardomains.sameview.branding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies that BuiltinSymbolRenderer produces a valid 512×512 RGBA PNG for every
 * built-in symbol, and that the output is metadata-clean.
 *
 * Block 1 — SESSION_BRANDING_V1.md §6.3 and §7.5
 * Instrumentation test: Context and Bitmap APIs require Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class BuiltinSymbolRendererTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val tempFile by lazy { File(appContext.cacheDir, "builtin_symbol_test_output.png") }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    // ── I-01: each symbol renders to a valid 512×512 RGBA PNG ─────────────────

    @Test
    fun render_heart_producesValid512x512RgbaPng() = assertSymbolOutput(BuiltinBrandingSymbol.HEART)

    @Test
    fun render_star_producesValid512x512RgbaPng() = assertSymbolOutput(BuiltinBrandingSymbol.STAR)

    @Test
    fun render_camera_producesValid512x512RgbaPng() = assertSymbolOutput(BuiltinBrandingSymbol.CAMERA)

    @Test
    fun render_home_producesValid512x512RgbaPng() = assertSymbolOutput(BuiltinBrandingSymbol.HOME)

    @Test
    fun render_pin_producesValid512x512RgbaPng() = assertSymbolOutput(BuiltinBrandingSymbol.PIN)

    @Test
    fun render_fire_producesValid512x512RgbaPng() = assertSymbolOutput(BuiltinBrandingSymbol.FIRE)

    // ── N-11: built-in symbol output is metadata-clean ────────────────────────

    @Test
    fun render_anySymbol_outputPngHasNoGpsOrMakeModel() {
        val output = BuiltinSymbolRenderer.render(appContext, BuiltinBrandingSymbol.HEART)
        tempFile.writeBytes(output)

        val exif = ExifInterface(tempFile.absolutePath)
        val latLon = FloatArray(2)
        assertTrue(
            "Built-in symbol PNG must not contain GPS coordinates",
            !exif.getLatLong(latLon)
        )
        assertNull(
            "Built-in symbol PNG must not contain TAG_MAKE",
            exif.getAttribute(ExifInterface.TAG_MAKE)
        )
        assertNull(
            "Built-in symbol PNG must not contain TAG_GPS_LATITUDE",
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        )
    }

    // ── BuiltinBrandingSymbol.fromId ──────────────────────────────────────────

    @Test
    fun fromId_knownIds_returnCorrectSymbols() {
        assertEquals(BuiltinBrandingSymbol.HEART, BuiltinBrandingSymbol.fromId("heart"))
        assertEquals(BuiltinBrandingSymbol.STAR, BuiltinBrandingSymbol.fromId("star"))
        assertEquals(BuiltinBrandingSymbol.CAMERA, BuiltinBrandingSymbol.fromId("camera"))
        assertEquals(BuiltinBrandingSymbol.HOME, BuiltinBrandingSymbol.fromId("home"))
        assertEquals(BuiltinBrandingSymbol.PIN, BuiltinBrandingSymbol.fromId("pin"))
        assertEquals(BuiltinBrandingSymbol.FIRE, BuiltinBrandingSymbol.fromId("fire"))
    }

    @Test
    fun fromId_unknownId_returnsNull() {
        assertNull(BuiltinBrandingSymbol.fromId("unknown_symbol"))
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun assertSymbolOutput(symbol: BuiltinBrandingSymbol) {
        val output = BuiltinSymbolRenderer.render(appContext, symbol)

        assertTrue("Output for '${symbol.id}' must not be empty", output.isNotEmpty())

        val decoded: Bitmap? = BitmapFactory.decodeByteArray(output, 0, output.size)
        assertNotNull("Output for '${symbol.id}' must decode to a Bitmap", decoded)
        decoded!!

        assertEquals("Width for '${symbol.id}' must be 512", 512, decoded.width)
        assertEquals("Height for '${symbol.id}' must be 512", 512, decoded.height)
        assertEquals("Config for '${symbol.id}' must be ARGB_8888", Bitmap.Config.ARGB_8888, decoded.config)

        // The VectorDrawable is drawn with a non-transparent fill (#17202F).
        // The centre pixel must therefore be non-transparent.
        val centre = decoded.getPixel(256, 256)
        assertEquals(
            "Centre pixel for '${symbol.id}' must be fully opaque",
            255, Color.alpha(centre)
        )
        decoded.recycle()
    }
}
