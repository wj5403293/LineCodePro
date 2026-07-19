package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import cn.lineai.data.db.LineCodeDatabase;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public final class DiffRepository extends BaseRepository implements DiffStore {
    private final SecureRandom random = new SecureRandom();

    public DiffRepository(LineCodeDatabase database) {
        super(database);
    }

    public synchronized DiffRecord recordDiff(
            String filePath,
            String oldContent,
            String newContent,
            boolean oldExists
    ) {
        long now = System.currentTimeMillis();
        DiffRecord record = new DiffRecord(
                now + "_" + Long.toString(Math.abs(random.nextLong()), 36),
                filePath,
                oldContent,
                newContent,
                oldExists,
                now,
                false
        );
        ContentValues values = new ContentValues();
        values.put("id", record.getId());
        values.put("file_path", record.getFilePath());
        values.put("old_content", record.getOldContent());
        values.put("new_content", record.getNewContent());
        values.put("old_exists", record.isOldExists() ? 1 : 0);
        values.put("timestamp", record.getTimestamp());
        values.put("reverted", record.isReverted() ? 1 : 0);
        values.put("raw_json", "");
        insertOrReplace("diff_records", values);
        return record;
    }

    public synchronized DiffRecord getDiff(String diffId) {
        if (diffId == null || diffId.length() == 0) {
            return null;
        }
        return queryOne("diff_records", null, "id = ?", new String[]{diffId}, this::readRecord);
    }

    public synchronized List<DiffRecord> getDiffChain(String filePath) {
        if (filePath == null || filePath.length() == 0) {
            return new ArrayList<>();
        }
        return queryList("diff_records", null, "file_path = ?", new String[]{filePath}, "timestamp ASC", this::readRecord);
    }

    public synchronized RevertResult revertDiff(String diffId) {
        DiffRecord target = getDiff(diffId);
        if (target == null) {
            return RevertResult.error("Specified diff record not found");
        }
        if (target.isReverted()) {
            return RevertResult.ok("This change has been reverted");
        }

        List<DiffRecord> chain = getDiffChain(target.getFilePath());
        int targetIndex = -1;
        for (int i = 0; i < chain.size(); i++) {
            if (target.getId().equals(chain.get(i).getId())) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            return RevertResult.error("Specified diff record not found");
        }
        for (int i = targetIndex + 1; i < chain.size(); i++) {
            if (!chain.get(i).isReverted()) {
                return RevertResult.error("Please revert subsequent changes to this file first");
            }
        }

        return RevertResult.ok("Ready to revert", target);
    }

    public synchronized void markReverted(String diffId) {
        ContentValues values = new ContentValues();
        values.put("reverted", 1);
        database.getWritableDatabase().update("diff_records", values, "id = ?", new String[] {diffId});
    }

    private DiffRecord readRecord(Cursor cursor) {
        return new DiffRecord(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("file_path")),
                cursor.getString(cursor.getColumnIndexOrThrow("old_content")),
                cursor.getString(cursor.getColumnIndexOrThrow("new_content")),
                cursor.getInt(cursor.getColumnIndexOrThrow("old_exists")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                cursor.getInt(cursor.getColumnIndexOrThrow("reverted")) == 1
        );
    }

    public static final class RevertResult {
        private final boolean success;
        private final String message;
        private final DiffRecord diffRecord;

        private RevertResult(boolean success, String message, DiffRecord diffRecord) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.diffRecord = diffRecord;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public DiffRecord getDiffRecord() {
            return diffRecord;
        }

        static RevertResult ok(String message) {
            return new RevertResult(true, message, null);
        }

        static RevertResult ok(String message, DiffRecord diffRecord) {
            return new RevertResult(true, message, diffRecord);
        }

        static RevertResult error(String message) {
            return new RevertResult(false, message, null);
        }
    }
}
