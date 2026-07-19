package cn.lineai.log;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public final class ErrorLogFileProvider extends ContentProvider {
    public static final String AUTHORITY = "cn.lineai.errorlogs";

    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".errorlogs")
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
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
        File dir = new ErrorLogRepository(context.getFilesDir().getAbsolutePath()).directory();
        File file = new File(dir, uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment());
        try {
            String canonicalDir = dir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalDir) || !file.isFile()) {
                throw new FileNotFoundException("Log not found");
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
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
