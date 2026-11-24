package com.hideurl.browser;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateApkProvider extends ContentProvider {
    static File apk(Context context) { return new File(context.getCacheDir(), "verified-update.apk"); }
    static Uri uri(Context context) {
        return Uri.parse("content://" + context.getPackageName() + ".update/apk");
    }

    @Override public boolean onCreate() { return true; }

    private File resolve(Uri requested) {
        if (!uri(getContext()).equals(requested)) throw new IllegalArgumentException("Unknown update");
        return apk(getContext());
    }

    @Override public String getType(Uri request) {
        resolve(request);
        return "application/vnd.android.package-archive";
    }

    @Override public ParcelFileDescriptor openFile(Uri request, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(request), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri request, String[] projection, String selection, String[] args, String order) {
        File file = resolve(request);
        String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add("Hide-URL-Browser.apk");
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
