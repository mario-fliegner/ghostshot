// path: app/src/test/java/com/isardomains/sameview/ui/camera/ResolveExtensionForMimeTypeTest.kt
package com.isardomains.sameview.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [SessionStorage.resolveExtensionForMimeType] covering all MIME-type mappings
 * defined in SESSION_ORIGINALS_V1.md §6.2.
 */
class ResolveExtensionForMimeTypeTest {

    @Test fun jpeg_mapsToDotJpg() =
        assertEquals(".jpg", SessionStorage.resolveExtensionForMimeType("image/jpeg"))

    @Test fun heic_mapsToDotHeic() =
        assertEquals(".heic", SessionStorage.resolveExtensionForMimeType("image/heic"))

    @Test fun heif_normalizedToDotHeic() =
        assertEquals(".heic", SessionStorage.resolveExtensionForMimeType("image/heif"))

    @Test fun png_mapsToDotPng() =
        assertEquals(".png", SessionStorage.resolveExtensionForMimeType("image/png"))

    @Test fun webp_mapsToDotWebp() =
        assertEquals(".webp", SessionStorage.resolveExtensionForMimeType("image/webp"))

    @Test fun gif_mapsToDotGif() =
        assertEquals(".gif", SessionStorage.resolveExtensionForMimeType("image/gif"))

    @Test fun avif_mapsToDotAvif() =
        assertEquals(".avif", SessionStorage.resolveExtensionForMimeType("image/avif"))

    @Test fun bmp_mapsToDotBmp() =
        assertEquals(".bmp", SessionStorage.resolveExtensionForMimeType("image/bmp"))

    @Test fun null_mapsToDotBin() =
        assertEquals(".bin", SessionStorage.resolveExtensionForMimeType(null))

    @Test fun emptyString_mapsToDotBin() =
        assertEquals(".bin", SessionStorage.resolveExtensionForMimeType(""))

    @Test fun unknownMimeType_mapsToDotBin() =
        assertEquals(".bin", SessionStorage.resolveExtensionForMimeType("image/unknown-format"))

    @Test fun applicationOctetStream_mapsToDotBin() =
        assertEquals(".bin", SessionStorage.resolveExtensionForMimeType("application/octet-stream"))

    @Test fun uppercaseJpeg_isCaseNormalized() =
        assertEquals(".jpg", SessionStorage.resolveExtensionForMimeType("IMAGE/JPEG"))

    @Test fun mixedCaseHeic_isCaseNormalized() =
        assertEquals(".heic", SessionStorage.resolveExtensionForMimeType("Image/Heic"))
}
