package cn.lineai.share;

import android.content.Context;
import cn.lineai.model.ChatMessage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportFormatResolverTest {

    @Test
    public void registerAddsFormat() {
        ExportFormatResolver resolver = new ExportFormatResolver();
        int initialSize = resolver.size();
        resolver.register(new TestFormat("Test"));
        assertEquals(initialSize + 1, resolver.size());
    }

    @Test
    public void getDisplayNamesReturnsAllNames() {
        ExportFormatResolver resolver = new ExportFormatResolver();
        resolver.register(new TestFormat("FormatA"));
        resolver.register(new TestFormat("FormatB"));
        String[] names = resolver.getDisplayNames();
        boolean hasA = false, hasB = false;
        for (String name : names) {
            if ("FormatA".equals(name)) hasA = true;
            if ("FormatB".equals(name)) hasB = true;
        }
        assertTrue(hasA);
        assertTrue(hasB);
    }

    @Test
    public void getReturnsCorrectFormat() {
        ExportFormatResolver resolver = new ExportFormatResolver();
        TestFormat format = new TestFormat("MyFormat");
        resolver.register(format);
        ExportFormat retrieved = resolver.get(resolver.size() - 1);
        assertSame(format, retrieved);
    }

    private static class TestFormat implements ExportFormat {
        private final String name;
        TestFormat(String name) { this.name = name; }
        @Override public String displayName() { return name; }
        @Override public ExportResult execute(Context context, List<ChatMessage> messages) { return null; }
    }
}
