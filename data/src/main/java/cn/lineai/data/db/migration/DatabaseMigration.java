package cn.lineai.data.db.migration;

import android.database.sqlite.SQLiteDatabase;

public abstract class DatabaseMigration {
    public abstract int getTargetVersion();

    public abstract void apply(SQLiteDatabase db);
}
