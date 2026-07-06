package cn.lineai.mvp;

public final class QuoteController {

    private static final int PREVIEW_MAX_LENGTH = 80;

    public interface QuotePreview {
        void showQuote(String previewText);
        void hideQuote();
    }

    private String quotedText;
    private QuotePreview preview;

    public QuoteController() {
    }

    public void setPreview(QuotePreview preview) {
        this.preview = preview;
    }

    public void setQuote(String text) {
        if (text != null && !text.isEmpty()) {
            quotedText = text;
            String previewText = text.length() > PREVIEW_MAX_LENGTH
                    ? text.substring(0, PREVIEW_MAX_LENGTH) + "..."
                    : text;
            if (preview != null) {
                preview.showQuote(previewText);
            }
        } else {
            clearQuote();
        }
    }

    public String composeWithQuote(String userInput) {
        if (quotedText == null || quotedText.isEmpty()) {
            return userInput;
        }
        String result = "> " + quotedText.replace("\n", "\n> ") + "\n\n" + userInput;
        clearQuote();
        return result;
    }

    public void clearQuote() {
        quotedText = null;
        if (preview != null) {
            preview.hideQuote();
        }
    }

    public boolean hasQuote() {
        return quotedText != null && !quotedText.isEmpty();
    }
}
