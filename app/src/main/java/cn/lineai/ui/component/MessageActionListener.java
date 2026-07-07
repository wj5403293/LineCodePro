package cn.lineai.ui.component;

import cn.lineai.model.ChatMessage;

public interface MessageActionListener {
    void onCopyMessage(ChatMessage message);
    void onRecallMessage(ChatMessage message);
    void onQuoteMessage(ChatMessage message);
    void onShareMessage(ChatMessage message);
    void onSelectMessage(ChatMessage message, android.view.View messageView);
    void onMultiSelectMessage(ChatMessage message);
}
