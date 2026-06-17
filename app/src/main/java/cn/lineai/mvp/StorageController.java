package cn.lineai.mvp;

public interface StorageController {
    void onClearDiffCache();

    void onClearChatHistory();

    void onKeepAliveSettingsChanged();
}
