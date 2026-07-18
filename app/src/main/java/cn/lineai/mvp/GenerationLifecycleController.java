package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.model.ChatMessage;
import cn.lineai.service.KeepAliveService;
import java.util.ArrayList;

final class GenerationLifecycleController {
    private final Context context;
    private final ArrayList<ChatMessage> messages;
    private GenerationFlowController generationFlowController;
    private ModelCancellationToken currentCancellationToken;
    private boolean keepAliveActive;

    GenerationLifecycleController(Context context, ArrayList<ChatMessage> messages) {
        this.context = context.getApplicationContext();
        this.messages = messages;
    }

    void setGenerationFlowController(GenerationFlowController generationFlowController) {
        this.generationFlowController = generationFlowController;
    }

    void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
        currentCancellationToken = cancellationToken;
    }

    ModelCancellationToken currentCancellationToken() {
        return currentCancellationToken;
    }

    void cancelActiveGeneration() {
        if (generationFlowController != null) {
            generationFlowController.cancelActiveGeneration();
        }
        if (currentCancellationToken != null) {
            currentCancellationToken.cancel();
            currentCancellationToken = null;
        }
        stopKeepAlive();
    }

    void startKeepAlive() {
        if (keepAliveActive) {
            return;
        }
        keepAliveActive = true;
        try {
            KeepAliveService.startGeneration(context);
        } catch (Exception ignored) {
        }
    }

    void stopKeepAlive() {
        if (!keepAliveActive) {
            return;
        }
        keepAliveActive = false;
        try {
            KeepAliveService.stopGeneration(context);
        } catch (Exception ignored) {
        }
    }

    void markStreamingMessagesStopped() {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.isStreaming()) {
                if (message.isCompactBlock()) {
                    messages.set(i, message.withCompactStatus(ChatMessage.COMPACT_STATUS_ERROR, false));
                } else {
                    messages.set(i, message.withContent(message.getContent(), message.getReasoningContent(), false));
                }
            }
        }
    }
}
