package cn.lineai.mvp;

import android.os.SystemClock;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.ToolCallTextParser;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.context.MemoryExtractionService;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.MessageContentSanitizer;
import cn.lineai.model.ModelConfig;
import cn.lineai.mvp.agent.AgentExecutionController;
import cn.lineai.mvp.agent.AgentProgressSession;
import cn.lineai.mvp.agent.PendingToolExecution;
import cn.lineai.mvp.agent.ToolExecutionBatch;
import cn.lineai.state.TodoStateStore;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

final class GenerationFlowController {
    private static final long STREAM_RENDER_INTERVAL_MS = 80L;
    private static final String SHELL_EXECUTE_TOOL = "shell_execute";
    private static final String TOOL_REVIEW_SESSION_AUTO = "session_auto";

    interface Host {
        String nextId();

        String projectPath();

        String projectSource();

        String currentConversationId();

        String syncModePermission();

        void persistCurrentConversation();

        void render();

        void stopGenerationKeepAlive();

        void setCurrentCancellationToken(ModelCancellationToken cancellationToken);

        default boolean isSshExecutionMode() {
            return false;
        }

        default boolean isTerminalProviderExecutionMode() {
            return false;
        }
    }

    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final ModelClient modelClient;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final ExtensionStore extensionRepository;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolRunController toolRunController;
    private final ToolMessageController toolMessageController;
    private final ModelPromptController modelPromptController;
    private final GenerationController generationController;
    private final AgentExecutionController agentExecutionController;
    private final TodoStateStore todoStateStore;
    private final MainThreadDispatcher mainThread;
    private final BackgroundTaskRunner backgroundTasks;
    private final Host host;
    private final Set<String> sessionAutoConfirmedTools = new HashSet<>();
    private final HashMap<String, PendingAgentToolReview> pendingAgentToolReviews = new HashMap<>();
    private final HashMap<String, PendingAgentToolRequest> pendingAgentToolRequests = new HashMap<>();
    private final StringBuilder pendingStreamTextDelta = new StringBuilder();
    private final StringBuilder pendingStreamReasoningDelta = new StringBuilder();
    private final HashMap<String, StringBuilder> streamingRawTextByMessageId = new HashMap<>();
    private final AgentExecutionController.Host agentHost = new AgentExecutionController.Host() {
        @Override
        public String projectPath() {
            return host.projectPath();
        }

        @Override
        public String projectSource() {
            return host.projectSource();
        }

        @Override
        public boolean isSshExecutionMode() {
            return host.isSshExecutionMode();
        }

        @Override
        public boolean isTerminalProviderExecutionMode() {
            return host.isTerminalProviderExecutionMode();
        }

        @Override
        public void syncModePermission() {
            host.syncModePermission();
        }

        @Override
        public void addOrReplaceToolResult(ToolResult result) {
            mainThread.dispatch(() -> GenerationFlowController.this.addOrReplaceToolResult(result));
        }

        @Override
        public void render() {
            mainThread.dispatch(host::render);
        }

        @Override
        public void scheduleAgentProgressRender(AgentProgressSession session) {
            GenerationFlowController.this.scheduleAgentProgressRender(session);
        }

        @Override
        public void postToolProgress(
                int generationId,
                ModelCancellationToken cancellationToken,
                String toolCallId,
                String toolName,
                String content,
                boolean error
        ) {
            GenerationFlowController.this.postToolProgress(
                    generationId,
                    cancellationToken,
                    toolCallId,
                    toolName,
                    content,
                    error
            );
        }

        @Override
        public void requestAgentToolReview(String displayToolCallId, ToolCall call, ToolResult pendingToolResult) {
            mainThread.post(() -> {
                synchronized (pendingAgentToolRequests) {
                    pendingAgentToolRequests.put(displayToolCallId, new PendingAgentToolRequest(call, pendingToolResult));
                }
                host.persistCurrentConversation();
                host.render();
            });
        }

        @Override
        public void clearAgentToolReview(String displayToolCallId) {
            mainThread.post(() -> {
                synchronized (pendingAgentToolRequests) {
                    pendingAgentToolRequests.remove(displayToolCallId);
                }
                host.render();
            });
        }
    };

    private String pendingStreamAssistantId = "";
    private int pendingStreamGenerationId = -1;
    private boolean streamRenderScheduled;
    private long lastStreamRenderAt;
    private PendingToolExecution pendingToolExecution;
    private String sessionAutoConfirmedConversationId = "";

    GenerationFlowController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ModelClient modelClient,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            MemoryExtractionService memoryExtractionService,
            ExtensionStore extensionRepository,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolExecutionCoordinator toolExecutionCoordinator,
            cn.lineai.data.repository.ToolSettingsStore toolSettingsStore,
            ToolMessageController toolMessageController,
            ModelPromptController modelPromptController,
            GenerationController generationController,
            AgentExecutionController agentExecutionController,
            TodoStateStore todoStateStore,
            MainThreadDispatcher mainThread,
            BackgroundTaskRunner backgroundTasks,
            Host host
    ) {
        this(
                messages,
                chatSessionStore,
                modelClient,
                aiBehaviorSettingsRepository,
                memoryExtractionService,
                extensionRepository,
                toolRegistry,
                toolExecutor,
                new ToolRunController(toolExecutionCoordinator, toolRegistry, toolSettingsStore),
                toolMessageController,
                modelPromptController,
                generationController,
                agentExecutionController,
                todoStateStore,
                mainThread,
                backgroundTasks,
                host
        );
    }

    GenerationFlowController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ModelClient modelClient,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            MemoryExtractionService memoryExtractionService,
            ExtensionStore extensionRepository,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ToolRunController toolRunController,
            ToolMessageController toolMessageController,
            ModelPromptController modelPromptController,
            GenerationController generationController,
            AgentExecutionController agentExecutionController,
            TodoStateStore todoStateStore,
            MainThreadDispatcher mainThread,
            BackgroundTaskRunner backgroundTasks,
            Host host
    ) {
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.modelClient = modelClient;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.memoryExtractionService = memoryExtractionService;
        this.extensionRepository = extensionRepository;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.toolRunController = toolRunController;
        this.toolMessageController = toolMessageController;
        this.modelPromptController = modelPromptController;
        this.generationController = generationController;
        this.agentExecutionController = agentExecutionController;
        this.todoStateStore = todoStateStore;
        this.mainThread = mainThread;
        this.backgroundTasks = backgroundTasks;
        this.host = host;
        if (this.agentExecutionController != null) {
            this.agentExecutionController.setToolReviewAwaiter(new AgentExecutionController.ToolReviewAwaiter() {
                @Override
                public String awaitReview(String displayToolCallId, ToolCall call, ModelCancellationToken cancellationToken) throws InterruptedException {
                    return awaitAgentToolReview(displayToolCallId, call, cancellationToken);
                }

                @Override
                public boolean isAutoConfirmed(ToolCall call) {
                    return isSessionAutoConfirmed(call);
                }
            });
        }
    }

    void startInitialModelRequest(
            int generationId,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            String userInput
    ) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return;
        }
        ArrayList<ModelMessage> requestMessages = modelPromptController.buildModelMessages(userInput);
        String assistantId = host.nextId();
        streamingRawTextByMessageId.put(assistantId, new StringBuilder());
        messages.add(new ChatMessage(assistantId, ChatMessage.Role.ASSISTANT, "", true));
        host.persistCurrentConversation();
        host.render();

        backgroundTasks.execute("linecode-model-stream", () -> {
            try {
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                ModelRequestOptions requestOptions = modelPromptController.requestOptions(aiSettings, selectedModel, 0);
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
                finishGeneration(generationId, assistantId, selectedModel, response, cancellationToken, 0);
            } catch (ModelCompletionException e) {
                failGeneration(generationId, assistantId, "模型通信失败：\n" + e.getMessage());
            }
        });
    }

    void handleToolReview(String state) {
        PendingToolExecution pending = pendingToolExecution;
        if (pending == null) {
            return;
        }
        handlePendingToolReview(pending, state);
    }

    boolean handleAgentToolReview(String toolCallId, String state) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return false;
        }
        PendingAgentToolReview pending;
        synchronized (pendingAgentToolReviews) {
            pending = pendingAgentToolReviews.get(toolCallId);
        }
        if (pending == null) {
            return false;
        }
        pending.resolve(state);
        if (isSessionAutoReview(state, pending.toolCall())) {
            rememberSessionAutoConfirmation(pending.toolCall());
        }
        return true;
    }

    boolean isPendingAgentToolReview(String toolCallId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return false;
        }
        synchronized (pendingAgentToolRequests) {
            return pendingAgentToolRequests.containsKey(toolCallId);
        }
    }

    void acceptAgentToolReview(String toolCallId, String state) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        PendingAgentToolRequest request;
        synchronized (pendingAgentToolRequests) {
            request = pendingAgentToolRequests.remove(toolCallId);
        }
        if (request == null) {
            return;
        }
        handleAgentToolReview(toolCallId, state);
    }

    private void handlePendingToolReview(PendingToolExecution pending, String state) {
        if (pending == null || pending.getToolCall() == null) {
            return;
        }
        if (!chatSessionStore.isActiveGeneration(pending.getGenerationId())) {
            pendingToolExecution = null;
            return;
        }
        boolean sessionAutoAccepted = isSessionAutoReview(state, pending.getToolCall());
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        pendingToolExecution = null;
        if ("rejected".equals(normalizedState)) {
            ToolResult rejected = new ToolResult(
                    pending.getToolCall().getId(),
                    pending.getToolCall().getName(),
                    rejectedToolMessage(pending.getToolCall()),
                    true,
                    "",
                    "rejected",
                    ""
            );
            addOrReplaceToolResult(rejected);
            host.persistCurrentConversation();
            host.render();
            continueToolExecution(
                    pending.getGenerationId(),
                    pending.getSelectedModel(),
                    pending.getRemainingCalls(),
                    pending.getUsedToolCallCount(),
                    pending.getHomePath(),
                    pending.getCancellationToken()
            );
            return;
        }
        if (sessionAutoAccepted) {
            rememberSessionAutoConfirmation(pending.getToolCall());
        }

        ToolResult accepted = new ToolResult(
                pending.getToolCall().getId(),
                pending.getToolCall().getName(),
                "",
                false,
                "",
                "accepted",
                ""
        );
        addOrReplaceToolResult(accepted);
        host.persistCurrentConversation();
        host.render();
        executeAcceptedPendingTool(pending);
    }

    boolean isPendingToolReview(String toolCallId) {
        return toolCallId != null
                && toolCallId.length() > 0
                && pendingToolExecution != null
                && pendingToolExecution.getToolCall() != null
                && toolCallId.equals(pendingToolExecution.getToolCall().getId());
    }

    void postToolProgress(
            int generationId,
            ModelCancellationToken cancellationToken,
            String toolCallId,
            String toolName,
            String content,
            boolean error
    ) {
        mainThread.post(() -> {
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return;
            }
            if (toolCallId == null || toolCallId.length() == 0) {
                return;
            }
            addOrReplaceToolResult(new ToolResult(
                    toolCallId,
                    toolName,
                    content,
                    error,
                    "",
                    "running",
                    ""
            ));
            host.render();
        });
    }

    void flushPendingAssistantDelta() {
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
        if (!chatSessionStore.isActiveGeneration(generationId)) {
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
        host.render();
    }

    void cancelActiveGeneration() {
        pendingToolExecution = null;
        rejectPendingAgentToolReviews();
        synchronized (pendingAgentToolRequests) {
            pendingAgentToolRequests.clear();
        }
        clearStreamingRawText();
    }

    void clearStreamingRawText() {
        streamingRawTextByMessageId.clear();
    }

    void clearSessionAutoToolConfirmations() {
        synchronized (sessionAutoConfirmedTools) {
            sessionAutoConfirmedTools.clear();
            sessionAutoConfirmedConversationId = host.currentConversationId();
        }
    }

    void scheduleAgentProgressRender(AgentProgressSession session) {
        if (session == null || !session.shouldScheduleRender()) {
            return;
        }
        mainThread.postDelayed(() -> flushAgentProgress(session), session.renderDelayMs());
    }

    void markRunningAgentProgressStopped(String terminatedMessage) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() != ChatMessage.Role.TOOL) {
                continue;
            }
            ConversationResumeSanitizer.SanitizedPayload payload =
                    ConversationResumeSanitizer.sanitizeToolContent(message.getContent(), terminatedMessage);
            if (!payload.changed()) {
                continue;
            }
            messages.set(i, new ChatMessage(
                    message.getId(),
                    ChatMessage.Role.TOOL,
                    payload.content(),
                    message.getReasoningContent(),
                    false,
                    message.isHidden(),
                    message.isExcludeFromContext(),
                    message.getToolCalls(),
                    message.getToolResults(),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError() || payload.error(),
                    message.getDiffId(),
                    message.getReviewState(),
                    message.getReviewMessage()
            ));
        }
        addTerminatedResultsForUnfinishedToolCalls(terminatedMessage);
    }

    private void appendAssistantDelta(int generationId, String assistantId, String textDelta, String reasoningDelta) {
        mainThread.post(() -> {
            if (!chatSessionStore.isActiveGeneration(generationId)) {
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

    private void finishGeneration(
            int generationId,
            String assistantId,
            ModelConfig selectedModel,
            ModelCompletionResponse response,
            ModelCancellationToken cancellationToken,
            int usedToolCallCount
    ) {
        mainThread.post(() -> {
            flushPendingAssistantDelta();
            if (!chatSessionStore.isActiveGeneration(generationId)) {
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
                if (!generationController.canExecuteToolCalls(selectedModel, usedToolCallCount, toolCalls.size())) {
                    messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.ASSISTANT,
                            generationController.toolLimitMessage(selectedModel, usedToolCallCount, toolCalls.size()), false));
                    finishActiveGeneration();
                    host.persistCurrentConversation();
                    host.render();
                    return;
                }
                host.persistCurrentConversation();
                host.render();
                executeToolsAndContinue(
                        generationId,
                        selectedModel,
                        toolCalls,
                        usedToolCallCount + toolCalls.size(),
                        cancellationToken
                );
                return;
            }
            finishActiveGeneration();
            host.persistCurrentConversation();
            scheduleMemoryExtractionIfNeeded(selectedModel);
            host.render();
        });
    }

    private void executeToolsAndContinue(
            int generationId,
            ModelConfig selectedModel,
            List<ToolCall> toolCalls,
            int usedToolCallCount,
            ModelCancellationToken cancellationToken
    ) {
        continueToolExecution(
                generationId,
                selectedModel,
                toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls),
                usedToolCallCount,
                host.projectPath(),
                cancellationToken
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
        backgroundTasks.execute("linecode-tool-execute", () -> {
            ToolExecutionBatch batch = executeToolCallsUntilPending(toolCalls, homePath, selectedModel, cancellationToken, generationId, usedToolCallCount);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return;
            }
            mainThread.post(() -> {
                if (!chatSessionStore.isActiveGeneration(generationId)) {
                    return;
                }
                handleToolExecutionBatch(generationId, selectedModel, usedToolCallCount, homePath, cancellationToken, batch);
            });
        });
    }

    private ToolExecutionBatch executeToolCallsUntilPending(
            List<ToolCall> toolCalls,
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId,
            int usedToolCallCount
    ) {
        host.syncModePermission();
        toolRegistry.reloadExtensions();
        ToolExecutionCoordinator.ToolExecutionPlan plan = toolRunController.createPlan(toolCalls);
        HashMap<String, ToolResult> resultById = new HashMap<>();
        ToolContext context = toolContext(homePath, selectedModel, cancellationToken, generationId, usedToolCallCount);

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
                    restoreInterrupt(e);
                    resultById.put(call.getId(), new ToolResult(call.getId(), call.getName(), "执行失败: " + describeException(e), true));
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
                        toolRunController.orderedResults(toolCalls, resultById),
                        call,
                        toolRunController.remainingCalls(sequentialTasks, i + 1)
                );
            }
            resultById.put(call.getId(), executeToolCallWithSessionPolicy(call, context));
        }

        return new ToolExecutionBatch(toolRunController.orderedResults(toolCalls, resultById), null, new ArrayList<>());
    }

    private void handleToolExecutionBatch(
            int generationId,
            ModelConfig selectedModel,
            int usedToolCallCount,
            String homePath,
            ModelCancellationToken cancellationToken,
            ToolExecutionBatch batch
    ) {
        toolMessageController.addOrReplaceToolResults(batch.getCompletedResults());
        if (batch.getPendingCall() != null) {
            ToolResult pendingResult = new ToolResult(
                    batch.getPendingCall().getId(),
                    batch.getPendingCall().getName(),
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
                    batch.getPendingCall(),
                    batch.getRemainingCalls(),
                    usedToolCallCount,
                    homePath,
                    cancellationToken
            );
            host.persistCurrentConversation();
            host.render();
            return;
        }
        host.persistCurrentConversation();
        continueModelAfterTools(generationId, selectedModel, usedToolCallCount, cancellationToken);
    }

    private void continueModelAfterTools(
            int generationId,
            ModelConfig selectedModel,
            int usedToolCallCount,
            ModelCancellationToken cancellationToken
    ) {
        ArrayList<ModelMessage> nextRequestMessages = modelPromptController.buildModelMessages("", usedToolCallCount);
        String nextAssistantId = host.nextId();
        streamingRawTextByMessageId.put(nextAssistantId, new StringBuilder());
        messages.add(new ChatMessage(nextAssistantId, ChatMessage.Role.ASSISTANT, "", true));
        host.render();
        backgroundTasks.execute("linecode-tool-continuation", () -> {
            try {
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                ModelRequestOptions nextRequestOptions = modelPromptController.requestOptions(aiSettings, selectedModel, usedToolCallCount);
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
                finishGeneration(generationId, nextAssistantId, selectedModel, response, cancellationToken, usedToolCallCount);
            } catch (ModelCompletionException e) {
                failGeneration(generationId, nextAssistantId, "模型通信失败：\n" + e.getMessage());
            }
        });
    }

    private ToolContext toolContext(
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId,
            int usedToolCallCount
    ) {
        final int[] counter = new int[]{Math.max(0, usedToolCallCount)};
        return new ToolContext(homePath, extensionRepository.skillWriteRoots(homePath), new ToolContext.AgentRunner() {
            @Override
            public ToolResult runAgent(JSONObject input, ToolContext context) {
                return agentExecutionController.runAgentTool(input, context, selectedModel, cancellationToken, generationId, agentHost, counter[0]);
            }

            @Override
            public ToolResult runAgentPipeline(JSONObject input, ToolContext context) {
                return agentExecutionController.runAgentPipelineTool(input, context, selectedModel, cancellationToken, generationId, agentHost, counter[0]);
            }
        }, "", (toolCallId, toolName, content, error) ->
                postToolProgress(generationId, cancellationToken, toolCallId, toolName, content, error),
                todoStateStore);
    }

    private String awaitAgentToolReview(
            String displayToolCallId,
            ToolCall call,
            ModelCancellationToken cancellationToken
    ) throws InterruptedException {
        if (displayToolCallId == null || displayToolCallId.length() == 0) {
            return "accepted";
        }
        PendingAgentToolReview pending = new PendingAgentToolReview(call);
        synchronized (pendingAgentToolReviews) {
            pendingAgentToolReviews.put(displayToolCallId, pending);
        }
        try {
            while (true) {
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    return "rejected";
                }
                if (pending.await(250L)) {
                    return pending.state();
                }
            }
        } finally {
            synchronized (pendingAgentToolReviews) {
                pendingAgentToolReviews.remove(displayToolCallId);
            }
        }
    }

    private void executeAcceptedPendingTool(PendingToolExecution pending) {
        backgroundTasks.execute("linecode-tool-confirmed", () -> {
            ToolResult result;
            try {
                host.syncModePermission();
                toolRegistry.reloadExtensions();
                result = toolExecutor
                        .executeConfirmed(pending.getToolCall(), toolContext(
                                pending.getHomePath(),
                                pending.getSelectedModel(),
                                pending.getCancellationToken(),
                                pending.getGenerationId(),
                                pending.getUsedToolCallCount()
                        ))
                        .withReview("accepted", "");
            } catch (Exception e) {
                restoreInterrupt(e);
                result = new ToolResult(
                        pending.getToolCall().getId(),
                        pending.getToolCall().getName(),
                        "执行失败: " + describeException(e),
                        true,
                        "",
                        "accepted",
                        ""
                );
            }
            ToolResult finalResult = result;
            if (pending.getCancellationToken() != null && pending.getCancellationToken().isCancelled()) {
                return;
            }
            mainThread.post(() -> {
                if (!chatSessionStore.isActiveGeneration(pending.getGenerationId())) {
                    return;
                }
                addOrReplaceToolResult(finalResult);
                host.persistCurrentConversation();
                host.render();
                continueToolExecution(
                        pending.getGenerationId(),
                        pending.getSelectedModel(),
                        pending.getRemainingCalls(),
                        pending.getUsedToolCallCount(),
                        pending.getHomePath(),
                        pending.getCancellationToken()
                );
            });
        });
    }

    private ToolResult executeToolCallWithSessionPolicy(ToolCall call, ToolContext context) {
        if (isSessionAutoConfirmed(call)) {
            return toolExecutor.executeConfirmed(call, context).withReview("accepted", "");
        }
        return toolExecutor.execute(call, context);
    }

    private boolean shouldPauseForConfirmation(ToolCall call) {
        host.syncModePermission();
        if (isSessionAutoConfirmed(call)) {
            return false;
        }
        return toolRunController.shouldPauseForConfirmation(call);
    }

    private boolean isSessionAutoReview(String state, ToolCall call) {
        return TOOL_REVIEW_SESSION_AUTO.equals(state)
                && call != null
                && SHELL_EXECUTE_TOOL.equals(call.getName());
    }

    private void rememberSessionAutoConfirmation(ToolCall call) {
        if (call == null || !SHELL_EXECUTE_TOOL.equals(call.getName())) {
            return;
        }
        synchronized (sessionAutoConfirmedTools) {
            syncSessionAutoToolConfirmationsLocked();
            sessionAutoConfirmedTools.add(call.getName());
        }
    }

    private boolean isSessionAutoConfirmed(ToolCall call) {
        if (call == null) {
            return false;
        }
        synchronized (sessionAutoConfirmedTools) {
            syncSessionAutoToolConfirmationsLocked();
            return sessionAutoConfirmedTools.contains(call.getName());
        }
    }

    private void syncSessionAutoToolConfirmationsLocked() {
        String conversationId = host.currentConversationId();
        if (!conversationId.equals(sessionAutoConfirmedConversationId)) {
            sessionAutoConfirmedTools.clear();
            sessionAutoConfirmedConversationId = conversationId;
        }
    }

    private void failGeneration(int generationId, String assistantId, String text) {
        mainThread.post(() -> {
            flushPendingAssistantDelta();
            if (!chatSessionStore.isActiveGeneration(generationId)) {
                return;
            }
            int index = findMessageIndex(assistantId);
            if (index >= 0) {
                ChatMessage message = messages.get(index);
                messages.set(index, message.withContent(text, message.getReasoningContent(), false));
            } else {
                messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.ASSISTANT, text, false));
            }
            streamingRawTextByMessageId.remove(assistantId);
            finishActiveGeneration();
            host.persistCurrentConversation();
            host.render();
        });
    }

    private void finishActiveGeneration() {
        chatSessionStore.setStreaming(false);
        host.setCurrentCancellationToken(null);
        host.stopGenerationKeepAlive();
    }

    private void scheduleAssistantDeltaFlush() {
        if (streamRenderScheduled) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delay = Math.max(0L, STREAM_RENDER_INTERVAL_MS - (now - lastStreamRenderAt));
        streamRenderScheduled = true;
        mainThread.postDelayed(this::flushPendingAssistantDelta, delay);
    }

    private void flushAgentProgress(AgentProgressSession session) {
        if (!mainThread.isMainThread()) {
            mainThread.post(() -> flushAgentProgress(session));
            return;
        }
        if (session == null || !session.canRender()) {
            if (session != null) {
                session.notifyMirror();
            }
            return;
        }
        if (!chatSessionStore.isActiveGeneration(session.getGenerationId())) {
            return;
        }
        session.notifyMirror();
        addOrReplaceToolResult(session.snapshotResult());
        host.render();
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
        String capturedProjectPath = host.projectPath();
        backgroundTasks.execute("linecode-memory-extract", () -> memoryExtractionService.extractAndStore(
                selectedModel,
                capturedProjectPath,
                userInput,
                transcript
        ));
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
        String text = MessageContentSanitizer.stripInlineDataImages(content).trim();
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

    void addTerminatedResultsForUnfinishedToolCalls(String terminatedMessage) {
        toolMessageController.addTerminatedResultsForUnfinishedToolCalls(terminatedMessage);
    }

    private void rejectPendingAgentToolReviews() {
        synchronized (pendingAgentToolReviews) {
            for (PendingAgentToolReview pending : pendingAgentToolReviews.values()) {
                if (pending != null) {
                    pending.resolve("rejected");
                }
            }
            pendingAgentToolReviews.clear();
        }
    }

    private void addOrReplaceToolResult(ToolResult result) {
        toolMessageController.addOrReplaceToolResult(result);
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
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null && cause.getMessage().trim().length() > 0) {
            return cause.getMessage().trim();
        }
        String name = error.getClass().getSimpleName();
        return name.length() == 0 ? "未知错误" : name;
    }

    private static final class PendingAgentToolReview {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final ToolCall toolCall;
        private String state = "accepted";

        PendingAgentToolReview(ToolCall toolCall) {
            this.toolCall = toolCall;
        }

        boolean await(long timeoutMs) throws InterruptedException {
            return latch.await(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        }

        void resolve(String nextState) {
            state = "rejected".equals(nextState) ? "rejected" : "accepted";
            latch.countDown();
        }

        String state() {
            return state;
        }

        ToolCall toolCall() {
            return toolCall;
        }
    }

    private static final class PendingAgentToolRequest {
        final ToolCall call;
        final ToolResult pending;

        PendingAgentToolRequest(ToolCall call, ToolResult pending) {
            this.call = call;
            this.pending = pending;
        }
    }

    private int findMessageIndex(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
