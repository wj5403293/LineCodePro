package cn.lineai.tool.builtin;

import android.content.Context;
import java.io.File;

public final class PhoneScreenshotCache {
    private static final String DIR_NAME = "phone-control-screenshots";

    private PhoneScreenshotCache() {
    }

    public static File directory(Context context) {
        return new File(context.getCacheDir(), DIR_NAME);
    }

    public static boolean isInside(Context context, File file) throws Exception {
        if (context == null || file == null) {
            return false;
        }
        return FileToolPathPolicy.isInside(directory(context).getCanonicalFile(), file.getCanonicalFile());
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        deleteRecursively(directory(context));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
