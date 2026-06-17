package cn.lineai.mvp;

public interface ArchiveController {
    void onLineCodeExportRequested();

    void onLineCodeExportTargetPicked(String uri, String displayName);

    void onLineCodeExportCancelled();

    void onLineCodeImportRequested();

    void onLineCodeImportPicked(String uri, String displayName);

    void onLineCodeImportCancelled();
}
