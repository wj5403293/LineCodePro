package cn.lineai.share.format;

import android.content.Context;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ExportFormat;
import cn.lineai.share.ExportResult;
import cn.lineai.share.ShareFileProvider;
import java.io.File;
import java.util.List;

public final class PdfFormat implements ExportFormat {
    @Override
    public String displayName() { return "PDF 文件"; }

    @Override
    public ExportResult execute(Context context, List<ChatMessage> messages) {
        PdfDocument doc = new PdfDocument();
        new PdfRenderer().render(doc, messages);
        File dir = new File(context.getCacheDir(), "share");
        File file = PdfExporter.save(doc, dir);
        Uri uri = ShareFileProvider.uriFor(context, file);
        return ExportResult.forFile(file, "application/pdf", uri);
    }
}
