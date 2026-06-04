package cn.lineai.mvp;

import cn.lineai.context.ContextManager;
import cn.lineai.context.ContextSnapshot;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextInfo;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelRepository;
import cn.lineai.model.OutputSettings;
import cn.lineai.workspace.WorkspacePaths;
import java.util.List;

public final class ChatUiStateAssembler {
    private final ModelRepository modelRepository;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final OutputSettingsRepository outputSettingsRepository;
    private final ContextManager contextManager;

    public ChatUiStateAssembler(
            ModelRepository modelRepository,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            OutputSettingsRepository outputSettingsRepository,
            ContextManager contextManager
    ) {
        this.modelRepository = modelRepository;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.outputSettingsRepository = outputSettingsRepository;
        this.contextManager = contextManager;
    }

    public ChatUiState assemble(
            String projectLabel,
            String projectSource,
            String projectPath,
            String activeChatMode,
            boolean streaming,
            List<ChatMessage> messages
    ) {
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        boolean hasConfiguredModel = selectedModel != null;
        ModelContextInfo contextInfo = selectedModel == null
                ? ModelContextParser.parse("")
                : ModelContextParser.parse(selectedModel.getModelId());
        AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
        OutputSettings outputSettings = outputSettingsRepository.get();
        ContextSnapshot contextSnapshot = contextManager.snapshot(messages, contextInfo.getContextTokens(),
                aiSettings.isPreserveReasoningEnabled());
        String modelLabel = selectedModel == null
                ? "未选择模型"
                : contextInfo.getApiModelId();
        String uiProjectPath = WorkspacePaths.SOURCE_SSH.equals(projectSource) && safe(projectPath).length() == 0
                ? "SSH 登录目录"
                : safe(projectPath);
        return new ChatUiState(
                projectLabel,
                uiProjectPath,
                modelLabel,
                contextSnapshot.getPercent() + "% / " + contextInfo.getContextLabel(),
                contextSnapshot.getPercent(),
                streaming,
                hasConfiguredModel,
                aiSettings.isThinkingScrollEnabled(),
                aiSettings.isThinkingAutoExpandEnabled(),
                outputSettings.isCodeWrapEnabled(),
                outputSettings.getBrowserMode(),
                activeChatMode,
                messages
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
