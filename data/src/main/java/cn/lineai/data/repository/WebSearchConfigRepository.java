package cn.lineai.data.repository;

import cn.lineai.model.WebSearchConfig;

public class WebSearchConfigRepository {
    public static final String KEY_WEB_SEARCH_CONFIG = "@lineai_web_search_config";

    private final SettingsRepository settingsRepository;

    public WebSearchConfigRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /**
     * 仅供测试子类使用的默认构造函数。子类应重写 {@link #get()} / {@link #save(WebSearchConfig)}。
     */
    protected WebSearchConfigRepository() {
        this.settingsRepository = null;
    }

    public synchronized WebSearchConfig get() {
        return WebSearchConfig.fromJson(settingsRepository.getString(KEY_WEB_SEARCH_CONFIG, ""));
    }

    public synchronized void save(WebSearchConfig config) {
        WebSearchConfig value = config == null ? WebSearchConfig.defaultConfig() : config;
        try {
            settingsRepository.setString(KEY_WEB_SEARCH_CONFIG, value.toJson().toString());
        } catch (Exception ignored) {
            settingsRepository.setString(KEY_WEB_SEARCH_CONFIG, "");
        }
    }
}
