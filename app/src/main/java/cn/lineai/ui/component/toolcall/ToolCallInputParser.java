package cn.lineai.ui.component.toolcall;

import cn.lineai.tool.ToolCall;
import org.json.JSONObject;

/**
 * 工具调用输入解析工具：从 ToolCall 提取 JSON 输入，并生成用于卡片展示的标签文本。
 */
final class ToolCallInputParser {
    private ToolCallInputParser() {
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

    static String displayInputLabel(String name, JSONObject input, String workspacePath) {
        if (input == null) {
            return name == null ? "" : name;
        }
        if ("file_read".equals(name)) {
            String filePath = input.optString("file_path");
            if (filePath.length() > 0) {
                return ToolCallPathDisplay.workspaceDisplayPath(workspacePath, filePath);
            }
        }
        if ("list_dir".equals(name)) {
            String path = input.optString("path");
            if (path.length() > 0) {
                return ToolCallPathDisplay.workspaceDisplayPath(workspacePath, path);
            }
        }
        return inputLabel(name, input);
    }
}
