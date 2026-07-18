package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.SheetOption;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.StoragePermissionManager;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ProjectWorkspaceController {
    interface Host {
        boolean isViewAttached();

        boolean isTermuxSshHost();

        void applyProject(ProjectRecord project);

        void resetTodoState();

        void requestSshFileTreeLoad(boolean force);

        void showSheet(String title, List<SheetOption> options);

        void showInputDialog(String title, String message, String initialValue, String actionId);

        void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId);

        void hideOverlays();

        void openExternalProjectPicker();

        void openManageAllFilesPermissionSettings();

        void requestLegacyStoragePermissions();

        void showNotice(String text);

        void render();
    }

    private final Context context;
    private final ProjectStore projectStore;
    private final ToolSettingsStore toolSettingsStore;
    private final SshFileTreeStore sshFileTreeStore;
    private final StoragePermissionManager storagePermissionManager;
    private final SafPathResolver safPathResolver;
    private final Host host;
    private final BackgroundRunner backgroundRunner;
    private final UiDispatcher uiDispatcher;
    private final ProjectSheetController projectSheetController;
    private boolean pendingExternalProjectOpen;
    private boolean startupProjectAvailabilityChecked;

    ProjectWorkspaceController(
            Context context,
            ProjectStore projectStore,
            ToolSettingsStore toolSettingsStore,
            SshFileTreeStore sshFileTreeStore,
            StoragePermissionManager storagePermissionManager,
            SafPathResolver safPathResolver,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this.context = context.getApplicationContext();
        this.projectStore = projectStore;
        this.toolSettingsStore = toolSettingsStore;
        this.sshFileTreeStore = sshFileTreeStore;
        this.storagePermissionManager = storagePermissionManager;
        this.safPathResolver = safPathResolver;
        this.host = host;
        this.backgroundRunner = backgroundRunner;
        this.uiDispatcher = uiDispatcher;
        projectSheetController = new ProjectSheetController(
                projectStore,
                new ProjectSheetController.Host() {
                    @Override
                    public String executionMode() {
                        return currentExecutionMode();
                    }

                    @Override
                    public boolean isTermuxSshHost() {
                        return host.isTermuxSshHost();
                    }

                    @Override
                    public boolean hasExternalStorageAccess() {
                        return storagePermissionManager.hasExternalStorageAccess();
                    }

                    @Override
                    public String storagePermissionMessage() {
                        return storagePermissionManager.permissionDeniedMessage();
                    }
                }
        );
    }

    void showProjectSheet() {
        if (!host.isViewAttached()) {
            return;
        }
        ProjectSheetController.ProjectSheet sheet = projectSheetController.buildProjectSheet();
        host.showSheet(sheet.getTitle(), sheet.getOptions());
    }

    void removeCurrentProject() {
        String executionMode = currentExecutionMode();
        ProjectRecord selected = projectStore.getSelectedProject(executionMode);
        if (isProtectedProject(selected)) {
            return;
        }
        boolean deleted = projectStore.deleteProject(selected.getId(), executionMode);
        if (deleted) {
            host.applyProject(projectStore.ensureSelectedProjectPath(executionMode));
            host.resetTodoState();
            host.render();
        }
    }

    boolean canRemoveCurrentProject() {
        ProjectRecord selected = projectStore.getSelectedProject();
        return selected != null && !WorkspacePaths.DEFAULT_PROJECT_ID.equals(selected.getId());
    }

    boolean handleDialogInput(String actionId, String value) {
        String id = actionId == null ? "" : actionId;
        if (!id.startsWith("project:create:")) {
            return false;
        }
        createProjectFromInput(id.substring("project:create:".length()), value);
        return true;
    }

    boolean handleSheetOption(String id) {
        if (id != null && id.startsWith("project:select:")) {
            selectProject(id.substring("project:select:".length()));
            host.hideOverlays();
            host.render();
            return true;
        }
        if (id != null && id.startsWith("project:delete:")) {
            deleteProjectFromPicker(id.substring("project:delete:".length()));
            return true;
        }
        if ("project:open_local_saf".equals(id)) {
            requestOpenLocalProjectSaf();
            return true;
        }
        if ("project:create".equals(id)) {
            if (host.isViewAttached()) {
                host.showInputDialog("创建工作区", "输入工作区名称", "", "project:create:" + currentExecutionMode());
            }
            return true;
        }
        if ("storage:manage_all_files".equals(id)) {
            openStoragePermissionSettings();
            host.render();
            return true;
        }
        return false;
    }

    void applyDirectoryPickerProject(String path, boolean ssh) {
        if (ssh) {
            ProjectRecord project = projectStore.saveSshProject(path, WorkspacePaths.basename(path));
            host.applyProject(project);
            host.requestSshFileTreeLoad(true);
            return;
        }
        ProjectRecord project = projectStore.saveExternalProject(path, WorkspacePaths.basename(path));
        host.applyProject(project);
    }

    void onExternalProjectTreePicked(String treeUri) {
        pendingExternalProjectOpen = false;
        String path = safPathResolver.treeUriToFileSystemPath(treeUri);
        if (path.length() == 0) {
            host.showNotice("无法将 SAF 目录转换为文件系统路径。请选择内部存储、Download 或具体目录。");
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            host.showNotice("外部工作区不可访问：\n" + path + "\n请确认已开启“管理所有文件”权限。");
            return;
        }
        if (isSshExecutionMode() && host.isTermuxSshHost()) {
            ProjectRecord project = projectStore.saveSshProject(path, WorkspacePaths.basename(path));
            host.applyProject(project);
            host.requestSshFileTreeLoad(true);
        } else {
            ProjectRecord project = projectStore.saveExternalProject(path, WorkspacePaths.basename(path));
            host.applyProject(project);
        }
        if (host.isViewAttached()) {
            host.hideOverlays();
        }
        host.render();
    }

    void onExternalProjectPickerCancelled() {
        pendingExternalProjectOpen = false;
    }

    void onStoragePermissionResult() {
        if (!pendingExternalProjectOpen || !host.isViewAttached() || !storagePermissionManager.hasExternalStorageAccess()) {
            return;
        }
        pendingExternalProjectOpen = false;
        host.openExternalProjectPicker();
    }

    void validateSelectedProjectAvailabilityOnStartup() {
        if (startupProjectAvailabilityChecked) {
            return;
        }
        startupProjectAvailabilityChecked = true;
        String executionMode = currentExecutionMode();
        ProjectRecord selected = projectStore.getSelectedProject(executionMode);
        if (selected == null) {
            return;
        }
        if (WorkspacePaths.SOURCE_EXTERNAL.equals(selected.getSource())) {
            String path = WorkspacePaths.displayPath(selected.getPath());
            if (path.length() > 0 && !new File(path).isDirectory()) {
                switchToDefaultProjectWithDialog(
                        executionMode,
                        "工作区不可访问",
                        "已保存的工作区不存在或无法访问：\n" + path + "\n\n已自动切换到默认 home。"
                );
            }
            return;
        }
        if (!WorkspacePaths.SOURCE_SSH.equals(selected.getSource())) {
            return;
        }
        String path = WorkspacePaths.displayPath(selected.getPath());
        if (path.length() == 0) {
            return;
        }
        backgroundRunner.execute("linecode-project-startup-check", () -> {
            try {
                boolean exists = sshFileTreeStore.directoryExists(path);
                if (!exists) {
                    uiDispatcher.post(() -> switchToDefaultProjectWithDialog(
                            ToolSettingsRepository.EXECUTION_SSH,
                            "SSH 工作区不可访问",
                            "已保存的 SSH 工作区不存在：\n" + path + "\n\n已自动切换到 ~。"
                    ));
                }
            } catch (Exception e) {
                uiDispatcher.post(() -> switchToDefaultProjectWithDialog(
                        ToolSettingsRepository.EXECUTION_SSH,
                        "SSH 工作区不可访问",
                        "无法访问已保存的 SSH 工作区：\n" + path + "\n\n" + e.getMessage() + "\n\n已自动切换到 ~。"
                ));
            }
        });
    }

    private void selectProject(String id) {
        String executionMode = currentExecutionMode();
        projectStore.setSelected(id, executionMode);
        ProjectRecord project = projectStore.ensureSelectedProjectPath(executionMode);
        if (WorkspacePaths.SOURCE_EXTERNAL.equals(project.getSource())
                && !storagePermissionManager.hasExternalStorageAccess()) {
            pendingExternalProjectOpen = false;
            if (host.isViewAttached()) {
                host.hideOverlays();
                if (storagePermissionManager.needsManageAllFilesPermission()) {
                    host.openManageAllFilesPermissionSettings();
                } else {
                    host.requestLegacyStoragePermissions();
                }
            }
            host.showNotice(storagePermissionManager.permissionDeniedMessage());
            return;
        }
        host.applyProject(project);
        host.requestSshFileTreeLoad(true);
    }

    private void deleteProjectFromPicker(String id) {
        String executionMode = currentExecutionMode();
        boolean deleted = projectStore.deleteProject(id, executionMode);
        if (deleted) {
            host.applyProject(projectStore.ensureSelectedProjectPath(executionMode));
        }
        host.render();
        if (host.isViewAttached()) {
            showProjectSheet();
        }
    }

    private void requestOpenLocalProjectSaf() {
        pendingExternalProjectOpen = true;
        if (!host.isViewAttached()) {
            return;
        }
        if (storagePermissionManager.hasExternalStorageAccess()) {
            pendingExternalProjectOpen = false;
            host.hideOverlays();
            host.openExternalProjectPicker();
            return;
        }
        host.hideOverlays();
        if (storagePermissionManager.needsManageAllFilesPermission()) {
            host.openManageAllFilesPermissionSettings();
        } else {
            host.requestLegacyStoragePermissions();
        }
    }

    private void openStoragePermissionSettings() {
        pendingExternalProjectOpen = false;
        if (!host.isViewAttached()) {
            return;
        }
        host.hideOverlays();
        if (storagePermissionManager.needsManageAllFilesPermission()) {
            host.openManageAllFilesPermissionSettings();
        } else if (!storagePermissionManager.hasExternalStorageAccess()) {
            host.requestLegacyStoragePermissions();
        }
    }

    private void createProjectFromInput(String executionMode, String name) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() == 0) {
            host.showNotice("工作区名称不能为空。");
            return;
        }
        if (ToolSettingsRepository.EXECUTION_SSH.equals(ToolSettingsRepository.normalizeExecutionMode(executionMode))) {
            backgroundRunner.execute("linecode-ssh-project-create", () -> {
                try {
                    String path = sshFileTreeStore.createManagedProject(cleanName);
                    ProjectRecord project = projectStore.saveSshProject(path, cleanName);
                    uiDispatcher.post(() -> {
                        host.applyProject(project);
                        host.requestSshFileTreeLoad(true);
                        host.render();
                    });
                } catch (Exception e) {
                    uiDispatcher.post(() -> host.showNotice("创建 SSH 工作区失败: " + e.getMessage()));
                }
            });
            return;
        }
        try {
            ProjectRecord project = projectStore.createManagedProject(cleanName);
            host.applyProject(project);
            host.render();
        } catch (RuntimeException e) {
            host.showNotice("创建工作区失败: " + e.getMessage());
        }
    }

    private void switchToDefaultProjectWithDialog(String executionMode, String title, String message) {
        ProjectRecord fallback = projectStore.selectDefaultProject(executionMode);
        host.applyProject(fallback);
        host.requestSshFileTreeLoad(true);
        host.render();
        if (host.isViewAttached()) {
            host.showConfirmationDialog(title, message, context.getString(R.string.common_confirm), false, "project:missing_notice");
        }
    }

    private boolean isProtectedProject(ProjectRecord project) {
        return project == null
                || WorkspacePaths.DEFAULT_PROJECT_ID.equals(project.getId())
                || "ssh:default".equals(project.getId());
    }

    private boolean isSshExecutionMode() {
        return ToolSettingsRepository.EXECUTION_SSH.equals(currentExecutionMode());
    }

    private String currentExecutionMode() {
        return toolSettingsStore.getExecutionMode();
    }
}
