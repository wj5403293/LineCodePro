package cn.lineai.data.service;

import cn.lineai.data.repository.DiffRecord;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 负责将 DiffRecord 中的旧内容写回文件系统。
 * 从 DiffRepository 中提取，遵循 SRP：数据持久化与文件操作分离。
 */
public final class FileRestorer {

    private FileRestorer() {
    }

    public static void restoreOldContent(DiffRecord record) throws Exception {
        File file = new File(record.getFilePath());
        if (!record.isOldExists()) {
            if (file.exists() && !file.delete()) {
                throw new java.io.IOException("Cannot delete file: " + record.getFilePath());
            }
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("Cannot create parent directory: " + parent.getPath());
        }
        FileOutputStream output = new FileOutputStream(file, false);
        try {
            output.write(record.getOldContent().getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }
}
