package cn.lineai.data.repository;

import java.util.Map;

/**
 * 设置仓储接口，定义 SettingsRepository 的公开契约。
 */
public interface SettingsStore {
    String getString(String key, String fallback);

    boolean getBoolean(String key, boolean fallback);

    long getLong(String key, long fallback);

    void setString(String key, String value);

    void setBoolean(String key, boolean value);

    void setLong(String key, long value);

    void remove(String key);

    void clearLineCodeSettings();

    Map<String, String> getLineCodeSettings();
}
