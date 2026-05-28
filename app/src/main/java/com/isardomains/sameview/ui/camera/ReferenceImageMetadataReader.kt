package com.isardomains.sameview.ui.camera

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.isardomains.sameview.BuildConfig

object ReferenceImageMetadataReader {

    fun read(uri: Uri, resolver: ContentResolver): ReferenceImageMetadata? {
        // Bounds — InputStream is sufficient; BitmapFactory only needs sequential read.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsRead = try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
        if (!boundsRead || opts.outWidth <= 0 || opts.outHeight <= 0) {
            return null
        }

        // Apply setRequireOriginal only for genuine MediaStore URIs (authority == "media").
        // setRequireOriginal() is a pure URI string operation that appends ?require_original=1 —
        // it never throws for SAF/DocumentProvider URIs such as
        // com.android.providers.media.documents. However, when the modified URI is then passed to
        // openFileDescriptor() the document provider throws SecurityException because the
        // require_original flag requires a MediaStore-granted URI, not a document grant.
        // That SecurityException is silently swallowed by the outer catch block below, which
        // leaves GPS null even though the normal FD path would have worked perfectly.
        // Catch SecurityException as a safety net for any edge case where this guard is insufficient.
        val exifUri: Uri = if (uri.scheme == "content" && uri.authority == "media") {
            try {
                MediaStore.setRequireOriginal(uri)
            } catch (_: UnsupportedOperationException) {
                uri
            } catch (_: SecurityException) {
                uri
            }
        } else {
            uri
        }

        // Orientation + GPS — FileDescriptor gives ExifInterface random access, which is
        // required for reliable HEIC metadata parsing. The InputStream-based path returns
        // wrong GPS values for Samsung HEIC files (GPS IFD detected but DMS conversion
        // silently returns 0/0 without random access).
        var exifOrientation = ExifInterface.ORIENTATION_UNDEFINED
        var gpsLatitude: Double? = null
        var gpsLongitude: Double? = null
        var gpsAltitude: Double? = null
        try {
            resolver.openFileDescriptor(exifUri, "r")?.use { pfd ->
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
