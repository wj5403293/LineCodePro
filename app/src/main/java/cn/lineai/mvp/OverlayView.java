package cn.lineai.mvp;

import cn.lineai.model.SheetOption;
import java.util.List;

public interface OverlayView {
    void showDrawer();

    void showSheet(String title, List<SheetOption> options);

    void showFileActionDialog(String title, String subtitle, List<SheetOption> options);

    void showInputDialog(String title, String message, String initialValue, String actionId);

    void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId);

    void hideOverlays();

    void hideDirectoryPicker();

    void hideAttachmentPicker();

    void exportCurrentChat();

    void enterMessageSelectMode();
}
