package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.KeepAliveRepository;
import cn.lineai.data.repository.StorageStatsRepository;
import cn.lineai.service.KeepAliveService;
import java.util.ArrayList;
import cn.lineai.model.ChatMessage;

final class StorageMaintenanceController {
    interface Host {
        void clearCurrentConversation();

        void refreshStorageScreen();

        void render();
    }

    private final Context context;
    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final ConversationStore conversationRepository;
    private final KeepAliveRepository keepAliveRepository;
    private final StorageStatsRepository storageStatsRepository;
    private final Host host;

    StorageMaintenanceController(
            Context context,
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ConversationStore conversationRepository,
            KeepAliveRepository keepAliveRepository,
            StorageStatsRepository storageStatsRepository,
            Host host
    ) {
        this.context = context.getApplicationContext();
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.conversationRepository = conversationRepository;
        this.keepAliveRepository = keepAliveRepository;
        this.storageStatsRepository = storageStatsRepository;
        this.host = host;
    }

    void clearDiffCache() {
        storageStatsRepository.clearDiffCache();
        host.refreshStorageScreen();
        host.render();
    }

    void clearChatHistory() {
        storageStatsRepository.clearChatHistory();
        messages.clear();
        chatSessionStore.clearCurrentConversation();
        conversationRepository.clearAll();
        host.clearCurrentConversation();
        host.refreshStorageScreen();
        host.render();
    }

    void applyKeepAliveSettings() {
        KeepAliveRepository.KeepAliveSettings settings = keepAliveRepository.getSettings();
        if (settings.wakeLockEnabled || settings.foregroundEnabled || settings.fakeAudioEnabled) {
            KeepAliveService.start(context,
                    settings.wakeLockEnabled,
                    settings.foregroundEnabled,
                    settings.fakeAudioEnabled);
        } else {
            KeepAliveService.stop(context);
        }
    }
}
