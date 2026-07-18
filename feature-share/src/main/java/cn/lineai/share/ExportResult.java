package cn.lineai.share;

import android.net.Uri;
import java.io.File;

public final class ExportResult {
    public static final int ACTION_SHARE_FILE = 1;
    public static final int ACTION_CLIPBOARD = 2;
    public static final int ACTION_SHARE_TEXT = 3;

    private final File file;
    private final String mimeType;
    private final int action;
    private final String content;
    private final Uri uri;

    private ExportResult(int action, File file, String mimeType, String content, Uri uri) {
        this.action = action;
        this.file = file;
        this.mimeType = mimeType;
        this.content = content;
        this.uri = uri;
    }

    public static ExportResult forFile(File file, String mimeType, Uri uri) {
        return new ExportResult(ACTION_SHARE_FILE, file, mimeType, null, uri);
    }

    public static ExportResult forClipboard(String text) {
        return new ExportResult(ACTION_CLIPBOARD, null, null, text, null);
    }

    public static ExportResult forShareText(String text) {
        return new ExportResult(ACTION_SHARE_TEXT, null, null, text, null);
    }

    public int getAction() {
        return action;
    }

    public File getFile() {
        return file;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getContent() {
        return content;
    }

    public Uri getUri() {
        return uri;
    }
}
