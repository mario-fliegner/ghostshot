package com.isardomains.sameview.ui.camera

import android.content.ContentResolver
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.isardomains.sameview.BuildConfig
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Writes GPS EXIF tags to JPEGs via [ExifInterface].
 *
 * Invariants:
 * - [ExifInterface.TAG_ORIENTATION] is never touched.
 * - [writeGpsToFile] and [writeGpsToUri] are fail-soft: they never throw.
 * - GPS must only be written when recreation guidance is enabled (enforced by callers).
 */
internal object GpsExifWriter {

    private const val TAG = "GpsExifWriter"

    /**
     * Writes GPS tags to [exif]. Does NOT call [ExifInterface.saveAttributes] — caller
     * is responsible. Does NOT touch [ExifInterface.TAG_ORIENTATION].
     */
    fun writeGps(exif: ExifInterface, snapshot: GpsSnapshot) {
        val lat = snapshot.latitude
        val lon = snapshot.longitude
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0.0) "N" else "S")
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDmsRationalString(abs(lat)))
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0.0) "E" else "W")
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDmsRationalString(abs(lon)))
        val alt = snapshot.altitude
        if (alt != null) {
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt >= 0.0) "0" else "1")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(abs(alt) * 1000.0).roundToLong()}/1000")
        }
    }

    /**
     * Opens [file], writes GPS tags, saves. Fail-soft — never throws.
     * Preserves all existing EXIF tags including ORIENTATION.
     */
    fun writeGpsToFile(file: File, snapshot: GpsSnapshot) {
        try {
            ExifInterface(file.absolutePath).apply {
                writeGps(this, snapshot)
                saveAttributes()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) { Log.w(TAG, "GPS write failed for ${file.name}: ${e.message}") }
        }
    }

    /**
     * Opens [uri] via [resolver], writes GPS tags, saves. Fail-soft — never throws.
     * Preserves all existing EXIF tags including ORIENTATION.
     */
    fun writeGpsToUri(resolver: ContentResolver, uri: Uri, snapshot: GpsSnapshot) {
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                ExifInterface(pfd.fileDescriptor).apply {
                    writeGps(this, snapshot)
                    saveAttributes()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) { Log.w(TAG, "GPS write failed for $uri: ${e.message}") }
        }
    }

    /**
     * Converts a non-negative decimal degree value to an EXIF DMS rational string
     * in the format "deg/1,min/1,secMicros/1000000".
     *
     * Rounding at second boundaries is normalised upward (60 sec → 1 min, 60 min → 1 deg)
     * to avoid out-of-range EXIF values near poles or at minute/degree boundaries.
     */
    internal fun toDmsRationalString(absDegrees: Double): String {
        val deg = floor(absDegrees).toLong()
        val minFull = (absDegrees - deg.toDouble()) * 60.0
        val min = floor(minFull).toLong()
        val secFull = (minFull - min.toDouble()) * 60.0
        var secNum = (secFull * 1_000_000.0).roundToLong()
        var normalizedMin = min
        if (secNum >= 60_000_000L) {
            secNum -= 60_000_000L
            normalizedMin++
        }
        var normalizedDeg = deg
        if (normalizedMin >= 60L) {
            normalizedMin -= 60L
            normalizedDeg++
        }
        return "$normalizedDeg/1,$normalizedMin/1,$secNum/1000000"
    }
}
