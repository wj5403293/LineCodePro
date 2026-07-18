package cn.lineai.share;

import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportResultTest {

    @Test
    public void forFileCreatesShareFileAction() {
        File file = new File("/tmp/test.png");
        ExportResult result = ExportResult.forFile(file, "image/png", null);
        assertEquals(ExportResult.ACTION_SHARE_FILE, result.getAction());
        assertEquals(file, result.getFile());
        assertEquals("image/png", result.getMimeType());
        assertNull(result.getUri());
    }

    @Test
    public void forClipboardCreatesClipboardAction() {
        ExportResult result = ExportResult.forClipboard("hello");
        assertEquals(ExportResult.ACTION_CLIPBOARD, result.getAction());
        assertEquals("hello", result.getContent());
    }

    @Test
    public void forShareTextCreatesShareTextAction() {
        ExportResult result = ExportResult.forShareText("share me");
        assertEquals(ExportResult.ACTION_SHARE_TEXT, result.getAction());
        assertEquals("share me", result.getContent());
    }
}
