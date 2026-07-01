package cn.lineai.data.db.migration;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AddMessageTextChunksTableTest {
    @Test
    public void targetsSchemaVersionFour() {
        AddMessageTextChunksTable migration = new AddMessageTextChunksTable();

        assertEquals(4, migration.getTargetVersion());
    }
}
