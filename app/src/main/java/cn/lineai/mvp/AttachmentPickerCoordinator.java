package cn.lineai.mvp;

import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.data.repository.IpcFileTreeStore;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import java.util.HashSet;
import java.util.Set;

public final class AttachmentPickerCoordinator {
    public interface Host {
        boolean isStreaming();

        boolean isSshExecutionMode();

        boolean isTerminalProviderExecutionMode();

        String projectPath();

        String defaultHomePath();

        boolean isViewAttached();

        void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source);
    }

    private final FileTreeStore fileTreeRepository;
    private final SshFileTreeStore sshFileTreeRepository;
    private final IpcFileTreeStore ipcFileTreeRepository;
    private final BackgroundRunner backgroundRunner;
    private final UiDispatcher uiDispatcher;
    private final Host host;

    private final Set<String> expandedPaths = new HashSet<>();
    private FileTreeNode tree;
    private String rootPath = "";
    private String source = "";
    private boolean loading;
    private String message = "";
    private boolean active;
    private int loadGeneration;

    public AttachmentPickerCoordinator(
            FileTreeStore fileTreeRepository,
            SshFileTreeStore sshFileTreeRepository,
            IpcFileTreeStore ipcFileTreeRepository,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher,
            Host host
    ) {
        this.fileTreeRepository = fileTreeRepository;
        this.sshFileTreeRepository = sshFileTreeRepository;
        this.ipcFileTreeRepository = ipcFileTreeRepository;
        this.backgroundRunner = backgroundRunner;
        this.uiDispatcher = uiDispatcher;
        this.host = host;
    }

    public void onAttachmentPickerRequested() {
        if (host.isStreaming()) {
            return;
        }
        active = true;
        if (host.isSshExecutionMode()) {
            source = InputAttachment.SOURCE_SSH;
        } else if (host.isTerminalProviderExecutionMode()) {
            source = InputAttachment.SOURCE_TERMINAL_PROVIDER;
        } else {
            source = InputAttachment.SOURCE_LOCAL;
        }
        rootPath = attachmentRootPath(source);
        expandedPaths.clear();
        if (rootPath.length() > 0) {
            expandedPaths.add(rootPath);
        }
        refreshAttachmentPicker(true);
    }

    public void onAttachmentPickerNodeSelected(String path, boolean directory) {
        if (!active || !directory || path == null || path.length() == 0) {
            return;
        }
        String cleanPath = path.trim();
        if (expandedPaths.contains(cleanPath)) {
            expandedPaths.remove(cleanPath);
        } else {
            expandedPaths.add(cleanPath);
        }
        refreshAttachmentPicker(false);
    }

    public void onAttachmentPickerCancelled() {
        active = false;
        loading = false;
        message = "";
        loadGeneration++;
    }

    private void refreshAttachmentPicker(boolean resetTree) {
        if (!active || !host.isViewAttached()) {
            return;
        }
        if (resetTree) {
            tree = null;
        }
        if (InputAttachment.SOURCE_SSH.equals(source)) {
            refreshSshAttachmentPicker();
            return;
        }
        if (InputAttachment.SOURCE_TERMINAL_PROVIDER.equals(source)) {
            refreshIpcAttachmentPicker();
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
        renderAttachmentPicker();
    }

    private void refreshSshAttachmentPicker() {
        loading = true;
        message = "正在通过 SFTP 读取 SSH 文件...";
        renderAttachmentPicker();
        int generation = ++loadGeneration;
        String root = rootPath;
        HashSet<String> expanded = new HashSet<>(expandedPaths);
        backgroundRunner.execute("linecode-ssh-attachment-picker", () -> {
            try {
                FileTreeNode node = sshFileTreeRepository.buildTree(root, expanded);
                uiDispatcher.post(() -> {
                    if (!active || generation != loadGeneration) {
                        return;
                    }
                    tree = node;
                    rootPath = node.getPath();
                    expandedPaths.add(node.getPath());
                    loading = false;
                    message = "";
                    renderAttachmentPicker();
                });
            } catch (Exception e) {
                uiDispatcher.post(() -> {
                    if (!active || generation != loadGeneration) {
                        return;
                    }
                    tree = null;
                    loading = false;
                    message = e.getMessage();
                    renderAttachmentPicker();
                });
            }
        });
    }

    private void refreshIpcAttachmentPicker() {
        loading = true;
        message = "正在通过 IPC 读取终端提供者目录...";
        renderAttachmentPicker();
        int generation = ++loadGeneration;
        String root = rootPath;
        HashSet<String> expanded = new HashSet<>(expandedPaths);
        backgroundRunner.execute("linecode-ipc-attachment-picker", () -> {
            try {
                FileTreeNode node = ipcFileTreeRepository.buildTree(root, expanded);
                uiDispatcher.post(() -> {
                    if (!active || generation != loadGeneration) {
                        return;
                    }
                    tree = node;
                    rootPath = node.getPath();
                    expandedPaths.add(node.getPath());
                    loading = false;
                    message = "";
                    renderAttachmentPicker();
                });
            } catch (Exception e) {
                uiDispatcher.post(() -> {
                    if (!active || generation != loadGeneration) {
                        return;
                    }
                    tree = null;
                    loading = false;
                    message = e.getMessage();
                    renderAttachmentPicker();
                });
            }
        });
    }

    private void renderAttachmentPicker() {
        if (!active || !host.isViewAttached()) {
            return;
        }
        String title;
        if (InputAttachment.SOURCE_SSH.equals(source)) {
            title = "选择 SSH 文件";
        } else if (InputAttachment.SOURCE_TERMINAL_PROVIDER.equals(source)) {
            title = "选择终端提供者文件";
        } else {
            title = "选择本地文件";
        }
        host.showAttachmentPicker(title, tree, loading, message, source);
    }

    private String attachmentRootPath(String source) {
        if (InputAttachment.SOURCE_SSH.equals(source)) {
            return host.projectPath().length() == 0 ? "." : host.projectPath();
        }
        if (InputAttachment.SOURCE_TERMINAL_PROVIDER.equals(source)) {
            return host.projectPath().length() == 0 ? "." : host.projectPath();
        }
        if (host.projectPath().length() > 0) {
            return host.projectPath();
        }
        return host.defaultHomePath();
    }
}
