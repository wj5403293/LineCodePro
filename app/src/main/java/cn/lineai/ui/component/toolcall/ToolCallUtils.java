package cn.lineai.ui.component.toolcall;

import cn.lineai.tool.ToolCall;
import org.json.JSONObject;

final class ToolCallUtils {
    private ToolCallUtils() {
    }

    static JSONObject parseInput(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments().trim().length() == 0) {
            return new JSONObject();
        }
        try {
            return new JSONObject(toolCall.getArguments());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    static String inputLabel(String name, JSONObject input) {
        if (input == null) {
            return name;
        }
        String[] keys = new String[] {"file_path", "pattern", "query", "url", "path", "command"};
        for (String key : keys) {
            String value = input.optString(key);
            if (value.length() > 0) {
                return value;
            }
        }
        return name == null ? "" : name;
    }

    static String prettyJson(JSONObject input) {
        if (input == null) {
            return "{}";
        }
        try {
            return input.toString(2);
        } catch (Exception ignored) {
            return input.toString();
        }
    }

    static boolean isReadTool(String name) {
        return "file_read".equals(name) || "glob".equals(name) || "list_dir".equals(name)
                || "web_search".equals(name) || "web_fetch".equals(name);
    }

    static boolean isWriteTool(String name) {
        return "file_write".equals(name) || "file_edit".equals(name);
    }

    static boolean isDeleteTool(String name) {
        return "file_delete".equals(name);
    }

    static boolean isHttpTool(String name) {
        return "http_server".equals(name);
    }

    static boolean isCustomMcpTool(String name) {
        return name != null && name.startsWith("mcpx_");
    }

    static boolean isShellTool(String name) {
        return "shell_execute".equals(name);
    }

    static boolean isAgentTool(String name) {
        return "agent".equals(name);
    }

    static boolean isAgentPipelineTool(String name) {
        return "agent_pipeline".equals(name);
    }
}
