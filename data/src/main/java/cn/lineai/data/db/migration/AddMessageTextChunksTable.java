package cn.lineai.data.db.migration;

import android.database.sqlite.SQLiteDatabase;

/**
 * Adds the side table used to keep oversized message text out of CursorWindow-sized rows.
 */
public final class AddMessageTextChunksTable extends DatabaseMigration {
    @Override
    public int getTargetVersion() {
        return 4;
    }

    @Override
    public void apply(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS message_text_chunks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,"
                + "field_name TEXT NOT NULL,"
                + "chunk_order INTEGER NOT NULL,"
                + "content TEXT NOT NULL DEFAULT '',"
                + "UNIQUE(message_id, field_name, chunk_order)"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_text_chunks_message_field "
                + "ON message_text_chunks(message_id, field_name, chunk_order)");
    }
}
