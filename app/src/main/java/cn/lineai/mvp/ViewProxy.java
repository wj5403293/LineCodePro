package cn.lineai.mvp;

import cn.lineai.model.ChatUiState;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.SheetOption;
import java.util.List;

public final class ViewProxy implements MainContract.View {
    private MainContract.View view;

    public void attach(MainContract.View v) {
        this.view = v;
    }

    public void detach() {
        this.view = null;
    }

    public boolean isAttached() {
        return view != null;
    }

    public MainContract.View raw() {
        return view;
    }

    // ChatRenderView

    @Override
    public void render(ChatUiState state) {
        if (view != null) {
            view.render(state);
        }
    }

    @Override
    public void setComposerDraft(String text) {
        if (view != null) {
            view.setComposerDraft(text);
        }
    }

    @Override
    public void setComposerDraft(String text, List<InputAttachment> attachments) {
        if (view != null) {
            view.setComposerDraft(text, attachments);
        }
    }

    // OverlayView

    @Override
    public void showDrawer() {
        if (view != null) {
            view.showDrawer();
        }
    }

    @Override
    public void showSheet(String title, List<SheetOption> options) {
        if (view != null) {
            view.showSheet(title, options);
        }
    }

    @Override
    public void showFileActionDialog(String title, String subtitle, List<SheetOption> options) {
        if (view != null) {
            view.showFileActionDialog(title, subtitle, options);
        }
    }

    @Override
    public void showInputDialog(String title, String message, String initialValue, String actionId) {
        if (view != null) {
            view.showInputDialog(title, message, initialValue, actionId);
        }
    }

    @Override
    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
        if (view != null) {
            view.showConfirmationDialog(title, message, confirmLabel, danger, actionId);
        }
    }

    @Override
    public void hideOverlays() {
        if (view != null) {
            view.hideOverlays();
        }
    }

    @Override
    public void hideDirectoryPicker() {
        if (view != null) {
            view.hideDirectoryPicker();
        }
    }

    @Override
    public void hideAttachmentPicker() {
        if (view != null) {
            view.hideAttachmentPicker();
        }
    }

    @Override
    public void exportCurrentChat() {
        if (view != null) {
            view.exportCurrentChat();
        }
    }

    @Override
    public void enterMessageSelectMode() {
        if (view != null) {
            view.enterMessageSelectMode();
        }
    }

    // PickerView

    @Override
    public void showDirectoryPicker(String title, String subtitle, FileTreeNode tree, String selectedPath, boolean loading, String message) {
        if (view != null) {
            view.showDirectoryPicker(title, subtitle, tree, selectedPath, loading, message);
        }
    }

    @Override
    public void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source) {
        if (view != null) {
            view.showAttachmentPicker(title, tree, loading, message, source);
        }
    }

    @Override
    public void openExternalProjectPicker() {
        if (view != null) {
            view.openExternalProjectPicker();
        }
    }

    @Override
    public void openLineCodeImportPicker() {
        if (view != null) {
            view.openLineCodeImportPicker();
        }
    }

    @Override
    public void openLineCodeExportPicker(String fileName) {
        if (view != null) {
            view.openLineCodeExportPicker(fileName);
        }
    }

    // ScreenView

    @Override
    public void showScreen(String screenId) {
        if (view != null) {
            view.showScreen(screenId);
        }
    }

    @Override
    public void showScreen(String screenId, boolean forward) {
        if (view != null) {
            view.showScreen(screenId, forward);
        }
    }

    @Override
    public void showScreen(String screenId, boolean forward, boolean animate) {
        if (view != null) {
            view.showScreen(screenId, forward, animate);
        }
    }

    @Override
    public void showChatScreen() {
        if (view != null) {
            view.showChatScreen();
        }
    }

    @Override
    public void openExternalUrl(String url) {
        if (view != null) {
            view.openExternalUrl(url);
        }
    }

    @Override
    public void recreateForTheme(String screenId) {
        if (view != null) {
            view.recreateForTheme(screenId);
        }
    }

    @Override
    public void evictScreen(String screenId) {
        if (view != null) {
            view.evictScreen(screenId);
        }
    }

    @Override
    public void invalidateScreen(String screenId) {
        if (view != null) {
            view.invalidateScreen(screenId);
        }
    }

    // PermissionView

    @Override
    public void openManageAllFilesPermissionSettings() {
        if (view != null) {
            view.openManageAllFilesPermissionSettings();
        }
    }

    @Override
    public void requestLegacyStoragePermissions() {
        if (view != null) {
            view.requestLegacyStoragePermissions();
        }
    }
}
