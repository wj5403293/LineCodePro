package cn.lineai.tool;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ModelStore;
import cn.lineai.tool.builtin.FileIo;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import java.io.File;
import org.json.JSONObject;

public final class ToolExecutor {
    private final ToolRegistry registry;
    private final ToolSettingsStore settingsRepository;
    private final DiffStore diffRepository;
    private final ModelStore modelRepository;
    private final SshFileTreeStore sshFileTreeRepository;
    private final ModelServiceProvider modelServiceProvider;
    private final PromptTemplateRepository promptTemplateRepository;

    public ToolExecutor(ToolRegistry registry, ToolSettingsStore settingsRepository) {
        this(registry, settingsRepository, null, null, null, null, null);
    }

    public ToolExecutor(ToolRegistry registry, ToolSettingsStore settingsRepository, DiffStore diffRepository) {
        this(registry, settingsRepository, diffRepository, null, null, null, null);
    }

    public ToolExecutor(
            ToolRegistry registry,
            ToolSettingsStore settingsRepository,
            DiffStore diffRepository,
            ModelStore modelRepository,
            SshFileTreeStore sshFileTreeRepository,
            ModelServiceProvider modelServiceProvider,
            PromptTemplateRepository promptTemplateRepository
    ) {
        this.registry = registry;
        this.settingsRepository = settingsRepository;
        this.diffRepository = diffRepository;
        this.modelRepository = modelRepository;
        this.sshFileTreeRepository = sshFileTreeRepository;
        this.modelServiceProvider = modelServiceProvider;
        this.promptTemplateRepository = promptTemplateRepository;
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
        ToolContext baseContext = context == null ? ToolContext.builder().homePath("").build() : context;
        if (context == null || needsInjection(context)) {
            baseContext = injectDependencies(baseContext);
        }
        ToolContext callContext = baseContext.withToolCallId(toolCall.getId());
        BaseTool tool = registry.get(toolCall.getName());
        if (tool == null) {
            return new ToolResult(toolCall.getId(), toolCall.getName(), "未知工具: " + toolCall.getName(), true);
        }
        PermissionResult permission = settingsRepository.canExecuteTool(tool.getName(), tool.getCategory());
        if (!permission.isAllowed()) {
            return new ToolResult(toolCall.getId(), tool.getName(), permission.getReason(), true);
        }
        if (tool.needsConfirmation() && settingsRepository.needsConfirmation(tool.getName()) && !confirmed) {
            return new ToolResult(toolCall.getId(), tool.getName(), "工具需要确认后才能执行: " + tool.getName(), true);
        }
        JSONObject input;
        try {
            String args = ToolArgsCleaner.clean(toolCall.getArguments());
            input = args.trim().length() == 0 ? new JSONObject() : new JSONObject(args);
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

    private boolean needsInjection(ToolContext context) {
        return context.getToolSettingsStore() == null
                || context.getModelRepository() == null
                || context.getModelServiceProvider() == null;
    }

    private ToolContext injectDependencies(ToolContext context) {
        return ToolContext.builder()
                .homePath(context.getHomePath())
                .extraWriteRoots(context.getExtraWriteRoots())
                .agentRunner(context.getAgentRunner())
                .toolCallId(context.getToolCallId())
                .todoStateStore(context.getTodoStateStore())
                .toolSettingsStore(context.getToolSettingsStore() != null ? context.getToolSettingsStore() : settingsRepository)
                .modelRepository(context.getModelRepository() != null ? context.getModelRepository() : modelRepository)
                .sshFileTreeRepository(context.getSshFileTreeRepository() != null ? context.getSshFileTreeRepository() : sshFileTreeRepository)
                .modelServiceProvider(context.getModelServiceProvider() != null ? context.getModelServiceProvider() : modelServiceProvider)
                .promptTemplateRepository(context.getPromptTemplateRepository() != null ? context.getPromptTemplateRepository() : promptTemplateRepository)
                .build();
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
        ExceptionUtils.restoreInterrupt(error);
    }

    private static String describeException(Exception error) {
        return ExceptionUtils.describeException(error);
    }
}
