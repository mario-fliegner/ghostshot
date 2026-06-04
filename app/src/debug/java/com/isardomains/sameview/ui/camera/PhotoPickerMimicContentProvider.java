package com.isardomains.sameview.ui.camera;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Test-only ContentProvider simulating the Photo Picker / MediaStore split-source behaviour
 * that causes the double-rotation format-mismatch bug.
 *
 * This is a pure-Java class (no Kotlin stdlib) so it can run in the test APK's own process
 * without depending on the app APK's Kotlin runtime. android:exported="true" in the test
 * manifest is required so the app process (different UID from the test APK package) can
 * reach this provider via ContentResolver.
 *
 * Behaviour:
 * - URI without "require_original" query param → serves the pre-rotated file
 * - URI with "require_original=1" (appended by MediaStore.setRequireOriginal) → serves original
 */
public class PhotoPickerMimicContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.isardomains.sameview.test.photopickermimicmedia";
    private static final String PARAM_ORIGINAL_PATH = "original_path";
    private static final String PARAM_PREROTATED_PATH = "prerotated_path";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        boolean useOriginal = uri.getQueryParameter("require_original") != null;
        String path = useOriginal
                ? uri.getQueryParameter(PARAM_ORIGINAL_PATH)
                : uri.getQueryParameter(PARAM_PREROTATED_PATH);
        if (path == null) {
            throw new FileNotFoundException("Missing path parameter in URI: " + uri);
        }
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + path);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        String path = uri.getQueryParameter(PARAM_ORIGINAL_PATH);
        if (path == null) return null;
        File file = new File(path);
        MatrixCursor cursor = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }

    public static Uri uriFor(File originalFile, File prerotatedFile) {
        // require_original=1 is pre-embedded so the provider always serves the original file.
        // MediaStore.setRequireOriginal() on Android 16+ rejects non-MediaStore authorities
        // and throws IllegalArgumentException; resolveSourceUri() catches that and returns the
        // URI unchanged — so the flag must already be present for openFile() to see it.
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path("/image")
                .appendQueryParameter(PARAM_ORIGINAL_PATH, originalFile.getAbsolutePath())
                .appendQueryParameter(PARAM_PREROTATED_PATH, prerotatedFile.getAbsolutePath())
                .appendQueryParameter("require_original", "1")
                .build();
    }
}
