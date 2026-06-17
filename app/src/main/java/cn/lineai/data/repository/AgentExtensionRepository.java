package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.ExtensionAgentConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;

/**
 * Agent 扩展 CRUD 仓库，负责 extension_agents 表的读写。
 */
public final class AgentExtensionRepository extends BaseRepository {

    public AgentExtensionRepository(LineCodeDatabase database) {
        super(database);
    }

    public synchronized List<ExtensionAgentConfig> getAgentExtensions() {
        ArrayList<ExtensionAgentConfig> agents = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, enabled, name, slug, prompt, trigger, tool_names_json, mcp_ids_json, created_at, updated_at "
                        + "FROM extension_agents ORDER BY updated_at DESC",
                new String[0]
        );
        try {
            while (cursor.moveToNext()) {
                agents.add(new ExtensionAgentConfig(
                        value(cursor, "id"),
                        intValue(cursor, "enabled") != 0,
                        value(cursor, "name"),
                        value(cursor, "slug"),
                        value(cursor, "prompt"),
                        value(cursor, "trigger"),
                        jsonStringList(value(cursor, "tool_names_json")),
                        jsonStringList(value(cursor, "mcp_ids_json")),
                        longValue(cursor, "created_at"),
                        longValue(cursor, "updated_at")
                ));
            }
        } finally {
            cursor.close();
        }
        return agents;
    }

    public synchronized ExtensionAgentConfig saveAgentExtension(ExtensionAgentConfig input) {
        long now = System.currentTimeMillis();
        String id = input == null || input.getId().length() == 0 ? nextId("agent") : input.getId();
        long createdAt = input == null || input.getCreatedAt() <= 0 ? now : input.getCreatedAt();
        ExtensionAgentConfig saved = new ExtensionAgentConfig(
                id,
                input == null || input.isEnabled(),
                input == null ? "" : input.getName(),
                normalizeSlug(input == null ? "" : input.getSlug(), input == null ? "" : input.getName()),
                input == null ? "" : input.getPrompt(),
                input == null ? "" : input.getTrigger(),
                input == null ? Collections.emptyList() : input.getToolNames(),
                input == null ? Collections.emptyList() : input.getMcpIds(),
                createdAt,
                now
        );
        ContentValues values = new ContentValues();
        values.put("id", saved.getId());
        values.put("enabled", saved.isEnabled() ? 1 : 0);
        values.put("name", saved.getName());
        values.put("slug", saved.getSlug());
        values.put("prompt", saved.getPrompt());
        values.put("trigger", saved.getTrigger());
        values.put("tool_names_json", jsonArray(saved.getToolNames()).toString());
        values.put("mcp_ids_json", jsonArray(saved.getMcpIds()).toString());
        values.put("created_at", saved.getCreatedAt());
        values.put("updated_at", saved.getUpdatedAt());
        values.put("raw_json", "");
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.insertWithOnConflict("extension_agents", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return saved;
    }

    public synchronized void setAgentEnabled(String id, boolean enabled) {
        updateEnabled("extension_agents", id, enabled);
    }

    public synchronized void deleteAgent(String id) {
        database.getWritableDatabase().delete("extension_agents", "id = ?", new String[] {safe(id)});
    }

    private String normalizeSlug(String raw, String fallback) {
        String source = safe(raw).trim().length() == 0 ? safe(fallback) : safe(raw);
        String lower = source.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_') {
                builder.append(ch);
            } else if (Character.isWhitespace(ch)) {
                builder.append('-');
            }
        }
        String value = builder.toString();
        while (value.contains("--")) {
            value = value.replace("--", "-");
        }
        value = trim(value, '-');
        return value.length() == 0 ? "agent-" + System.currentTimeMillis() : value;
    }

    private ArrayList<String> jsonStringList(String raw) {
        ArrayList<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(safe(raw).length() == 0 ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i).trim();
                if (value.length() > 0) {
                    values.add(value);
                }
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    private JSONArray jsonArray(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                if (value != null && value.trim().length() > 0) {
                    array.put(value.trim());
                }
            }
        }
        return array;
    }

    private String trim(String value, char ch) {
        String text = safe(value);
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == ch) {
            start++;
        }
        while (end > start && text.charAt(end - 1) == ch) {
            end--;
        }
        return text.substring(start, end);
    }
}
