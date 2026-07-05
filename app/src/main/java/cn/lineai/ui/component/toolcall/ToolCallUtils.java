package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolRegistry;
import org.json.JSONObject;

/**
 * 工具调用工具集合的门面：聚合 {@link ToolCallInputParser}、
 * {@link ToolCallPathDisplay}、{@link ToolCallJsonFormatter}、
 * {@link ToolCategory} 的静态方法，调用方只需 import 这一个类。
 */
public final class ToolCallUtils {
    private static ToolRegistry toolRegistry;

    private ToolCallUtils() {
    }

    public static void setToolRegistry(ToolRegistry registry) {
        toolRegistry = registry;
    }

    static ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public static ToolDisplayCategory getDisplayCategory(String name) {
        if (toolRegistry != null) {
            return toolRegistry.getCachedDisplayCategory(name);
        }
        return fallbackDisplayCategory(name);
    }

    private static ToolDisplayCategory fallbackDisplayCategory(String name) {
        if (name == null) return ToolDisplayCategory.GENERIC;
        if ("file_read".equals(name) || "glob".equals(name) || "list_dir".equals(name)
                || "web_search".equals(name) || "web_fetch".equals(name)
                || "image_understanding".equals(name)) return ToolDisplayCategory.READ;
        if ("file_write".equals(name) || "file_edit".equals(name)) return ToolDisplayCategory.WRITE;
        if ("file_delete".equals(name)) return ToolDisplayCategory.DELETE;
        if ("http_server".equals(name)) return ToolDisplayCategory.HTTP;
        if ("shell_execute".equals(name)) return ToolDisplayCategory.SHELL;
        if ("agent".equals(name)) return ToolDisplayCategory.AGENT;
        if ("agent_pipeline".equals(name)) return ToolDisplayCategory.AGENT_PIPELINE;
        if ("todo_update".equals(name)) return ToolDisplayCategory.TODO;
        if ("image_generation".equals(name)) return ToolDisplayCategory.IMAGE_GENERATION;
        if (name.startsWith("phone_")) return ToolDisplayCategory.PHONE_CONTROL;
        if (name.startsWith("agentx_")) return ToolDisplayCategory.AGENT;
        if (name.startsWith("mcpx_")) return ToolDisplayCategory.GENERIC;
        return ToolDisplayCategory.GENERIC;
    }

    static JSONObject parseInput(ToolCall toolCall) {
        return ToolCallInputParser.parseInput(toolCall);
    }

    static String inputLabel(String name, JSONObject input) {
        return ToolCallInputParser.inputLabel(name, input);
    }

    static String displayInputLabel(Context context, String name, JSONObject input, String workspacePath) {
        return ToolCallInputParser.displayInputLabel(context, name, input, workspacePath);
    }

    static String workspaceDisplayPath(String workspacePath, String path) {
        return ToolCallPathDisplay.workspaceDisplayPath(workspacePath, path);
    }

    static String prettyJson(JSONObject input) {
        return ToolCallJsonFormatter.prettyJson(input);
    }

    static boolean isImageGenerationTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.IMAGE_GENERATION;
    }

    static boolean isReadTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.READ;
    }

    static boolean isWriteTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.WRITE;
    }

    static boolean isDeleteTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.DELETE;
    }

    static boolean isHttpTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.HTTP;
    }

    static boolean isCustomMcpTool(String name) {
        return name != null && name.startsWith("mcpx_");
    }

    static boolean isCustomAgentTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.AGENT && name != null && name.startsWith("agentx_");
    }

    static boolean isShellTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.SHELL;
    }

    static boolean isAgentTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.AGENT && (name == null || !name.startsWith("agentx_"));
    }

    static boolean isAgentPipelineTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.AGENT_PIPELINE;
    }

    static boolean isTodoTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.TODO;
    }

    static boolean isPhoneControlTool(String name) {
        return getDisplayCategory(name) == ToolDisplayCategory.PHONE_CONTROL;
    }
}
