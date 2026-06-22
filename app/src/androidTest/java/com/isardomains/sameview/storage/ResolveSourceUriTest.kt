// path: app/src/androidTest/java/com/isardomains/sameview/storage/ResolveSourceUriTest.kt
package com.isardomains.sameview.storage

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isardomains.sameview.ui.camera.SessionStorage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for [SessionStorage.resolveSourceUri].
 *
 * Verifies that Photo Picker URIs (authority "media", path "/picker/...") are
 * never modified with ?requireOriginal=1 — the picker provider rejects such URIs
 * in openInputStream on Android 16, causing session save to fail.
 */
@RunWith(AndroidJUnit4::class)
class ResolveSourceUriTest {

    // Typical Android 13+ Photo Picker URI from the default media picker.
    private val pickerUri = Uri.parse(
        "content://media/picker/0/com.android.providers.media.photopicker/media/1000121954"
    )

    // Picker URI as observed in the real crash log.
    private val pickerUriFromCrash = Uri.parse(
        "content://media/picker/0/com.android.providers.media.photopicker/media/1000121954"
    )

    @Test
    fun pickerUri_returnsUnchanged_noRequireOriginalAppended() {
        val result = SessionStorage.resolveSourceUri(pickerUri)
        assertSame("Picker URI must be returned as-is (same object)", pickerUri, result)
        assertFalse(
            "Picker URI must not contain ?requireOriginal=1",
            result.toString().contains("requireOriginal")
        )
    }

    @Test
    fun pickerUri_fromCrashLog_returnsUnchanged() {
        val result = SessionStorage.resolveSourceUri(pickerUriFromCrash)
        assertFalse(
            "Crash-log picker URI must not have requireOriginal appended",
            result.toString().contains("requireOriginal")
        )
    }

    @Test
    fun pickerUri_differentUserId_returnsUnchanged() {
        val uri = Uri.parse(
            "content://media/picker/1/com.android.providers.media.photopicker/media/9999"
        )
        val result = SessionStorage.resolveSourceUri(uri)
        assertFalse(result.toString().contains("requireOriginal"))
    }

    @Test
    fun safUri_nonMediaAuthority_returnsUnchanged() {
        val safUri = Uri.parse(
            "content://com.android.providers.media.documents/document/image%3A123"
        )
        val result = SessionStorage.resolveSourceUri(safUri)
        assertSame(safUri, result)
    }

    @Test
    fun fileUri_returnsUnchanged() {
        val fileUri = Uri.parse("file:///data/local/tmp/test.jpg")
        val result = SessionStorage.resolveSourceUri(fileUri)
        assertSame(fileUri, result)
    }

    @Test
    fun classicMediaStoreUri_pathNotPicker_isNotTreatedAsPicker() {
        // content://media/external/images/media/123 — classic MediaStore URI.
        // setRequireOriginal may or may not succeed on the test device, but the result
        // must NOT contain a "/picker/" path and must not be null.
        val mediaUri = Uri.parse("content://media/external/images/media/123")
        val result = SessionStorage.resolveSourceUri(mediaUri)
        assertFalse(
            "Classic MediaStore URI path must not start with /picker/",
            result.path?.startsWith("/picker/") == true
        )
    }
}
