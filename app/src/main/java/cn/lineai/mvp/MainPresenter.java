package cn.lineai.mvp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.ToolCallTextParser;
import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.context.ContextManager;
import cn.lineai.context.ContextSnapshot;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationRepository;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.FileTreeRepository;
import cn.lineai.data.repository.LearningContextRepository;
import cn.lineai.data.repository.MemoryExtractionService;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextInfo;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelRepository;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.SheetOption;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.PermissionResult;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.StoragePermissionManager;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainPresenter implements MainContract.Presenter {
    private static final long STREAM_RENDER_INTERVAL_MS = 80L;
    private static final long AGENT_PROGRESS_RENDER_INTERVAL_MS = 100L;
    private static final int AGENT_MAX_TURNS = 8;
    private static final String AGENT_TERMINATED_MESSAGE = "Agent 已终止。";

    private final ArrayList<ChatMessage> messages = new ArrayList<>();
    private final ArrayList<String> screenStack = new ArrayList<>();
    private final Set<String> expandedFilePaths = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ModelRepository modelRepository;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final OutputSettingsRepository outputSettingsRepository;
    private final ConversationRepository conversationRepository;
    private final ProjectRepository projectRepository;
    private final LearningContextRepository learningContextRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final ToolSettingsRepository toolSettingsRepository;
    private final DiffRepository diffRepository;
    private final FileTreeRepository fileTreeRepository = new FileTreeRepository();
    private final ContextManager contextManager = new ContextManager();
    private final ModelClient modelClient = new ModelClient();
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolExecutionCoordinator toolExecutionCoordinator = new ToolExecutionCoordinator();
    private final SystemPromptProvider systemPromptProvider;
    private final StoragePermissionManager storagePermissionManager;
    private final SafPathResolver safPathResolver = new SafPathResolver();
    private MainContract.View view;
    private ModelCancellationToken currentCancellationToken;
    private int messageSequence = 1;
    private int generationSequence = 1;
    private final StringBuilder pendingStreamTextDelta = new StringBuilder();
    private final StringBuilder pendingStreamReasoningDelta = new StringBuilder();
    private final HashMap<String, StringBuilder> streamingRawTextByMessageId = new HashMap<>();
    private String pendingStreamAssistantId = "";
    private int pendingStreamGenerationId = -1;
    private boolean streamRenderScheduled;
    private long lastStreamRenderAt;
    private boolean streaming;
    private String currentConversationId = "";
    private long currentConversationCreatedAt;
    private String projectLabel = "LineCode";
    private String projectPath = "";
    private String permissionMode = "ask";
    private boolean pendingExternalProjectOpen;
    private PendingToolExecution pendingToolExecution;

    private static final class ToolExecutionBatch {
        private final ArrayList<ToolResult> completedResults;
        private final ToolCall pendingCall;
        private final ArrayList<ToolCall> remainingCalls;

        ToolExecutionBatch(ArrayList<ToolResult> completedResults, ToolCall pendingCall, ArrayList<ToolCall> remainingCalls) {
            this.completedResults = completedResults == null ? new ArrayList<>() : completedResults;
            this.pendingCall = pendingCall;
            this.remainingCalls = remainingCalls == null ? new ArrayList<>() : remainingCalls;
        }
    }

    private static final class PendingToolExecution {
        private final int generationId;
        private final ModelConfig selectedModel;
        private final ToolCall toolCall;
        private final ArrayList<ToolCall> remainingCalls;
        private final int usedToolCallCount;
        private final String homePath;
        private final ModelCancellationToken cancellationToken;

        PendingToolExecution(
                int generationId,
                ModelConfig selectedModel,
                ToolCall toolCall,
                ArrayList<ToolCall> remainingCalls,
                int usedToolCallCount,
                String homePath,
                ModelCancellationToken cancellationToken
        ) {
            this.generationId = generationId;
            this.selectedModel = selectedModel;
            this.toolCall = toolCall;
            this.remainingCalls = remainingCalls == null ? new ArrayList<>() : new ArrayList<>(remainingCalls);
            this.usedToolCallCount = usedToolCallCount;
            this.homePath = homePath == null ? "" : homePath;
            this.cancellationToken = cancellationToken;
        }
    }

    private static final class AgentRunResult {
        private final String output;
        private final int toolCallCount;
        private final boolean error;

        AgentRunResult(String output, int toolCallCount, boolean error) {
            this.output = output == null ? "" : output;
            this.toolCallCount = toolCallCount;
            this.error = error;
        }
    }

    private static final class PipelineAgent {
        private final String id;
        private final String type;
        private final String description;
        private final String prompt;
        private final ArrayList<String> dependencies;

        PipelineAgent(String id, String type, String description, String prompt, ArrayList<String> dependencies) {
            this.id = id == null ? "" : id;
            this.type = type == null ? "" : type;
            this.description = description == null ? "" : description;
            this.prompt = prompt == null ? "" : prompt;
            this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
        }
    }

    private final class AgentProgressSession {
        private final int generationId;
        private final String toolCallId;
        private final String toolName;
        private final String type;
        private final String description;
        private final LinkedHashMap<String, ToolCall> displayToolCalls = new LinkedHashMap<>();
        private final LinkedHashMap<String, ToolResult> displayToolResults = new LinkedHashMap<>();
        private final HashMap<String, String> displayIdByOriginalId = new HashMap<>();
        private String output = "";
        private String thinking = "";
        private String status = "running";
        private String modelContent = "";
        private boolean error;
        private boolean renderScheduled;
        private long lastRenderAt;

        AgentProgressSession(int generationId, String toolCallId, String toolName, String type, String description) {
            this.generationId = generationId;
            this.toolCallId = toolCallId == null ? "" : toolCallId;
            this.toolName = toolName == null ? "" : toolName;
            this.type = type == null ? "" : type;
            this.description = description == null ? "" : description;
        }

        synchronized boolean canRender() {
            return toolCallId.length() > 0;
        }

        synchronized void beginTurn() {
            output = "";
            status = "running";
            error = false;
        }

        synchronized void appendText(String delta) {
            if (delta != null && delta.length() > 0) {
                output += delta;
                addToolCalls(ToolCallTextParser.parseStreamingPreview(output).getToolCalls());
            }
        }

        synchronized void appendThinking(String delta) {
            if (delta != null && delta.length() > 0) {
                thinking += delta;
            }
        }

        synchronized void setTurnResult(String nextOutput, String nextThinking) {
            output = nextOutput == null ? "" : nextOutput;
            if (nextThinking != null && nextThinking.trim().length() > 0) {
                thinking = nextThinking;
            }
        }

        synchronized void addToolCalls(List<ToolCall> calls) {
            if (calls == null) {
                return;
            }
            for (ToolCall call : calls) {
                if (call == null || call.getId().length() == 0 || call.getName().length() == 0) {
                    continue;
                }
                if (displayIdByOriginalId.containsKey(call.getId())) {
                    continue;
                }
                String displayId = toolCallId + "_agent_" + displayToolCalls.size();
                displayIdByOriginalId.put(call.getId(), displayId);
                displayToolCalls.put(displayId, new ToolCall(displayId, call.getName(), call.getArguments()));
            }
        }

        synchronized void putToolResult(ToolCall originalCall, ToolResult result) {
            if (originalCall == null || result == null) {
                return;
            }
            String displayId = displayIdByOriginalId.get(originalCall.getId());
            if (displayId == null || displayId.length() == 0) {
                addToolCalls(java.util.Collections.singletonList(originalCall));
                displayId = displayIdByOriginalId.get(originalCall.getId());
            }
            if (displayId == null || displayId.length() == 0) {
                return;
            }
            displayToolResults.put(displayId, result.withCall(displayId, originalCall.getName()));
        }

        synchronized void setFinished(String nextStatus, boolean nextError, String nextModelContent) {
            status = nextStatus == null || nextStatus.length() == 0 ? "done" : nextStatus;
            error = nextError;
            modelContent = nextModelContent == null ? "" : nextModelContent;
        }

        synchronized boolean shouldScheduleRender() {
            if (!canRender() || renderScheduled) {
                return false;
            }
            renderScheduled = true;
            return true;
        }

        synchronized long renderDelayMs() {
            long now = SystemClock.uptimeMillis();
            return Math.max(0L, AGENT_PROGRESS_RENDER_INTERVAL_MS - (now - lastRenderAt));
        }

        synchronized ToolResult snapshotResult() {
            renderScheduled = false;
            lastRenderAt = SystemClock.uptimeMillis();
            return new ToolResult(toolCallId, toolName, payload(), error);
        }

        private String payload() {
            try {
                JSONObject object = new JSONObject();
                object.put("linecode_agent_progress", true);
                object.put("kind", toolName);
                object.put("status", status);
                object.put("type", type);
                object.put("description", description);
                object.put("output", visibleOutput(output));
                object.put("thinking", thinking);
                object.put("tool_call_count", displayToolCalls.size());
                object.put("model_content", modelContent);
                JSONArray tools = new JSONArray();
                for (ToolCall call : displayToolCalls.values()) {
                    JSONObject item = new JSONObject()
                            .put("id", call.getId())
                            .put("name", call.getName())
                            .put("arguments", call.getArguments());
                    ToolResult result = displayToolResults.get(call.getId());
                    if (result != null) {
                        item.put("result", new JSONObject()
                                .put("content", result.getContent())
                                .put("is_error", result.isError())
                                .put("diff_id", result.getDiffId())
                                .put("review_state", result.getReviewState())
                                .put("review_message", result.getReviewMessage()));
                    }
                    tools.put(item);
                }
                object.put("tool_calls", tools);
                return object.toString();
            } catch (Exception e) {
                return modelContent.length() > 0 ? modelContent : visibleOutput(output);
            }
        }

        private String visibleOutput(String rawOutput) {
            ToolCallTextParser.Result parsed = ToolCallTextParser.parse(rawOutput);
            return parsed.hasToolMarkup() ? parsed.getText() : rawOutput == null ? "" : rawOutput;
        }
    }

    public MainPresenter(Context context) {
        modelRepository = new ModelRepository(context);
        aiBehaviorSettingsRepository = new AiBehaviorSettingsRepository(context);
        outputSettingsRepository = new OutputSettingsRepository(context);
        conversationRepository = new ConversationRepository(context);
        projectRepository = new ProjectRepository(context);
        learningContextRepository = new LearningContextRepository(context);
        memoryExtractionService = new MemoryExtractionService(context, learningContextRepository);
        toolSettingsRepository = new ToolSettingsRepository(context);
        diffRepository = new DiffRepository(context);
        toolRegistry = new ToolRegistry(context);
        toolExecutor = new ToolExecutor(toolRegistry, toolSettingsRepository, diffRepository);
        systemPromptProvider = new SystemPromptProvider(context);
        storagePermissionManager = new StoragePermissionManager(context);
        permissionMode = toolSettingsRepository.getPermissionMode();
        applyProject(projectRepository.ensureSelectedProjectPath());
        expandedFilePaths.add(projectPath);
        loadCurrentConversation();
    }

    @Override
    public void attachView(MainContract.View view) {
        this.view = view;
        render();
    }

    @Override
    public void detachView() {
        view = null;
    }

    @Override
    public void onMenuClick() {
        if (view != null) {
            view.showDrawer();
        }
    }

    @Override
    public void onProjectClick() {
        if (view == null) {
            return;
        }
        ArrayList<SheetOption> options = new ArrayList<>();
        ProjectRecord selected = projectRepository.getSelectedProject();
        List<ProjectRecord> projects = projectRepository.getProjects();
        for (ProjectRecord project : projects) {
            options.add(new SheetOption(
                    "project:select:" + project.getId(),
                    project.getLabel(),
                    project.getDescription().length() > 0 ? project.getDescription() : WorkspacePaths.displayPath(project.getPath()),
                    project.getId().equals(selected.getId())
            ));
        }
        options.add(new SheetOption("project:open", "打开外部工作区", "通过 SAF 选择外部目录，并使用真实 /storage 路径", false));
        options.add(new SheetOption("project:create", "创建工作区", "在 .linecode/project 下创建托管项目", false));
        options.add(new SheetOption("storage:manage_all_files", "管理所有文件权限",
                storagePermissionManager.hasExternalStorageAccess() ? "已授权，可访问外部工作区" : storagePermissionManager.permissionDeniedMessage(),
                storagePermissionManager.hasExternalStorageAccess()));
        view.showSheet("工作区", options);
    }

    @Override
    public void onPermissionClick() {
        if (view == null) {
            return;
        }
        permissionMode = toolSettingsRepository.getPermissionMode();
        ArrayList<SheetOption> options = new ArrayList<>();
        options.add(new SheetOption(ToolSettingsRepository.PERMISSION_AUTO, "自动", "自动执行已启用工具，危险工具按策略确认",
                ToolSettingsRepository.PERMISSION_AUTO.equals(permissionMode)));
        options.add(new SheetOption(ToolSettingsRepository.PERMISSION_CONFIRM, "确认", "危险操作需要确认后执行",
                ToolSettingsRepository.PERMISSION_CONFIRM.equals(permissionMode)));
        options.add(new SheetOption(ToolSettingsRepository.PERMISSION_READONLY, "只读", "仅允许读取、搜索和列目录，禁止写入与 Shell",
                ToolSettingsRepository.PERMISSION_READONLY.equals(permissionMode)));
        options.add(new SheetOption("storage:manage_all_files", "管理所有文件权限",
                storagePermissionManager.hasExternalStorageAccess() ? "已授权，可访问外部工作区" : storagePermissionManager.permissionDeniedMessage(),
                storagePermissionManager.hasExternalStorageAccess()));
        view.showSheet("权限设置", options);
    }

    @Override
    public void onNewConversation() {
        cancelActiveGeneration();
        streaming = false;
        messages.clear();
        currentConversationId = String.valueOf(System.currentTimeMillis());
        currentConversationCreatedAt = System.currentTimeMillis();
        persistCurrentConversation();
        render();
    }

    @Override
    public void onConversationSelected(String id) {
        if (id == null || id.length() == 0 || id.equals(currentConversationId)) {
            if (view != null) {
                view.hideOverlays();
            }
            return;
        }
        cancelActiveGeneration();
        streaming = false;
        loadConversation(id);
        if (view != null) {
            view.hideOverlays();
        }
        render();
    }

    @Override
    public void onConversationDeleted(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        conversationRepository.deleteConversation(id);
        if (id.equals(currentConversationId)) {
            cancelActiveGeneration();
            streaming = false;
            messages.clear();
            currentConversationId = "";
            currentConversationCreatedAt = 0;
        }
        render();
    }

    @Override
    public void onFileNodeSelected(String path) {
        if (path == null || path.length() == 0) {
            return;
        }
        if (fileTreeRepository.isDirectory(path)) {
            if (expandedFilePaths.contains(path)) {
                expandedFilePaths.remove(path);
            } else {
                expandedFilePaths.add(path);
            }
            render();
        }
    }

    @Override
    public void onFileTreeRefresh() {
        expandedFilePaths.add(projectPath);
        render();
    }

    @Override
    public void onMoreClick() {
        if (view == null) {
            return;
        }
        ArrayList<SheetOption> options = new ArrayList<>();
        options.add(new SheetOption("tutorial", "教程", "打开初学者教程", false));
        options.add(new SheetOption("settings", "设置", "模型、主题、数据与实验功能", false));
        options.add(new SheetOption("compact", "压缩上下文", "将早期上下文总结为隐藏摘要", false));
        options.add(new SheetOption("clear", "清空对话", "清空当前对话消息", false));
        view.showSheet("更多", options);
    }

    @Override
    public void onSendMessage(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || streaming) {
            return;
        }
        ensureCurrentConversation();
        messages.add(new ChatMessage(nextId(), ChatMessage.Role.USER, trimmed, false));
        persistCurrentConversation();
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT,
                    "还没有可用模型。请进入 设置 → 模型管理 → 添加模型，保存后再发送消息。",
                    false));
            persistCurrentConversation();
            render();
            return;
        }

        int generationId = generationSequence++;
        ArrayList<ModelMessage> requestMessages = buildModelMessages(trimmed);
        String assistantId = nextId();
        ModelCancellationToken cancellationToken = new ModelCancellationToken();
        currentCancellationToken = cancellationToken;
        streaming = true;
        streamingRawTextByMessageId.put(assistantId, new StringBuilder());
        messages.add(new ChatMessage(assistantId, ChatMessage.Role.ASSISTANT, "", true));
        render();

        new Thread(() -> {
            try {
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                ModelRequestOptions requestOptions = requestOptions(aiSettings, selectedModel, 0);
                ModelCompletionResponse response = modelClient.stream(selectedModel, requestMessages, new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        appendAssistantDelta(generationId, assistantId, delta, "");
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        appendAssistantDelta(generationId, assistantId, "", delta);
                    }
                }, cancellationToken, requestOptions);
                finishGeneration(generationId, assistantId, selectedModel, requestOptions, response, 0);
            } catch (ModelCompletionException e) {
                failGeneration(generationId, assistantId, "模型通信失败：\n" + e.getMessage());
            }
        }, "linecode-model-stream").start();
    }

    @Override
    public void onStopGeneration() {
        flushPendingAssistantDelta();
        cancelActiveGeneration();
        streaming = false;
        generationSequence++;
        streamingRawTextByMessageId.clear();
        markStreamingMessagesStopped();
        markRunningAgentProgressStopped();
        persistCurrentConversation();
        render();
    }

    @Override
    public void onToolReview(String toolCallId, String state, String diffId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        if (pendingToolExecution != null && toolCallId.equals(pendingToolExecution.toolCall.getId())) {
            handlePendingToolReview(state);
            return;
        }
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        String resolvedDiffId = diffId == null ? "" : diffId;
        if ("rejected".equals(normalizedState)) {
            if (resolvedDiffId.length() == 0) {
                resolvedDiffId = findToolMessageDiffId(toolCallId);
            }
            if (resolvedDiffId.length() > 0) {
                String targetDiffId = resolvedDiffId;
                new Thread(() -> {
                    DiffRepository.RevertResult result = diffRepository.revertDiff(targetDiffId);
                    mainHandler.post(() -> {
                        updateToolReview(toolCallId, targetDiffId, result.isSuccess() ? "rejected" : "", result.getMessage());
                        persistCurrentConversation();
                        render();
                    });
                }, "linecode-diff-revert").start();
                return;
            }
        }
        updateToolReview(toolCallId, resolvedDiffId, normalizedState, "");
        persistCurrentConversation();
        render();
    }

    @Override
    public void onSheetOptionSelected(String id) {
        if (id != null && id.startsWith("project:select:")) {
            selectProject(id.substring("project:select:".length()));
        } else if ("project:open".equals(id)) {
            requestOpenExternalProject();
        } else if ("project:create".equals(id)) {
            ProjectRecord project = projectRepository.createManagedProject("Project-" + System.currentTimeMillis());
            applyProject(project);
        } else if ("storage:manage_all_files".equals(id)) {
            openStoragePermissionSettings();
        } else if (isPermissionModeOption(id)) {
            permissionMode = ToolSettingsRepository.normalizePermissionMode(id);
            toolSettingsRepository.setPermissionMode(permissionMode);
        } else if ("settings".equals(id)) {
            showScreen("settings");
        } else if ("tutorial".equals(id)) {
            showScreen("tutorial");
        } else if ("clear".equals(id)) {
            messages.clear();
            persistCurrentConversation();
        }
        if (view != null && !"settings".equals(id) && !"tutorial".equals(id)) {
            view.hideOverlays();
        }
        render();
    }

    private boolean isPermissionModeOption(String id) {
        return ToolSettingsRepository.PERMISSION_AUTO.equals(id)
                || ToolSettingsRepository.PERMISSION_CONFIRM.equals(id)
                || ToolSettingsRepository.PERMISSION_READONLY.equals(id)
                || "ask".equals(id)
                || "workspace".equals(id)
                || "manual".equals(id);
    }

    @Override
    public void onScreenBack() {
        if (screenStack.isEmpty()) {
            return;
        }
        screenStack.remove(screenStack.size() - 1);
        if (view == null) {
            return;
        }
        if (screenStack.isEmpty()) {
            view.showChatScreen();
        } else {
            view.showScreen(screenStack.get(screenStack.size() - 1));
        }
    }

    @Override
    public void onSettingsItemSelected(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        showScreen(id);
    }

    @Override
    public void onOpenUrl(String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (safeUrl.length() == 0) {
            return;
        }
        if (OutputSettings.BROWSER_EXTERNAL.equals(outputSettingsRepository.get().getBrowserMode())) {
            if (view != null) {
                view.openExternalUrl(safeUrl);
            }
            return;
        }
        showScreen("browser:" + safeUrl);
    }

    @Override
    public AiBehaviorSettings getAiBehaviorSettings() {
        return aiBehaviorSettingsRepository.get();
    }

    @Override
    public void onAiToneModeChanged(String toneMode) {
        aiBehaviorSettingsRepository.setToneMode(toneMode);
    }

    @Override
    public void onAiReasoningEffortChanged(String effort) {
        aiBehaviorSettingsRepository.setReasoningEffort(effort);
    }

    @Override
    public void onAiThinkingScrollChanged(boolean enabled) {
        aiBehaviorSettingsRepository.setThinkingScrollEnabled(enabled);
        render();
    }

    @Override
    public void onAiThinkingAutoExpandChanged(boolean enabled) {
        aiBehaviorSettingsRepository.setThinkingAutoExpandEnabled(enabled);
        render();
    }

    @Override
    public void onAiPreserveReasoningChanged(boolean enabled) {
        aiBehaviorSettingsRepository.setPreserveReasoningEnabled(enabled);
    }

    @Override
    public void onAiLearningModeChanged(boolean enabled) {
        aiBehaviorSettingsRepository.setLearningModeEnabled(enabled);
    }

    @Override
    public MemoryOverviewState getMemoryOverview() {
        return learningContextRepository.getOverview(projectPath);
    }

    @Override
    public void onMemorySaved(String id, String scope, String content) {
        learningContextRepository.saveMemory(id, scope, projectPath, content);
    }

    @Override
    public void onMemoryDeleted(String id) {
        learningContextRepository.deleteMemory(id);
    }

    @Override
    public OutputSettings getOutputSettings() {
        return outputSettingsRepository.get();
    }

    @Override
    public void onCodeWrapChanged(boolean enabled) {
        outputSettingsRepository.setCodeWrapEnabled(enabled);
        render();
    }

    @Override
    public void onBrowserModeChanged(String mode) {
        outputSettingsRepository.setBrowserMode(mode);
        render();
    }

    @Override
    public McpSettingsState getMcpSettingsState() {
        return toolSettingsRepository.getMcpSettingsState();
    }

    @Override
    public void onMcpExecutionModeChanged(String mode) {
        toolSettingsRepository.setExecutionMode(mode);
        if (view != null) {
            view.showScreen("mcp");
        }
        render();
    }

    @Override
    public void onMcpToolGroupChanged(String id, boolean enabled) {
        toolSettingsRepository.setMcpEnabled(id, enabled);
        if (view != null) {
            view.showScreen("mcp");
        }
        render();
    }

    @Override
    public void onMcpWebSearchConfigChanged(WebSearchConfig config) {
        toolSettingsRepository.setWebSearchConfig(config);
    }

    @Override
    public List<ModelConfig> getModels() {
        return modelRepository.getModels();
    }

    @Override
    public ModelConfig getModel(String id) {
        return modelRepository.getModel(id);
    }

    @Override
    public List<ConversationRecord> getConversationMetas() {
        return conversationRepository.getConversationMetas();
    }

    @Override
    public String getCurrentConversationId() {
        return currentConversationId;
    }

    @Override
    public FileTreeNode getFileTree() {
        return fileTreeRepository.buildTree(projectPath, expandedFilePaths);
    }

    @Override
    public String getSelectedModelId() {
        return modelRepository.getSelectedModelId();
    }

    @Override
    public void onModelSelected(String id) {
        modelRepository.setSelectedModelId(id);
        if (view != null) {
            view.showScreen("models");
        }
        render();
    }

    @Override
    public void onModelSaved(ModelConfig model) {
        ModelConfig saved = modelRepository.save(model);
        modelRepository.setSelectedModelId(saved.getId());
        while (!screenStack.isEmpty() && !"models".equals(screenStack.get(screenStack.size() - 1))) {
            screenStack.remove(screenStack.size() - 1);
        }
        if (screenStack.isEmpty()) {
            screenStack.add("models");
        }
        if (view != null) {
            view.showScreen("models");
        }
        render();
    }

    @Override
    public void onModelsDeleted(List<String> ids) {
        modelRepository.deleteModels(ids);
        if (view != null) {
            view.showScreen("models");
        }
        render();
    }

    @Override
    public void onExternalProjectTreePicked(String treeUri) {
        pendingExternalProjectOpen = false;
        String path = safPathResolver.treeUriToFileSystemPath(treeUri);
        if (path.length() == 0) {
            showNotice("无法将 SAF 目录转换为文件系统路径。请选择内部存储、Download 或具体目录。");
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            showNotice("外部工作区不可访问：\n" + path + "\n请确认已开启“管理所有文件”权限。");
            return;
        }
        ProjectRecord project = projectRepository.saveExternalProject(path, WorkspacePaths.basename(path));
        applyProject(project);
        if (view != null) {
            view.hideOverlays();
        }
        render();
    }

    @Override
    public void onExternalProjectPickerCancelled() {
        pendingExternalProjectOpen = false;
    }

    @Override
    public void onStoragePermissionResult() {
        if (!pendingExternalProjectOpen || view == null || !storagePermissionManager.hasExternalStorageAccess()) {
            return;
        }
        pendingExternalProjectOpen = false;
        view.openExternalProjectPicker();
    }

    private void showScreen(String screenId) {
        screenStack.add(screenId);
        if (view != null) {
            view.hideOverlays();
            view.showScreen(screenId);
        }
    }

    private void selectProject(String id) {
        projectRepository.setSelected(id);
        ProjectRecord project = projectRepository.ensureSelectedProjectPath();
        if (WorkspacePaths.SOURCE_EXTERNAL.equals(project.getSource())
                && !storagePermissionManager.hasExternalStorageAccess()) {
            pendingExternalProjectOpen = false;
            if (view != null) {
                view.hideOverlays();
                if (storagePermissionManager.needsManageAllFilesPermission()) {
                    view.openManageAllFilesPermissionSettings();
                } else {
                    view.requestLegacyStoragePermissions();
                }
            }
            showNotice(storagePermissionManager.permissionDeniedMessage());
            return;
        }
        applyProject(project);
    }

    private void applyProject(ProjectRecord project) {
        if (project == null) {
            return;
        }
        projectLabel = project.getLabel().length() == 0 ? "LineCode" : project.getLabel();
        projectPath = WorkspacePaths.displayPath(project.getPath());
        if (projectPath.length() == 0) {
            projectPath = projectRepository.getDefaultHomePath();
        }
        expandedFilePaths.clear();
        expandedFilePaths.add(projectPath);
    }

    private void requestOpenExternalProject() {
        pendingExternalProjectOpen = true;
        if (view == null) {
            return;
        }
        if (storagePermissionManager.hasExternalStorageAccess()) {
            pendingExternalProjectOpen = false;
            view.hideOverlays();
            view.openExternalProjectPicker();
            return;
        }
        view.hideOverlays();
        if (storagePermissionManager.needsManageAllFilesPermission()) {
            view.openManageAllFilesPermissionSettings();
        } else {
            view.requestLegacyStoragePermissions();
        }
    }

    private void openStoragePermissionSettings() {
        pendingExternalProjectOpen = false;
        if (view == null) {
            return;
        }
        view.hideOverlays();
        if (storagePermissionManager.needsManageAllFilesPermission()) {
            view.openManageAllFilesPermissionSettings();
        } else if (!storagePermissionManager.hasExternalStorageAccess()) {
            view.requestLegacyStoragePermissions();
        }
    }

    private void showNotice(String text) {
        messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, text, false));
        render();
    }

    private void render() {
        if (view == null) {
            return;
        }
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        boolean hasConfiguredModel = selectedModel != null;
        ModelContextInfo contextInfo = selectedModel == null
                ? ModelContextParser.parse("")
                : ModelContextParser.parse(selectedModel.getModelId());
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        OutputSettings outputSettings = outputSettingsRepository.get();
        ContextSnapshot contextSnapshot = contextManager.snapshot(messages, contextInfo.getContextTokens(), aiSettings.isPreserveReasoningEnabled());
        String modelLabel = selectedModel == null
                ? "未选择模型"
                : contextInfo.getApiModelId();
        view.render(new ChatUiState(
                projectLabel,
                projectPath,
                modelLabel,
                contextSnapshot.getPercent() + "% / " + contextInfo.getContextLabel(),
                contextSnapshot.getPercent(),
                streaming,
                hasConfiguredModel,
                aiSettings.isThinkingScrollEnabled(),
                aiSettings.isThinkingAutoExpandEnabled(),
                outputSettings.isCodeWrapEnabled(),
                outputSettings.getBrowserMode(),
                messages
        ));
    }

    private ArrayList<ModelMessage> buildModelMessages(String userInput) {
        return buildModelMessages(userInput, 0);
    }

    private ArrayList<ModelMessage> buildModelMessages(String userInput, int usedToolCallCount) {
        ArrayList<ModelMessage> modelMessages = new ArrayList<>();
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        String learningContext = aiSettings.isLearningModeEnabled()
                ? learningContextRepository.buildLearningContext(projectPath, userInput, currentConversationId)
                : "";
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        String systemPrompt = systemPromptProvider.build(projectPath, aiSettings.getToneMode(), learningContext, buildToolPrompt(selectedModel, usedToolCallCount));
        modelMessages.add(new SystemModelMessage(systemPrompt));
        int contextTokens = selectedModel == null
                ? ModelContextParser.parse("").getContextTokens()
                : ModelContextParser.parse(selectedModel.getModelId()).getContextTokens();
        int reservedTokens = contextManager.estimateTokens(systemPrompt) + 2048;
        boolean includeReasoning = aiSettings.isPreserveReasoningEnabled();
        List<ChatMessage> contextWindow = contextManager.selectWindow(messages, contextTokens, reservedTokens, includeReasoning);
        for (ChatMessage message : contextWindow) {
            modelMessages.add(toModelMessage(message, includeReasoning));
        }
        return modelMessages;
    }

    private ModelMessage toModelMessage(ChatMessage message, boolean includeReasoning) {
        if (message.getRole() == ChatMessage.Role.SYSTEM) {
            return new SystemModelMessage(message.getContent());
        }
        if (message.getRole() == ChatMessage.Role.TOOL) {
            return new ToolModelMessage(
                    modelToolContent(message.getContent()),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError()
            );
        }
        if (message.getRole() == ChatMessage.Role.USER) {
            return new UserModelMessage(message.getContent());
        }
        return new AssistantModelMessage(message.getContent(),
                includeReasoning ? message.getReasoningContent() : "",
                message.getToolCalls());
    }

    private String buildToolPrompt(ModelConfig selectedModel, int usedToolCallCount) {
        if (!hasRemainingToolCalls(selectedModel, usedToolCallCount)) {
            return "## 可用工具\n当前模型的工具调用次数限制已用尽，当前没有可用工具。";
        }
        String prompt = toolSettingsRepository.buildToolPrompt(toolRegistry.getAll(), supportsNativeTools(selectedModel));
        int limit = selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
        if (limit == ModelConfig.UNLIMITED_TOOL_CALLS) {
            return prompt + "\n\n工具调用次数限制：不限制。";
        }
        int used = Math.max(0, usedToolCallCount);
        int remaining = Math.max(0, limit - used);
        return prompt + "\n\n工具调用次数限制：最多 " + limit + " 次；已使用 " + used + " 次；剩余 " + remaining + " 次。";
    }

    private boolean supportsNativeTools(ModelConfig selectedModel) {
        if (selectedModel == null) {
            return false;
        }
        ModelProtocolType type = selectedModel.getProtocolType();
        return type == ModelProtocolType.OPENAI_COMPATIBLE || type == ModelProtocolType.ANTHROPIC_MESSAGES;
    }

    private void appendAssistantDelta(int generationId, String assistantId, String textDelta, String reasoningDelta) {
        mainHandler.post(() -> {
            if (generationId != generationSequence - 1 || !streaming) {
                return;
            }
            if (pendingStreamGenerationId != generationId || !pendingStreamAssistantId.equals(assistantId)) {
                flushPendingAssistantDelta();
                pendingStreamGenerationId = generationId;
                pendingStreamAssistantId = assistantId;
            }
            if (textDelta != null && textDelta.length() > 0) {
                pendingStreamTextDelta.append(textDelta);
            }
            if (reasoningDelta != null && reasoningDelta.length() > 0) {
                pendingStreamReasoningDelta.append(reasoningDelta);
            }
            scheduleAssistantDeltaFlush();
        });
    }

    private void scheduleAgentProgressRender(AgentProgressSession session) {
        if (session == null || !session.shouldScheduleRender()) {
            return;
        }
        mainHandler.postDelayed(() -> flushAgentProgress(session), session.renderDelayMs());
    }

    private void flushAgentProgress(AgentProgressSession session) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> flushAgentProgress(session));
            return;
        }
        if (session == null || !session.canRender()) {
            return;
        }
        if (session.generationId != generationSequence - 1 || !streaming) {
            return;
        }
        addOrReplaceToolResult(session.snapshotResult());
        render();
    }

    private String modelToolContent(String content) {
        if (content == null || content.trim().length() == 0) {
            return "";
        }
        try {
            JSONObject object = new JSONObject(content);
            if (!object.optBoolean("linecode_agent_progress")) {
                return content;
            }
            String modelContent = object.optString("model_content");
            if (modelContent.trim().length() > 0) {
                return modelContent;
            }
            String output = object.optString("output");
            return output.trim().length() > 0 ? output : "Agent 仍在运行，尚未生成最终结果。";
        } catch (Exception ignored) {
            return content;
        }
    }

    private ModelRequestOptions requestOptions(AiBehaviorSettings aiSettings, ModelConfig selectedModel, int usedToolCallCount) {
        return new ModelRequestOptions(
                aiSettings.getReasoningEffort(),
                aiSettings.isPreserveReasoningEnabled(),
                hasRemainingToolCalls(selectedModel, usedToolCallCount)
                        ? toolRegistry.getByNameSet(toolSettingsRepository.getEnabledToolNames())
                        : new ArrayList<BaseTool>()
        );
    }

    private void finishGeneration(
            int generationId,
            String assistantId,
            ModelConfig selectedModel,
            ModelRequestOptions requestOptions,
            ModelCompletionResponse response,
            int usedToolCallCount
    ) {
        mainHandler.post(() -> {
            flushPendingAssistantDelta();
            if (generationId != generationSequence - 1 || !streaming) {
                return;
            }
            int index = findMessageIndex(assistantId);
            if (index < 0) {
                return;
            }
            ChatMessage message = messages.get(index);
            String rawResponseText = response.getText();
            StringBuilder rawStream = streamingRawTextByMessageId.remove(assistantId);
            if (rawResponseText.trim().length() == 0 && rawStream != null) {
                rawResponseText = rawStream.toString();
            }
            ToolCallTextParser.Result parsedTextToolCalls = ToolCallTextParser.parse(rawResponseText);
            List<ToolCall> toolCalls = mergeToolCalls(response.getToolCalls(), parsedTextToolCalls.getToolCalls());
            String parsedResponseText = parsedTextToolCalls.hasToolMarkup() ? parsedTextToolCalls.getText() : rawResponseText;
            String finalText = parsedTextToolCalls.hasToolMarkup()
                    ? parsedResponseText
                    : parsedResponseText.trim().length() == 0 ? message.getContent() : parsedResponseText;
            String finalReasoning = response.getReasoningContent().trim().length() == 0 ? message.getReasoningContent() : response.getReasoningContent();
            boolean hasToolCalls = !toolCalls.isEmpty();
            if (finalText.trim().length() == 0 && finalReasoning.trim().length() == 0 && !hasToolCalls) {
                finalText = "模型没有返回文本。";
            }
            messages.set(index, message.withContent(finalText, finalReasoning, false)
                    .withToolCalls(toolCalls, false));
            if (hasToolCalls) {
                if (!canExecuteToolCalls(selectedModel, usedToolCallCount, toolCalls.size())) {
                    messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT,
                            toolLimitMessage(selectedModel, usedToolCallCount, toolCalls.size()), false));
                    streaming = false;
                    currentCancellationToken = null;
                    persistCurrentConversation();
                    render();
                    return;
                }
                persistCurrentConversation();
                render();
                executeToolsAndContinue(generationId, selectedModel, toolCalls, usedToolCallCount + toolCalls.size());
                return;
            }
            streaming = false;
            currentCancellationToken = null;
            persistCurrentConversation();
            scheduleMemoryExtractionIfNeeded(selectedModel);
            render();
        });
    }

    private List<ToolCall> mergeToolCalls(List<ToolCall> nativeCalls, List<ToolCall> textCalls) {
        ArrayList<ToolCall> merged = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        if (nativeCalls != null) {
            for (ToolCall call : nativeCalls) {
                if (call == null || call.getName().length() == 0 || seen.contains(call.getId())) {
                    continue;
                }
                merged.add(call);
                seen.add(call.getId());
            }
        }
        if (textCalls != null) {
            for (ToolCall call : textCalls) {
                if (call == null || call.getName().length() == 0 || seen.contains(call.getId())) {
                    continue;
                }
                merged.add(call);
                seen.add(call.getId());
            }
        }
        return merged;
    }

    private void scheduleMemoryExtractionIfNeeded(ModelConfig selectedModel) {
        if (!aiBehaviorSettingsRepository.get().isLearningModeEnabled() || selectedModel == null) {
            return;
        }
        String userInput = recentUserInput();
        String transcript = recentTurnTranscript();
        if (userInput.trim().length() == 0 || transcript.trim().length() == 0) {
            return;
        }
        String capturedProjectPath = projectPath;
        new Thread(() -> memoryExtractionService.extractAndStore(
                selectedModel,
                capturedProjectPath,
                userInput,
                transcript
        ), "linecode-memory-extract").start();
    }

    private String recentUserInput() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.USER && message.getContent().trim().length() > 0) {
                return message.getContent();
            }
        }
        return "";
    }

    private String recentTurnTranscript() {
        int start = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.USER && message.getContent().trim().length() > 0) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.isHidden() || message.isExcludeFromContext()) {
                continue;
            }
            if (message.getRole() == ChatMessage.Role.USER) {
                appendTranscriptMessage(builder, "user", message.getContent(), 1400);
            } else if (message.getRole() == ChatMessage.Role.ASSISTANT) {
                appendTranscriptMessage(builder, "assistant", message.getContent(), 2200);
            }
            if (builder.length() > 6000) {
                return builder.substring(0, 5997) + "...";
            }
        }
        return builder.toString().trim();
    }

    private void appendTranscriptMessage(StringBuilder builder, String role, String content, int maxChars) {
        String text = content == null ? "" : content.trim();
        if (text.length() == 0) {
            return;
        }
        if (text.length() > maxChars) {
            text = text.substring(0, Math.max(0, maxChars - 3)) + "...";
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(role).append(": ").append(text);
    }

    private void executeToolsAndContinue(
            int generationId,
            ModelConfig selectedModel,
            List<ToolCall> toolCalls,
            int usedToolCallCount
    ) {
        continueToolExecution(
                generationId,
                selectedModel,
                toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls),
                usedToolCallCount,
                projectPath,
                currentCancellationToken
        );
    }

    private void continueToolExecution(
            int generationId,
            ModelConfig selectedModel,
            List<ToolCall> toolCalls,
            int usedToolCallCount,
            String homePath,
            ModelCancellationToken cancellationToken
    ) {
        new Thread(() -> {
            ToolExecutionBatch batch = executeToolCallsUntilPending(toolCalls, homePath, selectedModel, cancellationToken, generationId);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return;
            }
            mainHandler.post(() -> {
                if (generationId != generationSequence - 1 || !streaming) {
                    return;
                }
                handleToolExecutionBatch(generationId, selectedModel, usedToolCallCount, homePath, cancellationToken, batch);
            });
        }, "linecode-tool-execute").start();
    }

    private void handleToolExecutionBatch(
            int generationId,
            ModelConfig selectedModel,
            int usedToolCallCount,
            String homePath,
            ModelCancellationToken cancellationToken,
            ToolExecutionBatch batch
    ) {
        addOrReplaceToolResults(batch.completedResults);
        if (batch.pendingCall != null) {
            ToolResult pendingResult = new ToolResult(
                    batch.pendingCall.getId(),
                    batch.pendingCall.getName(),
                    "",
                    false,
                    "",
                    "pending",
                    ""
            );
            addOrReplaceToolResult(pendingResult);
            pendingToolExecution = new PendingToolExecution(
                    generationId,
                    selectedModel,
                    batch.pendingCall,
                    batch.remainingCalls,
                    usedToolCallCount,
                    homePath,
                    cancellationToken
            );
            persistCurrentConversation();
            render();
            return;
        }
        persistCurrentConversation();
        continueModelAfterTools(generationId, selectedModel, usedToolCallCount, cancellationToken);
    }

    private void continueModelAfterTools(
            int generationId,
            ModelConfig selectedModel,
            int usedToolCallCount,
            ModelCancellationToken cancellationToken
    ) {
        ArrayList<ModelMessage> nextRequestMessages = buildModelMessages("", usedToolCallCount);
        String nextAssistantId = nextId();
        streamingRawTextByMessageId.put(nextAssistantId, new StringBuilder());
        messages.add(new ChatMessage(nextAssistantId, ChatMessage.Role.ASSISTANT, "", true));
        render();
        new Thread(() -> {
            try {
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                ModelRequestOptions nextRequestOptions = requestOptions(aiSettings, selectedModel, usedToolCallCount);
                ModelCompletionResponse response = modelClient.stream(selectedModel, nextRequestMessages, new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        appendAssistantDelta(generationId, nextAssistantId, delta, "");
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        appendAssistantDelta(generationId, nextAssistantId, "", delta);
                    }
                }, cancellationToken, nextRequestOptions);
                finishGeneration(generationId, nextAssistantId, selectedModel, nextRequestOptions, response, usedToolCallCount);
            } catch (ModelCompletionException e) {
                failGeneration(generationId, nextAssistantId, "模型通信失败：\n" + e.getMessage());
            }
        }, "linecode-tool-continuation").start();
    }

    private boolean canExecuteToolCalls(ModelConfig selectedModel, int usedToolCallCount, int requestedCount) {
        int limit = selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
        if (limit == ModelConfig.UNLIMITED_TOOL_CALLS) {
            return true;
        }
        if (requestedCount <= 0) {
            return true;
        }
        return usedToolCallCount >= 0 && usedToolCallCount + requestedCount <= limit;
    }

    private boolean hasRemainingToolCalls(ModelConfig selectedModel, int usedToolCallCount) {
        int limit = selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
        return limit == ModelConfig.UNLIMITED_TOOL_CALLS || Math.max(0, usedToolCallCount) < limit;
    }

    private String toolLimitMessage(ModelConfig selectedModel, int usedToolCallCount, int requestedCount) {
        int limit = selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
        if (limit == 0) {
            return "当前模型已禁止工具调用，已停止继续执行。";
        }
        return "工具调用次数已达到上限，已停止继续执行。\n"
                + "已使用 " + Math.max(0, usedToolCallCount)
                + " 次，本次请求 " + Math.max(0, requestedCount)
                + " 次，限制为 " + limit + " 次。";
    }

    private ToolExecutionBatch executeToolCallsUntilPending(
            List<ToolCall> toolCalls,
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        ToolExecutionCoordinator.ToolExecutionPlan plan = toolExecutionCoordinator.createPlan(toolCalls);
        HashMap<String, ToolResult> resultById = new HashMap<>();
        ToolContext context = toolContext(homePath, selectedModel, cancellationToken, generationId);

        if (!plan.getConcurrentTasks().isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, plan.getConcurrentTasks().size()));
            ArrayList<ToolCall> concurrentCalls = new ArrayList<>(plan.getConcurrentTasks());
            ArrayList<Future<ToolResult>> futures = new ArrayList<>();
            for (ToolCall call : concurrentCalls) {
                futures.add(executor.submit(() -> toolExecutor.execute(call, context)));
            }
            for (int i = 0; i < futures.size(); i++) {
                ToolCall call = concurrentCalls.get(i);
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    executor.shutdownNow();
                    return new ToolExecutionBatch(new ArrayList<>(), null, new ArrayList<>());
                }
                try {
                    ToolResult result = futures.get(i).get();
                    resultById.put(call.getId(), result);
                } catch (Exception e) {
                    resultById.put(call.getId(), new ToolResult(call.getId(), call.getName(), "执行失败: " + e.getMessage(), true));
                }
            }
            executor.shutdownNow();
        }

        List<ToolCall> sequentialTasks = plan.getSequentialTasks();
        for (int i = 0; i < sequentialTasks.size(); i++) {
            ToolCall call = sequentialTasks.get(i);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return new ToolExecutionBatch(new ArrayList<>(), null, new ArrayList<>());
            }
            if (shouldPauseForConfirmation(call)) {
                return new ToolExecutionBatch(
                        orderedResults(toolCalls, resultById),
                        call,
                        remainingCalls(sequentialTasks, i + 1)
                );
            }
            resultById.put(call.getId(), toolExecutor.execute(call, context));
        }

        return new ToolExecutionBatch(orderedResults(toolCalls, resultById), null, new ArrayList<>());
    }

    private ToolContext toolContext(
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        return new ToolContext(homePath, new ToolContext.AgentRunner() {
            @Override
            public ToolResult runAgent(JSONObject input, ToolContext context) {
                return runAgentTool(input, context, selectedModel, cancellationToken, generationId);
            }

            @Override
            public ToolResult runAgentPipeline(JSONObject input, ToolContext context) {
                return runAgentPipelineTool(input, context, selectedModel, cancellationToken, generationId);
            }
        });
    }

    private ToolResult runAgentTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        if (selectedModel == null) {
            return new ToolResult("", "agent", "当前没有可用模型，无法运行 Agent。", true);
        }
        String type = AgentTool.normalizeType(input.optString("type"));
        String description = input.optString("description").trim();
        String prompt = input.optString("prompt").trim();
        String homePath = parentContext == null ? projectPath : parentContext.getHomePath();
        AgentProgressSession progress = new AgentProgressSession(
                generationId,
                parentContext == null ? "" : parentContext.getToolCallId(),
                "agent",
                type,
                description
        );
        AgentRunResult result = runAgentLoop(
                type,
                description,
                prompt,
                homePath,
                selectedModel,
                cancellationToken,
                progress
        );
        StringBuilder builder = new StringBuilder();
        builder.append("Agent 完成: ").append(description).append('\n')
                .append("类型: ").append(type).append('\n')
                .append("工具调用: ").append(result.toolCallCount).append('\n')
                .append("输出:\n").append(result.output);
        progress.setTurnResult(result.output, "");
        progress.setFinished(result.error ? "error" : "done", result.error, builder.toString().trim());
        flushAgentProgress(progress);
        return new ToolResult("", "agent", progress.snapshotResult().getContent(), result.error);
    }

    private ToolResult runAgentPipelineTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId
    ) {
        if (selectedModel == null) {
            return new ToolResult("", "agent_pipeline", "当前没有可用模型，无法运行 Agent 流水线。", true);
        }
        ArrayList<PipelineAgent> agents = parsePipelineAgents(input.optJSONArray("agents"));
        if (agents.isEmpty()) {
            return new ToolResult("", "agent_pipeline", "agent_pipeline.agents 不能为空。", true);
        }
        String dependencyError = validatePipelineDependencies(agents);
        if (dependencyError.length() > 0) {
            return new ToolResult("", "agent_pipeline", dependencyError, true);
        }
        ArrayList<ArrayList<PipelineAgent>> levels = dependencyLevels(agents);
        if (levels.isEmpty()) {
            return new ToolResult("", "agent_pipeline", "Agent 流水线存在循环依赖或重复 id，无法执行。", true);
        }

        LinkedHashMap<String, AgentRunResult> results = new LinkedHashMap<>();
        StringBuilder summary = new StringBuilder();
        summary.append("Agent 流水线完成: ").append(agents.size()).append(" 个任务");
        boolean hasError = false;
        int totalToolCalls = 0;
        for (ArrayList<PipelineAgent> level : levels) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return new ToolResult("", "agent_pipeline", "Agent 流水线已终止。", true);
            }
            for (PipelineAgent agent : level) {
                String prompt = agent.prompt + dependencyOutputContext(agent, results);
                AgentRunResult result = runAgentLoop(
                        agent.type,
                        agent.description,
                        prompt,
                        parentContext == null ? projectPath : parentContext.getHomePath(),
                        selectedModel,
                        cancellationToken,
                        null
                );
                results.put(agent.id, result);
                totalToolCalls += result.toolCallCount;
                hasError = hasError || result.error;
                summary.append("\n\n## ").append(agent.id).append(" · ").append(agent.description)
                        .append('\n').append("类型: ").append(agent.type)
                        .append('\n').append("状态: ").append(result.error ? "error" : "done")
                        .append('\n').append("工具调用: ").append(result.toolCallCount)
                        .append('\n').append(result.output);
            }
        }
        summary.append("\n\n总工具调用: ").append(totalToolCalls);
        return new ToolResult("", "agent_pipeline", summary.toString().trim(), hasError);
    }

    private AgentRunResult runAgentLoop(
            String type,
            String description,
            String prompt,
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            AgentProgressSession progress
    ) {
        ArrayList<BaseTool> agentTools = agentTools(type);
        Set<String> allowedToolNames = toolNames(agentTools);
        ArrayList<ModelMessage> agentMessages = new ArrayList<>();
        agentMessages.add(new SystemModelMessage(agentSystemPrompt(type, description, homePath, selectedModel, agentTools)));
        agentMessages.add(new UserModelMessage(prompt));
        int toolCallCount = 0;
        String lastOutput = "";

        for (int turn = 0; turn < AGENT_MAX_TURNS; turn++) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                if (progress != null) {
                    progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                    flushAgentProgress(progress);
                }
                return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
            }
            try {
                if (progress != null) {
                    progress.beginTurn();
                    scheduleAgentProgressRender(progress);
                }
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                List<BaseTool> nativeTools = supportsNativeTools(selectedModel) ? agentTools : new ArrayList<>();
                ModelRequestOptions options = new ModelRequestOptions(
                        aiSettings.getReasoningEffort(),
                        aiSettings.isPreserveReasoningEnabled(),
                        nativeTools
                );
                ModelStreamCallback callback = progress == null ? null : new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        progress.appendText(delta);
                        scheduleAgentProgressRender(progress);
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        progress.appendThinking(delta);
                        scheduleAgentProgressRender(progress);
                    }
                };
                ModelCompletionResponse response = modelClient.stream(selectedModel, agentMessages, callback, cancellationToken, options);
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    if (progress != null) {
                        progress.setTurnResult(AGENT_TERMINATED_MESSAGE, response.getReasoningContent());
                        progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                        flushAgentProgress(progress);
                    }
                    return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
                }
                ToolCallTextParser.Result parsedTextToolCalls = ToolCallTextParser.parse(response.getText());
                List<ToolCall> calls = mergeToolCalls(response.getToolCalls(), parsedTextToolCalls.getToolCalls());
                String output = parsedTextToolCalls.hasToolMarkup() ? parsedTextToolCalls.getText() : response.getText();
                if (progress != null) {
                    progress.setTurnResult(output, response.getReasoningContent());
                    progress.addToolCalls(calls);
                    flushAgentProgress(progress);
                }
                if (output.trim().length() > 0) {
                    lastOutput = output;
                }
                if (calls.isEmpty()) {
                    String finalOutput = lastOutput.trim().length() == 0 ? "Agent 没有返回文本。" : lastOutput;
                    return new AgentRunResult(finalOutput, toolCallCount, false);
                }

                agentMessages.add(new AssistantModelMessage(output, response.getReasoningContent(), calls));
                for (ToolCall call : calls) {
                    if (cancellationToken != null && cancellationToken.isCancelled()) {
                        if (progress != null) {
                            progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                            flushAgentProgress(progress);
                        }
                        return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
                    }
                    ToolResult toolResult = executeAgentToolCall(call, allowedToolNames, homePath);
                    toolCallCount++;
                    if (progress != null) {
                        progress.putToolResult(call, toolResult);
                        flushAgentProgress(progress);
                    }
                    agentMessages.add(new ToolModelMessage(
                            toolResult.getContent(),
                            toolResult.getToolCallId(),
                            toolResult.getToolName(),
                            toolResult.isError()
                    ));
                }
            } catch (ModelCompletionException e) {
                if (progress != null) {
                    progress.setFinished("error", true, "Agent 模型通信失败：\n" + e.getMessage());
                    flushAgentProgress(progress);
                }
                return new AgentRunResult("Agent 模型通信失败：\n" + e.getMessage(), toolCallCount, true);
            } catch (Exception e) {
                if (progress != null) {
                    progress.setFinished("error", true, "Agent 执行失败：\n" + e.getMessage());
                    flushAgentProgress(progress);
                }
                return new AgentRunResult("Agent 执行失败：\n" + e.getMessage(), toolCallCount, true);
            }
        }
        if (progress != null) {
            progress.setFinished("error", true, "Agent 达到最大轮次 " + AGENT_MAX_TURNS + "，最后输出：\n" + lastOutput);
            flushAgentProgress(progress);
        }
        return new AgentRunResult("Agent 达到最大轮次 " + AGENT_MAX_TURNS + "，最后输出：\n" + lastOutput, toolCallCount, true);
    }

    private ArrayList<BaseTool> agentTools(String type) {
        ArrayList<BaseTool> tools = new ArrayList<>();
        Set<String> enabled = toolSettingsRepository.getEnabledToolNames();
        for (BaseTool tool : toolRegistry.getByNameSet(enabled)) {
            if (tool == null || !isAgentToolAllowed(tool, type)) {
                continue;
            }
            tools.add(tool);
        }
        return tools;
    }

    private boolean isAgentToolAllowed(BaseTool tool, String type) {
        String name = tool.getName();
        if ("agent".equals(name) || "agent_pipeline".equals(name) || "shell_execute".equals(name) || "file_delete".equals(name)) {
            return false;
        }
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return tool.getCategory() == ToolCategory.READ;
        }
        return tool.getCategory() == ToolCategory.READ
                || tool.getCategory() == ToolCategory.WRITE
                || "http_server".equals(name);
    }

    private Set<String> toolNames(List<BaseTool> tools) {
        HashSet<String> names = new HashSet<>();
        if (tools == null) {
            return names;
        }
        for (BaseTool tool : tools) {
            if (tool != null) {
                names.add(tool.getName());
            }
        }
        return names;
    }

    private ToolResult executeAgentToolCall(ToolCall call, Set<String> allowedToolNames, String homePath) {
        if (call == null) {
            return new ToolResult("", "", "Agent 工具调用为空", true);
        }
        if (!allowedToolNames.contains(call.getName())) {
            return new ToolResult(call.getId(), call.getName(), "Agent 不允许调用此工具: " + call.getName(), true);
        }
        return toolExecutor.execute(call, new ToolContext(homePath));
    }

    private String agentSystemPrompt(
            String type,
            String description,
            String homePath,
            ModelConfig selectedModel,
            List<BaseTool> agentTools
    ) {
        return agentRolePrompt(type)
                + "\n\n你的任务: " + description
                + "\n\n" + agentWorkspacePrompt(homePath)
                + "\n\n" + toolSettingsRepository.buildToolPrompt(agentTools, supportsNativeTools(selectedModel));
    }

    private String agentRolePrompt(String type) {
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return "你是一个代码探索 Agent。你的任务是快速定位和分析代码，回答用户的问题。\n"
                    + "规则：\n"
                    + "- 只读取代码，不做任何修改。\n"
                    + "- 优先使用只读工具搜索和读取关键文件。\n"
                    + "- 给出简洁准确的中文回答，并标注文件路径和必要的行号。";
        }
        return "你是一个编程 Agent。你的任务是完成边界清晰的编程子任务。\n"
                + "规则：\n"
                + "- 只负责用户明确分派给你的任务区域，不要修改无关文件。\n"
                + "- 修改前先读取目标文件，完成后做最小可行验证。\n"
                + "- 如果工具失败，先重新读取和分析，不要盲目重复。\n"
                + "- 用中文总结完成内容、验证结果和剩余风险。";
    }

    private String agentWorkspacePrompt(String homePath) {
        String path = homePath == null || homePath.trim().length() == 0 ? projectPath : homePath.trim();
        return "当前工作区: " + path + "\n"
                + "所有文件路径默认相对此工作区。不要访问未授权路径，不要读取 API key、token、密码等敏感数据。";
    }

    private ArrayList<PipelineAgent> parsePipelineAgents(JSONArray array) {
        ArrayList<PipelineAgent> agents = new ArrayList<>();
        if (array == null) {
            return agents;
        }
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                return new ArrayList<>();
            }
            String id = object.optString("id").trim();
            if (id.length() == 0 || ids.contains(id)) {
                return new ArrayList<>();
            }
            ids.add(id);
            agents.add(new PipelineAgent(
                    id,
                    AgentTool.normalizeType(object.optString("type")),
                    object.optString("description").trim(),
                    object.optString("prompt").trim(),
                    dependencyList(object.optJSONArray("depends_on"))
            ));
        }
        return agents;
    }

    private String validatePipelineDependencies(ArrayList<PipelineAgent> agents) {
        if (agents == null) {
            return "agent_pipeline.agents 不能为空。";
        }
        for (PipelineAgent agent : agents) {
            for (String dependency : agent.dependencies) {
                if (agent.id.equals(dependency)) {
                    return "Agent 不能依赖自身: " + agent.id;
                }
            }
        }
        return "";
    }

    private ArrayList<String> dependencyList(JSONArray array) {
        ArrayList<String> dependencies = new ArrayList<>();
        if (array == null) {
            return dependencies;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                dependencies.add(value);
            }
        }
        return dependencies;
    }

    private ArrayList<ArrayList<PipelineAgent>> dependencyLevels(ArrayList<PipelineAgent> agents) {
        ArrayList<ArrayList<PipelineAgent>> levels = new ArrayList<>();
        HashSet<String> allIds = new HashSet<>();
        for (PipelineAgent agent : agents) {
            allIds.add(agent.id);
        }
        for (PipelineAgent agent : agents) {
            for (String dependency : agent.dependencies) {
                if (!allIds.contains(dependency)) {
                    return new ArrayList<>();
                }
            }
        }

        HashSet<String> completed = new HashSet<>();
        while (completed.size() < agents.size()) {
            ArrayList<PipelineAgent> level = new ArrayList<>();
            for (PipelineAgent agent : agents) {
                if (completed.contains(agent.id)) {
                    continue;
                }
                if (completed.containsAll(agent.dependencies)) {
                    level.add(agent);
                }
            }
            if (level.isEmpty()) {
                return new ArrayList<>();
            }
            for (PipelineAgent agent : level) {
                completed.add(agent.id);
            }
            levels.add(level);
        }
        return levels;
    }

    private String dependencyOutputContext(PipelineAgent agent, LinkedHashMap<String, AgentRunResult> results) {
        if (agent.dependencies.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n## 上游 Agent 输出\n");
        for (String dependency : agent.dependencies) {
            AgentRunResult result = results.get(dependency);
            if (result == null) {
                continue;
            }
            builder.append("\n### ").append(dependency).append('\n').append(result.output).append('\n');
        }
        builder.append("\n请基于以上结果继续你的任务。");
        return builder.toString();
    }

    private ArrayList<ToolResult> orderedResults(List<ToolCall> toolCalls, HashMap<String, ToolResult> resultById) {
        ArrayList<ToolResult> ordered = new ArrayList<>();
        if (toolCalls == null) {
            return ordered;
        }
        for (ToolCall call : toolCalls) {
            ToolResult result = resultById.get(call.getId());
            if (result != null) {
                ordered.add(result);
            }
        }
        return ordered;
    }

    private ArrayList<ToolCall> remainingCalls(List<ToolCall> calls, int startIndex) {
        ArrayList<ToolCall> remaining = new ArrayList<>();
        if (calls == null) {
            return remaining;
        }
        for (int i = Math.max(0, startIndex); i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            if (call != null) {
                remaining.add(call);
            }
        }
        return remaining;
    }

    private boolean shouldPauseForConfirmation(ToolCall call) {
        if (call == null) {
            return false;
        }
        BaseTool tool = toolRegistry.get(call.getName());
        if (tool == null || !tool.requiresConfirmation()) {
            return false;
        }
        PermissionResult permission = toolSettingsRepository.canExecuteTool(tool.getName(), tool.getCategory());
        if (!permission.isAllowed()) {
            return false;
        }
        return "file_delete".equals(tool.getName()) || toolSettingsRepository.needsConfirmation(tool.getName());
    }

    private void handlePendingToolReview(String state) {
        PendingToolExecution pending = pendingToolExecution;
        if (pending == null || pending.toolCall == null) {
            return;
        }
        if (pending.generationId != generationSequence - 1 || !streaming) {
            pendingToolExecution = null;
            return;
        }
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        pendingToolExecution = null;
        if ("rejected".equals(normalizedState)) {
            ToolResult rejected = new ToolResult(
                    pending.toolCall.getId(),
                    pending.toolCall.getName(),
                    rejectedToolMessage(pending.toolCall),
                    true,
                    "",
                    "rejected",
                    ""
            );
            addOrReplaceToolResult(rejected);
            persistCurrentConversation();
            render();
            continueToolExecution(
                    pending.generationId,
                    pending.selectedModel,
                    pending.remainingCalls,
                    pending.usedToolCallCount,
                    pending.homePath,
                    pending.cancellationToken
            );
            return;
        }

        ToolResult accepted = new ToolResult(
                pending.toolCall.getId(),
                pending.toolCall.getName(),
                "",
                false,
                "",
                "accepted",
                ""
        );
        addOrReplaceToolResult(accepted);
        persistCurrentConversation();
        render();
        executeAcceptedPendingTool(pending);
    }

    private void executeAcceptedPendingTool(PendingToolExecution pending) {
        new Thread(() -> {
            ToolResult result;
            try {
                result = toolExecutor
                        .executeConfirmed(pending.toolCall, toolContext(
                                pending.homePath,
                                pending.selectedModel,
                                pending.cancellationToken,
                                pending.generationId
                        ))
                        .withReview("accepted", "");
            } catch (Exception e) {
                result = new ToolResult(
                        pending.toolCall.getId(),
                        pending.toolCall.getName(),
                        "执行失败: " + e.getMessage(),
                        true,
                        "",
                        "accepted",
                        ""
                );
            }
            ToolResult finalResult = result;
            if (pending.cancellationToken != null && pending.cancellationToken.isCancelled()) {
                return;
            }
            mainHandler.post(() -> {
                if (pending.generationId != generationSequence - 1 || !streaming) {
                    return;
                }
                addOrReplaceToolResult(finalResult);
                persistCurrentConversation();
                render();
                continueToolExecution(
                        pending.generationId,
                        pending.selectedModel,
                        pending.remainingCalls,
                        pending.usedToolCallCount,
                        pending.homePath,
                        pending.cancellationToken
                );
            });
        }, "linecode-tool-confirmed").start();
    }

    private void addOrReplaceToolResults(List<ToolResult> results) {
        if (results == null) {
            return;
        }
        for (ToolResult result : results) {
            addOrReplaceToolResult(result);
        }
    }

    private void addOrReplaceToolResult(ToolResult result) {
        if (result == null || result.getToolCallId().length() == 0) {
            return;
        }
        int index = findToolMessageIndex(result.getToolCallId());
        String messageId = index >= 0 ? messages.get(index).getId() : nextId();
        ChatMessage message = ChatMessage.toolResult(
                messageId,
                result.getContent(),
                result.getToolCallId(),
                result.getToolName(),
                result.isError(),
                result.getDiffId(),
                result.getReviewState(),
                result.getReviewMessage()
        );
        if (index >= 0) {
            messages.set(index, message);
        } else {
            messages.add(message);
        }
    }

    private int findToolMessageIndex(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return -1;
        }
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.TOOL && toolCallId.equals(message.getToolCallId())) {
                return i;
            }
        }
        return -1;
    }

    private String rejectedToolMessage(ToolCall call) {
        String reason = "";
        try {
            JSONObject input = call.getArguments().trim().length() == 0
                    ? new JSONObject()
                    : new JSONObject(call.getArguments());
            reason = input.optString("reason").trim();
        } catch (Exception ignored) {
            reason = "";
        }
        if (reason.length() == 0) {
            return "用户拒绝执行此工具。";
        }
        return "用户拒绝删除：" + reason;
    }

    private void failGeneration(int generationId, String assistantId, String text) {
        mainHandler.post(() -> {
            flushPendingAssistantDelta();
            if (generationId != generationSequence - 1 || !streaming) {
                return;
            }
            int index = findMessageIndex(assistantId);
            if (index >= 0) {
                ChatMessage message = messages.get(index);
                messages.set(index, message.withContent(text, message.getReasoningContent(), false));
            } else {
                messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, text, false));
            }
            streamingRawTextByMessageId.remove(assistantId);
            streaming = false;
            currentCancellationToken = null;
            persistCurrentConversation();
            render();
        });
    }

    private void cancelActiveGeneration() {
        pendingToolExecution = null;
        if (currentCancellationToken != null) {
            currentCancellationToken.cancel();
            currentCancellationToken = null;
        }
    }

    private void scheduleAssistantDeltaFlush() {
        if (streamRenderScheduled) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delay = Math.max(0L, STREAM_RENDER_INTERVAL_MS - (now - lastStreamRenderAt));
        streamRenderScheduled = true;
        mainHandler.postDelayed(this::flushPendingAssistantDelta, delay);
    }

    private void flushPendingAssistantDelta() {
        streamRenderScheduled = false;
        if (pendingStreamTextDelta.length() == 0 && pendingStreamReasoningDelta.length() == 0) {
            return;
        }
        int generationId = pendingStreamGenerationId;
        String assistantId = pendingStreamAssistantId;
        String textDelta = pendingStreamTextDelta.toString();
        String reasoningDelta = pendingStreamReasoningDelta.toString();
        pendingStreamTextDelta.setLength(0);
        pendingStreamReasoningDelta.setLength(0);
        pendingStreamGenerationId = -1;
        pendingStreamAssistantId = "";
        if (generationId != generationSequence - 1 || !streaming) {
            return;
        }
        int index = findMessageIndex(assistantId);
        if (index < 0) {
            return;
        }
        ChatMessage message = messages.get(index);
        StringBuilder rawText = streamingRawTextByMessageId.get(assistantId);
        if (rawText == null) {
            rawText = new StringBuilder(message.getContent());
            streamingRawTextByMessageId.put(assistantId, rawText);
        }
        rawText.append(textDelta);
        ToolCallTextParser.Result parsedToolCalls = ToolCallTextParser.parseStreamingPreview(rawText.toString());
        String visibleText = parsedToolCalls.hasToolMarkup()
                ? parsedToolCalls.getText()
                : message.getContent() + textDelta;
        List<ToolCall> toolCalls = parsedToolCalls.hasToolMarkup()
                ? mergeToolCalls(message.getToolCalls(), parsedToolCalls.getToolCalls())
                : message.getToolCalls();
        messages.set(index, message.withContent(
                visibleText,
                message.getReasoningContent() + reasoningDelta,
                true
        ).withToolCalls(toolCalls, false));
        lastStreamRenderAt = SystemClock.uptimeMillis();
        render();
    }

    private void markStreamingMessagesStopped() {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.isStreaming()) {
                messages.set(i, message.withContent(message.getContent(), message.getReasoningContent(), false));
            }
        }
    }

    private void markRunningAgentProgressStopped() {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() != ChatMessage.Role.TOOL) {
                continue;
            }
            try {
                JSONObject object = new JSONObject(message.getContent());
                if (!object.optBoolean("linecode_agent_progress")) {
                    continue;
                }
                String status = object.optString("status");
                if (!"running".equals(status) && !"waiting_unlock".equals(status)) {
                    continue;
                }
                object.put("status", "error");
                String output = object.optString("output").trim();
                if (output.length() == 0) {
                    object.put("output", AGENT_TERMINATED_MESSAGE);
                } else if (!output.contains(AGENT_TERMINATED_MESSAGE)) {
                    object.put("output", output + "\n\n" + AGENT_TERMINATED_MESSAGE);
                }
                object.put("model_content", AGENT_TERMINATED_MESSAGE);
                messages.set(i, new ChatMessage(
                        message.getId(),
                        ChatMessage.Role.TOOL,
                        object.toString(),
                        message.getReasoningContent(),
                        false,
                        message.isHidden(),
                        message.isExcludeFromContext(),
                        message.getToolCalls(),
                        message.getToolResults(),
                        message.getToolCallId(),
                        message.getToolName(),
                        true,
                        message.getDiffId(),
                        message.getReviewState(),
                        message.getReviewMessage()
                ));
            } catch (Exception ignored) {
            }
        }
        addTerminatedResultsForUnfinishedAgents();
    }

    private void addTerminatedResultsForUnfinishedAgents() {
        ArrayList<ToolResult> terminatedResults = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.getRole() != ChatMessage.Role.ASSISTANT || !message.hasToolCalls()) {
                continue;
            }
            for (ToolCall call : message.getToolCalls()) {
                if (call == null || findToolMessageIndex(call.getId()) >= 0) {
                    continue;
                }
                if ("agent".equals(call.getName()) || "agent_pipeline".equals(call.getName())) {
                    terminatedResults.add(new ToolResult(call.getId(), call.getName(), AGENT_TERMINATED_MESSAGE, true));
                }
            }
        }
        addOrReplaceToolResults(terminatedResults);
    }

    private int findMessageIndex(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private String findToolMessageDiffId(String toolCallId) {
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.TOOL
                    && toolCallId.equals(message.getToolCallId())
                    && message.getDiffId().length() > 0) {
                return message.getDiffId();
            }
        }
        return "";
    }

    private void updateToolReview(String toolCallId, String diffId, String reviewState, String reviewMessage) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.TOOL && toolCallId.equals(message.getToolCallId())) {
                String resolvedDiffId = diffId == null || diffId.length() == 0 ? message.getDiffId() : diffId;
                messages.set(i, message.withToolReview(resolvedDiffId, reviewState, reviewMessage));
            }
        }
    }

    private void loadCurrentConversation() {
        ConversationRecord conversation = conversationRepository.getCurrentConversation();
        if (conversation != null) {
            applyConversation(conversation);
        }
    }

    private void loadConversation(String id) {
        ConversationRecord conversation = conversationRepository.getConversation(id);
        if (conversation == null) {
            return;
        }
        applyConversation(conversation);
        conversationRepository.setCurrentConversationId(conversation.getId());
    }

    private void applyConversation(ConversationRecord conversation) {
        messages.clear();
        for (MessageRecord record : conversation.getMessages()) {
            messages.add(record.toChatMessage());
        }
        currentConversationId = conversation.getId();
        currentConversationCreatedAt = conversation.getCreatedAt();
        resetMessageSequence();
    }

    private void ensureCurrentConversation() {
        if (currentConversationId.length() > 0) {
            return;
        }
        currentConversationId = String.valueOf(System.currentTimeMillis());
        currentConversationCreatedAt = System.currentTimeMillis();
    }

    private void persistCurrentConversation() {
        if (currentConversationId.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        ArrayList<MessageRecord> records = new ArrayList<>();
        for (ChatMessage message : messages) {
            records.add(new MessageRecord(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getReasoningContent(),
                    now,
                    false,
                    message.isHidden(),
                    message.isExcludeFromContext(),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError(),
                    messageRawJson(message)
            ));
        }
        ConversationRecord conversation = new ConversationRecord(
                currentConversationId,
                deriveTitle(),
                projectPath,
                currentConversationCreatedAt > 0 ? currentConversationCreatedAt : now,
                now,
                true,
                "",
                records
        );
        conversationRepository.saveConversation(conversation);
        if (aiBehaviorSettingsRepository.get().isLearningModeEnabled()) {
            learningContextRepository.indexConversation(projectPath, conversation);
        }
    }

    private String deriveTitle() {
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.USER && message.getContent().trim().length() > 0) {
                String firstLine = message.getContent().trim().replace('\n', ' ');
                return firstLine.length() > 28 ? firstLine.substring(0, 28) + "..." : firstLine;
            }
        }
        return "新对话";
    }

    private void resetMessageSequence() {
        int max = 0;
        for (ChatMessage message : messages) {
            String id = message.getId();
            if (id != null && id.startsWith("m")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        messageSequence = Math.max(max + 1, messages.size() + 1);
    }

    private String nextId() {
        return "m" + messageSequence++;
    }

    private String messageRawJson(ChatMessage message) {
        if (message != null && message.getRole() == ChatMessage.Role.TOOL) {
            try {
                return new JSONObject()
                        .put("diff_id", message.getDiffId())
                        .put("review_state", message.getReviewState())
                        .put("review_message", message.getReviewMessage())
                        .toString();
            } catch (Exception ignored) {
                return "";
            }
        }
        if (message == null || !message.hasToolCalls()) {
            return "";
        }
        try {
            JSONArray array = new JSONArray();
            for (ToolCall call : message.getToolCalls()) {
                array.put(new JSONObject()
                        .put("id", call.getId())
                        .put("name", call.getName())
                        .put("arguments", call.getArguments()));
            }
            return new JSONObject().put("tool_calls", array).toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
