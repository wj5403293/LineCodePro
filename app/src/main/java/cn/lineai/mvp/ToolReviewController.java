package cn.lineai.mvp;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.service.FileRestorer;

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
            String filePath = diffRecord == null ? "" : diffRecord.getFilePath();
            DiffRepository.RevertResult result = diffRepository.revertDiff(diffId);
            if (!result.isSuccess()) {
                mainThread.post(() -> {
                    toolMessageController.updateToolReview(toolCallId, diffId, "", result.getMessage());
                    host.persistCurrentConversation();
                    host.render();
                });
                return;
            }
            if (result.getDiffRecord() != null) {
                try {
                    FileRestorer.restoreOldContent(result.getDiffRecord());
                } catch (Exception e) {
                    mainThread.post(() -> {
                        toolMessageController.updateToolReview(
                                toolCallId,
                                diffId,
                                "",
                                "File restore failed: " + e.getMessage()
                        );
                        host.persistCurrentConversation();
                        host.render();
                    });
                    return;
                }
                diffRepository.markReverted(diffId);
            }
            mainThread.post(() -> {
                toolMessageController.updateToolReview(
                        toolCallId,
                        diffId,
                        "rejected",
                        "Reverted change to " + filePath
                );
                host.refreshFileTreeAfterRevert(filePath);
                host.persistCurrentConversation();
                host.render();
            });
        });
    }
}
