package cn.lineai.mvp;

import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.FileTreeNode;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DirectoryPickerController {
    interface Host {
        boolean isViewAttached();

        String projectPath();

        boolean isTermuxSshHost();

        void applySelectedProject(String path, boolean ssh);

        void hideDirectoryPicker();

        void showDirectoryPicker(
                String title,
                String subtitle,
                FileTreeNode tree,
                String selectedPath,
                boolean loading,
                String message
        );

        void render();
    }

    private static final String MODE_SSH_REMOTE = "ssh_remote";

    private final FileTreeStore fileTreeRepository;
    private final SshFileTreeStore sshFileTreeRepository;
    private final BackgroundRunner backgroundRunner;
    private final UiDispatcher uiDispatcher;
    private final Host host;

    private final Set<String> expandedPaths = new HashSet<>();
    private FileTreeNode tree;
    private String mode = "";
    private String selectedPath = "";
    private String rootPath = "";
    private boolean loading;
    private String message = "";

    DirectoryPickerController(
            FileTreeStore fileTreeRepository,
            SshFileTreeStore sshFileTreeRepository,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher,
            Host host
    ) {
        this.fileTreeRepository = fileTreeRepository;
        this.sshFileTreeRepository = sshFileTreeRepository;
        this.backgroundRunner = backgroundRunner;
        this.uiDispatcher = uiDispatcher;
        this.host = host;
    }

    void onNodeSelected(String path) {
        if (path == null || path.length() == 0) {
            return;
        }
        String cleanPath = path.trim();
        rootPath = cleanPath;
        selectedPath = cleanPath;
        expandedPaths.clear();
        expandedPaths.add(cleanPath);
        tree = rebuildTree(tree);
        renderPicker();
        refresh();
    }

    void onConfirmed() {
        String path = selectedPath == null ? "" : selectedPath.trim();
        if (path.length() == 0) {
            return;
        }
        host.applySelectedProject(path, isSshPicker());
        mode = "";
        tree = null;
        host.hideDirectoryPicker();
        host.render();
    }

    void onCancelled() {
        mode = "";
        loading = false;
    }

    void startLocal(String executionMode) {
        mode = ToolSettingsRepository.normalizeExecutionMode(executionMode);
        rootPath = defaultExternalStorageRoot();
        selectedPath = rootPath;
        expandedPaths.clear();
        expandedPaths.add(rootPath);
        refresh();
    }

    void startSsh() {
        mode = MODE_SSH_REMOTE;
        rootPath = host.projectPath().length() == 0 ? "." : host.projectPath();
        selectedPath = rootPath;
        expandedPaths.clear();
        expandedPaths.add(rootPath);
        refresh();
    }

    private void refresh() {
        if (!host.isViewAttached() || mode.length() == 0) {
            return;
        }
        if (isRemoteSshPicker()) {
            refreshSsh();
            return;
        }
        try {
            loading = false;
            message = "";
            tree = fileTreeRepository.buildReadableTree(rootPath, expandedPaths);
        } catch (RuntimeException e) {
            tree = null;
            message = e.getMessage();
        }
        renderPicker();
    }

    private void refreshSsh() {
        loading = true;
        message = "正在读取 SSH 目录...";
        renderPicker();
        String capturedRoot = rootPath;
        HashSet<String> capturedExpanded = new HashSet<>(expandedPaths);
        backgroundRunner.execute("linecode-ssh-directory-picker", () -> {
            try {
                FileTreeNode loadedTree = sshFileTreeRepository.buildTree(capturedRoot, capturedExpanded);
                uiDispatcher.post(() -> applySshTree(capturedRoot, loadedTree));
            } catch (Exception e) {
                uiDispatcher.post(() -> {
                    loading = false;
                    message = e.getMessage();
                    renderPicker();
                });
            }
        });
    }

    private void applySshTree(String requestedRoot, FileTreeNode loadedTree) {
        tree = loadedTree;
        String previousRoot = rootPath;
        String previousSelected = selectedPath;
        rootPath = loadedTree.getPath();
        if (selectedPath.length() == 0
                || isSamePickerPath(previousSelected, requestedRoot)
                || isSshHomeAlias(previousSelected)) {
            selectedPath = loadedTree.getPath();
        }
        if (isSamePickerPath(previousRoot, requestedRoot) || isSshHomeAlias(previousRoot)) {
            expandedPaths.add(loadedTree.getPath());
        }
        expandedPaths.add(loadedTree.getPath());
        tree = rebuildTree(tree);
        loading = false;
        message = "";
        renderPicker();
    }

    private FileTreeNode rebuildTree(FileTreeNode node) {
        if (node == null) {
            return null;
        }
        ArrayList<FileTreeNode> children = new ArrayList<>();
        List<FileTreeNode> rawChildren = node.getChildren();
        for (int i = 0; i < rawChildren.size(); i++) {
            children.add(rebuildTree(rawChildren.get(i)));
        }
        boolean expanded = node.isDirectory()
                && (node.isExpanded() || expandedPaths.contains(node.getPath()));
        return new FileTreeNode(node.getName(), node.getPath(), node.isDirectory(), expanded, children);
    }

    private String normalizePickerPath(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isSamePickerPath(String left, String right) {
        return normalizePickerPath(left).equals(normalizePickerPath(right));
    }

    private boolean isSshHomeAlias(String path) {
        String value = path == null ? "" : path.trim();
        return value.length() == 0 || ".".equals(value) || "~".equals(value);
    }

    private void renderPicker() {
        if (!host.isViewAttached()) {
            return;
        }
        boolean sshMode = isSshPicker();
        String title = sshMode ? "选择 SSH 工作区" : "选择本地工作区";
        String subtitle = selectedPath.length() == 0 ? rootPath : selectedPath;
        host.showDirectoryPicker(title, subtitle, tree, selectedPath, loading, message);
    }

    private boolean isRemoteSshPicker() {
        return MODE_SSH_REMOTE.equals(mode)
                || (ToolSettingsRepository.EXECUTION_SSH.equals(mode) && !host.isTermuxSshHost());
    }

    private boolean isSshPicker() {
        return MODE_SSH_REMOTE.equals(mode)
                || ToolSettingsRepository.EXECUTION_SSH.equals(mode);
    }

    private String defaultExternalStorageRoot() {
        File primary = new File("/storage/emulated/0");
        if (primary.isDirectory()) {
            return primary.getAbsolutePath();
        }
        File storage = new File("/storage");
        return storage.isDirectory() ? storage.getAbsolutePath() : "/";
    }
}
