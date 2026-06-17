package cn.lineai.mvp;

import cn.lineai.model.FileTreeNode;

public interface PickerView {
    void showDirectoryPicker(String title, String subtitle, FileTreeNode tree, String selectedPath, boolean loading, String message);

    void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source);

    void openExternalProjectPicker();

    void openLineCodeImportPicker();

    void openLineCodeExportPicker(String fileName);
}
