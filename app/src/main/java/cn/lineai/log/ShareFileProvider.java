package cn.lineai.log;

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

public final class ShareFileProvider extends ContentProvider {

    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".fileprovider")
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.endsWith(".md")) return "application/octet-stream";
        if (name != null && name.endsWith(".pdf")) return "application/pdf";
        if (name != null && name.endsWith(".png")) return "image/png";
        if (name != null && name.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) {
            throw new FileNotFoundException("Read only");
        }
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("No context");
        }
        File dir = new File(context.getCacheDir(), "share");
        File file = new File(dir, uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment());
        try {
            String canonicalDir = dir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalDir) || !file.isFile()) {
                throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // QQ and other apps call query() to get file name and size before reading
        Context context = getContext();
        if (context == null) return null;
        File dir = new File(context.getCacheDir(), "share");
        String segment = uri.getLastPathSegment();
        if (segment == null) return null;
        File file = new File(dir, segment);
        if (!file.isFile()) return null;

        String[] cols = (projection != null) ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = file.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
