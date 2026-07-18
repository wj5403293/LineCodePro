package cn.lineai.mvp;

import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.model.SheetOption;
import java.util.List;

class ProjectWorkspaceHost implements ProjectWorkspaceController.Host {
    private final MainCoordinator coordinator;

    ProjectWorkspaceHost(MainCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public boolean isViewAttached() {
        return coordinator.viewProxy().isAttached();
    }

    @Override
    public boolean isTermuxSshHost() {
        return coordinator.isTermuxSshHost();
    }

    @Override
    public void applyProject(ProjectRecord project) {
        coordinator.applyProject(project);
    }

    @Override
    public void resetTodoState() {
        coordinator.resetTodoState();
    }

    @Override
    public void requestSshFileTreeLoad(boolean force) {
        coordinator.requestSshFileTreeLoad(force);
    }

    @Override
    public void showSheet(String title, List<SheetOption> options) {
        coordinator.viewProxy().showSheet(title, options);
    }

    @Override
    public void showInputDialog(String title, String message, String initialValue, String actionId) {
        coordinator.viewProxy().showInputDialog(title, message, initialValue, actionId);
    }

    @Override
    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
        coordinator.viewProxy().showConfirmationDialog(title, message, confirmLabel, danger, actionId);
    }

    @Override
    public void hideOverlays() {
        coordinator.viewHideOverlays();
    }

    @Override
    public void openExternalProjectPicker() {
        coordinator.viewProxy().openExternalProjectPicker();
    }

    @Override
    public void openManageAllFilesPermissionSettings() {
        coordinator.viewProxy().openManageAllFilesPermissionSettings();
    }

    @Override
    public void requestLegacyStoragePermissions() {
        coordinator.viewProxy().requestLegacyStoragePermissions();
    }

    @Override
    public void showNotice(String text) {
        coordinator.showNotice(text);
    }

    @Override
    public void render() {
        coordinator.render();
    }
}
