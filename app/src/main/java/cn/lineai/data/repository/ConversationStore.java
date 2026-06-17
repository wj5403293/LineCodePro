package cn.lineai.data.repository;

import java.util.List;

/**
 * 会话仓储接口，定义 ConversationRepository 的公开契约。
 */
public interface ConversationStore {
    List<ConversationRecord> getConversationMetas();

    List<ConversationRecord> getAllConversations();

    ConversationRecord getConversation(String id);

    String getCurrentConversationId();

    ConversationRecord getCurrentConversation();

    void saveConversation(ConversationRecord conversation);

    void setCurrentConversationId(String id);

    void deleteConversation(String id);

    void clearAll();
}
