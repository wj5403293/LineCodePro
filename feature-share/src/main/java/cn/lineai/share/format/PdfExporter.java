package cn.lineai.share.format;

import android.graphics.pdf.PdfDocument;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class PdfExporter {
    private PdfExporter() {}

    public static File save(PdfDocument doc, File dir) {
        dir.mkdirs();
        File file = new File(dir, "chat_export.pdf");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.writeTo(fos);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save PDF", e);
        } finally {
            doc.close();
        }
        return file;
    }
}
