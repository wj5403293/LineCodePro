package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.Strings;

final class MessageTextChunkStore {
    static final int CHUNK_SIZE = 64 * 1024;
    private static final int SQL_SUBSTR_START_OFFSET = 1;

    private final LineCodeDatabase database;

    MessageTextChunkStore(LineCodeDatabase database) {
        this.database = database;
    }

    void save(SQLiteDatabase db, String messageId, String fieldName, String value) {
        db.delete("message_text_chunks", "message_id = ? AND field_name = ?", new String[] {safe(messageId), safe(fieldName)});
        String text = safe(value);
        if (text.length() == 0) {
            return;
        }
        for (int start = 0, order = 0; start < text.length(); start += CHUNK_SIZE, order++) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            ContentValues values = new ContentValues();
            values.put("message_id", safe(messageId));
            values.put("field_name", safe(fieldName));
            values.put("chunk_order", order);
            values.put("content", text.substring(start, end));
            db.insertWithOnConflict("message_text_chunks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    String read(SQLiteDatabase db, String messageId, String fieldName) {
        String chunks = readChunks(db, messageId, fieldName, 0);
        if (chunks.length() > 0) {
            return chunks;
        }
        return readLegacyMessageField(db, messageId, fieldName);
    }

    String readFirstChars(SQLiteDatabase db, String messageId, String fieldName, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        String chunks = readChunks(db, messageId, fieldName, maxChars);
        if (chunks.length() > 0) {
            return chunks;
        }
        return readLegacyMessageFieldPrefix(db, messageId, fieldName, maxChars);
    }

    long totalLength(SQLiteDatabase db, String tableName, String columnName) {
        return queryLong(db,
                "SELECT COALESCE(SUM(length(" + safeFieldName(columnName) + ")), 0) FROM " + safeTableName(tableName),
                new String[0]);
    }

    private String readChunks(SQLiteDatabase db, String messageId, String fieldName, int maxChars) {
        StringBuilder builder = new StringBuilder();
        Cursor cursor = db.query(
                "message_text_chunks",
                new String[] {"content"},
                "message_id = ? AND field_name = ?",
                new String[] {safe(messageId), safe(fieldName)},
                null,
                null,
                "chunk_order ASC"
        );
        try {
            while (cursor.moveToNext()) {
                String chunk = value(cursor, 0);
                if (maxChars > 0 && builder.length() + chunk.length() > maxChars) {
                    builder.append(chunk, 0, Math.max(0, maxChars - builder.length()));
                    break;
                }
                builder.append(chunk);
                if (maxChars > 0 && builder.length() >= maxChars) {
                    break;
                }
            }
        } finally {
            cursor.close();
        }
        return builder.toString();
    }

    private String readLegacyMessageField(SQLiteDatabase db, String messageId, String fieldName) {
        int length = (int) Math.min(Integer.MAX_VALUE, queryLong(db,
                "SELECT COALESCE(length(" + safeFieldName(fieldName) + "), 0) FROM messages WHERE id = ? LIMIT 1",
                new String[] {safe(messageId)}));
        if (length <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(length);
        for (int start = 0; start < length; start += CHUNK_SIZE) {
            builder.append(readLegacyMessageFieldRange(db, messageId, fieldName, start, CHUNK_SIZE));
        }
        return builder.toString();
    }

    private String readLegacyMessageFieldPrefix(SQLiteDatabase db, String messageId, String fieldName, int maxChars) {
        return readLegacyMessageFieldRange(db, messageId, fieldName, 0, maxChars);
    }

    private String readLegacyMessageFieldRange(SQLiteDatabase db, String messageId, String fieldName, int start, int maxChars) {
        Cursor cursor = db.rawQuery(
                "SELECT substr(" + safeFieldName(fieldName) + ", ?, ?) FROM messages WHERE id = ? LIMIT 1",
                new String[] {String.valueOf(start + SQL_SUBSTR_START_OFFSET), String.valueOf(maxChars), safe(messageId)});
        try {
            return cursor.moveToFirst() ? value(cursor, 0) : "";
        } finally {
            cursor.close();
        }
    }

    private long queryLong(SQLiteDatabase db, String sql, String[] args) {
        Cursor cursor = db.rawQuery(sql, args);
        try {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private String value(Cursor cursor, int index) {
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static String safe(String value) {
        return Strings.nullToEmpty(value);
    }

    private String safeFieldName(String fieldName) {
        if ("content".equals(fieldName)
                || "reasoning_content".equals(fieldName)
                || "raw_json".equals(fieldName)
                || "old_content".equals(fieldName)
                || "new_content".equals(fieldName)
                || "arguments".equals(fieldName)
                || "text".equals(fieldName)) {
            return fieldName;
        }
        throw new IllegalArgumentException("Unsupported text field: " + fieldName);
    }

    private String safeTableName(String tableName) {
        if ("messages".equals(tableName)
                || "message_text_chunks".equals(tableName)
                || "diff_records".equals(tableName)
                || "tool_calls".equals(tableName)
                || "tool_results".equals(tableName)
                || "conversation_index".equals(tableName)) {
            return tableName;
        }
        throw new IllegalArgumentException("Unsupported text table: " + tableName);
    }
}
