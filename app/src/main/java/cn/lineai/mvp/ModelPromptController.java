package cn.lineai.mvp;

import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.ai.protocol.OpenAiCompatibleCapabilities;
import cn.lineai.context.ContextManager;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatMode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.MessageContentSanitizer;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelStore;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.workspace.WorkspacePaths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ModelPromptController {
    interface Host {
        String syncModePermission();

        String projectPath();

        String projectSource();

        boolean isTerminalProviderExecutionMode();
    }

    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ChatModeRepository chatModeRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final LearningContextStore learningContextRepository;
    private final ContextManager contextManager;
    private final ModelStore modelRepository;
    private final ExtensionStore extensionRepository;
    private final SystemPromptProvider systemPromptProvider;
    private final ToolSettingsStore toolSettingsRepository;
    private final ToolRegistry toolRegistry;
    private final cn.lineai.state.TodoStateStore todoStateStore;
    private final Host host;

    ModelPromptController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            ChatModeRepository chatModeRepository,
            PromptTemplateRepository promptTemplateRepository,
            LearningContextStore learningContextRepository,
            ContextManager contextManager,
            ModelStore modelRepository,
            ExtensionStore extensionRepository,
            SystemPromptProvider systemPromptProvider,
            ToolSettingsStore toolSettingsRepository,
            ToolRegistry toolRegistry,
            cn.lineai.state.TodoStateStore todoStateStore,
            Host host
    ) {
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.chatModeRepository = chatModeRepository;
        this.promptTemplateRepository = promptTemplateRepository;
        this.learningContextRepository = learningContextRepository;
        this.contextManager = contextManager;
        this.modelRepository = modelRepository;
        this.extensionRepository = extensionRepository;
        this.systemPromptProvider = systemPromptProvider;
        this.toolSettingsRepository = toolSettingsRepository;
        this.toolRegistry = toolRegistry;
        this.todoStateStore = todoStateStore;
        this.host = host;
    }

    ArrayList<ModelMessage> buildModelMessages(String userInput) {
        return buildModelMessages(userInput, 0);
    }

    ArrayList<ModelMessage> buildModelMessages(String userInput, int usedToolCallCount) {
        ArrayList<ModelMessage> modelMessages = new ArrayList<>();
        String activeChatMode = host.syncModePermission();
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        String projectPath = host.projectPath();
        String learningContext = aiSettings.isLearningModeEnabled()
                ? learningContextRepository.buildLearningContext(projectPath, userInput, chatSessionStore.getCurrentConversationId())
                : "";
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        String promptHomePath = promptHomePath();
        String extensionContext = extensionRepository.buildExtensionPrompt(projectPath);
        String attachmentContext = buildAttachmentPrompt(messages);
        String systemContext = joinPromptContext(joinPromptContext(learningContext, attachmentContext), extensionContext);
        String systemPrompt = systemPromptProvider.build(
                promptHomePath,
                aiSettings.getToneMode(),
                chatModePromptContext(activeChatMode),
                systemContext,
                buildToolPrompt(selectedModel, usedToolCallCount),
                selectedModel,
                renderTodoStateForPrompt()
        );
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

    ModelRequestOptions requestOptions(AiBehaviorSettings aiSettings, ModelConfig selectedModel, int usedToolCallCount) {
        host.syncModePermission();
        toolRegistry.reloadExtensions();
        Set<String> enabledToolNames = toolSettingsRepository.getEnabledToolNames(toolRegistry.getAll());
        return new ModelRequestOptions(
                aiSettings.getReasoningEffort(),
                aiSettings.isPreserveReasoningEnabled(),
                hasRemainingToolCalls(selectedModel, usedToolCallCount)
                        ? toolRegistry.getByNameSet(enabledToolNames)
                        : new ArrayList<BaseTool>()
        );
    }

    String promptHomePath() {
        String projectPath = host.projectPath();
        if (host.isTerminalProviderExecutionMode() && projectPath.length() == 0) {
            return "~";
        }
        if (WorkspacePaths.SOURCE_SSH.equals(host.projectSource()) && projectPath.length() == 0) {
            return "~";
        }
        return projectPath;
    }

    private String renderTodoStateForPrompt() {
        if (todoStateStore == null || todoStateStore.isEmpty()) {
            return "";
        }
        java.util.List<cn.lineai.model.TodoItem> snapshot = todoStateStore.snapshot();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < snapshot.size(); i++) {
            cn.lineai.model.TodoItem item = snapshot.get(i);
            if (item == null) {
                continue;
            }
            builder.append(i + 1)
                    .append(". [")
                    .append(item.getStatus())
                    .append("] ")
                    .append(item.getContent());
            if (i < snapshot.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String chatModePromptContext(String mode) {
        String normalized = ChatMode.normalize(mode);
        if (ChatMode.CHAT.equals(normalized)) {
            return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_CHAT_MODE_CHAT);
        }
        if (ChatMode.PLAN.equals(normalized)) {
            return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_CHAT_MODE_PLAN);
        }
        if (ChatMode.CONTROL.equals(normalized)) {
            return ChatMode.promptContext(ChatMode.CONTROL);
        }
        return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_CHAT_MODE_AGENT);
    }

    private ModelMessage toModelMessage(ChatMessage message, boolean includeReasoning) {
        if (message.getRole() == ChatMessage.Role.SYSTEM) {
            return new SystemModelMessage(message.getContent());
        }
        if (message.getRole() == ChatMessage.Role.TOOL) {
            return new ToolModelMessage(
                    modelToolContent(message),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError()
            );
        }
        if (message.getRole() == ChatMessage.Role.USER) {
            return new UserModelMessage(message.getContent(), message.getResponseInputItemJson());
        }
        return new AssistantModelMessage(MessageContentSanitizer.forModel(message),
                includeReasoning ? message.getReasoningContent() : "",
                message.getToolCalls());
    }

    private String modelToolContent(ChatMessage message) {
        return MessageContentSanitizer.toolContentForModel(message);
    }

    private String buildToolPrompt(ModelConfig selectedModel, int usedToolCallCount) {
        host.syncModePermission();
        if (!hasRemainingToolCalls(selectedModel, usedToolCallCount)) {
            return "## 可用工具\n当前没有可用工具。";
        }
        toolRegistry.reloadExtensions();
        return toolSettingsRepository.buildToolPrompt(toolRegistry.getAll(), supportsNativeTools(selectedModel));
    }

    private String buildAttachmentPrompt(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int sectionCount = 0;
        for (ChatMessage message : history) {
            if (message == null || message.getRole() != ChatMessage.Role.USER || !message.hasAttachments()) {
                continue;
            }
            String label = recallText(message.getContent(), message.getAttachments()).trim();
            if (label.length() == 0) {
                label = "用户消息 " + (sectionCount + 1);
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("### ").append(label).append('\n');
            for (InputAttachment attachment : message.getAttachments()) {
                builder.append("- ")
                        .append(attachment.getName())
                        .append(" (")
                        .append(attachment.getSource())
                        .append("): ")
                        .append(attachment.getPath())
                        .append('\n');
            }
            sectionCount++;
        }
        if (sectionCount == 0) {
            return "";
        }
        return "## 附加文件位置\n"
                + "这些路径来自用户在输入框左侧选择的文件；除非用户明确要求，不要在回复中原样复述。\n"
                + builder.toString().trim();
    }

    private String joinPromptContext(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.length() == 0) {
            return right;
        }
        if (right.length() == 0) {
            return left;
        }
        return left + "\n\n" + right;
    }

    private boolean supportsNativeTools(ModelConfig selectedModel) {
        if (selectedModel == null) {
            return false;
        }
        ModelProtocolType type = selectedModel.getProtocolType();
        if (type == ModelProtocolType.OPENAI_COMPATIBLE) {
            return OpenAiCompatibleCapabilities.supportsNativeTools(selectedModel);
        }
        return type == ModelProtocolType.ANTHROPIC_MESSAGES
                || type == ModelProtocolType.CODEX_RESPONSES;
    }

    private boolean hasRemainingToolCalls(ModelConfig selectedModel, int usedToolCallCount) {
        int limit = selectedModel == null ? ModelConfig.DEFAULT_TOOL_CALL_LIMIT : selectedModel.getToolCallLimit();
        return limit == ModelConfig.UNLIMITED_TOOL_CALLS || Math.max(0, usedToolCallCount) < limit;
    }

    private String recallText(String content, List<InputAttachment> attachments) {
        String value = content == null ? "" : content.trim();
        if ("已附加文件".equals(value) && attachments != null && !attachments.isEmpty()) {
            return "";
        }
        return content == null ? "" : content;
    }
}
