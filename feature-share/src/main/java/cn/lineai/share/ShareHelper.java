package cn.lineai.share;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.ClipboardManager;
import android.net.Uri;
import android.widget.Toast;
import java.io.File;

public final class ShareHelper {

    private ShareHelper() {}

    public static void shareFile(Context context, File file, String mimeType) {
        Uri uri = ShareFileProvider.uriFor(context, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newRawUri("", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Share file"));
    }

    public static void shareText(Context context, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, "Share text"));
    }

    public static void copy(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("chat", text));
        }
    }
}
