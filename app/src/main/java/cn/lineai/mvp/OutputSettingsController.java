package cn.lineai.mvp;

import cn.lineai.model.OutputSettings;

public interface OutputSettingsController {
    OutputSettings getOutputSettings();

    void onCodeWrapChanged(boolean enabled);

    void onBrowserModeChanged(String mode);

    void onBrowserJavaScriptChanged(boolean enabled);
}
