package cn.lineai.share.format;

import android.content.Context;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ChatMessages;
import cn.lineai.share.ExportFormat;
import cn.lineai.share.ExportResult;
import java.util.List;

public final class ClipboardFormat implements ExportFormat {
    private static final int WARN_LENGTH = 5000;

    @Override
    public String displayName() { return "Copy to clipboard"; }

    @Override
    public ExportResult execute(Context context, List<ChatMessage> messages) {
        String text = ChatMessages.toPlainText(messages);
        return ExportResult.forClipboard(text);
    }

    public boolean shouldWarn(String text) {
        return text.length() > WARN_LENGTH;
    }
}
