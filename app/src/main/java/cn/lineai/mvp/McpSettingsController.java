package cn.lineai.mvp;

import cn.lineai.model.McpSettingsState;
import cn.lineai.model.WebSearchConfig;

public interface McpSettingsController {
    McpSettingsState getMcpSettingsState();

    void onMcpExecutionModeChanged(String mode);

    void onMcpToolGroupChanged(String id, boolean enabled);

    void onMcpWebSearchConfigChanged(WebSearchConfig config);

    String getImageUnderstandingModelId();

    void onImageUnderstandingModelSelected(String id);

    String getImageGenerationModelId();

    void onImageGenerationModelSelected(String id);
}
