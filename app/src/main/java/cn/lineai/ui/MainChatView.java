package cn.lineai.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.model.ModelProviderPresets;
import cn.lineai.model.SheetOption;
import cn.lineai.mvp.MainContract;
import cn.lineai.mvp.MainUiController;
import cn.lineai.security.UrlPolicy;
import cn.lineai.ui.component.BottomSheetView;
import cn.lineai.ui.component.AttachmentPickerSheetView;
import cn.lineai.ui.component.ChatMessageListView;
import cn.lineai.ui.component.ComposerView;
import cn.lineai.ui.component.AboutScreenView;
import cn.lineai.ui.component.AgentExtensionEditScreenView;
import cn.lineai.ui.component.DataSettingsScreenView;
import cn.lineai.ui.component.DirectoryPickerSheetView;
import cn.lineai.ui.component.DrawerView;
import cn.lineai.ui.component.ExperimentalSettingsScreenView;
import cn.lineai.ui.component.ExtensionDetailScreenView;
import cn.lineai.ui.component.ExtensionsScreenView;
import cn.lineai.ui.component.HeaderView;
import cn.lineai.ui.component.InAppBrowserScreenView;
import cn.lineai.ui.component.InputSettingsScreenView;
import cn.lineai.ui.component.KeepAliveSettingsScreenView;
import cn.lineai.ui.component.LLMSettingsScreenView;
import cn.lineai.ui.component.LicensesScreenView;
import cn.lineai.ui.component.MCPSettingsScreenView;
import cn.lineai.ui.component.MemorySettingsScreenView;
import cn.lineai.ui.component.MessageActionListener;
import cn.lineai.ui.component.McpExtensionEditScreenView;
import cn.lineai.ui.component.ModelAddOptionsScreenView;
import cn.lineai.ui.component.ModelAddScreenView;
import cn.lineai.ui.component.ModelListScreenView;
import cn.lineai.ui.component.OutputSettingsScreenView;
import cn.lineai.ui.component.PluginPageScreenView;
import cn.lineai.ui.component.PromptTemplatesScreenView;
import cn.lineai.ui.component.ScreenFactories;
import cn.lineai.ui.component.ScreenRegistry;
import cn.lineai.ui.component.SettingsScreenView;
import cn.lineai.ui.component.ShellCommandScreenView;
import cn.lineai.ui.component.SimpleSettingsScreenView;
import cn.lineai.ui.component.SshSettingsScreenView;
import cn.lineai.ui.component.StorageManagementScreenView;
import cn.lineai.ui.component.TerminalProviderDetailScreenView;
import cn.lineai.ui.component.TermuxIntegrationScreenView;
import cn.lineai.ui.component.ThemeSettingsScreenView;
import cn.lineai.ui.component.ToolSettingsScreenView;
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

        void createDocument(String mimeType, String displayName, DocumentCreateCallback callback);
    }

    public interface DocumentPickCallback {
        void onDocumentPicked(String uri, String displayName);

        void onDocumentPickCancelled();
    }

    public interface DocumentCreateCallback {
        void onDocumentCreated(String uri, String displayName);

        void onDocumentCreateCancelled();
    }

    private final MainUiController presenter;
    private final HeaderView headerView;
    private final LinearLayout contentView;
    private final ChatMessageListView messageListView;
    private final ComposerView composerView;
    private final DrawerView drawerView;
    private final BottomSheetView bottomSheetView;
    private final DirectoryPickerSheetView directoryPickerSheetView;
    private final AttachmentPickerSheetView attachmentPickerSheetView;
    private final FrameLayout screenHost;
    private ChatUiState lastState;
    private String shellCommandText = "";
    private String currentScreenId = "";
    private final ScreenRegistry screenRegistry = new ScreenRegistry();
    private String attachmentPickerTitle = "";
    private String attachmentPickerMessage = "";
    private String attachmentPickerSource = InputAttachment.SOURCE_LOCAL;
    private boolean attachmentPickerLoading;
    private FileTreeNode attachmentPickerTree;

    public MainChatView(Context context, MainUiController presenter) {
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
        messageListView.setMessageActionListener(new MessageActionListener() {
            @Override
            public void onCopyMessage(ChatMessage message) {
                copyMessage(message);
            }

            @Override
            public void onRecallMessage(ChatMessage message) {
                if (message != null) {
                    MainChatView.this.presenter.onRecallMessage(message.getId());
                }
            }
        });
        contentView.addView(messageListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        composerView = new ComposerView(context);
        composerView.setListener(new ComposerView.Listener() {
            @Override
            public void onSend(String text, List<InputAttachment> attachments) {
                MainChatView.this.presenter.onSendMessage(text, attachments);
            }

            @Override
            public void onAttachClick() {
                MainChatView.this.presenter.onAttachmentPickerRequested();
            }

            @Override
            public void onModeChanged(String mode) {
                MainChatView.this.presenter.onChatModeChanged(mode);
            }

            @Override
            public void onStop() {
                MainChatView.this.presenter.onStopGeneration();
            }

            @Override
            public void onModelQuickSwitch(String modelId) {
                MainChatView.this.presenter.onModelQuickSwitch(modelId);
            }

            @Override
            public void onModelManageClick() {
                MainChatView.this.presenter.showModelManagement();
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
            public void onFileNodeSelected(String path, boolean directory) {
                MainChatView.this.presenter.onFileNodeSelected(path, directory);
            }

            @Override
            public void onFileNodeLongPressed(String path, String name, boolean directory, boolean root) {
                MainChatView.this.presenter.onFileNodeLongPressed(path, name, directory, root);
            }

            @Override
            public void onFileTreeActivated() {
                MainChatView.this.presenter.onFileTreeActivated();
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

        directoryPickerSheetView = new DirectoryPickerSheetView(context);
        directoryPickerSheetView.setListener(new DirectoryPickerSheetView.Listener() {
            @Override
            public void onDirectoryPickerClosed() {
                MainChatView.this.presenter.onDirectoryPickerCancelled();
            }

            @Override
            public void onDirectoryPicked(String path) {
                MainChatView.this.presenter.onDirectoryPickerNodeSelected(path);
            }

            @Override
            public void onDirectoryPickerConfirmed() {
                MainChatView.this.presenter.onDirectoryPickerConfirmed();
            }
        });
        addView(directoryPickerSheetView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        attachmentPickerSheetView = new AttachmentPickerSheetView(context);
        attachmentPickerSheetView.setListener(new AttachmentPickerSheetView.Listener() {
            @Override
            public void onAttachmentPickerClosed() {
                MainChatView.this.presenter.onAttachmentPickerCancelled();
            }

            @Override
            public void onAttachmentNodeSelected(String path, boolean directory) {
                MainChatView.this.presenter.onAttachmentPickerNodeSelected(path, directory);
            }

            @Override
            public void onAttachmentFileToggled(String path, String name, String source) {
                composerView.toggleAttachment(new InputAttachment(name, path, source));
                renderAttachmentPicker();
            }
        });
        addView(attachmentPickerSheetView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        screenHost = new FrameLayout(context);
        screenHost.setBackgroundColor(LineTheme.BG);
        screenHost.setClickable(true);
        screenHost.setFocusable(true);
        screenHost.setFocusableInTouchMode(true);
        screenHost.setVisibility(GONE);
        addView(screenHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        installSystemBarInsetsHandling();
        registerScreenFactories();
    }

    private void registerScreenFactories() {
        screenRegistry.register(new ScreenFactories.SettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.LlmSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.PromptTemplatesScreenFactory());
        screenRegistry.register(new ScreenFactories.InputSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ToolSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.McpSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.OutputSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ThemeSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ExperimentalSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.DataSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.StorageManagementScreenFactory());
        screenRegistry.register(new ScreenFactories.MemorySettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.KeepAliveSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.SshSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.TermuxIntegrationScreenFactory());
        screenRegistry.register(new ScreenFactories.AboutScreenFactory());
        screenRegistry.register(new ScreenFactories.LicensesScreenFactory());
        screenRegistry.register(new ScreenFactories.TutorialScreenFactory());
        screenRegistry.register(new ScreenFactories.PluginPageScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelListScreenFactory());
        screenRegistry.register(new ScreenFactories.ImageUnderstandingModelScreenFactory());
        screenRegistry.register(new ScreenFactories.ImageGenerationModelScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddOptionsScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddLocalScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddPresetScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelEditScreenFactory());
        screenRegistry.register(new ScreenFactories.ExtensionsScreenFactory());
        screenRegistry.register(new ScreenFactories.TerminalProviderScreenFactory());
        screenRegistry.register(new ScreenFactories.AgentEditScreenFactory());
        screenRegistry.register(new ScreenFactories.McpEditScreenFactory());
        screenRegistry.register(new ScreenFactories.ExtensionDetailScreenFactory());
        screenRegistry.register(new ScreenFactories.BrowserScreenFactory());
        screenRegistry.register(new ScreenFactories.BrowserPrefixScreenFactory());
        screenRegistry.register(new ScreenFactories.ShellCommandScreenFactory());
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
    public void setComposerDraft(String text) {
        showChatScreen();
        composerView.setDraft(text);
    }

    @Override
    public void setComposerDraft(String text, List<InputAttachment> attachments) {
        showChatScreen();
        composerView.setDraft(text, attachments);
    }

    @Override
    public void showDrawer() {
        KeyboardController.clearFocusAndHide(this);
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
        renderDrawer(lastState);
        drawerView.open();
    }

    @Override
    public void showSheet(String title, List<SheetOption> options) {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
        bottomSheetView.show(title, options);
    }

    @Override
    public void showFileActionDialog(String title, String subtitle, List<SheetOption> options) {
        KeyboardController.clearFocusAndHide(this);
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();

        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 16));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        TextView titleView = LineTheme.textMedium(getContext(),
                title == null || title.length() == 0 ? getContext().getString(R.string.dialog_file_action_title) : title,
                LineTheme.FONT_LG,
                LineTheme.TEXT);
        panel.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (subtitle != null && subtitle.length() > 0) {
            TextView subtitleView = LineTheme.text(getContext(), subtitle, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            subtitleView.setSingleLine(false);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            subtitleParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
            panel.addView(subtitleView, subtitleParams);
        }

        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        dividerParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.XS);
        panel.addView(divider, dividerParams);

        if (options != null) {
            for (SheetOption option : options) {
                panel.addView(fileActionRow(dialog, option), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }

        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                shown.setLayout(insetDialogWidth(), LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    @Override
    public void showInputDialog(String title, String message, String initialValue, String actionId) {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelectAllOnFocus(true);
        int horizontalPadding = LineTheme.dp(getContext(), LineTheme.LG);
        input.setPadding(horizontalPadding, LineTheme.dp(getContext(), LineTheme.SM), horizontalPadding, LineTheme.dp(getContext(), LineTheme.SM));
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(title == null ? "" : title)
                .setMessage(message == null ? "" : message)
                .setView(input)
                .setNegativeButton(getContext().getString(R.string.common_cancel), null)
                .setPositiveButton(getContext().getString(R.string.common_confirm), (d, which) -> presenter.onDialogInputSubmitted(actionId, input.getText().toString()))
                .create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();
    }

    @Override
    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(title == null ? "" : title)
                .setMessage(message == null ? "" : message)
                .setNegativeButton(getContext().getString(R.string.common_cancel), null)
                .setPositiveButton(confirmLabel == null || confirmLabel.length() == 0
                        ? getContext().getString(R.string.common_confirm)
                        : confirmLabel,
                        (d, which) -> presenter.onDialogConfirmed(actionId))
                .create();
        if (danger) {
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(LineTheme.DANGER));
        }
        dialog.show();
    }

    @Override
    public void showDirectoryPicker(String title, String subtitle, FileTreeNode tree, String selectedPath, boolean loading, String message) {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        attachmentPickerSheetView.close();
        directoryPickerSheetView.show(title, subtitle, tree, selectedPath, loading, message);
    }

    @Override
    public void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source) {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerTitle = title == null ? "" : title;
        attachmentPickerTree = tree;
        attachmentPickerLoading = loading;
        attachmentPickerMessage = message == null ? "" : message;
        attachmentPickerSource = InputAttachment.SOURCE_SSH.equals(source)
                ? InputAttachment.SOURCE_SSH
                : InputAttachment.SOURCE_LOCAL;
        renderAttachmentPicker();
    }

    @Override
    public void hideOverlays() {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
    }

    @Override
    public void hideDirectoryPicker() {
        directoryPickerSheetView.close();
    }

    @Override
    public void hideAttachmentPicker() {
        attachmentPickerSheetView.close();
    }

    @Override
    public void showScreen(String screenId) {
        currentScreenId = screenId == null ? "" : screenId;
        KeyboardController.clearFocusAndHide(screenHost);
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
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
    public void openLineCodeImportPicker() {
        Context context = getContext();
        if (!(context instanceof WorkspaceHost)) {
            return;
        }
        ((WorkspaceHost) context).openDocumentPicker("*/*", new String[] {"LineCode.linecode"}, new DocumentPickCallback() {
            @Override
            public void onDocumentPicked(String uri, String displayName) {
                presenter.onLineCodeImportPicked(uri, displayName);
            }

            @Override
            public void onDocumentPickCancelled() {
                presenter.onLineCodeImportCancelled();
            }
        });
    }

    @Override
    public void openLineCodeExportPicker(String fileName) {
        Context context = getContext();
        if (!(context instanceof WorkspaceHost)) {
            return;
        }
        ((WorkspaceHost) context).createDocument("application/zip", fileName, new DocumentCreateCallback() {
            @Override
            public void onDocumentCreated(String uri, String displayName) {
                presenter.onLineCodeExportTargetPicked(uri, displayName);
            }

            @Override
            public void onDocumentCreateCancelled() {
                presenter.onLineCodeExportCancelled();
            }
        });
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
        String safeUrl = UrlPolicy.normalizeHttpOrHttpsUrl(url);
        if (safeUrl.length() == 0) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(getContext(), getContext().getString(R.string.toast_open_link_failed, safeUrl), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        String text = message.getContent();
        if ((text == null || text.length() == 0) && message.getReasoningContent().length() > 0) {
            text = message.getReasoningContent();
        }
        if (text == null || text.length() == 0) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("LineCode message", text));
            Toast.makeText(getContext(), getContext().getString(R.string.toast_copied), Toast.LENGTH_SHORT).show();
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
                projectLabel.length() == 0 ? getContext().getString(R.string.header_project_default) : projectLabel,
                projectPath.length() == 0 ? "" : projectPath,
                presenter.canRemoveCurrentProject(),
                drawerView.isFilesTabActive() ? presenter.getFileTree() : null
        );
    }

    private View fileActionRow(Dialog dialog, SheetOption option) {
        Context context = getContext();
        String id = option == null ? "" : option.getId();
        boolean danger = id.startsWith("file:delete:");
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            presenter.onSheetOptionSelected(id);
        });
        LineTheme.padding(row, 0, 14, 0, 14);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView label = LineTheme.text(context,
                option == null ? "" : option.getLabel(),
                LineTheme.FONT_MD,
                danger ? LineTheme.DANGER : LineTheme.TEXT,
                Typeface.NORMAL);
        labels.addView(label, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        String description = option == null ? "" : option.getDescription();
        if (description != null && description.length() > 0) {
            TextView desc = LineTheme.text(context, description, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            desc.setSingleLine(false);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            labels.addView(desc, descParams);
        }
        return row;
    }

    private int insetDialogWidth() {
        int width = getResources().getDisplayMetrics().widthPixels - LineTheme.dp(getContext(), 32);
        return Math.max(LineTheme.dp(getContext(), 280), width);
    }

    public boolean handleBackPressed() {
        if (screenHost.getVisibility() == VISIBLE) {
            presenter.onScreenBackFrom(currentScreenId);
            return true;
        }
        if (directoryPickerSheetView.getVisibility() == VISIBLE) {
            directoryPickerSheetView.close();
            return true;
        }
        if (attachmentPickerSheetView.getVisibility() == VISIBLE) {
            attachmentPickerSheetView.close();
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

    public void handleScreenBack() {
        presenter.onScreenBackFrom(currentScreenId);
    }

    public String getCurrentScreenId() {
        return currentScreenId;
    }

    public String getShellCommandText() {
        return shellCommandText;
    }

    private void renderAttachmentPicker() {
        attachmentPickerSheetView.show(
                attachmentPickerTitle,
                attachmentPickerTree,
                composerView.selectedAttachmentPaths(attachmentPickerSource),
                attachmentPickerLoading,
                attachmentPickerMessage,
                attachmentPickerSource
        );
    }

    private View buildScreen(String screenId) {
        View view = screenRegistry.createScreen(screenId, this, presenter, getContext());
        if (view != null) {
            return view;
        }
        return simpleScreen(screenId);
    }

    private View simpleScreen(String screenId) {
        String title = titleFor(screenId);
        String subtitle = subtitleFor(screenId);
        String[] rows = rowsFor(screenId);
        return new SimpleSettingsScreenView(getContext(), title, subtitle, rows, this::handleScreenBack);
    }

    private String titleFor(String screenId) {
        Context context = getContext();
        if ("llm".equals(screenId)) return context.getString(R.string.screen_llm_title);
        if ("promptTemplates".equals(screenId)) return context.getString(R.string.screen_prompt_templates_title);
        if ("input".equals(screenId)) return context.getString(R.string.screen_input_title);
        if ("mcp".equals(screenId)) return context.getString(R.string.screen_mcp_title);
        if ("toolSettings".equals(screenId)) return context.getString(R.string.screen_tools_title);
        if ("theme".equals(screenId)) return context.getString(R.string.fallback_screen_theme_title);
        if ("output".equals(screenId)) return context.getString(R.string.fallback_screen_output_title);
        if ("experimental".equals(screenId)) return context.getString(R.string.screen_experimental_title);
        if ("storage".equals(screenId)) return context.getString(R.string.screen_storage_title);
        if ("memory".equals(screenId)) return context.getString(R.string.screen_memory_title);
        if ("data".equals(screenId)) return context.getString(R.string.fallback_screen_data_title);
        if ("keepAlive".equals(screenId)) return context.getString(R.string.fallback_screen_keep_alive_title_alt);
        if ("sshSettings".equals(screenId)) return context.getString(R.string.screen_ssh_title);
        if ("termuxIntegration".equals(screenId)) return context.getString(R.string.screen_termux_title);
        if ("about".equals(screenId)) return context.getString(R.string.fallback_screen_about_title_alt);
        if ("modelAddOptions".equals(screenId)) return context.getString(R.string.screen_model_add_options_title);
        if ("licenses".equals(screenId)) return context.getString(R.string.fallback_screen_licenses_title);
        if ("tutorial".equals(screenId)) return context.getString(R.string.fallback_screen_tutorial_title_alt);
        if (screenId != null && screenId.startsWith("extension:")) return context.getString(R.string.fallback_screen_extension_title);
        return context.getString(R.string.header_project_default);
    }

    private String subtitleFor(String screenId) {
        Context context = getContext();
        if ("about".equals(screenId)) return context.getString(R.string.fallback_screen_about_subtitle);
        if ("modelAddOptions".equals(screenId)) return context.getString(R.string.fallback_screen_model_add_options_subtitle);
        if (screenId != null && screenId.startsWith("extension:")) return context.getString(R.string.fallback_screen_extension_subtitle);
        return context.getString(R.string.fallback_screen_default_subtitle);
    }

    private String[] rowsFor(String screenId) {
        Context context = getContext();
        if ("llm".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_llm_tone),
                context.getString(R.string.fallback_row_llm_thinking),
                context.getString(R.string.fallback_row_llm_keep_reasoning),
                context.getString(R.string.fallback_row_llm_prompts)
        };
        if ("input".equals(screenId)) return new String[] {context.getString(R.string.fallback_row_input_enter)};
        if ("mcp".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_mcp_execution),
                context.getString(R.string.fallback_row_mcp_local),
                context.getString(R.string.fallback_row_mcp_ssh),
                context.getString(R.string.fallback_row_mcp_confirm)
        };
        if ("toolSettings".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_tools_image_understanding),
                context.getString(R.string.fallback_row_tools_web_search),
                context.getString(R.string.fallback_row_tools_model_select),
                context.getString(R.string.fallback_row_tools_search_api)
        };
        if ("theme".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_theme_dark),
                context.getString(R.string.fallback_row_theme_light),
                context.getString(R.string.fallback_row_theme_coffee),
                context.getString(R.string.fallback_row_theme_high_contrast)
        };
        if ("output".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_output_code_wrap),
                context.getString(R.string.fallback_row_output_open_mode),
                context.getString(R.string.fallback_row_output_browser_js),
                context.getString(R.string.fallback_row_output_markdown_preview)
        };
        if ("experimental".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_experimental_keyboard),
                context.getString(R.string.fallback_row_experimental_rendering)
        };
        if ("storage".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_storage_chat),
                context.getString(R.string.fallback_row_storage_config),
                context.getString(R.string.fallback_row_storage_diff_cache),
                context.getString(R.string.fallback_row_storage_workspace)
        };
        if ("memory".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_memory_long_term),
                context.getString(R.string.fallback_row_memory_project),
                context.getString(R.string.fallback_row_memory_short_term),
                context.getString(R.string.fallback_row_memory_index)
        };
        if ("data".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_data_full_export),
                context.getString(R.string.fallback_row_data_linecode_import),
                context.getString(R.string.fallback_row_data_archive)
        };
        if ("keepAlive".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_keep_alive_wake_lock),
                context.getString(R.string.fallback_row_keep_alive_foreground),
                context.getString(R.string.fallback_row_keep_alive_fake_music),
                context.getString(R.string.fallback_row_keep_alive_battery_whitelist)
        };
        if ("sshSettings".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_ssh_host),
                context.getString(R.string.fallback_row_ssh_port),
                context.getString(R.string.fallback_row_ssh_username),
                context.getString(R.string.fallback_row_ssh_private_key),
                context.getString(R.string.fallback_row_ssh_test)
        };
        if ("termuxIntegration".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_termux_grant),
                context.getString(R.string.fallback_row_termux_run_command_perm),
                context.getString(R.string.fallback_row_termux_auto_ssh)
        };
        if ("about".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_about_version),
                context.getString(R.string.screen_about_open_source_licenses)
        };
        if ("modelAddOptions".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_model_add_custom_api),
                context.getString(R.string.fallback_row_model_add_local_gguf),
                context.getString(R.string.fallback_row_model_add_openai_provider),
                context.getString(R.string.fallback_row_model_add_codex_preset)
        };
        if ("tutorial".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_tutorial_beginner),
                context.getString(R.string.fallback_row_tutorial_pro),
                context.getString(R.string.fallback_row_tutorial_tools)
        };
        if (screenId != null && screenId.startsWith("extension:")) return new String[] {
                context.getString(R.string.fallback_row_extension_add),
                context.getString(R.string.fallback_row_extension_installed),
                context.getString(R.string.fallback_row_extension_enabled),
                context.getString(R.string.fallback_row_extension_manage)
        };
        return new String[] {context.getString(R.string.fallback_row_default), context.getString(R.string.fallback_row_default_subtitle)};
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
