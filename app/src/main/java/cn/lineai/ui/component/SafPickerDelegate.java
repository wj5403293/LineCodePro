package cn.lineai.ui.component;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/**
 * Encapsulates the Storage Access Framework pickers used by the chat workspace.
 *
 * <p>Centralises the three pick flows the app needs:</p>
 * <ul>
 *     <li>{@link Intent#ACTION_OPEN_DOCUMENT_TREE openDocumentTree} — used when the user wants
 *         to point the workspace at a SAF tree (e.g. an external project).</li>
 *     <li>{@link Intent#ACTION_OPEN_DOCUMENT openDocument} — used to import a single
 *         user-visible file (e.g. a {@code .linecode} archive).</li>
 *     <li>{@link Intent#ACTION_CREATE_DOCUMENT createDocument} — used to export a single
 *         user-visible file (e.g. a {@code .linecode} archive).</li>
 * </ul>
 *
 * <p>The delegate also resolves the chosen URIs' display names and takes persistable URI
 * permissions as appropriate, and routes activity results back to whichever callback was
 * registered when the pick started. Only one of each type of callback can be in-flight at
 * any given time; starting a new pick overwrites the previous callback.</p>
 *
 * <p>Hosts must call {@link #onActivityResult(int, int, Intent)} from their
 * {@code onActivityResult} override, and
 * {@link #onRequestPermissionsResult(int, int[])} is a no-op hook (kept for symmetry) so
 * the host can simply forward both callbacks to this class.</p>
 */
public final class SafPickerDelegate {

    /** Request code for {@code ACTION_OPEN_DOCUMENT_TREE}. */
    public static final int REQUEST_OPEN_DOCUMENT_TREE = 7001;
    /** Request code for {@code ACTION_OPEN_DOCUMENT}. */
    public static final int REQUEST_OPEN_DOCUMENT = 7003;
    /** Request code for {@code ACTION_CREATE_DOCUMENT}. */
    public static final int REQUEST_CREATE_DOCUMENT = 7004;

    /** Callback for a successful or cancelled tree pick. */
    public interface TreePickCallback {
        void onTreePicked(String uri);
        void onCancelled();
    }

    /** Callback for a successful or cancelled single-document pick. */
    public interface DocumentPickCallback {
        void onDocumentPicked(String uri, String displayName);
        void onCancelled();
    }

    /** Callback for a successful or cancelled create-document pick. */
    public interface DocumentCreateCallback {
        void onDocumentCreated(String uri, String displayName);
        void onCancelled();
    }

    private final Activity activity;
    private TreePickCallback treeCallback;
    private DocumentPickCallback documentPickCallback;
    private DocumentCreateCallback documentCreateCallback;

    public SafPickerDelegate(Activity activity) {
        this.activity = activity;
    }

    /**
     * Launch the system "Open a directory tree" picker. The tree's URI is granted persistent
     * read/write permission when the user grants the temporary flags.
     */
    public void openDocumentTree(TreePickCallback callback) {
        this.treeCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE);
    }

    /**
     * Launch the system "Open a file" picker.
     *
     * @param mimeType  desired MIME type, or {@code null}/empty to fall back to {@code * / *}.
     * @param extensions optional list of file extensions used to populate
     *                   {@link Intent#EXTRA_TITLE}; the first entry (if any) is used.
     * @param callback  invoked with the picked URI and display name, or {@link
     *                  DocumentPickCallback#onCancelled()} on cancel / no-data.
     */
    public void openDocument(String mimeType, String[] extensions, DocumentPickCallback callback) {
        this.documentPickCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType == null || mimeType.length() == 0 ? "*/*" : mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (extensions != null && extensions.length > 0) {
            intent.putExtra(Intent.EXTRA_TITLE, extensions[0]);
        }
        activity.startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
    }

    /**
     * Launch the system "Create a file" picker. Both read and write persistable permissions
     * are taken when granted.
     *
     * @param mimeType    desired MIME type, or {@code null}/empty for
     *                    {@code application/octet-stream}.
     * @param displayName suggested file name; {@code null}/empty defaults to
     *                    {@code LineCode.linecode}.
     * @param callback    invoked with the resulting URI and display name, or
     *                    {@link DocumentCreateCallback#onCancelled()} on cancel.
     */
    public void createDocument(String mimeType, String displayName, DocumentCreateCallback callback) {
        this.documentCreateCallback = callback;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType == null || mimeType.length() == 0
                ? "application/octet-stream" : mimeType);
        intent.putExtra(Intent.EXTRA_TITLE,
                displayName == null || displayName.length() == 0
                        ? "LineCode.linecode" : displayName);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_CREATE_DOCUMENT);
    }

    /**
     * Forward {@code onActivityResult} from the hosting activity. Returns {@code true} if
     * the request was one of this delegate's; the host can use that to skip further
     * dispatching.
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OPEN_DOCUMENT) {
            handleDocumentResult(resultCode, data);
            return true;
        }
        if (requestCode == REQUEST_CREATE_DOCUMENT) {
            handleCreateDocumentResult(resultCode, data);
            return true;
        }
        if (requestCode == REQUEST_OPEN_DOCUMENT_TREE) {
            handleTreeResult(resultCode, data);
            return true;
        }
        return false;
    }

    /**
     * Hook kept for hosts that want a single place to forward permission results; this
     * delegate does not itself issue runtime permission requests, so this is a no-op.
     */
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        // Intentionally empty: SAF does not require runtime permissions on modern Android.
    }

    private void handleTreeResult(int resultCode, Intent data) {
        TreePickCallback callback = treeCallback;
        treeCallback = null;
        if (callback == null) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            callback.onCancelled();
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags();
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            takePersistableReadPermission(uri);
        }
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            takePersistableWritePermission(uri);
        }
        callback.onTreePicked(uri.toString());
    }

    private void handleDocumentResult(int resultCode, Intent data) {
        DocumentPickCallback callback = documentPickCallback;
        documentPickCallback = null;
        if (callback == null) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            callback.onCancelled();
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags != 0) {
            takePersistableReadPermission(uri);
        }
        callback.onDocumentPicked(uri.toString(), displayName(uri));
    }

    private void handleCreateDocumentResult(int resultCode, Intent data) {
        DocumentCreateCallback callback = documentCreateCallback;
        documentCreateCallback = null;
        if (callback == null) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            callback.onCancelled();
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags();
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            takePersistableReadPermission(uri);
        }
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            takePersistableWritePermission(uri);
        }
        callback.onDocumentCreated(uri.toString(), displayName(uri));
    }

    private void takePersistableReadPermission(Uri uri) {
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    private void takePersistableWritePermission(Uri uri) {
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    private String displayName(Uri uri) {
        if (uri == null) {
            return "";
        }
        ContentResolver resolver = activity.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && name.length() > 0) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String path = uri.getLastPathSegment();
        return path == null ? "skill.zip" : path;
    }
}
