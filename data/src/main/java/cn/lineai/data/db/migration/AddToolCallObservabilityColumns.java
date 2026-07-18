package cn.lineai.data.db.migration;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeSchema;
import java.util.Locale;

public final class AddToolCallObservabilityColumns extends DatabaseMigration {
    private static final String TABLE = "tool_calls";
    private static final String ADD_COLUMN_PREFIX = " ADD COLUMN ";

    @Override
    public int getTargetVersion() {
        return 2;
    }

    @Override
    public void apply(SQLiteDatabase db) {
        for (String sql : LineCodeSchema.ADD_COLUMNS_SQL) {
            String columnName = extractColumnName(sql);
            if (columnName == null || columnExists(db, TABLE, columnName)) {
                continue;
            }
            db.execSQL(sql);
        }
    }

    private static String extractColumnName(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int idx = upper.indexOf(ADD_COLUMN_PREFIX);
        if (idx < 0) {
            return null;
        }
        String rest = sql.substring(idx + ADD_COLUMN_PREFIX.length()).trim();
        int end = 0;
        while (end < rest.length() && !Character.isWhitespace(rest.charAt(end))) {
            end++;
        }
        return end == 0 ? null : rest.substring(0, end);
    }

    private static boolean columnExists(SQLiteDatabase db, String table, String column) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex < 0) {
                return false;
            }
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }
}
