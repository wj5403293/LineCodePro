package cn.lineai.mvp;

import cn.lineai.mvp.QuoteController;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuoteControllerTest {

    @Test
    public void setQuoteStoresText() {
        QuoteController controller = new QuoteController();
        controller.setQuote("Hello world");
        assertTrue(controller.hasQuote());
    }

    @Test
    public void composeWithQuotePrefixesQuote() {
        QuoteController controller = new QuoteController();
        controller.setQuote("quoted text");
        String result = controller.composeWithQuote("my input");
        assertTrue(result.startsWith("> quoted text"));
        assertTrue(result.contains("my input"));
    }

    @Test
    public void composeWithQuoteClearsAfterSend() {
        QuoteController controller = new QuoteController();
        controller.setQuote("quoted text");
        controller.composeWithQuote("my input");
        assertFalse(controller.hasQuote());
    }

    @Test
    public void composeWithoutQuoteReturnsInputUnchanged() {
        QuoteController controller = new QuoteController();
        String result = controller.composeWithQuote("my input");
        assertEquals("my input", result);
    }

    @Test
    public void clearQuoteRemovesQuote() {
        QuoteController controller = new QuoteController();
        controller.setQuote("text");
        controller.clearQuote();
        assertFalse(controller.hasQuote());
    }

    @Test
    public void setQuoteNotifiesPreview() {
        QuoteController controller = new QuoteController();
        boolean[] shown = {false};
        controller.setPreview(new QuoteController.QuotePreview() {
            @Override
            public void showQuote(String previewText) { shown[0] = true; }
            @Override
            public void hideQuote() {}
        });
        controller.setQuote("test");
        assertTrue(shown[0]);
    }

    @Test
    public void clearQuoteHidesPreview() {
        QuoteController controller = new QuoteController();
        boolean[] hidden = {false};
        controller.setPreview(new QuoteController.QuotePreview() {
            @Override
            public void showQuote(String previewText) {}
            @Override
            public void hideQuote() { hidden[0] = true; }
        });
        controller.setQuote("test");
        controller.clearQuote();
        assertTrue(hidden[0]);
    }

    @Test
    public void longQuoteIsTruncatedInPreview() {
        QuoteController controller = new QuoteController();
        String[] captured = {null};
        controller.setPreview(new QuoteController.QuotePreview() {
            @Override
            public void showQuote(String previewText) { captured[0] = previewText; }
            @Override
            public void hideQuote() {}
        });
        String longText = "This is a very long quote that should be truncated to 80 characters in the preview. " +
                "It keeps going and going and going past the limit.";
        controller.setQuote(longText);
        assertNotNull(captured[0]);
        assertTrue(captured[0].endsWith("..."));
        assertTrue(captured[0].length() <= 83); // 80 + "..."
    }
}
