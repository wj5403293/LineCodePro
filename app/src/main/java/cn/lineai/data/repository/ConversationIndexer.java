package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.ChatMessage;

/**
 * 对话索引构建与存储。负责将对话内容分词后写入 conversation_index 表。
 */
public final class ConversationIndexer extends BaseRepository {
    public ConversationIndexer(LineCodeDatabase database) {
        super(database);
    }

    public synchronized void indexConversation(String projectId, ConversationRecord conversation) {
        if (conversation == null || conversation.getId().length() == 0) {
            return;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("conversation_index", "conversation_id = ?", new String[] {conversation.getId()});
            for (MessageRecord message : conversation.getMessages()) {
                if (!shouldIndex(message)) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", conversation.getId() + ":" + message.getId());
                values.put("project_id", safe(projectId));
                values.put("conversation_id", conversation.getId());
                values.put("message_id", message.getId());
                values.put("role", message.getRole().getProtocolName());
                values.put("text", message.getContent());
                values.put("title", conversation.getTitle());
                values.put("created_at", message.getTimestamp() > 0 ? message.getTimestamp() : conversation.getCreatedAt());
                values.put("updated_at", conversation.getUpdatedAt());
                values.put("raw_json", message.getRawJson());
                db.insertWithOnConflict("conversation_index", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean shouldIndex(MessageRecord message) {
        if (message == null || message.isHidden() || message.isExcludeFromContext()) {
            return false;
        }
        ChatMessage.Role role = message.getRole();
        return (role == ChatMessage.Role.USER || role == ChatMessage.Role.ASSISTANT)
                && message.getContent().trim().length() > 0;
    }
}
