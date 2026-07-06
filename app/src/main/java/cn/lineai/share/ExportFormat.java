package cn.lineai.share;

import android.content.Context;
import cn.lineai.model.ChatMessage;
import java.util.List;

public interface ExportFormat {
    String displayName();

    ExportResult execute(Context context, List<ChatMessage> messages);
}
