package cn.lineai.data.db.migration;

import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeSchema;

/**
 * 在 v3 迁移中创建 ipc_providers 表及其启用状态索引。
 *
 * <p>建表与索引 SQL 直接引用 {@link LineCodeSchema#SQL_CREATE_IPC_PROVIDERS}
 * 与 {@link LineCodeSchema#SQL_CREATE_INDEX_IPC_PROVIDERS}，与
 * {@link LineCodeSchema#CREATE_SQL} 同源，避免两处维护漂移。
 * 语句均使用 {@code IF NOT EXISTS}，对已通过 {@code onCreate} 建表的全新安装重复执行亦安全。
 */
public final class AddIpcProvidersTable extends DatabaseMigration {
    @Override
    public int getTargetVersion() {
        return 3;
    }

    @Override
    public void apply(SQLiteDatabase db) {
        db.execSQL(LineCodeSchema.SQL_CREATE_IPC_PROVIDERS);
        db.execSQL(LineCodeSchema.SQL_CREATE_INDEX_IPC_PROVIDERS);
    }
}
