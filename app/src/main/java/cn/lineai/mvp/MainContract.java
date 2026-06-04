package cn.lineai.mvp;

import cn.lineai.model.ChatUiState;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.SheetOption;
import cn.lineai.model.SkillRecord;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.BaseTool;
import java.util.Map;
import java.util.List;

public interface MainContract {
    interface View {
        void render(ChatUiState state);

        void showDrawer();

        void showSheet(String title, List<SheetOption> options);

        void hideOverlays();

        void showScreen(String screenId);

        void showChatScreen();

        void openExternalProjectPicker();

        void openManageAllFilesPermissionSettings();

        void requestLegacyStoragePermissions();

        void openExternalUrl(String url);

        void recreateForTheme(String screenId);
    }

    interface Presenter {
        void attachView(View view);

        void detachView();

        void onMenuClick();

        void onProjectClick();

        void onPermissionClick();

        void onNewConversation();

        void onConversationSelected(String id);

        void onConversationDeleted(String id);

        void onCurrentProjectRemoveRequested();

        void onFileNodeSelected(String path);

        void onFileTreeRefresh();

        void onMoreClick();

        void onSendMessage(String text);

        void onStopGeneration();

        void onToolReview(String toolCallId, String state, String diffId);

        void onSheetOptionSelected(String id);

        void onScreenBack();

        void onScreenBackFrom(String screenId);

        void onSettingsItemSelected(String id);

        void onOpenUrl(String url);

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

        ExtensionOverviewState getExtensionOverview();

        void onAgentExtensionSaved(ExtensionAgentConfig config);

        ExtensionAgentConfig onAgentDraftGenerated(String description) throws Exception;

        List<BaseTool> getExtensionAvailableTools();

        void onMcpExtensionSaved(ExtensionMcpConfig config);

        List<McpToolSummary> onMcpToolsQuery(String url, List<McpRequestHeader> headers) throws Exception;

        SkillRecord onSkillCreated(String location, String name, String description, String content);

        SkillRecord onSkillInstalled(String location, String sourcePath, String name) throws Exception;

        SkillRecord onSkillInstalledFromUri(String location, String uri, String displayName) throws Exception;

        void onExtensionEnabledChanged(String kind, String id, boolean enabled);

        void onExtensionDeleted(String kind, String id);

        List<ModelConfig> getModels();

        ModelConfig getModel(String id);

        List<ConversationRecord> getConversationMetas();

        String getCurrentConversationId();

        FileTreeNode getFileTree();

        boolean canRemoveCurrentProject();

        String getSelectedModelId();

        void onModelSelected(String id);

        void onModelSaved(ModelConfig model);

        void onModelsDeleted(List<String> ids);

        void onExternalProjectTreePicked(String treeUri);

        void onExternalProjectPickerCancelled();

        void onStoragePermissionResult();
    }
}
