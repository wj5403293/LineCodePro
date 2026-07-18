package cn.lineai.model;

/**
 * UI layer representation of a conversation, decoupled from the data layer ConversationRecord.
 */
public final class ConversationUiModel {
    private final String id;
    private final String title;
    private final long updatedAt;

    public ConversationUiModel(String id, String title, long updatedAt) {
        this.id = id == null ? "" : id;
        this.title = title == null || title.length() == 0 ? "新对话" : title;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
