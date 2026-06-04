package cn.lineai.mvp;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.model.WebSearchConfig;
import java.util.Map;

public interface SettingsController {
    void onMoreClick();

    void onSettingsItemSelected(String id);

    AiBehaviorSettings getAiBehaviorSettings();

    void onAiToneModeChanged(String toneMode);

    void onAiReasoningEffortChanged(String effort);

    void onAiThinkingScrollChanged(boolean enabled);

    void onAiThinkingAutoExpandChanged(boolean enabled);

    void onAiPreserveReasoningChanged(boolean enabled);

    void onAiLearningModeChanged(boolean enabled);

    MemoryOverviewState getMemoryOverview();

    void onMemorySaved(String id, String scope, String content);

    void onMemoryDeleted(String id);

    OutputSettings getOutputSettings();

    void onCodeWrapChanged(boolean enabled);

    void onBrowserModeChanged(String mode);

    ThemeSettingsState getThemeSettings();

    void onThemeModeChanged(String mode);

    void onCustomThemeColorsSaved(Map<String, String> colors);

    McpSettingsState getMcpSettingsState();

    void onMcpExecutionModeChanged(String mode);

    void onMcpToolGroupChanged(String id, boolean enabled);

    void onMcpWebSearchConfigChanged(WebSearchConfig config);
}
