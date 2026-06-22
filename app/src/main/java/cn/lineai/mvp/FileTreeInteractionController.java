package cn.lineai.mvp;

import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.model.FileTreeNode;
import java.util.HashSet;
import java.util.Set;

final class FileTreeInteractionController {
    interface Host {
        boolean isSshExecutionMode();

        boolean isTerminalProviderExecutionMode();

        String projectPath();

        String parentPath(String path);

        void render();
    }

    private final FileTreeStore fileTreeRepository;
    private final SshFileTreeController sshFileTreeController;
    private final IpcFileTreeController ipcFileTreeController;
    private final Host host;
    private final Set<String> expandedPaths = new HashSet<>();

    FileTreeInteractionController(
            FileTreeStore fileTreeRepository,
            SshFileTreeController sshFileTreeController,
            IpcFileTreeController ipcFileTreeController,
            Host host
    ) {
        this.fileTreeRepository = fileTreeRepository;
        this.sshFileTreeController = sshFileTreeController;
        this.ipcFileTreeController = ipcFileTreeController;
        this.host = host;
    }

    boolean isExpanded(String path) {
        return expandedPaths.contains(path);
    }

    void addExpandedPath(String path) {
        if (path != null && path.length() > 0) {
            expandedPaths.add(path);
        }
    }

    void clearExpandedPaths() {
        expandedPaths.clear();
    }

    void resetToProjectRoot() {
        expandedPaths.clear();
        addExpandedPath(host.projectPath());
    }

    FileTreeNode getFileTree() {
        if (host.isTerminalProviderExecutionMode()) {
            return ipcFileTreeController.getFileTree();
        }
        if (host.isSshExecutionMode()) {
            return sshFileTreeController.getFileTree();
        }
        return fileTreeRepository.buildTree(host.projectPath(), expandedPaths);
    }

    void activate() {
        requestRemoteFileTrees(false);
        host.render();
    }

    void refresh() {
        resetToProjectRoot();
        requestRemoteFileTrees(true);
        host.render();
    }

    void handleNodeSelected(String path, boolean directory) {
        if (path == null || path.length() == 0 || !directory) {
            return;
        }
        if (expandedPaths.contains(path)) {
            expandedPaths.remove(path);
            rebuildRemoteTrees();
        } else {
            expandedPaths.add(path);
            if (host.isSshExecutionMode()) {
                sshFileTreeController.requestDirectoryLoad(path, false, false);
                sshFileTreeController.rebuildCachedTree();
            }
            if (host.isTerminalProviderExecutionMode()) {
                ipcFileTreeController.requestDirectoryLoad(path, false, false);
                ipcFileTreeController.rebuildCachedTree();
            }
        }
        host.render();
    }

    void refreshAfterRevert(String filePath) {
        String parentPath = host.parentPath(filePath);
        addExpandedPath(parentPath);
        if (host.isSshExecutionMode()) {
            sshFileTreeController.refreshDirectoryAfterFileOperation(parentPath);
        }
        if (host.isTerminalProviderExecutionMode()) {
            ipcFileTreeController.refreshDirectoryAfterFileOperation(parentPath);
        }
    }

    private void requestRemoteFileTrees(boolean force) {
        sshFileTreeController.requestFileTreeLoad(force);
        ipcFileTreeController.requestFileTreeLoad(force);
    }

    private void rebuildRemoteTrees() {
        if (host.isSshExecutionMode()) {
            sshFileTreeController.rebuildCachedTree();
        }
        if (host.isTerminalProviderExecutionMode()) {
            ipcFileTreeController.rebuildCachedTree();
        }
    }
}
