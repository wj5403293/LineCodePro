package cn.lineai.mvp;

import cn.lineai.model.KeepAliveSettings;
import cn.lineai.model.StorageStatsUiModel;
import cn.lineai.log.ErrorLogEntry;
import cn.lineai.model.SshConfig;
import cn.lineai.ssh.TermuxHelper;
import java.util.List;

public interface MainUiController extends ChatController,
        WorkspaceController,
        SettingsController,
        ExtensionController,
        ModelController,
        NavigationController {
    void attachView(MainContract.View view);

    void detachView();

    void destroy();

    // Phone control
    boolean isPhoneControlAccessibilityEnabled();

    boolean isPhoneControlDisclaimerAccepted();

    boolean isPhoneControlPermissionEnabled(String permissionId);

    void onPhoneControlSetPermissionEnabled(String permissionId, boolean enabled);

    void onPhoneControlAcceptDisclaimer();

    void onPhoneControlPermissionEnabledChanged(String permissionId, boolean enabled);

    // Error logs
    List<ErrorLogEntry> getErrorLogs();

    void clearErrorLogs();

    // Storage stats
    StorageStatsUiModel getStorageStats();

    // Keep alive
    KeepAliveSettings getKeepAliveSettings();

    void setKeepAliveWakeLockEnabled(boolean enabled);

    void setKeepAliveForegroundEnabled(boolean enabled);

    void setKeepAliveFakeAudioEnabled(boolean enabled);

    void updateKeepAliveService();

    void updateKeepAliveServiceStatus(String status);

    // SSH
    SshConfig getSshConfig();

    void saveSshConfig(SshConfig config);

    String testSshConnection(SshConfig config) throws Exception;

    // Termux
    void openTermux() throws Exception;

    TermuxHelper.TermuxSetupResult setupTermuxSsh(int timeoutMs) throws Exception;

    void onResume(String currentScreenId);

    void onEnterBackground();
}
