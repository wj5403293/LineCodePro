package cn.lineai.mvp;

import cn.lineai.model.SheetOption;
import java.util.ArrayList;

class FileOperationHost extends HostBase implements FileOperationController.Host {
    FileOperationHost(MainCoordinator coordinator) {
        super(coordinator);
    }

    @Override
    public boolean isSshExecutionMode() {
        return coordinator().isSshExecutionMode();
    }

    @Override
    public boolean isTerminalProviderExecutionMode() {
        return coordinator().isTerminalProviderExecutionMode();
    }

    @Override
    public void showInputDialog(String title, String message, String initialValue, String actionId) {
        coordinator().viewProxy().showInputDialog(title, message, initialValue, actionId);
    }

    @Override
    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
        coordinator().viewProxy().showConfirmationDialog(title, message, confirmLabel, danger, actionId);
    }

    @Override
    public void showFileActionDialog(String title, String subtitle, ArrayList<SheetOption> options) {
        coordinator().viewProxy().showFileActionDialog(title, subtitle, options);
    }

    @Override
    public void addExpandedPath(String path) {
        coordinator().fileTreeInteractionController.addExpandedPath(path);
    }

    @Override
    public void refreshSshDirectoryAfterFileOperation(String path) {
        coordinator().sshFileTreeController.refreshDirectoryAfterFileOperation(path);
    }

    @Override
    public void refreshIpcDirectoryAfterFileOperation(String path) {
        coordinator().ipcFileTreeController.refreshDirectoryAfterFileOperation(path);
    }

    @Override
    public void showNotice(String text) {
        coordinator().showNotice(text);
    }
}
