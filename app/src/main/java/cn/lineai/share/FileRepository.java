package cn.lineai.share;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class FileRepository {

    private FileRepository() {}

    public static File write(Context context, String name, String content) {
        File dir = new File(context.getCacheDir(), "share");
        dir.mkdirs();
        File file = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write share file", e);
        }
        return file;
    }

    public static File writeBytes(Context context, String name, byte[] data) {
        File dir = new File(context.getCacheDir(), "share");
        dir.mkdirs();
        File file = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write share file", e);
        }
        return file;
    }

    public static boolean exists(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }
}
