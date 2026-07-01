package cn.lineai.data.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LineCodeSchemaTest {
    @Test
    public void createSqlContainsCoreDataSystems() {
        String schema = String.join("\n", LineCodeSchema.CREATE_SQL);

        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS model_configs"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS conversations"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS messages"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS message_text_chunks"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS settings"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS projects"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS memories"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS working_memory"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS conversation_index"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS import_jobs"));
        assertFalse(schema.contains("AsyncStorage"));
    }

    @Test
    public void optionalSearchUsesNativeAndroidFts4() {
        String fts = String.join("\n", LineCodeSchema.OPTIONAL_FTS_SQL).toLowerCase();

        assertTrue(fts.contains("using fts4"));
        assertTrue(fts.contains("memories_fts"));
        assertTrue(fts.contains("conversation_index_fts"));
        assertTrue(fts.contains("working_memory_fts"));
    }

    @Test
    public void versionIsAtLeastTwo() {
        assertTrue("schema version should have advanced to at least 2 for migrations",
                LineCodeSchema.VERSION >= 2);
    }

    @Test
    public void messageTextChunksSchemaIsDeclared() {
        String schema = String.join("\n", LineCodeSchema.CREATE_SQL);

        assertTrue(schema.contains("message_text_chunks"));
        assertTrue(schema.contains("field_name TEXT NOT NULL"));
        assertTrue(schema.contains("chunk_order INTEGER NOT NULL"));
        assertTrue(schema.contains("UNIQUE(message_id, field_name, chunk_order)"));
        assertTrue(schema.contains("idx_message_text_chunks_message_field"));
    }

    @Test
    public void createSqlAddsToolCallObservabilityColumns() {
        String schema = String.join("\n", LineCodeSchema.CREATE_SQL);

        assertTrue(schema.contains("duration_ms INTEGER NOT NULL DEFAULT 0"));
        assertTrue(schema.contains("error_message TEXT"));
        int toolCallsCreateIdx = schema.indexOf("CREATE TABLE IF NOT EXISTS tool_calls");
        int durationIdx = schema.indexOf("duration_ms", toolCallsCreateIdx);
        int errorIdx = schema.indexOf("error_message", toolCallsCreateIdx);
        int rawJsonIdx = schema.indexOf("raw_json", toolCallsCreateIdx);
        assertTrue("duration_ms must appear inside tool_calls CREATE block", durationIdx > toolCallsCreateIdx);
        assertTrue("error_message must appear inside tool_calls CREATE block", errorIdx > toolCallsCreateIdx);
        assertTrue("duration_ms must be declared before raw_json", durationIdx < rawJsonIdx);
        assertTrue("error_message must be declared before raw_json", errorIdx < rawJsonIdx);
    }

    @Test
    public void migrationsTableIsDeclared() {
        String migrations = String.join("\n", LineCodeSchema.MIGRATIONS_SQL);

        assertTrue(migrations.contains("CREATE TABLE IF NOT EXISTS schema_migrations"));
        assertTrue(migrations.contains("version INTEGER PRIMARY KEY"));
        assertTrue(migrations.contains("applied_at INTEGER NOT NULL"));
    }

    @Test
    public void addColumnsSqlTargetsToolCallsObservability() {
        String sql = String.join("\n", LineCodeSchema.ADD_COLUMNS_SQL);

        assertTrue(sql.contains("ALTER TABLE tool_calls ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("ALTER TABLE tool_calls ADD COLUMN error_message TEXT"));
    }

    @Test
    public void dropSqlIncludesSchemaMigrationsTable() {
        String drop = String.join("\n", LineCodeSchema.DROP_SQL);

        assertTrue(drop.contains("DROP TABLE IF EXISTS schema_migrations"));
        assertTrue(drop.contains("DROP TABLE IF EXISTS message_text_chunks"));
    }

    @Test
    public void addColumnsSqlAndMigrationsAreNonEmpty() {
        assertTrue(LineCodeSchema.ADD_COLUMNS_SQL.length > 0);
        assertTrue(LineCodeSchema.MIGRATIONS_SQL.length > 0);
    }

    @Test
    public void addColumnsSqlEntriesAreUnique() {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (String sql : LineCodeSchema.ADD_COLUMNS_SQL) {
            assertTrue("duplicate ADD COLUMNS entry: " + sql, seen.add(sql));
        }
    }
}
