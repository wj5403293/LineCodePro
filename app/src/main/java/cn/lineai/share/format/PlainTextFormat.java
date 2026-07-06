package cn.lineai.share.format;

import android.content.Context;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ChatMessages;
import cn.lineai.share.ExportFormat;
import cn.lineai.share.ExportResult;
import java.util.List;

public final class PlainTextFormat implements ExportFormat {
    @Override
    public String displayName() { return "纯文本分享"; }

    @Override
    public ExportResult execute(Context context, List<ChatMessage> messages) {
        String text = ChatMessages.toPlainText(messages);
        return ExportResult.forShareText(text);
    }
}
