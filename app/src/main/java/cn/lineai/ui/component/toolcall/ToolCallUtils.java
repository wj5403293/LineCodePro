package cn.lineai.ui.component.toolcall;

import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolCategory;
import org.json.JSONObject;

/**
 * 工具调用工具集合的门面：保留原有方法签名以兼容调用方与测试，
 * 具体实现委托到 {@link ToolCallInputParser}、{@link ToolCallPathDisplay}、
 * {@link ToolCallJsonFormatter} 与 {@link ToolCategory}。
 */
final class ToolCallUtils {
    private ToolCallUtils() {
    }

    static JSONObject parseInput(ToolCall toolCall) {
        return ToolCallInputParser.parseInput(toolCall);
    }

    static String inputLabel(String name, JSONObject input) {
        return ToolCallInputParser.inputLabel(name, input);
    }

    static String displayInputLabel(String name, JSONObject input, String workspacePath) {
        return ToolCallInputParser.displayInputLabel(name, input, workspacePath);
    }

    static String workspaceDisplayPath(String workspacePath, String path) {
        return ToolCallPathDisplay.workspaceDisplayPath(workspacePath, path);
    }

    static String prettyJson(JSONObject input) {
        return ToolCallJsonFormatter.prettyJson(input);
    }

    static boolean isImageGenerationTool(String name) {
        return ToolCategory.isImageGenerationType(name);
    }

    static boolean isReadTool(String name) {
        return ToolCategory.isReadType(name);
    }

    static boolean isWriteTool(String name) {
        return ToolCategory.isWriteType(name);
    }

    static boolean isDeleteTool(String name) {
        return ToolCategory.isDeleteType(name);
    }

    static boolean isHttpTool(String name) {
        return ToolCategory.isHttpType(name);
    }

    static boolean isCustomMcpTool(String name) {
        return ToolCategory.isCustomMcpType(name);
    }

    static boolean isCustomAgentTool(String name) {
        return ToolCategory.isCustomAgentType(name);
    }

    static boolean isShellTool(String name) {
        return ToolCategory.isShellType(name);
    }

    static boolean isAgentTool(String name) {
        return ToolCategory.isAgentType(name);
    }

    static boolean isAgentPipelineTool(String name) {
        return ToolCategory.isAgentPipelineType(name);
    }

    static boolean isTodoTool(String name) {
        return ToolCategory.isTodoType(name);
    }
}
