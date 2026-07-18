package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository 层公共基类，集中维护 Cursor 读取与 null 安全辅助方法。
 */
public abstract class BaseRepository {
    protected final LineCodeDatabase database;

    /** Cursor 行映射回调 */
    @FunctionalInterface
    public interface CursorMapper<T> {
        T map(Cursor cursor);
    }

    protected BaseRepository(LineCodeDatabase database) {
        this.database = database;
    }

    /** 通用查询模板（rawQuery），返回列表 */
    protected <T> List<T> queryList(String sql, String[] args, CursorMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = database.getReadableDatabase().rawQuery(sql, args);
            while (cursor != null && cursor.moveToNext()) {
                result.add(mapper.map(cursor));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    /** 通用查询模板（query），返回列表 */
    protected <T> List<T> queryList(String table, String[] columns, String selection, String[] selectionArgs, String orderBy, CursorMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = database.getReadableDatabase().query(table, columns, selection, selectionArgs, null, null, orderBy);
            while (cursor != null && cursor.moveToNext()) {
                result.add(mapper.map(cursor));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    /** 通用单条查询（rawQuery） */
    protected <T> T queryOne(String sql, String[] args, CursorMapper<T> mapper) {
        Cursor cursor = null;
        try {
            cursor = database.getReadableDatabase().rawQuery(sql, args);
            if (cursor != null && cursor.moveToFirst()) {
                return mapper.map(cursor);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    /** 通用单条查询（query） */
    protected <T> T queryOne(String table, String[] columns, String selection, String[] selectionArgs, CursorMapper<T> mapper) {
        Cursor cursor = null;
        try {
            cursor = database.getReadableDatabase().query(table, columns, selection, selectionArgs, null, null, null, "1");
            if (cursor != null && cursor.moveToFirst()) {
                return mapper.map(cursor);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    /** 通用 insertOrReplace（带事务） */
    protected long insertOrReplace(String table, ContentValues values) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            long rowId = db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
            return rowId;
        } finally {
            db.endTransaction();
        }
    }

    /** 通用 deleteById */
    protected int deleteById(String table, String id) {
        return database.getWritableDatabase().delete(table, "id = ?", new String[]{safe(id)});
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
        return Strings.nullToEmpty(value);
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
