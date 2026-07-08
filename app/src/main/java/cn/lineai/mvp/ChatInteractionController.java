package cn.lineai.mvp;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChatInteractionController {
    interface Host {
        String nextId();

        String agentTerminatedMessage();

        String syncModePermission();

        void ensureCurrentConversation();

        void persistCurrentConversation();

        void loadConversation(String id);

        void cancelActiveGeneration();

        void startGenerationKeepAlive();

        void stopGenerationKeepAlive();

        void setCurrentCancellationToken(ModelCancellationToken cancellationToken);

        void markStreamingMessagesStopped();

        void resetTodoState();

        void hideOverlays();

        void showChatScreen();

        void setComposerDraft(String text, ArrayList<InputAttachment> attachments);

        void render();
    }

    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final ConversationStore conversationRepository;
    private final ModelStore modelRepository;
    private final ChatModeRepository chatModeRepository;
    private final ToolSettingsStore toolSettingsRepository;
    private final ContextCompactionController contextCompactionController;
    private final GenerationFlowController generationFlowController;
    private final Host host;
    private String lastMessageModelId = "";

    ChatInteractionController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ConversationStore conversationRepository,
            ModelStore modelRepository,
            ChatModeRepository chatModeRepository,
            ToolSettingsStore toolSettingsRepository,
            ContextCompactionController contextCompactionController,
            GenerationFlowController generationFlowController,
            Host host
    ) {
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.conversationRepository = conversationRepository;
        this.modelRepository = modelRepository;
        this.chatModeRepository = chatModeRepository;
        this.toolSettingsRepository = toolSettingsRepository;
        this.contextCompactionController = contextCompactionController;
        this.generationFlowController = generationFlowController;
        this.host = host;
    }

    void newConversation() {
        host.cancelActiveGeneration();
        if (chatSessionStore.isStreaming()) {
            host.markStreamingMessagesStopped();
            generationFlowController.markRunningAgentProgressStopped(host.agentTerminatedMessage());
        }
        chatSessionStore.setStreaming(false);
        host.stopGenerationKeepAlive();
        host.persistCurrentConversation();
        chatSessionStore.startNewConversation(System.currentTimeMillis());
        generationFlowController.clearSessionAutoToolConfirmations();
        lastMessageModelId = "";
        host.hideOverlays();
        host.showChatScreen();
        host.render();
    }

    void selectConversation(String id) {
        if (id == null || id.length() == 0) {
            host.hideOverlays();
            return;
        }
        host.cancelActiveGeneration();
        boolean wasStreaming = chatSessionStore.isStreaming();
        if (wasStreaming) {
            host.markStreamingMessagesStopped();
            generationFlowController.markRunningAgentProgressStopped(host.agentTerminatedMessage());
        }
        chatSessionStore.setStreaming(false);
        host.stopGenerationKeepAlive();
        if (wasStreaming || !id.equals(chatSessionStore.getCurrentConversationId())) {
            host.persistCurrentConversation();
        }
        host.loadConversation(id);
        lastMessageModelId = "";
        generationFlowController.clearSessionAutoToolConfirmations();
        host.resetTodoState();
        host.hideOverlays();
        host.showChatScreen();
        host.render();
    }

    void deleteConversation(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        conversationRepository.deleteConversation(id);
        if (id.equals(chatSessionStore.getCurrentConversationId())) {
            host.cancelActiveGeneration();
            chatSessionStore.setStreaming(false);
            host.stopGenerationKeepAlive();
            chatSessionStore.clearCurrentConversation();
            generationFlowController.clearSessionAutoToolConfirmations();
            host.resetTodoState();
            lastMessageModelId = "";
        }
        host.render();
    }

    void sendMessage(String text) {
        sendMessage(text, Collections.emptyList());
    }

    void sendMessage(String text, List<InputAttachment> attachments) {
        String trimmed = text == null ? "" : text.trim();
        ArrayList<InputAttachment> safeAttachments = sanitizeAttachments(attachments);
        if ((trimmed.isEmpty() && safeAttachments.isEmpty()) || chatSessionStore.isStreaming()) {
            return;
        }
        host.ensureCurrentConversation();
        String userContent = composeUserContent(trimmed, safeAttachments);
        messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.USER, userContent, false, safeAttachments));
        host.persistCurrentConversation();
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.ASSISTANT,
                    "还没有可用模型。请进入 设置 → 模型管理 → 添加模型，保存后再发送消息。",
                    false));
            host.persistCurrentConversation();
            host.render();
            return;
        }

        String currentModelId = selectedModel.getModelId();
        if (lastMessageModelId.length() > 0 && !lastMessageModelId.equals(currentModelId)) {
            messages.add(ChatMessage.modelSwitchNotice(host.nextId(), lastMessageModelId, currentModelId));
            host.persistCurrentConversation();
        }
        lastMessageModelId = currentModelId;

        int generationId = chatSessionStore.nextGenerationId();
        ModelCancellationToken cancellationToken = new ModelCancellationToken();
        host.setCurrentCancellationToken(cancellationToken);
        chatSessionStore.setStreaming(true);
        generationFlowController.setSmoothStream(cn.lineai.model.ChatMode.CHAT.equals(chatModeRepository.getMode()));
        host.startGenerationKeepAlive();
        host.render();

        String activeUserMessageId = messages.get(messages.size() - 1).getId();
        if (contextCompactionController.shouldAutoCompactBeforeRequest(selectedModel, activeUserMessageId)) {
            contextCompactionController.startContextCompaction(
                    generationId,
                    selectedModel,
                    cancellationToken,
                    true,
                    activeUserMessageId,
                    userContent
            );
            return;
        }
        generationFlowController.startInitialModelRequest(generationId, selectedModel, cancellationToken, userContent);
    }

    void recallMessage(String messageId) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        String targetId = messageId == null ? "" : messageId;
        if (targetId.length() == 0) {
            return;
        }
        int targetIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (targetId.equals(message.getId()) && message.getRole() == ChatMessage.Role.USER) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            return;
        }
        ChatMessage recalled = messages.get(targetIndex);
        String recalledText = recallText(recalled.getContent(), recalled.getAttachments());
        ArrayList<InputAttachment> recalledAttachments = new ArrayList<>(recalled.getAttachments());
        while (messages.size() > targetIndex) {
            messages.remove(messages.size() - 1);
        }
        host.persistCurrentConversation();
        host.render();
        host.setComposerDraft(recalledText, recalledAttachments);
    }

    void changeChatMode(String mode) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        chatModeRepository.applyMode(mode, toolSettingsRepository);
        generationFlowController.setSmoothStream(cn.lineai.model.ChatMode.CHAT.equals(mode));
        host.syncModePermission();
        host.render();
    }

    void stopGeneration() {
        generationFlowController.flushPendingAssistantDelta();
        host.cancelActiveGeneration();
        chatSessionStore.setStreaming(false);
        host.stopGenerationKeepAlive();
        chatSessionStore.invalidateActiveGeneration();
        host.markStreamingMessagesStopped();
        generationFlowController.markRunningAgentProgressStopped(host.agentTerminatedMessage());
        host.persistCurrentConversation();
        host.render();
    }

    void clearCurrentConversation() {
        String currentConversationId = chatSessionStore.getCurrentConversationId();
        messages.clear();
        if (currentConversationId.length() > 0) {
            conversationRepository.deleteConversation(currentConversationId);
        }
        chatSessionStore.clearCurrentConversation();
        generationFlowController.clearSessionAutoToolConfirmations();
    }

    void resetModelTracking() {
        lastMessageModelId = "";
    }

    private ArrayList<InputAttachment> sanitizeAttachments(List<InputAttachment> rawAttachments) {
        ArrayList<InputAttachment> result = new ArrayList<>();
        if (rawAttachments == null) {
            return result;
        }
        for (InputAttachment attachment : rawAttachments) {
            if (attachment == null || attachment.getPath().length() == 0) {
                continue;
            }
            boolean exists = false;
            for (InputAttachment current : result) {
                if (current.matches(attachment.getPath(), attachment.getSource())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                result.add(new InputAttachment(attachment.getName(), attachment.getPath(), attachment.getSource()));
            }
        }
        return result;
    }

    private String composeUserContent(String text, List<InputAttachment> attachments) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() > 0) {
            return trimmed;
        }
        return attachments == null || attachments.isEmpty() ? "" : "已附加文件";
    }

    private String recallText(String content, List<InputAttachment> attachments) {
        String value = content == null ? "" : content.trim();
        if ("已附加文件".equals(value) && attachments != null && !attachments.isEmpty()) {
            return "";
        }
        return content == null ? "" : content;
    }
}
