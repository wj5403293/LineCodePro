package cn.lineai.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.model.ModelProviderPresets;
import cn.lineai.model.SheetOption;
import cn.lineai.mvp.MainContract;
import cn.lineai.ui.component.BottomSheetView;
import cn.lineai.ui.component.ChatMessageListView;
import cn.lineai.ui.component.ComposerView;
import cn.lineai.ui.component.AboutScreenView;
import cn.lineai.ui.component.AgentExtensionEditScreenView;
import cn.lineai.ui.component.DataSettingsScreenView;
import cn.lineai.ui.component.DrawerView;
import cn.lineai.ui.component.ExperimentalSettingsScreenView;
import cn.lineai.ui.component.ExtensionDetailScreenView;
import cn.lineai.ui.component.ExtensionsScreenView;
import cn.lineai.ui.component.HeaderView;
import cn.lineai.ui.component.InAppBrowserScreenView;
import cn.lineai.ui.component.KeepAliveSettingsScreenView;
import cn.lineai.ui.component.LLMSettingsScreenView;
import cn.lineai.ui.component.LicensesScreenView;
import cn.lineai.ui.component.MCPSettingsScreenView;
import cn.lineai.ui.component.MemorySettingsScreenView;
import cn.lineai.ui.component.McpExtensionEditScreenView;
import cn.lineai.ui.component.ModelAddOptionsScreenView;
import cn.lineai.ui.component.ModelAddScreenView;
import cn.lineai.ui.component.ModelListScreenView;
import cn.lineai.ui.component.OutputSettingsScreenView;
import cn.lineai.ui.component.PluginPageScreenView;
import cn.lineai.ui.component.SettingsScreenView;
import cn.lineai.ui.component.ShellCommandScreenView;
import cn.lineai.ui.component.SimpleSettingsScreenView;
import cn.lineai.ui.component.SshSettingsScreenView;
import cn.lineai.ui.component.StorageManagementScreenView;
import cn.lineai.ui.component.TermuxIntegrationScreenView;
import cn.lineai.ui.component.ThemeSettingsScreenView;
import cn.lineai.ui.component.TutorialScreenView;
import cn.lineai.ui.theme.LineTheme;
import cn.lineai.ui.util.KeyboardController;
import java.util.List;

public final class MainChatView extends FrameLayout implements MainContract.View {
    public interface WorkspaceHost {
        void openExternalProjectPicker();

        void openManageAllFilesPermissionSettings();

        void requestLegacyStoragePermissions();

        void recreateMainView(String screenId);

        void openDocumentPicker(String mimeType, String[] extensions, DocumentPickCallback callback);
    }

    public interface DocumentPickCallback {
        void onDocumentPicked(String uri, String displayName);

        void onDocumentPickCancelled();
    }

    private final MainContract.Presenter presenter;
    private final HeaderView headerView;
    private final LinearLayout contentView;
    private final ChatMessageListView messageListView;
    private final ComposerView composerView;
    private final DrawerView drawerView;
    private final BottomSheetView bottomSheetView;
    private final FrameLayout screenHost;
    private ChatUiState lastState;
    private String shellCommandText = "";
    private String currentScreenId = "";

    public MainChatView(Context context, MainContract.Presenter presenter) {
        super(context);
        this.presenter = presenter;
        setBackgroundColor(LineTheme.BG);

        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        addView(contentView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        headerView = new HeaderView(context);
        headerView.setListener(new HeaderView.Listener() {
            @Override
            public void onMenuClick() {
                MainChatView.this.presenter.onMenuClick();
            }

            @Override
            public void onProjectClick() {
                MainChatView.this.presenter.onProjectClick();
            }

            @Override
            public void onPermissionClick() {
                MainChatView.this.presenter.onPermissionClick();
            }

            @Override
            public void onNewConversationClick() {
                MainChatView.this.presenter.onNewConversation();
            }

            @Override
            public void onMoreClick() {
                MainChatView.this.presenter.onMoreClick();
            }
        });
        contentView.addView(headerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        messageListView = new ChatMessageListView(context);
        messageListView.setToolReviewListener(new cn.lineai.ui.component.toolcall.ToolReviewListener() {
            @Override
            public void onToolReview(String toolCallId, String state, String diffId) {
                MainChatView.this.presenter.onToolReview(toolCallId, state, diffId);
            }

            @Override
            public void onViewShellCommand(String command) {
                shellCommandText = command == null ? "" : command;
                MainChatView.this.presenter.onSettingsItemSelected("shellCommand");
            }
        });
        messageListView.setMarkdownLinkHandler(url -> MainChatView.this.presenter.onOpenUrl(url));
        contentView.addView(messageListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        composerView = new ComposerView(context);
        composerView.setListener(new ComposerView.Listener() {
            @Override
            public void onSend(String text) {
                MainChatView.this.presenter.onSendMessage(text);
            }

            @Override
            public void onStop() {
                MainChatView.this.presenter.onStopGeneration();
            }
        });
        contentView.addView(composerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        drawerView = new DrawerView(context);
        drawerView.setListener(new DrawerView.Listener() {
            @Override
            public void onCloseDrawer() {
            }

            @Override
            public void onNewConversation() {
                MainChatView.this.presenter.onNewConversation();
            }

            @Override
            public void onConversationSelected(String id) {
                MainChatView.this.presenter.onConversationSelected(id);
            }

            @Override
            public void onConversationDeleted(String id) {
                MainChatView.this.presenter.onConversationDeleted(id);
            }

            @Override
            public void onCurrentProjectRemoveRequested() {
                MainChatView.this.presenter.onCurrentProjectRemoveRequested();
            }

            @Override
            public void onFileNodeSelected(String path) {
                MainChatView.this.presenter.onFileNodeSelected(path);
            }

            @Override
            public void onFileTreeRefresh() {
                MainChatView.this.presenter.onFileTreeRefresh();
            }
        });
        addView(drawerView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        bottomSheetView = new BottomSheetView(context);
        bottomSheetView.setListener(new BottomSheetView.Listener() {
            @Override
            public void onSheetDismissed() {
            }

            @Override
            public void onSheetOptionSelected(String id) {
                MainChatView.this.presenter.onSheetOptionSelected(id);
            }
        });
        addView(bottomSheetView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        screenHost = new FrameLayout(context);
        screenHost.setBackgroundColor(LineTheme.BG);
        screenHost.setClickable(true);
        screenHost.setFocusable(true);
        screenHost.setFocusableInTouchMode(true);
        screenHost.setVisibility(GONE);
        addView(screenHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        installSystemBarInsetsHandling();
    }

    @Override
    public void render(ChatUiState state) {
        lastState = state;
        headerView.render(state);
        messageListView.render(state);
        composerView.render(state);
        if (drawerView.getVisibility() == VISIBLE) {
            renderDrawer(state);
        }
    }

    @Override
    public void showDrawer() {
        KeyboardController.clearFocusAndHide(this);
        bottomSheetView.close();
        renderDrawer(lastState);
        drawerView.open();
    }

    @Override
    public void showSheet(String title, List<SheetOption> options) {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.show(title, options);
    }

    @Override
    public void hideOverlays() {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
    }

    @Override
    public void showScreen(String screenId) {
        currentScreenId = screenId == null ? "" : screenId;
        KeyboardController.clearFocusAndHide(screenHost);
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        screenHost.removeAllViews();
        screenHost.addView(buildScreen(screenId), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        screenHost.setVisibility(VISIBLE);
        screenHost.requestFocus();
        screenHost.bringToFront();
    }

    @Override
    public void showChatScreen() {
        currentScreenId = "";
        KeyboardController.clearFocusAndHide(screenHost);
        KeyboardController.clearFocusAndHide(this);
        screenHost.removeAllViews();
        screenHost.setVisibility(GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        KeyboardController.clearFocusAndHide(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void openExternalProjectPicker() {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).openExternalProjectPicker();
        }
    }

    @Override
    public void openManageAllFilesPermissionSettings() {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).openManageAllFilesPermissionSettings();
        }
    }

    @Override
    public void requestLegacyStoragePermissions() {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).requestLegacyStoragePermissions();
        }
    }

    @Override
    public void openExternalUrl(String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (safeUrl.length() == 0) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(getContext(), "无法打开链接: " + safeUrl, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void recreateForTheme(String screenId) {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).recreateMainView(screenId);
        }
    }

    private void renderDrawer(ChatUiState state) {
        String projectLabel = state == null ? "" : state.getProjectLabel();
        String projectPath = state == null ? "" : state.getProjectPath();
        if (state == null) {
            projectLabel = "";
            projectPath = "";
        }
        drawerView.render(
                presenter.getConversationMetas(),
                presenter.getCurrentConversationId(),
                projectLabel.length() == 0 ? "LineCode" : projectLabel,
                projectPath.length() == 0 ? "" : projectPath,
                presenter.canRemoveCurrentProject(),
                presenter.getFileTree()
        );
    }

    public boolean handleBackPressed() {
        if (screenHost.getVisibility() == VISIBLE) {
            presenter.onScreenBackFrom(currentScreenId);
            return true;
        }
        if (bottomSheetView.getVisibility() == VISIBLE) {
            bottomSheetView.close();
            return true;
        }
        if (drawerView.getVisibility() == VISIBLE) {
            drawerView.close();
            return true;
        }
        return false;
    }

    private void handleScreenBack() {
        presenter.onScreenBackFrom(currentScreenId);
    }

    private View buildScreen(String screenId) {
        Context context = getContext();
        if ("settings".equals(screenId)) {
            return new SettingsScreenView(context, new SettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onItem(String id) {
                    presenter.onSettingsItemSelected(id);
                }
            });
        }
        if ("models".equals(screenId)) {
            return new ModelListScreenView(context, presenter.getModels(), presenter.getSelectedModelId(), new ModelListScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onAddModel() {
                    presenter.onSettingsItemSelected("modelAddOptions");
                }

                @Override
                public void onSelectModel(String id) {
                    presenter.onModelSelected(id);
                }

                @Override
                public void onEditModel(String id) {
                    presenter.onSettingsItemSelected("modelEdit:" + id);
                }

                @Override
                public void onDeleteModels(List<String> ids) {
                    presenter.onModelsDeleted(ids);
                }
            });
        }
        if ("extensions".equals(screenId)) {
            return new ExtensionsScreenView(context, new ExtensionsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onOpen(String id) {
                    presenter.onSettingsItemSelected("extension:" + id);
                }
            });
        }
        if ("llm".equals(screenId)) {
            return new LLMSettingsScreenView(context, presenter.getAiBehaviorSettings(), new LLMSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onToneModeChanged(String toneMode) {
                    presenter.onAiToneModeChanged(toneMode);
                }

                @Override
                public void onReasoningEffortChanged(String effort) {
                    presenter.onAiReasoningEffortChanged(effort);
                }

                @Override
                public void onThinkingScrollChanged(boolean enabled) {
                    presenter.onAiThinkingScrollChanged(enabled);
                }

                @Override
                public void onThinkingAutoExpandChanged(boolean enabled) {
                    presenter.onAiThinkingAutoExpandChanged(enabled);
                }

                @Override
                public void onPreserveReasoningChanged(boolean enabled) {
                    presenter.onAiPreserveReasoningChanged(enabled);
                }

                @Override
                public void onLearningModeChanged(boolean enabled) {
                    presenter.onAiLearningModeChanged(enabled);
                }
            });
        }
        if ("mcp".equals(screenId)) {
            return new MCPSettingsScreenView(context, presenter.getMcpSettingsState(), new MCPSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onExecutionModeChanged(String mode) {
                    presenter.onMcpExecutionModeChanged(mode);
                }

                @Override
                public void onToolGroupChanged(String id, boolean enabled) {
                    presenter.onMcpToolGroupChanged(id, enabled);
                }

                @Override
                public void onWebSearchConfigChanged(cn.lineai.model.WebSearchConfig config) {
                    presenter.onMcpWebSearchConfigChanged(config);
                }

                @Override
                public void onOpenSshSettings() {
                    presenter.onSettingsItemSelected("sshSettings");
                }

                @Override
                public void onOpenTermuxIntegration() {
                    presenter.onSettingsItemSelected("termuxIntegration");
                }
            });
        }
        if ("output".equals(screenId)) {
            return new OutputSettingsScreenView(context, presenter.getOutputSettings(), new OutputSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onCodeWrapChanged(boolean enabled) {
                    presenter.onCodeWrapChanged(enabled);
                }

                @Override
                public void onBrowserModeChanged(String mode) {
                    presenter.onBrowserModeChanged(mode);
                }
            });
        }
        if ("theme".equals(screenId)) {
            return new ThemeSettingsScreenView(context, presenter.getThemeSettings(), new ThemeSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onThemeModeChanged(String mode) {
                    presenter.onThemeModeChanged(mode);
                }

                @Override
                public void onCustomThemeColorsSaved(java.util.Map<String, String> colors) {
                    presenter.onCustomThemeColorsSaved(colors);
                }
            });
        }
        if ("experimental".equals(screenId)) {
            return new ExperimentalSettingsScreenView(context, this::handleScreenBack);
        }
        if ("data".equals(screenId)) {
            return new DataSettingsScreenView(context, this::handleScreenBack);
        }
        if ("storage".equals(screenId)) {
            return new StorageManagementScreenView(context, this::handleScreenBack);
        }
        if ("memory".equals(screenId)) {
            return new MemorySettingsScreenView(context, new MemorySettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public cn.lineai.model.MemoryOverviewState getMemoryOverview() {
                    return presenter.getMemoryOverview();
                }

                @Override
                public void onMemorySaved(String id, String scope, String content) {
                    presenter.onMemorySaved(id, scope, content);
                }

                @Override
                public void onMemoryDeleted(String id) {
                    presenter.onMemoryDeleted(id);
                }
            });
        }
        if ("keepAlive".equals(screenId)) {
            return new KeepAliveSettingsScreenView(context, this::handleScreenBack);
        }
        if ("sshSettings".equals(screenId)) {
            return new SshSettingsScreenView(context, new SshSettingsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onOpenTermuxIntegration() {
                    presenter.onSettingsItemSelected("termuxIntegration");
                }
            });
        }
        if ("termuxIntegration".equals(screenId)) {
            return new TermuxIntegrationScreenView(context, this::handleScreenBack);
        }
        if ("about".equals(screenId)) {
            return new AboutScreenView(context, new AboutScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onOpenLicenses() {
                    presenter.onSettingsItemSelected("licenses");
                }
            });
        }
        if ("licenses".equals(screenId)) {
            return new LicensesScreenView(context, this::handleScreenBack);
        }
        if ("tutorial".equals(screenId)) {
            return new TutorialScreenView(context, this::handleScreenBack);
        }
        if ("pluginPage".equals(screenId)) {
            return new PluginPageScreenView(context, "插件页面", this::handleScreenBack);
        }
        if (screenId != null && screenId.startsWith("browser:")) {
            return new InAppBrowserScreenView(context, screenId.substring("browser:".length()), this::handleScreenBack);
        }
        if ("browser".equals(screenId)) {
            return new InAppBrowserScreenView(context, "about:blank", this::handleScreenBack);
        }
        if ("agentEdit".equals(screenId) || (screenId != null && screenId.startsWith("agentEdit:"))) {
            cn.lineai.model.ExtensionOverviewState overview = presenter.getExtensionOverview();
            cn.lineai.model.ExtensionAgentConfig editingAgent = findAgent(overview, screenId == null ? "" : screenId.substring("agentEdit".length()).replaceFirst("^:", ""));
            return new AgentExtensionEditScreenView(
                    context,
                    editingAgent,
                    presenter.getExtensionAvailableTools(),
                    presenter.getMcpSettingsState().getConfigs(),
                    overview.getMcps(),
                    new AgentExtensionEditScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public cn.lineai.model.ExtensionAgentConfig onGenerateDraft(String description) throws Exception {
                    return presenter.onAgentDraftGenerated(description);
                }

                @Override
                public void onSave(cn.lineai.model.ExtensionAgentConfig config) {
                    presenter.onAgentExtensionSaved(config);
                }
            });
        }
        if ("mcpEdit".equals(screenId) || (screenId != null && screenId.startsWith("mcpEdit:"))) {
            cn.lineai.model.ExtensionOverviewState overview = presenter.getExtensionOverview();
            cn.lineai.model.ExtensionMcpConfig editingMcp = findMcp(overview, screenId == null ? "" : screenId.substring("mcpEdit".length()).replaceFirst("^:", ""));
            return new McpExtensionEditScreenView(context, editingMcp, new McpExtensionEditScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public java.util.List<cn.lineai.model.McpToolSummary> onQueryTools(String url, java.util.List<cn.lineai.model.McpRequestHeader> headers) throws Exception {
                    return presenter.onMcpToolsQuery(url, headers);
                }

                @Override
                public void onSave(cn.lineai.model.ExtensionMcpConfig config) {
                    presenter.onMcpExtensionSaved(config);
                }
            });
        }
        if (screenId != null && screenId.startsWith("extension:")) {
            String kind = screenId.substring("extension:".length());
            return new ExtensionDetailScreenView(context, kind, presenter.getExtensionOverview(), new ExtensionDetailScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onAddAgent() {
                    presenter.onSettingsItemSelected("agentEdit");
                }

                @Override
                public void onEditAgent(String id) {
                    presenter.onSettingsItemSelected("agentEdit:" + id);
                }

                @Override
                public void onAddMcp() {
                    presenter.onSettingsItemSelected("mcpEdit");
                }

                @Override
                public void onEditMcp(String id) {
                    presenter.onSettingsItemSelected("mcpEdit:" + id);
                }

                @Override
                public void onCreateSkill(String location, String name, String description, String content) {
                    presenter.onSkillCreated(location, name, description, content);
                }

                @Override
                public void onInstallSkill(String location, String sourcePath, String name) {
                    try {
                        presenter.onSkillInstalled(location, sourcePath, name);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(getContext(), e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onInstallSkillFromUri(String location, String uri, String displayName) {
                    try {
                        presenter.onSkillInstalledFromUri(location, uri, displayName);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(getContext(), e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onEnabledChanged(String kind, String id, boolean enabled) {
                    presenter.onExtensionEnabledChanged(kind, id, enabled);
                }

                @Override
                public void onDelete(String kind, String id) {
                    presenter.onExtensionDeleted(kind, id);
                }
            });
        }
        if ("shellCommand".equals(screenId)) {
            return new ShellCommandScreenView(context, shellCommandText, this::handleScreenBack);
        }
        if ("modelAddOptions".equals(screenId)) {
            return new ModelAddOptionsScreenView(context, new ModelAddOptionsScreenView.Listener() {
                @Override
                public void onBack() {
                    handleScreenBack();
                }

                @Override
                public void onCustom() {
                    presenter.onSettingsItemSelected("modelAdd");
                }

                @Override
                public void onLocal() {
                    presenter.onSettingsItemSelected("modelAdd:local");
                }

                @Override
                public void onProvider(String id) {
                    presenter.onSettingsItemSelected("modelAdd:preset:" + id);
                }
            });
        }
        if ("modelAdd".equals(screenId)) {
            return modelAddScreen(context, null, false);
        }
        if ("modelAdd:local".equals(screenId)) {
            return modelAddScreen(context, null, true);
        }
        if (screenId != null && screenId.startsWith("modelAdd:preset:")) {
            return modelAddScreen(context, ModelProviderPresets.find(screenId.substring("modelAdd:preset:".length())), false);
        }
        if (screenId != null && screenId.startsWith("modelEdit:")) {
            ModelConfig model = presenter.getModel(screenId.substring("modelEdit:".length()));
            if (model != null) {
                return modelEditScreen(context, model);
            }
            return modelAddScreen(context, null, false);
        }
        return simpleScreen(screenId);
    }

    private cn.lineai.model.ExtensionAgentConfig findAgent(cn.lineai.model.ExtensionOverviewState state, String id) {
        if (state == null || id == null || id.length() == 0) {
            return null;
        }
        for (cn.lineai.model.ExtensionAgentConfig agent : state.getAgents()) {
            if (id.equals(agent.getId())) {
                return agent;
            }
        }
        return null;
    }

    private cn.lineai.model.ExtensionMcpConfig findMcp(cn.lineai.model.ExtensionOverviewState state, String id) {
        if (state == null || id == null || id.length() == 0) {
            return null;
        }
        for (cn.lineai.model.ExtensionMcpConfig mcp : state.getMcps()) {
            if (id.equals(mcp.getId())) {
                return mcp;
            }
        }
        return null;
    }

    private View modelAddScreen(Context context, ModelProviderPreset preset, boolean local) {
        return new ModelAddScreenView(context, preset, local, null, new ModelAddScreenView.Listener() {
            @Override
            public void onBack() {
                handleScreenBack();
            }

            @Override
            public void onSave(ModelConfig model) {
                presenter.onModelSaved(model);
            }
        });
    }

    private View modelEditScreen(Context context, ModelConfig model) {
        return new ModelAddScreenView(context, null, model.getProtocolType().name().equals("LOCAL_GGUF"), model, new ModelAddScreenView.Listener() {
            @Override
            public void onBack() {
                handleScreenBack();
            }

            @Override
            public void onSave(ModelConfig model) {
                presenter.onModelSaved(model);
            }
        });
    }

    private String providerLabel(String id) {
        if ("deepseek".equals(id)) return "DeepSeek";
        if ("glm".equals(id)) return "GLM";
        if ("mimo".equals(id)) return "Mimo";
        if ("mimo-token-plan".equals(id)) return "Mimo Token 计划";
        if ("kimi".equals(id)) return "Kimi";
        if ("qwen".equals(id)) return "Qwen";
        if ("openai".equals(id)) return "OpenAI";
        if ("codex".equals(id)) return "Codex";
        if ("claude".equals(id)) return "Claude";
        if ("gemini".equals(id)) return "Gemini";
        if ("openrouter".equals(id)) return "OpenRouter";
        return null;
    }

    private View simpleScreen(String screenId) {
        String title = titleFor(screenId);
        String subtitle = subtitleFor(screenId);
        String[] rows = rowsFor(screenId);
        return new SimpleSettingsScreenView(getContext(), title, subtitle, rows, this::handleScreenBack);
    }

    private String titleFor(String screenId) {
        if ("llm".equals(screenId)) return "AI 行为";
        if ("mcp".equals(screenId)) return "工具与执行";
        if ("theme".equals(screenId)) return "主题与外观";
        if ("output".equals(screenId)) return "输出与浏览";
        if ("experimental".equals(screenId)) return "实验性渲染";
        if ("storage".equals(screenId)) return "存储管理";
        if ("memory".equals(screenId)) return "记忆";
        if ("data".equals(screenId)) return "数据管理";
        if ("keepAlive".equals(screenId)) return "后台保活";
        if ("sshSettings".equals(screenId)) return "SSH 连接";
        if ("termuxIntegration".equals(screenId)) return "Termux 对接";
        if ("about".equals(screenId)) return "关于 LineCode";
        if ("modelAddOptions".equals(screenId)) return "添加模型";
        if ("licenses".equals(screenId)) return "开源许可";
        if ("tutorial".equals(screenId)) return "教程";
        if (screenId != null && screenId.startsWith("extension:")) return "扩展详情";
        return "LineCode";
    }

    private String subtitleFor(String screenId) {
        if ("about".equals(screenId)) return "LineCode Java 原生 View 版本。当前页面用于对齐 LineAI 的 Android UI 结构。";
        if ("modelAddOptions".equals(screenId)) return "选择添加模型的方式，后续会接入真实表单和本地模型选择。";
        if (screenId != null && screenId.startsWith("extension:")) return "管理 Agent、MCP、Skills 扩展的安装、启用状态和删除操作。";
        return "该页面已经接入原生导航与统一样式，后续继续补真实设置项和业务逻辑。";
    }

    private String[] rowsFor(String screenId) {
        if ("llm".equals(screenId)) return new String[] {"交流语气", "思考强度", "保留 reasoning", "数学公式渲染"};
        if ("mcp".equals(screenId)) return new String[] {"MCP 执行模式", "SSH 工具", "网页搜索", "工具确认策略"};
        if ("theme".equals(screenId)) return new String[] {"深色主题", "浅色主题", "咖啡主题", "高对比模式"};
        if ("output".equals(screenId)) return new String[] {"代码自动换行", "网页打开方式", "Markdown 预览"};
        if ("experimental".equals(screenId)) return new String[] {"实验性键盘避让", "实验性消息渲染"};
        if ("storage".equals(screenId)) return new String[] {"聊天记录", "配置文件", "Diff 缓存", "工作区占用"};
        if ("memory".equals(screenId)) return new String[] {"长期记忆", "项目记忆", "短期记忆", "检索索引"};
        if ("data".equals(screenId)) return new String[] {"完整导出", ".linecode 导入", "数据归档"};
        if ("keepAlive".equals(screenId)) return new String[] {"Wake Lock", "前台服务", "模拟音乐播放", "电池白名单"};
        if ("sshSettings".equals(screenId)) return new String[] {"Host", "Port", "Username", "Private key", "测试连接"};
        if ("termuxIntegration".equals(screenId)) return new String[] {"授权指令", "RUN_COMMAND 权限", "自动配置 OpenSSH"};
        if ("about".equals(screenId)) return new String[] {"版本 1.0", "开源许可"};
        if ("modelAddOptions".equals(screenId)) return new String[] {"自定义 API 模型", "本地 GGUF 模型", "OpenAI 兼容供应商", "Codex 预设"};
        if ("tutorial".equals(screenId)) return new String[] {"初学者教程", "专业模式教程", "工具调用说明"};
        if (screenId != null && screenId.startsWith("extension:")) return new String[] {"添加扩展", "已安装列表", "启用状态", "长按管理"};
        return new String[] {"界面骨架", "业务逻辑待接入"};
    }

    @SuppressWarnings("deprecation")
    private void installSystemBarInsetsHandling() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        setOnApplyWindowInsetsListener((view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            contentView.setPadding(0, systemBars.top, 0, bottomInset);
            screenHost.setPadding(0, systemBars.top, 0, bottomInset);
            return insets;
        });
        post(this::requestApplyInsets);
    }
}
