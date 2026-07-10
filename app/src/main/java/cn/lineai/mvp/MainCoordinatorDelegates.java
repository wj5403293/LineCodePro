package cn.lineai.mvp;

import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.ScannedProvider;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ConversationUiModel;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.InputSettings;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.OutputSettings;
import cn.lineai.model.PromptTemplateItem;
import cn.lineai.model.SkillRecord;
import cn.lineai.model.ThemeSettingsState;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.security.UrlPolicy;
import cn.lineai.tool.BaseTool;
import java.util.List;
import java.util.Map;

abstract class MainCoordinatorDelegates implements MainUiController {
    protected abstract MainContract.View delegateView();

    protected abstract SettingsManagementController settingsDelegate();

    protected abstract LineCodeArchiveController archiveDelegate();

    protected abstract ExtensionManagementController extensionManagementDelegate();

    protected abstract ExtensionDraftController extensionDraftDelegate();

    protected abstract IpcProviderController ipcProviderDelegate();

    protected abstract ModelManagementController modelManagementDelegate();

    protected abstract ChatInteractionController chatInteractionDelegate();

    protected abstract FileTreeInteractionController fileTreeInteractionDelegate();

    protected abstract FileOperationController fileOperationDelegate();

    protected abstract DirectoryPickerController directoryPickerDelegate();

    protected abstract AttachmentPickerCoordinator attachmentPickerDelegate();

    protected abstract PermissionModeController permissionModeDelegate();

    protected abstract ProjectWorkspaceController projectWorkspaceDelegate();

    protected abstract OverlayActionController overlayActionDelegate();

    protected abstract GenerationFlowController generationFlowDelegate();

    protected abstract ToolReviewController toolReviewDelegate();

    protected abstract ConversationStore conversationStoreDelegate();

    protected abstract ChatSessionStore chatSessionDelegate();

    protected abstract ModelInteractionController modelInteractionDelegate();

    protected abstract StorageMaintenanceController storageMaintenanceDelegate();

    protected abstract void delegateShowScreen(String screenId);

    protected abstract void delegateRefreshVisibleScreen(String screenId);

    protected abstract void delegateRender();

    protected abstract void delegateReloadExtensions();

    @Override
    public void onSettingsItemSelected(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        delegateShowScreen(id);
    }

    @Override
    public void onOpenUrl(String url) {
        String safeUrl = UrlPolicy.normalizeHttpOrHttpsUrl(url);
        if (safeUrl.length() == 0) {
            return;
        }
        MainContract.View view = delegateView();
        if (OutputSettings.BROWSER_EXTERNAL.equals(settingsDelegate().getOutputSettings().getBrowserMode())) {
            if (view != null) {
                view.openExternalUrl(safeUrl);
            }
            return;
        }
        delegateShowScreen("browser:" + safeUrl);
    }

    @Override
    public void showModelManagement() {
        delegateShowScreen("models");
    }

    @Override
    public void onProjectClick() {
        projectWorkspaceDelegate().showProjectSheet();
    }

    @Override
    public void onPermissionClick() {
        permissionModeDelegate().showPermissionSheet();
    }

    @Override
    public void onNewConversation() {
        chatInteractionDelegate().newConversation();
    }

    @Override
    public void onConversationSelected(String id) {
        chatInteractionDelegate().selectConversation(id);
    }

    @Override
    public void onConversationDeleted(String id) {
        chatInteractionDelegate().deleteConversation(id);
    }

    @Override
    public void onCurrentProjectRemoveRequested() {
        projectWorkspaceDelegate().removeCurrentProject();
    }

    @Override
    public void onFileNodeSelected(String path, boolean directory) {
        fileTreeInteractionDelegate().handleNodeSelected(path, directory);
    }

    @Override
    public void onFileNodeLongPressed(String path, String name, boolean directory, boolean root) {
        fileOperationDelegate().showFileNodeActions(path, name, directory, root);
    }

    @Override
    public void onFileTreeActivated() {
        fileTreeInteractionDelegate().activate();
    }

    @Override
    public void onFileTreeRefresh() {
        fileTreeInteractionDelegate().refresh();
    }

    @Override
    public void onDirectoryPickerNodeSelected(String path) {
        directoryPickerDelegate().onNodeSelected(path);
    }

    @Override
    public void onDirectoryPickerConfirmed() {
        directoryPickerDelegate().onConfirmed();
    }

    @Override
    public void onDirectoryPickerCancelled() {
        directoryPickerDelegate().onCancelled();
    }

    @Override
    public void onDialogInputSubmitted(String actionId, String value) {
        overlayActionDelegate().handleDialogInput(actionId, value);
    }

    @Override
    public void onDialogConfirmed(String actionId) {
        overlayActionDelegate().handleDialogConfirmed(actionId);
    }

    @Override
    public void onMoreClick() {
        overlayActionDelegate().showMoreActions();
    }

    @Override
    public void onSendMessage(String text) {
        chatInteractionDelegate().sendMessage(text);
    }

    @Override
    public void onSendMessage(String text, List<InputAttachment> attachments) {
        chatInteractionDelegate().sendMessage(text, attachments);
    }

    @Override
    public void onRecallMessage(String messageId) {
        chatInteractionDelegate().recallMessage(messageId);
    }

    @Override
    public void onAttachmentPickerRequested() {
        attachmentPickerDelegate().onAttachmentPickerRequested();
    }

    @Override
    public void onAttachmentPickerNodeSelected(String path, boolean directory) {
        attachmentPickerDelegate().onAttachmentPickerNodeSelected(path, directory);
    }

    @Override
    public void onAttachmentPickerCancelled() {
        attachmentPickerDelegate().onAttachmentPickerCancelled();
    }

    @Override
    public void onChatModeChanged(String mode) {
        chatInteractionDelegate().changeChatMode(mode);
    }

    @Override
    public void onStopGeneration() {
        chatInteractionDelegate().stopGeneration();
    }

    @Override
    public void onToolReview(String toolCallId, String state, String diffId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        if (generationFlowDelegate().isPendingToolReview(toolCallId)) {
            generationFlowDelegate().handleToolReview(state);
            return;
        }
        if (generationFlowDelegate().isPendingAgentToolReview(toolCallId)) {
            generationFlowDelegate().acceptAgentToolReview(toolCallId, state);
            return;
        }
        if (generationFlowDelegate().handleAgentToolReview(toolCallId, state)) {
            return;
        }
        toolReviewDelegate().review(toolCallId, state, diffId);
    }

    @Override
    public void onSheetOptionSelected(String id) {
        overlayActionDelegate().handleSheetOption(id);
    }

    @Override
    public List<ConversationUiModel> getConversationMetas() {
        List<ConversationRecord> records = conversationStoreDelegate().getConversationMetas();
        java.util.ArrayList<ConversationUiModel> models = new java.util.ArrayList<>();
        if (records != null) {
            for (ConversationRecord r : records) {
                models.add(new ConversationUiModel(r.getId(), r.getTitle(), r.getUpdatedAt()));
            }
        }
        return models;
    }

    @Override
    public String getCurrentConversationId() {
        return chatSessionDelegate().getCurrentConversationId();
    }

    @Override
    public FileTreeNode getFileTree() {
        return fileTreeInteractionDelegate().getFileTree();
    }

    @Override
    public boolean canRemoveCurrentProject() {
        return projectWorkspaceDelegate().canRemoveCurrentProject();
    }

    @Override
    public void onModelQuickSwitch(String modelId) {
        modelInteractionDelegate().quickSwitch(modelId);
    }

    @Override
    public void onModelTest(ModelConfig model) {
        modelInteractionDelegate().testModel(model);
    }

    @Override
    public void onExternalProjectTreePicked(String treeUri) {
        projectWorkspaceDelegate().onExternalProjectTreePicked(treeUri);
    }

    @Override
    public void onExternalProjectPickerCancelled() {
        projectWorkspaceDelegate().onExternalProjectPickerCancelled();
    }

    @Override
    public void onStoragePermissionResult() {
        projectWorkspaceDelegate().onStoragePermissionResult();
    }

    @Override
    public AiBehaviorSettings getAiBehaviorSettings() {
        return settingsDelegate().getAiBehaviorSettings();
    }

    @Override
    public void onAiToneModeChanged(String toneMode) {
        settingsDelegate().setAiToneMode(toneMode);
    }

    @Override
    public void onAiReasoningEffortChanged(String effort) {
        settingsDelegate().setAiReasoningEffort(effort);
    }

    @Override
    public void onAiThinkingScrollChanged(boolean enabled) {
        settingsDelegate().setAiThinkingScrollEnabled(enabled);
    }

    @Override
    public void onAiThinkingAutoExpandChanged(boolean enabled) {
        settingsDelegate().setAiThinkingAutoExpandEnabled(enabled);
    }

    @Override
    public void onAiPreserveReasoningChanged(boolean enabled) {
        settingsDelegate().setAiPreserveReasoningEnabled(enabled);
    }

    @Override
    public void onAiLearningModeChanged(boolean enabled) {
        settingsDelegate().setAiLearningModeEnabled(enabled);
    }

    @Override
    public InputSettings getInputSettings() {
        return settingsDelegate().getInputSettings();
    }

    @Override
    public void onEnterKeyBehaviorChanged(String behavior) {
        settingsDelegate().setEnterKeyBehavior(behavior);
    }

    @Override
    public List<PromptTemplateItem> getPromptTemplates() {
        return settingsDelegate().getPromptTemplates();
    }

    @Override
    public void onPromptTemplateSaved(String id, String value) {
        settingsDelegate().savePromptTemplate(id, value);
    }

    @Override
    public void onPromptTemplateReset(String id) {
        settingsDelegate().resetPromptTemplate(id);
    }

    @Override
    public MemoryOverviewState getMemoryOverview() {
        return settingsDelegate().getMemoryOverview();
    }

    @Override
    public void onMemorySaved(String id, String scope, String content) {
        settingsDelegate().saveMemory(id, scope, content);
    }

    @Override
    public void onMemoryDeleted(String id) {
        settingsDelegate().deleteMemory(id);
    }

    @Override
    public OutputSettings getOutputSettings() {
        return settingsDelegate().getOutputSettings();
    }

    @Override
    public void onCodeWrapChanged(boolean enabled) {
        settingsDelegate().setCodeWrapEnabled(enabled);
    }

    @Override
    public void onBrowserModeChanged(String mode) {
        settingsDelegate().setBrowserMode(mode);
    }

    @Override
    public void onBrowserJavaScriptChanged(boolean enabled) {
        settingsDelegate().setBrowserJavaScriptEnabled(enabled);
    }

    @Override
    public void onAllowAnyHttpChanged(boolean enabled) {
        settingsDelegate().setAllowAnyHttp(enabled);
    }

    @Override
    public ThemeSettingsState getThemeSettings() {
        return settingsDelegate().getThemeSettings();
    }

    @Override
    public void onThemeModeChanged(String mode) {
        settingsDelegate().setThemeMode(mode);
    }

    @Override
    public void onCustomThemeColorsSaved(Map<String, String> colors) {
        settingsDelegate().saveCustomThemeColors(colors);
    }

    @Override
    public McpSettingsState getMcpSettingsState() {
        return settingsDelegate().getMcpSettingsState();
    }

    @Override
    public void onMcpExecutionModeChanged(String mode) {
        settingsDelegate().setMcpExecutionMode(mode);
    }

    @Override
    public void onMcpToolGroupChanged(String id, boolean enabled) {
        settingsDelegate().setMcpToolGroupEnabled(id, enabled);
    }

    @Override
    public void onMcpWebSearchConfigChanged(WebSearchConfig config) {
        settingsDelegate().setMcpWebSearchConfig(config);
    }

    @Override
    public String getImageUnderstandingModelId() {
        return settingsDelegate().getImageUnderstandingModelId();
    }

    @Override
    public void onImageUnderstandingModelSelected(String id) {
        settingsDelegate().setImageUnderstandingModelId(id);
    }

    @Override
    public String getImageGenerationModelId() {
        return settingsDelegate().getImageGenerationModelId();
    }

    @Override
    public void onImageGenerationModelSelected(String id) {
        settingsDelegate().setImageGenerationModelId(id);
    }

    @Override
    public void onLineCodeExportRequested() {
        archiveDelegate().requestExport();
    }

    @Override
    public void onLineCodeExportTargetPicked(String uri, String displayName) {
        archiveDelegate().exportTargetPicked(uri);
    }

    @Override
    public void onLineCodeExportCancelled() {
        archiveDelegate().exportCancelled();
    }

    @Override
    public void onLineCodeImportRequested() {
        archiveDelegate().requestImport();
    }

    @Override
    public void onLineCodeImportPicked(String uri, String displayName) {
        archiveDelegate().importPicked(uri, displayName);
    }

    @Override
    public void onLineCodeImportCancelled() {
        archiveDelegate().importCancelled();
    }

    @Override
    public ExtensionOverviewState getExtensionOverview() {
        return extensionManagementDelegate().getOverview();
    }

    @Override
    public void onAgentExtensionSaved(ExtensionAgentConfig config) {
        extensionManagementDelegate().saveAgentExtension(config);
    }

    @Override
    public ExtensionAgentConfig onAgentDraftGenerated(String description) throws Exception {
        return extensionDraftDelegate().generateAgentDraft(description);
    }

    @Override
    public List<BaseTool> getExtensionAvailableTools() {
        return extensionDraftDelegate().getAvailableTools();
    }

    @Override
    public void onMcpExtensionSaved(ExtensionMcpConfig config) {
        extensionManagementDelegate().saveMcpExtension(config);
    }

    @Override
    public List<McpToolSummary> onMcpToolsQuery(String url, List<McpRequestHeader> headers) throws Exception {
        return extensionManagementDelegate().queryMcpTools(url, headers);
    }

    @Override
    public SkillRecord onSkillCreated(String location, String name, String description, String content) {
        return extensionManagementDelegate().createSkill(location, name, description, content);
    }

    @Override
    public SkillRecord onSkillInstalled(String location, String sourcePath, String name) throws Exception {
        return extensionManagementDelegate().installSkill(location, sourcePath, name);
    }

    @Override
    public SkillRecord onSkillInstalledFromUri(String location, String uri, String displayName) throws Exception {
        return extensionManagementDelegate().installSkillFromUri(location, uri, displayName);
    }

    @Override
    public void onExtensionEnabledChanged(String kind, String id, boolean enabled) {
        extensionManagementDelegate().setExtensionEnabled(kind, id, enabled);
    }

    @Override
    public void onExtensionDeleted(String kind, String id) {
        extensionManagementDelegate().deleteExtension(kind, id);
    }

    @Override
    public List<ScannedProvider> onTerminalProviderScan() {
        return ipcProviderDelegate().onTerminalProviderScan();
    }

    @Override
    public List<ScannedProvider> getTerminalProviderScanResults() {
        return ipcProviderDelegate().getTerminalProviderScanResults();
    }

    @Override
    public boolean hasTerminalProviderScanned() {
        return ipcProviderDelegate().hasTerminalProviderScanned();
    }

    @Override
    public void onTerminalProviderSaved(IpcProviderConfig config) {
        ipcProviderDelegate().onTerminalProviderSaved(config);
    }

    @Override
    public void onTerminalProviderEnabledChanged(String id, boolean enabled) {
        ipcProviderDelegate().onTerminalProviderEnabledChanged(id, enabled);
    }

    @Override
    public void onTerminalProviderDeleted(String id) {
        ipcProviderDelegate().onTerminalProviderDeleted(id);
    }

    @Override
    public List<ModelConfig> getModels() {
        return modelManagementDelegate().getModels();
    }

    @Override
    public ModelConfig getModel(String id) {
        return modelManagementDelegate().getModel(id);
    }

    @Override
    public String getSelectedModelId() {
        return modelManagementDelegate().getSelectedModelId();
    }

    @Override
    public void onModelSelected(String id) {
        modelManagementDelegate().selectModel(id);
    }

    @Override
    public void onModelSaved(ModelConfig model) {
        modelManagementDelegate().saveModel(model);
    }

    @Override
    public void onModelsDeleted(List<String> ids) {
        modelManagementDelegate().deleteModels(ids);
    }

    @Override
    public void onPhoneControlPermissionEnabledChanged(String permissionId, boolean enabled) {
        if (permissionId == null || permissionId.length() == 0) {
            return;
        }
        delegateReloadExtensions();
        delegateRefreshVisibleScreen("phoneControl");
        delegateRender();
    }

    @Override
    public void onResume(String currentScreenId) {
        if ("phoneControl".equals(currentScreenId)) {
            delegateRefreshVisibleScreen("phoneControl");
            delegateRender();
        }
    }

    @Override
    public void onEnterBackground() {
        delegateView().hideOverlays();
    }
}
