package cn.lineai.mvp;

import cn.lineai.data.repository.FileTreeRepository;
import cn.lineai.data.repository.IpcFileTreeRepository;
import cn.lineai.data.repository.SshFileTreeRepository;
import cn.lineai.model.SheetOption;
import java.util.ArrayList;

public final class FileOperationController {
    interface BackgroundRunner extends cn.lineai.mvp.BackgroundRunner {
    }

    interface FileStore {
        void createFile(String parentPath, String name) throws Exception;

        void createDirectory(String parentPath, String name) throws Exception;

        void rename(String path, String newName) throws Exception;

        void delete(String path) throws Exception;

        void copyInto(String sourcePath, String targetDirectoryPath) throws Exception;
    }

    interface Host extends CoordinatorHost {
        boolean isSshExecutionMode();

        boolean isTerminalProviderExecutionMode();

        void showInputDialog(String title, String message, String initialValue, String actionId);

        void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId);

        void showFileActionDialog(String title, String subtitle, ArrayList<SheetOption> options);

        void addExpandedPath(String path);

        void refreshSshDirectoryAfterFileOperation(String path);

        void refreshIpcDirectoryAfterFileOperation(String path);

        void showNotice(String text);
    }

    interface UiDispatcher extends cn.lineai.mvp.UiDispatcher {
    }

    private static final class LocalFileStore implements FileStore {
        private final FileTreeRepository repository;

        LocalFileStore(FileTreeRepository repository) {
            this.repository = repository;
        }

        @Override
        public void createFile(String parentPath, String name) throws Exception {
            repository.createFile(parentPath, name);
        }

        @Override
        public void createDirectory(String parentPath, String name) throws Exception {
            repository.createDirectory(parentPath, name);
        }

        @Override
        public void rename(String path, String newName) throws Exception {
            repository.rename(path, newName);
        }

        @Override
        public void delete(String path) throws Exception {
            repository.delete(path);
        }

        @Override
        public void copyInto(String sourcePath, String targetDirectoryPath) throws Exception {
            repository.copyInto(sourcePath, targetDirectoryPath);
        }
    }

    private static final class SshFileStore implements FileStore {
        private final SshFileTreeRepository repository;

        SshFileStore(SshFileTreeRepository repository) {
            this.repository = repository;
        }

        @Override
        public void createFile(String parentPath, String name) throws Exception {
            repository.createFile(parentPath, name);
        }

        @Override
        public void createDirectory(String parentPath, String name) throws Exception {
            repository.createDirectory(parentPath, name);
        }

        @Override
        public void rename(String path, String newName) throws Exception {
            repository.rename(path, newName);
        }

        @Override
        public void delete(String path) throws Exception {
            repository.delete(path);
        }

        @Override
        public void copyInto(String sourcePath, String targetDirectoryPath) throws Exception {
            repository.copyInto(sourcePath, targetDirectoryPath);
        }
    }

    private static final class IpcFileStore implements FileStore {
        private final IpcFileTreeRepository repository;

        IpcFileStore(IpcFileTreeRepository repository) {
            this.repository = repository;
        }

        @Override
        public void createFile(String parentPath, String name) throws Exception {
            repository.createFile(parentPath, name);
        }

        @Override
        public void createDirectory(String parentPath, String name) throws Exception {
            repository.createDirectory(parentPath, name);
        }

        @Override
        public void rename(String path, String newName) throws Exception {
            repository.rename(path, newName);
        }

        @Override
        public void delete(String path) throws Exception {
            repository.delete(path);
        }

        @Override
        public void copyInto(String sourcePath, String targetDirectoryPath) throws Exception {
            repository.copyInto(sourcePath, targetDirectoryPath);
        }
    }

    private final FileStore localFileStore;
    private final FileStore sshFileStore;
    private final FileStore ipcFileStore;
    private final Host host;
    private final BackgroundRunner backgroundRunner;
    private final UiDispatcher uiDispatcher;
    private String clipboardPath = "";
    private String clipboardName = "";
    private boolean clipboardSsh;
    private boolean clipboardIpc;

    public FileOperationController(
            FileTreeRepository fileTreeRepository,
            SshFileTreeRepository sshFileTreeRepository,
            IpcFileTreeRepository ipcFileTreeRepository,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this(
                new LocalFileStore(fileTreeRepository),
                new SshFileStore(sshFileTreeRepository),
                new IpcFileStore(ipcFileTreeRepository),
                host,
                backgroundRunner,
                uiDispatcher
        );
    }

    FileOperationController(
            FileStore localFileStore,
            FileStore sshFileStore,
            FileStore ipcFileStore,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this.localFileStore = localFileStore;
        this.sshFileStore = sshFileStore;
        this.ipcFileStore = ipcFileStore;
        this.host = host;
        this.backgroundRunner = backgroundRunner;
        this.uiDispatcher = uiDispatcher;
    }

    public String clipboardName() {
        return clipboardName;
    }

    public boolean canPasteInto(boolean directory) {
        if (!directory || clipboardPath.length() == 0) {
            return false;
        }
        if (host.isSshExecutionMode()) {
            return clipboardSsh;
        }
        if (host.isTerminalProviderExecutionMode()) {
            return clipboardIpc;
        }
        return !clipboardSsh && !clipboardIpc;
    }

    public void showFileNodeActions(String path, String name, boolean directory, boolean root) {
        if (path == null || path.length() == 0) {
            return;
        }
        ArrayList<SheetOption> options = new ArrayList<>();
        if (directory) {
            options.add(new SheetOption("file:create_file:" + path, "新建文件", path, false));
            options.add(new SheetOption("file:create_folder:" + path, "新建文件夹", path, false));
            if (canPasteInto(true)) {
                options.add(new SheetOption("file:paste:" + path, "粘贴", clipboardName, false));
            }
            if (!root) {
                options.add(new SheetOption("file:copy:" + path, "复制", name, false));
                options.add(new SheetOption("file:rename:" + path, "重命名", name, false));
                options.add(new SheetOption("file:delete:" + path, "删除", path, false));
            }
        } else {
            options.add(new SheetOption("file:copy:" + path, "复制", name, false));
            options.add(new SheetOption("file:rename:" + path, "重命名", name, false));
            options.add(new SheetOption("file:delete:" + path, "删除", path, false));
        }
        if (!options.isEmpty()) {
            host.showFileActionDialog(
                    root ? "工作区根目录" : (name == null || name.length() == 0 ? "文件操作" : name),
                    path,
                    options
            );
        }
    }

    public void requestCreateFile(String parentPath) {
        host.showInputDialog("新建文件", parentPath, "", "file:create_file:" + parentPath);
    }

    public void requestCreateFolder(String parentPath) {
        host.showInputDialog("新建文件夹", parentPath, "", "file:create_folder:" + parentPath);
    }

    public void requestRenameFileNode(String path) {
        host.showInputDialog("重命名", path, host.basename(path), "file:rename:" + path);
    }

    public void requestDeleteFileNode(String path) {
        host.showConfirmationDialog(
                "确认删除",
                "确定要删除 \"" + host.basename(path) + "\" 吗？此操作不可撤销。\n\n" + path,
                "删除",
                true,
                "file:delete:" + path
        );
    }

    public void createFileFromInput(String parentPath, String name) {
        runFileOperation(parentPath, () -> currentFileStore().createFile(parentPath, name));
    }

    public void createFolderFromInput(String parentPath, String name) {
        runFileOperation(parentPath, () -> currentFileStore().createDirectory(parentPath, name));
    }

    public void renameFileNodeFromInput(String path, String newName) {
        runFileOperation(host.parentPath(path), () -> currentFileStore().rename(path, newName));
    }

    public void deleteFileNode(String path) {
        runFileOperation(host.parentPath(path), () -> currentFileStore().delete(path));
    }

    public void copyFileNode(String path) {
        clipboardPath = path == null ? "" : path;
        clipboardName = host.basename(path);
        clipboardSsh = host.isSshExecutionMode();
        clipboardIpc = host.isTerminalProviderExecutionMode();
    }

    public void pasteFileNode(String targetDirectoryPath) {
        if (clipboardPath.length() == 0) {
            return;
        }
        if (host.isSshExecutionMode() && !clipboardSsh) {
            return;
        }
        if (host.isTerminalProviderExecutionMode() && !clipboardIpc) {
            return;
        }
        if (!host.isSshExecutionMode() && !host.isTerminalProviderExecutionMode() && (clipboardSsh || clipboardIpc)) {
            return;
        }
        runFileOperation(targetDirectoryPath, () -> currentFileStore().copyInto(clipboardPath, targetDirectoryPath));
    }

    private FileStore currentFileStore() {
        if (host.isTerminalProviderExecutionMode()) {
            return ipcFileStore;
        }
        if (host.isSshExecutionMode()) {
            return sshFileStore;
        }
        return localFileStore;
    }

    private void runFileOperation(String expandedPath, FileOperation operation) {
        backgroundRunner.execute("linecode-file-operation", () -> {
            try {
                operation.run();
                uiDispatcher.post(() -> {
                    if (expandedPath != null && expandedPath.length() > 0) {
                        host.addExpandedPath(expandedPath);
                    }
                    if (host.isSshExecutionMode()) {
                        host.refreshSshDirectoryAfterFileOperation(expandedPath);
                    }
                    if (host.isTerminalProviderExecutionMode()) {
                        host.refreshIpcDirectoryAfterFileOperation(expandedPath);
                    }
                    host.render();
                });
            } catch (Exception e) {
                uiDispatcher.post(() -> host.showNotice("文件操作失败: " + e.getMessage()));
            }
        });
    }

    private interface FileOperation {
        void run() throws Exception;
    }
}
