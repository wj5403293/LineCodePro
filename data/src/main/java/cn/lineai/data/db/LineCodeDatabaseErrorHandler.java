package cn.lineai.data.db;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 自定义数据库损坏处理器，替代默认的 {@link android.database.DefaultDatabaseErrorHandler}。
 *
 * <p>默认处理器在 {@link #onCorruption} 中直接 {@code deleteDatabase()}，会销毁整个数据库文件，
 * 导致聊天记录永久丢失。本处理器改为：
 * <ol>
 *   <li>将损坏的数据库文件复制为存档（保留现场，便于排查/人工恢复）；</li>
 *   <li>删除损坏文件，让下次打开时由 {@link LineCodeDatabase#onOpen} 重建空库；</li>
 *   <li>写入 {@code <db>.needs-restore} 标志，提示 {@code onOpen} 从最近备份恢复。</li>
 * </ol>
 * 全部异常被吞掉并记录日志，绝不向上抛出，避免二次崩溃。
 */
public final class LineCodeDatabaseErrorHandler implements DatabaseErrorHandler {
    private static final String TAG = "LineCodeDatabaseErrorHandler";
    static final String NEEDS_RESTORE_SUFFIX = ".needs-restore";

    private final Context context;

    public LineCodeDatabaseErrorHandler(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCorruption(SQLiteDatabase db) {
        String path = db != null ? db.getPath() : null;
        Log.e(TAG, "检测到数据库损坏: " + path + "，开始安全处理（不再删除数据）");
        try {
            File dbFile = resolveDbFile(db, path);
            if (dbFile != null && dbFile.exists()) {
                preserveCorruptedCopy(dbFile);
                markNeedsRestore(dbFile);
                deleteCorrupted(dbFile);
            }
        } catch (Throwable e) {
            Log.e(TAG, "处理损坏数据库时异常（已忽略）: " + e.getMessage(), e);
        }
    }

    private File resolveDbFile(SQLiteDatabase db, String path) {
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists() || f.getParentFile() != null) {
                return f;
            }
        }
        return context.getDatabasePath(LineCodeSchema.DATABASE_NAME);
    }

    private void preserveCorruptedCopy(File dbFile) {
        File archive = new File(dbFile.getParent(),
                dbFile.getName() + ".corrupted-" + System.currentTimeMillis());
        try {
            copyFile(dbFile, archive);
            Log.i(TAG, "已保留损坏存档: " + archive.getName());
        } catch (IOException e) {
            Log.w(TAG, "保留损坏存档失败: " + e.getMessage());
        }
    }

    private void markNeedsRestore(File dbFile) {
        File flag = new File(dbFile.getAbsolutePath() + NEEDS_RESTORE_SUFFIX);
        try {
            if (!flag.createNewFile()) {
                Log.w(TAG, "恢复标志已存在: " + flag.getName());
            }
        } catch (IOException e) {
            Log.w(TAG, "写入恢复标志失败: " + e.getMessage());
        }
    }

    private void deleteCorrupted(File dbFile) {
        if (!dbFile.delete()) {
            Log.w(TAG, "删除损坏库文件失败: " + dbFile.getName());
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
