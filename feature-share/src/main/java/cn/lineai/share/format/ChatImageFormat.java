package cn.lineai.share.format;

import android.content.Context;
import android.net.Uri;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ExportFormat;
import cn.lineai.share.ExportResult;
import cn.lineai.share.ShareFileProvider;
import java.io.File;
import java.util.List;

public final class ChatImageFormat implements ExportFormat {
    @Override
    public String displayName() { return "Chat screenshot (image)"; }

    @Override
    public ExportResult execute(Context context, List<ChatMessage> messages) {
        android.graphics.Bitmap bitmap = ChatBitmapRenderer.render(messages);
        File dir = new File(context.getCacheDir(), "share");
        File file = ImageExporter.save(bitmap, dir);
        Uri uri = ShareFileProvider.uriFor(context, file);
        return ExportResult.forFile(file, "image/png", uri);
    }
}
