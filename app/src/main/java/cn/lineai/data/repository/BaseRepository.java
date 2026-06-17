package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import java.util.UUID;

/**
 * Repository 层公共基类，集中维护 Cursor 读取与 null 安全辅助方法。
 */
public abstract class BaseRepository {
    protected final LineCodeDatabase database;

    protected BaseRepository(LineCodeDatabase database) {
        this.database = database;
    }

    protected String value(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    protected int intValue(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    protected long longValue(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    protected double doubleValue(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(index) ? 0.0 : cursor.getDouble(index);
    }

    protected String safe(String value) {
        return value == null ? "" : value;
    }

    protected SQLiteDatabase db() {
        return database.getWritableDatabase();
    }

    protected String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    protected void updateEnabled(String table, String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update(table, values, "id = ?", new String[] {safe(id)});
    }
}
