package cn.lineai.mvp;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.DiffStore;

final class ToolReviewController {
    interface Host {
        void refreshFileTreeAfterRevert(String filePath);

        void persistCurrentConversation();

        void render();
    }

    private final DiffStore diffRepository;
    private final ToolMessageController toolMessageController;
    private final BackgroundTaskRunner backgroundTasks;
    private final MainThreadDispatcher mainThread;
    private final Host host;

    ToolReviewController(
            DiffStore diffRepository,
            ToolMessageController toolMessageController,
            BackgroundTaskRunner backgroundTasks,
            MainThreadDispatcher mainThread,
            Host host
    ) {
        this.diffRepository = diffRepository;
        this.toolMessageController = toolMessageController;
        this.backgroundTasks = backgroundTasks;
        this.mainThread = mainThread;
        this.host = host;
    }

    void review(String toolCallId, String state, String diffId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        String resolvedDiffId = diffId == null ? "" : diffId;
        if ("rejected".equals(normalizedState)) {
            if (resolvedDiffId.length() == 0) {
                resolvedDiffId = toolMessageController.findToolMessageDiffId(toolCallId);
            }
            if (resolvedDiffId.length() > 0) {
                rejectWithRevert(toolCallId, resolvedDiffId);
                return;
            }
        }
        toolMessageController.updateToolReview(toolCallId, resolvedDiffId, normalizedState, "");
        host.persistCurrentConversation();
        host.render();
    }

    private void rejectWithRevert(String toolCallId, String diffId) {
        backgroundTasks.execute("linecode-diff-revert", () -> {
            DiffRecord diffRecord = diffRepository.getDiff(diffId);
            DiffRepository.RevertResult result = diffRepository.revertDiff(diffId);
            String filePath = diffRecord == null ? "" : diffRecord.getFilePath();
            mainThread.post(() -> {
                toolMessageController.updateToolReview(
                        toolCallId,
                        diffId,
                        result.isSuccess() ? "rejected" : "",
                        result.getMessage()
                );
                if (result.isSuccess()) {
                    host.refreshFileTreeAfterRevert(filePath);
                }
                host.persistCurrentConversation();
                host.render();
            });
        });
    }
}
