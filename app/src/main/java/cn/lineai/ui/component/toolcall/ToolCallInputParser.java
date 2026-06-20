package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolCategory;
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

    static String displayInputLabel(Context context, String name, JSONObject input, String workspacePath) {
        if (input == null) {
            return name == null ? "" : name;
        }
        if (ToolCategory.isPhoneControlType(name)) {
            return phoneControlLabel(context, name, input);
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

    private static String phoneControlLabel(Context context, String name, JSONObject input) {
        if ("phone_screenshot".equals(name)) {
            return context.getString(R.string.tool_call_phone_summary_screenshot);
        }
        if ("phone_view_hierarchy".equals(name)) {
            return context.getString(R.string.tool_call_phone_summary_view_hierarchy);
        }
        if ("phone_global_action".equals(name)) {
            String action = input.optString("action");
            return action.length() == 0
                    ? context.getString(R.string.tool_call_phone_summary_global_action)
                    : globalActionLabel(context, action);
        }
        if ("phone_click_view".equals(name)) {
            String resourceId = input.optString("resource_id");
            if (resourceId.length() > 0) {
                return context.getString(R.string.tool_call_phone_summary_click_view, resourceId);
            }
            String text = input.optString("text");
            if (text.length() > 0) {
                return context.getString(R.string.tool_call_phone_summary_click_text, text);
            }
        }
        if ("phone_swipe".equals(name)) {
            return context.getString(R.string.tool_call_phone_summary_swipe,
                    input.optInt("x1"), input.optInt("y1"), input.optInt("x2"), input.optInt("y2"));
        }
        if ("phone_click".equals(name) || "phone_long_press".equals(name) || "phone_click_view".equals(name)) {
            return context.getString(R.string.tool_call_phone_summary_point, input.optInt("x"), input.optInt("y"));
        }
        return name == null ? "" : name;
    }

    static String phoneControlActionName(Context context, String name) {
        if ("phone_screenshot".equals(name)) return context.getString(R.string.tool_call_phone_action_screenshot);
        if ("phone_click".equals(name)) return context.getString(R.string.tool_call_phone_action_click);
        if ("phone_swipe".equals(name)) return context.getString(R.string.tool_call_phone_action_swipe);
        if ("phone_long_press".equals(name)) return context.getString(R.string.tool_call_phone_action_long_press);
        if ("phone_view_hierarchy".equals(name)) return context.getString(R.string.tool_call_phone_action_view_hierarchy);
        if ("phone_click_view".equals(name)) return context.getString(R.string.tool_call_phone_action_click_view);
        if ("phone_global_action".equals(name)) return context.getString(R.string.tool_call_phone_action_global_action);
        return context.getString(R.string.tool_call_phone_action_default);
    }

    private static String globalActionLabel(Context context, String action) {
        if ("back".equals(action)) return context.getString(R.string.tool_call_phone_global_back);
        if ("home".equals(action)) return context.getString(R.string.tool_call_phone_global_home);
        if ("exit_app".equals(action)) return context.getString(R.string.tool_call_phone_global_exit_app);
        if ("recents".equals(action)) return context.getString(R.string.tool_call_phone_global_recents);
        if ("notifications".equals(action)) return context.getString(R.string.tool_call_phone_global_notifications);
        if ("quick_settings".equals(action)) return context.getString(R.string.tool_call_phone_global_quick_settings);
        if ("power_dialog".equals(action)) return context.getString(R.string.tool_call_phone_global_power_dialog);
        if ("lock_screen".equals(action)) return context.getString(R.string.tool_call_phone_global_lock_screen);
        return context.getString(R.string.tool_call_phone_global_unknown, action);
    }
}
