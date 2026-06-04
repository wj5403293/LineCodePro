package cn.lineai.mvp;

import cn.lineai.data.repository.ConversationRecord;
import java.util.List;

public interface ChatController {
    void onNewConversation();

    void onConversationSelected(String id);

    void onConversationDeleted(String id);

    void onSendMessage(String text);

    void onChatModeChanged(String mode);

    void onStopGeneration();

    void onToolReview(String toolCallId, String state, String diffId);

    List<ConversationRecord> getConversationMetas();

    String getCurrentConversationId();
}
