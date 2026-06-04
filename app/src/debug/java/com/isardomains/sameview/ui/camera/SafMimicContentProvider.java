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
 * Test-only ContentProvider simulating a SAF document provider (authority ≠ "media").
 *
 * This is a pure-Java class (no Kotlin stdlib) so it can run in the test APK's own process
 * without depending on the app APK's Kotlin runtime. android:exported="true" in the test
 * manifest is required so the app process (different UID from the test APK package) can
 * reach this provider via ContentResolver.
 *
 * Behaviour:
 * - Serves any file whose absolute path is in the "path" query parameter.
 * - Throws SecurityException if the URI contains "require_original", reproducing the real-device
 *   behaviour of com.android.providers.media.documents when MediaStore.setRequireOriginal() is
 *   incorrectly applied to a document URI.
 */
public class SafMimicContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.isardomains.sameview.test.safmimic";
    private static final String PARAM_PATH = "path";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        rejectRequireOriginal(uri);
        String path = uri.getQueryParameter(PARAM_PATH);
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
        rejectRequireOriginal(uri);
        String path = uri.getQueryParameter(PARAM_PATH);
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

    private void rejectRequireOriginal(Uri uri) {
        if (uri.getQueryParameter("require_original") != null) {
            throw new SecurityException(
                    "Test SAF provider: setRequireOriginal is not supported for document URIs — "
                    + "use ACTION_OPEN_DOCUMENT to obtain a document URI instead.");
        }
    }

    public static Uri uriFor(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .path("/image")
                .appendQueryParameter(PARAM_PATH, file.getAbsolutePath())
                .build();
    }
}
