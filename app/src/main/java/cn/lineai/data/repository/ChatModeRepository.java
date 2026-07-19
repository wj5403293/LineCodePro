package cn.lineai.data.repository;

import cn.lineai.model.ChatMode;

public final class ChatModeRepository {
    private static final String KEY_CHAT_MODE = "@linecode_chat_mode";
    private static final String KEY_RESTORE_PERMISSION_MODE = "@linecode_chat_mode_restore_permission";

    private final SettingsRepository settingsRepository;

    public ChatModeRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public synchronized String getMode() {
        return ChatMode.normalize(settingsRepository.getString(KEY_CHAT_MODE, ChatMode.DEFAULT));
    }

    public synchronized void initialize() {
        String storedMode = settingsRepository.getString(KEY_CHAT_MODE, "");
        if (storedMode.length() > 0) {
            applyMode(storedMode);
            return;
        }
        String initialMode = ChatMode.DEFAULT;
        settingsRepository.setString(KEY_CHAT_MODE, initialMode);
    }

    public synchronized void applyMode(String mode) {
        String normalized = ChatMode.normalize(mode);
        if (!normalized.equals(getMode())) {
            settingsRepository.setString(KEY_CHAT_MODE, normalized);
        }
    }

    /**
     * 根据 chat mode 变更同步权限模式。此方法由 Controller 层在调用 applyMode 后调用，
     * 避免 Repository 层产生跨 Repository 副作用。
     */
    public void applyPermissionForMode(String mode, ToolSettingsStore toolSettingsRepository) {
        if (toolSettingsRepository == null) {
            return;
        }
        String normalized = ChatMode.normalize(mode);
        String currentPermission = toolSettingsRepository.getPermissionMode();
        if (ChatMode.CHAT.equals(normalized)) {
            rememberRestorablePermission(currentPermission);
            if (!ToolSettingsRepository.PERMISSION_READONLY.equals(currentPermission)) {
                toolSettingsRepository.setPermissionMode(ToolSettingsRepository.PERMISSION_READONLY);
            }
            return;
        }
        if (ChatMode.CONTROL.equals(normalized)) {
            if (ToolSettingsRepository.PERMISSION_READONLY.equals(currentPermission)) {
                toolSettingsRepository.setPermissionMode(ToolSettingsRepository.PERMISSION_AUTO);
            } else {
                rememberRestorablePermission(currentPermission);
            }
            return;
        }
        if (ToolSettingsRepository.PERMISSION_READONLY.equals(currentPermission)) {
            toolSettingsRepository.setPermissionMode(getRestorablePermissionMode());
        } else {
            rememberRestorablePermission(currentPermission);
        }
    }

    public synchronized void setModeOnly(String mode) {
        String normalized = ChatMode.normalize(mode);
        if (!normalized.equals(getMode())) {
            settingsRepository.setString(KEY_CHAT_MODE, normalized);
        }
    }

    public synchronized void rememberRestorablePermission(String mode) {
        String normalized = ToolSettingsRepository.normalizePermissionMode(mode);
        if (ToolSettingsRepository.PERMISSION_READONLY.equals(normalized)) {
            return;
        }
        String existing = ToolSettingsRepository.normalizePermissionMode(
                settingsRepository.getString(KEY_RESTORE_PERMISSION_MODE, ToolSettingsRepository.PERMISSION_AUTO));
        if (!normalized.equals(existing)) {
            settingsRepository.setString(KEY_RESTORE_PERMISSION_MODE, normalized);
        }
    }

    private String getRestorablePermissionMode() {
        String mode = ToolSettingsRepository.normalizePermissionMode(
                settingsRepository.getString(KEY_RESTORE_PERMISSION_MODE, ToolSettingsRepository.PERMISSION_AUTO));
        return ToolSettingsRepository.PERMISSION_READONLY.equals(mode)
                ? ToolSettingsRepository.PERMISSION_AUTO
                : mode;
    }
}
