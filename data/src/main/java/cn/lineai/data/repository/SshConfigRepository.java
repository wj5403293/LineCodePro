package cn.lineai.data.repository;

import cn.lineai.model.SshConfig;

public final class SshConfigRepository {
    public static final String KEY_SSH_CONFIG = "@lineai_ssh_config";

    private final SettingsRepository settingsRepository;

    public SshConfigRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public synchronized SshConfig get() {
        return SshConfig.fromJson(settingsRepository.getString(KEY_SSH_CONFIG, ""));
    }

    public synchronized void save(SshConfig config) {
        settingsRepository.setString(KEY_SSH_CONFIG, (config == null ? SshConfig.defaultConfig() : config).toJson());
    }
}
