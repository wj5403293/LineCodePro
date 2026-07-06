package cn.lineai.share.format;

import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class ImageExporter {
    private ImageExporter() {}

    public static File save(Bitmap bitmap, File dir) {
        dir.mkdirs();
        File file = new File(dir, "chat_screenshot.png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image", e);
        }
        return file;
    }
}
