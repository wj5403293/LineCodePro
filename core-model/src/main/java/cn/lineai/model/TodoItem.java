package cn.lineai.model;

import org.json.JSONObject;

public final class TodoItem {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    private final String content;
    private final String status;

    public TodoItem(String content, String status) {
        this.content = content == null ? "" : content;
        this.status = normalizeStatus(status);
    }

    public String getContent() {
        return content;
    }

    public String getStatus() {
        return status;
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public static String normalizeStatus(String raw) {
        if (raw == null) {
            return STATUS_PENDING;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (STATUS_IN_PROGRESS.equals(value) || "inprogress".equals(value) || "in-progress".equals(value)) {
            return STATUS_IN_PROGRESS;
        }
        if (STATUS_COMPLETED.equals(value) || "done".equals(value) || "complete".equals(value) || "finished".equals(value)) {
            return STATUS_COMPLETED;
        }
        return STATUS_PENDING;
    }

    public static TodoItem fromJson(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        String content = obj.optString("content", "").trim();
        if (content.length() == 0) {
            return null;
        }
        return new TodoItem(content, obj.optString("status", STATUS_PENDING));
    }
}
