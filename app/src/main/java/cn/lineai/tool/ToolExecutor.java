package cn.lineai.tool;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.tool.builtin.FileIo;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import java.io.File;
import org.json.JSONObject;

public final class ToolExecutor {
    private final ToolRegistry registry;
    private final ToolSettingsStore settingsRepository;
    private final DiffStore diffRepository;

    public ToolExecutor(ToolRegistry registry, ToolSettingsStore settingsRepository) {
        this(registry, settingsRepository, null);
    }

    public ToolExecutor(ToolRegistry registry, ToolSettingsStore settingsRepository, DiffStore diffRepository) {
        this.registry = registry;
        this.settingsRepository = settingsRepository;
        this.diffRepository = diffRepository;
    }

    public ToolResult execute(ToolCall toolCall, ToolContext context) {
        return execute(toolCall, context, false);
    }

    public ToolResult executeConfirmed(ToolCall toolCall, ToolContext context) {
        return execute(toolCall, context, true);
    }

    private ToolResult execute(ToolCall toolCall, ToolContext context, boolean confirmed) {
        if (toolCall == null) {
            return new ToolResult("", "", "工具调用为空", true);
        }
        ToolContext callContext = (context == null ? new ToolContext("") : context).withToolCallId(toolCall.getId());
        BaseTool tool = registry.get(toolCall.getName());
        if (tool == null) {
            return new ToolResult(toolCall.getId(), toolCall.getName(), "未知工具: " + toolCall.getName(), true);
        }
        PermissionResult permission = settingsRepository.canExecuteTool(tool.getName(), tool.getCategory());
        if (!permission.isAllowed()) {
            return new ToolResult(toolCall.getId(), tool.getName(), permission.getReason(), true);
        }
        if (tool.requiresConfirmation() && settingsRepository.needsConfirmation(tool.getName()) && !confirmed) {
            return new ToolResult(toolCall.getId(), tool.getName(), "工具需要确认后才能执行: " + tool.getName(), true);
        }
        JSONObject input;
        try {
            input = toolCall.getArguments().trim().length() == 0
                    ? new JSONObject()
                    : new JSONObject(toolCall.getArguments());
        } catch (Exception e) {
            restoreInterrupt(e);
            return new ToolResult(toolCall.getId(), tool.getName(), "参数解析失败: " + describeException(e), true);
        }
        try {
            ToolResult result = shouldRecordDiff(tool)
                    ? executeWithDiff(tool, input, callContext)
                    : tool.execute(input, callContext);
            return result.withCall(toolCall.getId(), tool.getName());
        } catch (Exception e) {
            restoreInterrupt(e);
            return new ToolResult(toolCall.getId(), tool.getName(), "工具执行失败: " + describeException(e), true);
        }
    }

    private boolean shouldRecordDiff(BaseTool tool) {
        return diffRepository != null && tool.shouldRecordDiff();
    }

    private ToolResult executeWithDiff(BaseTool tool, JSONObject input, ToolContext context) {
        String path = input.optString("file_path");
        File file;
        boolean existed;
        String oldContent = "";
        try {
            file = FileToolPathPolicy.resolve(context, path);
            existed = file.exists();
            if (existed && file.isDirectory()) {
                return new ToolResult("", tool.getName(), "路径是一个目录，无法写入文件: " + path, true);
            }
            if (existed) {
                oldContent = FileIo.readUtf8(file);
            }
        } catch (Exception e) {
            restoreInterrupt(e);
            return new ToolResult("", tool.getName(), "无法读取原文件: " + path + "\n" + describeException(e), true);
        }

        ToolResult result = tool.execute(input, context);
        if (result.isError()) {
            return result;
        }

        String newContent;
        try {
            newContent = file.exists() ? FileIo.readUtf8(file) : "";
        } catch (Exception ignored) {
            newContent = input.optString("content");
        }
        if (!oldContent.equals(newContent)) {
            DiffRecord diff = diffRepository.recordDiff(file.getPath(), oldContent, newContent, existed);
            return result.withDiffId(diff.getId());
        }
        return result;
    }

    private static void restoreInterrupt(Exception error) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describeException(Exception error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        if (message != null && message.trim().length() > 0) {
            return message.trim();
        }
        String name = error.getClass().getSimpleName();
        return name.length() == 0 ? "未知错误" : name;
    }
}
