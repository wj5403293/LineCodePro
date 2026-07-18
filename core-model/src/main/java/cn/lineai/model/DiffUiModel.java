package cn.lineai.model;

/**
 * UI layer representation of a diff record, decoupled from the data layer DiffRecord.
 */
public final class DiffUiModel {
    private final String id;
    private final String filePath;
    private final String oldContent;
    private final String newContent;
    private final boolean reverted;

    public DiffUiModel(String id, String filePath, String oldContent, String newContent, boolean reverted) {
        this.id = id == null ? "" : id;
        this.filePath = filePath == null ? "" : filePath;
        this.oldContent = oldContent == null ? "" : oldContent;
        this.newContent = newContent == null ? "" : newContent;
        this.reverted = reverted;
    }

    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getOldContent() {
        return oldContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public boolean isReverted() {
        return reverted;
    }
}
