package com.isardomains.sameview.ui.camera

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.isardomains.sameview.BuildConfig

object ReferenceImageMetadataReader {

    /**
     * Reads image dimensions and EXIF metadata (orientation, GPS) from the given URI.
     *
     * Both the bounds read and the EXIF/GPS read use the same resolved source URI so that
     * rawWidth/rawHeight and exifOrientation always come from the same underlying file.
     *
     * For Photo Picker URIs (authority in [requireOriginalAuthorities]) the original
     * unmodified file is requested via [MediaStore.setRequireOriginal]. Without this,
     * MediaStore may serve a transcoded pre-rotated JPEG for [ContentResolver.openInputStream]
     * while [ContentResolver.openFileDescriptor] returns the original HEIC — the raw dims
     * would be already-portrait but exifOrientation would say ROTATE_90, doubling the rotation
     * and producing a false format-mismatch hint.
     *
     * @param requireOriginalAuthorities URI authorities for which [MediaStore.setRequireOriginal]
     *   is applied. Defaults to `{"media"}` (the Photo Picker / MediaStore authority). Override
     *   only in tests that simulate the Photo Picker split-source scenario.
     */
    fun read(
        uri: Uri,
        resolver: ContentResolver,
        requireOriginalAuthorities: Set<String> = setOf("media")
    ): ReferenceImageMetadata? {
        // Resolve the source URI once. For Photo Picker URIs this requests the original
        // file so both the bounds read and the EXIF read come from the same bytes.
        val sourceUri = resolveSourceUri(uri, requireOriginalAuthorities)

        // Bounds — use sourceUri. If that fails and sourceUri differs from uri (i.e.
        // setRequireOriginal was applied but the provider rejected it for openInputStream),
        // fall back to the plain uri and update effectiveUri so EXIF stays consistent.
        var effectiveUri = sourceUri
        var opts = readBounds(resolver, sourceUri)
        if (opts == null && sourceUri !== uri) {
            opts = readBounds(resolver, uri)
            effectiveUri = uri
        }
        if (opts == null) return null

        // Orientation + GPS — FileDescriptor gives ExifInterface random access, which is
        // required for reliable HEIC metadata parsing. The InputStream-based path returns
        // wrong GPS values for Samsung HEIC files (GPS IFD detected but DMS conversion
        // silently returns 0/0 without random access).
        var exifOrientation = ExifInterface.ORIENTATION_UNDEFINED
        var gpsLatitude: Double? = null
        var gpsLongitude: Double? = null
        var gpsAltitude: Double? = null
        try {
            resolver.openFileDescriptor(effectiveUri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)

                exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )

                val latLong = FloatArray(2)
                val coords: Pair<Double, Double>? =
                    if (exif.getLatLong(latLong) && !(latLong[0] == 0f && latLong[1] == 0f)) {
                        Pair(latLong[0].toDouble(), latLong[1].toDouble())
                    } else {
                        // getLatLong() returned false or landed on 0/0. Fall back to manual
                        // DMS-rational parsing of the raw EXIF tags as a second line of defence.
                        parseRawGpsCoordinates(exif)
                    }
                if (coords != null) {
                    gpsLatitude = coords.first
                    gpsLongitude = coords.second
                }

                gpsAltitude = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() }
                    ?: parseRawAltitude(exif)
            }
        } catch (_: Exception) {
            // exifOrientation stays UNDEFINED, GPS stays null — image is still usable.
        }

        if (BuildConfig.DEBUG) {
            Log.d("SameView.GPS", "EXIF read: hasGps=${gpsLatitude != null} lat=$gpsLatitude lon=$gpsLongitude altPresent=${gpsAltitude != null}")
        }

        val isRotated = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE

        return ReferenceImageMetadata(
            rawWidth = opts.outWidth,
            rawHeight = opts.outHeight,
            orientedWidth = if (isRotated) opts.outHeight else opts.outWidth,
            orientedHeight = if (isRotated) opts.outWidth else opts.outHeight,
            exifOrientation = exifOrientation,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsAltitude = gpsAltitude
        )
    }

    /**
     * Returns a URI that points to the original unmodified file for authorities in
     * [requireOriginalAuthorities], or the input [uri] unchanged for all other cases.
     * Catches all exceptions from [MediaStore.setRequireOriginal] and falls back to [uri].
     */
    private fun resolveSourceUri(uri: Uri, requireOriginalAuthorities: Set<String>): Uri {
        if (uri.scheme != "content" || uri.authority !in requireOriginalAuthorities) return uri
        return try {
            MediaStore.setRequireOriginal(uri)
        } catch (_: UnsupportedOperationException) {
            uri
        } catch (_: SecurityException) {
            uri
        } catch (_: IllegalArgumentException) {
            uri
        }
    }

    /**
     * Attempts to read image dimensions from [uri] via BitmapFactory in bounds-only mode.
     * Returns a populated [BitmapFactory.Options] on success, or null on any failure.
     */
    private fun readBounds(resolver: ContentResolver, uri: Uri): BitmapFactory.Options? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            if (opts.outWidth > 0 && opts.outHeight > 0) opts else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses "deg/den,min/den,sec/den" DMS-rational EXIF strings as produced by camera apps.
     * Samsung HEIC uses full-precision seconds, e.g. "45/1,28/1,32718719/1000000".
     */
    private fun parseDmsRational(dms: String): Double? {
        return try {
            val parts = dms.split(",")
            if (parts.size != 3) return null
            fun rational(s: String): Double {
                val slash = s.indexOf('/')
                if (slash < 0) return s.trim().toDouble()
                val num = s.substring(0, slash).trim().toLong()
                val den = s.substring(slash + 1).trim().toLong()
                if (den == 0L) return 0.0
                return num.toDouble() / den.toDouble()
            }
            val deg = rational(parts[0])
            val min = rational(parts[1])
            val sec = rational(parts[2])
            deg + min / 60.0 + sec / 3600.0
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRawGpsCoordinates(exif: ExifInterface): Pair<Double, Double>? {
        val latStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) ?: return null
        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) ?: return null
        val lonStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) ?: return null
        val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) ?: return null

        val lat = parseDmsRational(latStr) ?: return null
        val lon = parseDmsRational(lonStr) ?: return null

        val signedLat = if (latRef.uppercase() == "S") -lat else lat
        val signedLon = if (lonRef.uppercase() == "W") -lon else lon

        // Null Island (0/0) is almost certainly a parsing artefact, not a real coordinate.
        if (signedLat == 0.0 && signedLon == 0.0) return null
        return Pair(signedLat, signedLon)
    }

    private fun parseRawAltitude(exif: ExifInterface): Double? {
        val altStr = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) ?: return null
        val altRef = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF)
        return try {
            val slash = altStr.indexOf('/')
            val alt = if (slash >= 0) {
                val den = altStr.substring(slash + 1).trim().toLong()
                if (den == 0L) return null
                altStr.substring(0, slash).trim().toLong().toDouble() / den.toDouble()
            } else {
                altStr.trim().toDouble()
            }
            if (altRef == "1") -alt else alt
        } catch (_: Exception) {
            null
        }
    }

}
