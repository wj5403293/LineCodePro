package cn.lineai.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.SheetOption;
import cn.lineai.mvp.MainContract;
import cn.lineai.mvp.MainUiController;
import cn.lineai.security.UrlPolicy;
import cn.lineai.ui.component.AboutScreenView;
import cn.lineai.ui.component.AttachmentPickerSheetView;
import cn.lineai.ui.component.BackNavigation;
import cn.lineai.ui.component.BottomSheetView;
import cn.lineai.ui.component.ChatMessageListView;
import cn.lineai.ui.component.ComposerView;
import cn.lineai.ui.component.DataSettingsScreenView;
import cn.lineai.ui.component.DialogDimensions;
import cn.lineai.ui.component.DialogManager;
import cn.lineai.ui.component.DirectoryPickerSheetView;
import cn.lineai.ui.component.DrawerView;
import cn.lineai.ui.component.ExtensionDetailScreenView;
import cn.lineai.ui.component.ExtensionsScreenView;
import cn.lineai.ui.component.FileActionRow;
import cn.lineai.ui.component.HeaderView;
import cn.lineai.ui.component.InAppBrowserScreenView;
import cn.lineai.ui.component.InputSettingsScreenView;
import cn.lineai.ui.component.KeepAliveSettingsScreenView;
import cn.lineai.ui.component.AgentExtensionEditScreenView;
import cn.lineai.ui.component.LLMSettingsScreenView;
import cn.lineai.ui.component.LicensesScreenView;
import cn.lineai.ui.component.MCPSettingsScreenView;
import cn.lineai.ui.component.McpExtensionEditScreenView;
import cn.lineai.ui.component.MemorySettingsScreenView;
import cn.lineai.ui.component.MessageActionListener;
import cn.lineai.ui.component.ModelAddOptionsScreenView;
import cn.lineai.ui.component.ModelAddScreenView;
import cn.lineai.ui.component.ModelListScreenView;
import cn.lineai.ui.component.MainChatViewLayoutBuilder;
import cn.lineai.ui.component.OutputSettingsScreenView;
import cn.lineai.ui.component.PromptTemplatesScreenView;
import cn.lineai.ui.component.ScreenFactories;
import cn.lineai.ui.component.ScreenRegistry;
import cn.lineai.ui.component.SettingsScreenView;
import cn.lineai.ui.component.ShellCommandScreenView;
import cn.lineai.ui.component.SimpleScreenContent;
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
import java.util.LinkedHashMap;
import java.util.List;

public final class MainChatView extends FrameLayout implements MainContract.View, BackNavigation.BackTarget {
    private static final long SCREEN_ENTER_MS = 280L;
    private static final long SCREEN_EXIT_MS = 220L;

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
    private final DialogManager dialogManager = new DialogManager();
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
    private final LinkedHashMap<String, View> screenCache = new LinkedHashMap<>();
    private int screenAnimationGeneration;
    private boolean screenClosing;
    private String attachmentPickerTitle = "";
    private String attachmentPickerMessage = "";
    private String attachmentPickerSource = InputAttachment.SOURCE_LOCAL;
    private boolean attachmentPickerLoading;
    private FileTreeNode attachmentPickerTree;

    public MainChatView(Context context, MainUiController presenter) {
        super(context);
        this.presenter = presenter;
        setBackgroundColor(LineTheme.BG);

        MainChatViewLayoutBuilder.Result layout = MainChatViewLayoutBuilder.build(context);
        contentView = layout.contentView;
        screenHost = layout.screenHost;
        addView(contentView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(screenHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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

            @Override
            public void onQuoteMessage(ChatMessage message) {
                if (message != null && message.getContent() != null) {
                    composerView.setQuoteText(message.getContent());
                }
            }

            @Override
            public void onShareMessage(ChatMessage message) {
                if (message != null && message.getContent() != null) {
                    shareMessageAsMarkdown(message);
                }
            }

            @Override
            public void onSelectMessage(ChatMessage message, android.view.View messageView) {
                if (message != null && message.getContent() != null) {
                    triggerTextSelection(messageView, message.getContent());
                }
            }

            @Override
            public void onMultiSelectMessage(ChatMessage message) {
                showMultiSelectDialog(-1);
            }
        });
        messageListView.setMultiSelectListener(position -> showMultiSelectDialog(position));
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

            @Override
            public void onAiReasoningEffortChanged(String effort) {
                MainChatView.this.presenter.onAiReasoningEffortChanged(effort);
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

        MainChatViewLayoutBuilder.installSystemBarInsetsHandling(this, contentView, screenHost);
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
        screenRegistry.register(new ScreenFactories.DataSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.StorageManagementScreenFactory());
        screenRegistry.register(new ScreenFactories.MemorySettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ErrorLogsScreenFactory());
        screenRegistry.register(new ScreenFactories.KeepAliveSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.AdvancedFeaturesScreenFactory());
        screenRegistry.register(new ScreenFactories.PhoneControlScreenFactory());
        screenRegistry.register(new ScreenFactories.SshSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.TermuxIntegrationScreenFactory());
        screenRegistry.register(new ScreenFactories.AboutScreenFactory());
        screenRegistry.register(new ScreenFactories.LicensesScreenFactory());
        screenRegistry.register(new ScreenFactories.TutorialScreenFactory());
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
                panel.addView(FileActionRow.create(getContext(), dialog, option, presenter::onSheetOptionSelected), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }

        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                shown.setLayout(DialogDimensions.insetDialogWidth(getContext()), LayoutParams.WRAP_CONTENT);
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
        final String capturedActionId = actionId;
        dialogManager.showInput(getContext(), title, message, null, initialValue,
                value -> presenter.onDialogInputSubmitted(capturedActionId, value));
    }

    @Override
    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
        final String capturedActionId = actionId;
        dialogManager.showConfirm(getContext(), title, message, confirmLabel, danger,
                () -> presenter.onDialogConfirmed(capturedActionId),
                null);
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
        showScreen(screenId, true);
    }

    public void showScreen(String screenId, boolean forward) {
        showScreen(screenId, forward, true);
    }

    public void showScreen(String screenId, boolean forward, boolean animate) {
        int animationGeneration = ++screenAnimationGeneration;
        screenClosing = false;
        String previousScreenId = currentScreenId;
        currentScreenId = screenId == null ? "" : screenId;
        KeyboardController.clearFocusAndHide(screenHost);
        KeyboardController.clearFocusAndHide(this);
        drawerView.close();
        bottomSheetView.close();
        directoryPickerSheetView.close();
        attachmentPickerSheetView.close();
        screenHost.animate().cancel();
        View existing = previousScreenId.length() > 0 ? screenCache.get(previousScreenId) : null;
        if (existing == null || existing.getParent() != screenHost) {
            existing = screenHost.getChildCount() > 0 ? screenHost.getChildAt(screenHost.getChildCount() - 1) : null;
        }
        if (existing != null) {
            existing.animate().cancel();
            existing.setTranslationX(0f);
            existing.setAlpha(1f);
        }
        View cached = currentScreenId.length() > 0 ? screenCache.get(currentScreenId) : null;
        View nextView;
        if (cached != null && cached.getParent() == null) {
            nextView = cached;
        } else {
            nextView = buildScreen(currentScreenId);
            if (currentScreenId.length() > 0 && nextView != null) {
                screenCache.put(currentScreenId, nextView);
            }
        }
        if (nextView != null && nextView.getParent() == null) {
            screenHost.addView(nextView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        screenHost.setVisibility(VISIBLE);
        screenHost.setAlpha(1f);
        screenHost.setTranslationX(0f);
        screenHost.requestFocus();
        screenHost.bringToFront();
        if (nextView == null) {
            resetScreenHostAnimationState();
            return;
        }
        if (!animate || currentScreenId.equals(previousScreenId)) {
            nextView.animate().cancel();
            nextView.setTranslationX(0f);
            nextView.setAlpha(1f);
            for (int i = screenHost.getChildCount() - 1; i >= 0; i--) {
                View child = screenHost.getChildAt(i);
                if (child == nextView) {
                    continue;
                }
                child.animate().cancel();
                child.setTranslationX(0f);
                child.setAlpha(1f);
                screenHost.removeViewAt(i);
            }
            resetScreenHostAnimationState();
            return;
        }
        float distance = screenTransitionDistance();
        float enterFrom = forward ? distance : -distance;
        float exitTo = forward ? -distance : distance;
        if (existing == null || existing == nextView) {
            nextView.setTranslationX(enterFrom);
            nextView.setAlpha(1f);
            nextView.animate()
                    .translationX(0f)
                    .setDuration(SCREEN_ENTER_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (animationGeneration == screenAnimationGeneration) {
                            resetScreenHostAnimationState();
                        }
                    })
                    .start();
            return;
        }
        nextView.setTranslationX(enterFrom);
        nextView.setAlpha(1f);
        existing.setTranslationX(0f);
        existing.setAlpha(1f);
        final View exitingView = existing;
        existing.animate()
                .translationX(exitTo)
                .setDuration(SCREEN_ENTER_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        nextView.animate()
                .translationX(0f)
                .setDuration(SCREEN_ENTER_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (animationGeneration == screenAnimationGeneration) {
                        screenHost.removeView(exitingView);
                        exitingView.setTranslationX(0f);
                        resetScreenHostAnimationState();
                    }
                })
                .start();
    }

    @Override
    public void evictScreen(String screenId) {
        String safeId = screenId == null ? "" : screenId;
        if (safeId.length() == 0) {
            return;
        }
        View cached = screenCache.remove(safeId);
        if (cached != null && cached.getParent() instanceof ViewGroup) {
            ((ViewGroup) cached.getParent()).removeView(cached);
        }
    }

    public void invalidateScreen(String screenId) {
        String safeId = screenId == null ? "" : screenId;
        evictScreen(safeId);
        if (safeId.equals(currentScreenId)) {
            showScreen(safeId, true, false);
        }
    }

    @Override
    public void showChatScreen() {
        int animationGeneration = ++screenAnimationGeneration;
        currentScreenId = "";
        KeyboardController.clearFocusAndHide(screenHost);
        KeyboardController.clearFocusAndHide(this);
        screenHost.animate().cancel();
        if (screenHost.getVisibility() != VISIBLE) {
            screenClosing = false;
            screenHost.removeAllViews();
            screenHost.setVisibility(GONE);
            resetScreenHostAnimationState();
            return;
        }
        screenClosing = true;
        View existing = screenHost.getChildCount() > 0 ? screenHost.getChildAt(0) : null;
        if (existing == null) {
            screenHost.setVisibility(GONE);
            screenClosing = false;
            resetScreenHostAnimationState();
            return;
        }
        existing.animate().cancel();
        existing.animate()
                .translationX(screenTransitionDistance())
                .setDuration(SCREEN_EXIT_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (animationGeneration != screenAnimationGeneration) {
                        return;
                    }
                    screenHost.removeAllViews();
                    screenHost.setVisibility(GONE);
                    screenClosing = false;
                    existing.setTranslationX(0f);
                    resetScreenHostAnimationState();
                })
                .start();
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

    public boolean handleBackPressed() {
        return BackNavigation.handle(this);
    }

    @Override
    public boolean isScreenVisible() {
        return screenHost.getVisibility() == VISIBLE && !screenClosing;
    }

    @Override
    public boolean isDirectoryPickerVisible() {
        return directoryPickerSheetView.getVisibility() == VISIBLE;
    }

    @Override
    public boolean isAttachmentPickerVisible() {
        return attachmentPickerSheetView.getVisibility() == VISIBLE;
    }

    @Override
    public boolean isBottomSheetVisible() {
        return bottomSheetView.getVisibility() == VISIBLE;
    }

    @Override
    public boolean isDrawerVisible() {
        return drawerView.getVisibility() == VISIBLE;
    }

    @Override
    public void backFromScreen() {
        presenter.onScreenBackFrom(currentScreenId);
    }

    @Override
    public void closeDirectoryPicker() {
        directoryPickerSheetView.close();
    }

    @Override
    public void closeAttachmentPicker() {
        attachmentPickerSheetView.close();
    }

    @Override
    public void closeBottomSheet() {
        bottomSheetView.close();
    }

    @Override
    public void closeDrawer() {
        drawerView.close();
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
        Context context = getContext();
        String title = SimpleScreenContent.title(context, screenId);
        String subtitle = SimpleScreenContent.subtitle(context, screenId);
        String[] rows = SimpleScreenContent.rows(context, screenId);
        return new SimpleSettingsScreenView(context, title, subtitle, rows, this::handleScreenBack);
    }

    private float screenTransitionDistance() {
        int width = screenHost.getWidth();
        return width > 0 ? width : getResources().getDisplayMetrics().widthPixels;
    }

    private void resetScreenHostAnimationState() {
        screenHost.setAlpha(1f);
        screenHost.setTranslationX(0f);
    }

    private void showMultiSelectDialog(int triggerPosition) {
        List<ChatMessage> messages = messageListView.getMessages();
        if (messages == null || messages.isEmpty()) return;
        Context context = getContext();
        boolean[] checked = new boolean[messages.size()];
        // Pre-check the triggered message
        if (triggerPosition >= 0 && triggerPosition < checked.length) {
            checked[triggerPosition] = true;
        }
        String[] items = new String[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String role = msg.getRole() == ChatMessage.Role.USER ? "我" : "AI";
            String preview = msg.getContent() == null ? "" : msg.getContent();
            if (preview.length() > 50) preview = preview.substring(0, 50) + "...";
            items[i] = "[" + role + "] " + preview;
        }
        new android.app.AlertDialog.Builder(context)
                .setTitle("选择消息合并转发")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("合并转发", (dialog, which) -> {
                    List<ChatMessage> selected = new java.util.ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) selected.add(messages.get(i));
                    }
                    if (selected.isEmpty()) {
                        Toast.makeText(context, "未选择任何消息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showMergeFormatDialog(selected);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMergeFormatDialog(List<ChatMessage> selected) {
        String[] formats = {"对话截图(图片)", "PDF 文件", "Markdown 文件(.md)", "纯文本分享", "复制到剪贴板"};
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("导出格式")
                .setItems(formats, (dialog, which) -> {
                    if (which == 0) {
                        shareAsChatImage(selected);
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (ChatMessage msg : selected) {
                        String role = msg.getRole() == ChatMessage.Role.USER ? "我" : "AI";
                        String content = msg.getContent() == null ? "" : msg.getContent();
                        if (which == 1 || which == 2) {
                            sb.append("## ").append(role).append("\n\n").append(content).append("\n\n---\n\n");
                        } else {
                            sb.append(role).append("：\n").append(content).append("\n\n");
                        }
                    }
                    sb.append("—— 来自 LineCode Pro");
                    String text = sb.toString();
                    if (which == 4) {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("chat", text));
                        String warning = text.length() > 5000 ? " (注意: 内容较长，" + text.length() + "字)" : "";
                        Toast.makeText(getContext(), "已复制到剪贴板" + warning, Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        askFileNameAndShare("chat_export", ".pdf", selected, text, true);
                    } else if (which == 2) {
                        askFileNameAndShare("chat_export", ".md", selected, text, false);
                    } else {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "对话合并转发 - LineCode Pro");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
                        getContext().startActivity(Intent.createChooser(shareIntent, "合并转发"));
                    }
                })
                .show();
    }

    private void askFileNameAndShare(String defaultName, String ext, List<ChatMessage> messages, String mdText, boolean isPdf) {
        Context ctx = getContext();

        // 苹果风格圆角卡片布局
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cn.lineai.ui.theme.LineTheme.rounded(ctx, 0xFF2A2A3E, 18));
        int pad = cn.lineai.ui.theme.LineTheme.dp(ctx, 20);
        card.setPadding(pad, pad, pad, pad);

        // 提示文字
        TextView hint = cn.lineai.ui.theme.LineTheme.text(ctx, "后缀自动添加: " + ext, cn.lineai.ui.theme.LineTheme.FONT_XS, 0xFF999999, android.graphics.Typeface.NORMAL);
        card.addView(hint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 输入框（圆角）
        android.widget.EditText input = new android.widget.EditText(ctx);
        input.setText(defaultName);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setHint("文件名");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF666666);
        input.setTextSize(15);
        input.setBackground(cn.lineai.ui.theme.LineTheme.roundedStroke(ctx, 0xFF1E1E30, 12, 0xFF444466));
        int inputPad = cn.lineai.ui.theme.LineTheme.dp(ctx, 12);
        input.setPadding(inputPad, inputPad, inputPad, inputPad);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = cn.lineai.ui.theme.LineTheme.dp(ctx, 12);
        card.addView(input, inputLp);

        // AI取名按钮（圆角胶囊）
        TextView aiBtn = new TextView(ctx);
        aiBtn.setText("✨ AI 取名");
        aiBtn.setTextColor(0xFFFFFFFF);
        aiBtn.setTextSize(13);
        aiBtn.setGravity(android.view.Gravity.CENTER);
        aiBtn.setBackground(cn.lineai.ui.theme.LineTheme.rounded(ctx, 0xFF5B4FCF, 14));
        int btnPad = cn.lineai.ui.theme.LineTheme.dp(ctx, 8);
        aiBtn.setPadding(btnPad * 2, btnPad, btnPad * 2, btnPad);
        aiBtn.setClickable(true);
        aiBtn.setFocusable(true);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = cn.lineai.ui.theme.LineTheme.dp(ctx, 10);
        card.addView(aiBtn, btnLp);

        // AI取名点击逻辑
        aiBtn.setOnClickListener(v -> {
            aiBtn.setText("✨ 思考中...");
            aiBtn.setEnabled(false);
            // 简单取名：根据内容第一行生成文件名
            String summary = generateSmartFileName(mdText);
            input.setText(summary);
            input.selectAll();
            aiBtn.setText("✨ AI 取名");
            aiBtn.setEnabled(true);
        });

        // 包裹padding
        FrameLayout wrapper = new FrameLayout(ctx);
        int wrapPad = cn.lineai.ui.theme.LineTheme.dp(ctx, 16);
        wrapper.setPadding(wrapPad, wrapPad, wrapPad, 0);
        wrapper.addView(card);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("文件名称")
                .setView(wrapper)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = defaultName;
                    // 清理非法文件名字符
                    name = name.replaceAll("[/\\\\:*?\"<>|]", "_");
                    if (isPdf) {
                        shareAsPdfWithName(messages, name + ext);
                    } else {
                        shareAsFile(mdText, name + ext, "application/octet-stream");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String generateSmartFileName(String content) {
        if (content == null || content.isEmpty()) return "chat";
        // 取第一行有意义的内容作为文件名
        String[] lines = content.split("\n");
        for (String line : lines) {
            String clean = line.replaceAll("^[#>\\-*\\s]+", "").trim();
            if (clean.length() >= 4) {
                if (clean.length() > 20) clean = clean.substring(0, 20);
                return clean.replaceAll("[/\\\\:*?\"<>|]", "_");
            }
        }
        return "chat_" + System.currentTimeMillis() % 10000;
    }

    private void shareAsPdfWithName(List<ChatMessage> messages, String fileName) {
        shareAsPdfInternal(messages, fileName);
    }

    private void shareAsChatImage(List<ChatMessage> messages) {
        try {
            int imgWidth = 720;
            int padding = 32;
            int bubbleMaxWidth = imgWidth - padding * 2 - 60;
            int fontSize = 28;
            int nameFontSize = 22;
            int bubblePadH = 24;
            int bubblePadV = 18;
            int bubbleRadius = 24;
            int spacing = 20;

            android.graphics.Paint textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(fontSize);
            textPaint.setColor(0xFFE0E0E0);

            android.graphics.Paint namePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(nameFontSize);
            namePaint.setColor(0xFF999999);

            // 第一遍：计算总高度
            int totalHeight = padding; // top padding
            java.util.List<String[]> wrappedMessages = new java.util.ArrayList<>();
            for (ChatMessage msg : messages) {
                String content = msg.getContent() == null ? "" : msg.getContent();
                String[] lines = wrapText(content, textPaint, bubbleMaxWidth - bubblePadH * 2);
                wrappedMessages.add(lines);
                totalHeight += nameFontSize + 8; // name
                totalHeight += bubblePadV * 2 + lines.length * (fontSize + 6); // bubble
                totalHeight += spacing;
            }
            totalHeight += 50; // footer
            totalHeight += padding; // bottom padding

            // 创建 Bitmap
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(imgWidth, totalHeight, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            canvas.drawColor(0xFF1A1A2E); // 深色背景

            android.graphics.Paint bubblePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.RectF rect = new android.graphics.RectF();

            int y = padding;
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                boolean isUser = msg.getRole() == ChatMessage.Role.USER;
                String name = isUser ? "我" : "AI";
                String[] lines = wrappedMessages.get(i);

                int bubbleWidth = 0;
                for (String line : lines) {
                    bubbleWidth = Math.max(bubbleWidth, (int) textPaint.measureText(line));
                }
                bubbleWidth += bubblePadH * 2;
                bubbleWidth = Math.min(bubbleWidth, bubbleMaxWidth);
                int bubbleHeight = bubblePadV * 2 + lines.length * (fontSize + 6);

                int bubbleLeft;
                if (isUser) {
                    // 用户消息靠右
                    bubbleLeft = imgWidth - padding - bubbleWidth;
                    canvas.drawText(name, bubbleLeft + bubbleWidth - namePaint.measureText(name), y + nameFontSize, namePaint);
                } else {
                    // AI消息靠左
                    bubbleLeft = padding;
                    canvas.drawText(name, bubbleLeft, y + nameFontSize, namePaint);
                }
                y += nameFontSize + 8;

                // 画气泡背景
                bubblePaint.setColor(isUser ? 0xFF3B5998 : 0xFF2D2D44);
                rect.set(bubbleLeft, y, bubbleLeft + bubbleWidth, y + bubbleHeight);
                canvas.drawRoundRect(rect, bubbleRadius, bubbleRadius, bubblePaint);

                // 画文字
                textPaint.setColor(0xFFE8E8E8);
                int textY = y + bubblePadV + fontSize;
                for (String line : lines) {
                    canvas.drawText(line, bubbleLeft + bubblePadH, textY, textPaint);
                    textY += fontSize + 6;
                }
                y += bubbleHeight + spacing;
            }

            // Footer
            namePaint.setColor(0xFF666666);
            namePaint.setTextSize(20);
            String footer = "—— 来自 LineCode Pro";
            canvas.drawText(footer, (imgWidth - namePaint.measureText(footer)) / 2, y + 30, namePaint);

            // 保存分享
            java.io.File shareDir = new java.io.File(getContext().getCacheDir(), "share");
            shareDir.mkdirs();
            java.io.File imgFile = new java.io.File(shareDir, "chat_screenshot.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(imgFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            bitmap.recycle();

            Uri uri = cn.lineai.log.ShareFileProvider.uriFor(getContext(), imgFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.setClipData(android.content.ClipData.newRawUri("", uri));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(Intent.createChooser(shareIntent, "分享对话截图"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "截图生成失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String[] wrapText(String text, android.graphics.Paint paint, int maxWidth) {
        java.util.List<String> result = new java.util.ArrayList<>();
        String[] paragraphs = text.split("\n");
        for (String para : paragraphs) {
            if (para.length() == 0) {
                result.add("");
                continue;
            }
            int start = 0;
            while (start < para.length()) {
                int end = para.length();
                while (paint.measureText(para, start, end) > maxWidth && end > start + 1) {
                    end--;
                }
                result.add(para.substring(start, end));
                start = end;
            }
        }
        return result.toArray(new String[0]);
    }

    private void shareAsPdf(List<ChatMessage> messages) {
        shareAsPdfInternal(messages, "chat_export.pdf");
    }

    private void shareAsPdfInternal(List<ChatMessage> messages, String fileName) {
        try {
            android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
            int pageWidth = 595; // A4
            int pageHeight = 842;
            int margin = 40;
            int y = margin;
            int pageNum = 1;
            android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
            android.graphics.pdf.PdfDocument.Page page = doc.startPage(pageInfo);
            android.graphics.Canvas canvas = page.getCanvas();
            android.graphics.Paint titlePaint = new android.graphics.Paint();
            titlePaint.setTextSize(14); titlePaint.setColor(0xFF333333); titlePaint.setAntiAlias(true);
            titlePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setTextSize(11); textPaint.setColor(0xFF444444); textPaint.setAntiAlias(true);
            android.graphics.Paint linePaint = new android.graphics.Paint();
            linePaint.setColor(0xFFCCCCCC); linePaint.setStrokeWidth(1);

            for (ChatMessage msg : messages) {
                String role = msg.getRole() == ChatMessage.Role.USER ? "我" : "AI";
                String content = msg.getContent() == null ? "" : msg.getContent();
                // Title
                if (y + 20 > pageHeight - margin) {
                    doc.finishPage(page); pageNum++;
                    pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                    page = doc.startPage(pageInfo); canvas = page.getCanvas(); y = margin;
                }
                canvas.drawText(role, margin, y + 14, titlePaint); y += 22;
                // Content lines
                String[] lines = content.split("\n");
                for (String line : lines) {
                    // Word wrap
                    int maxChars = (pageWidth - margin * 2) / 6;
                    while (line.length() > 0) {
                        String seg = line.length() > maxChars ? line.substring(0, maxChars) : line;
                        line = line.length() > maxChars ? line.substring(maxChars) : "";
                        if (y + 15 > pageHeight - margin) {
                            doc.finishPage(page); pageNum++;
                            pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                            page = doc.startPage(pageInfo); canvas = page.getCanvas(); y = margin;
                        }
                        canvas.drawText(seg, margin, y + 11, textPaint); y += 14;
                    }
                }
                y += 8;
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint); y += 12;
            }
            // Footer
            if (y + 15 > pageHeight - margin) {
                doc.finishPage(page); pageNum++;
                pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                page = doc.startPage(pageInfo); canvas = page.getCanvas(); y = margin;
            }
            textPaint.setColor(0xFF999999);
            canvas.drawText("—— 来自 LineCode Pro", margin, y + 11, textPaint);
            doc.finishPage(page);

            java.io.File shareDir = new java.io.File(getContext().getCacheDir(), "share");
            shareDir.mkdirs();
            java.io.File pdfFile = new java.io.File(shareDir, fileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(pdfFile);
            doc.writeTo(fos);
            fos.close(); doc.close();
            shareAsFile(null, fileName, "application/pdf");
        } catch (Exception e) {
            Toast.makeText(getContext(), "PDF创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAsFile(String content, String fileName, String mimeType) {
        try {
            java.io.File shareDir = new java.io.File(getContext().getCacheDir(), "share");
            shareDir.mkdirs();
            java.io.File file = new java.io.File(shareDir, fileName);
            if (content != null) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                fos.write(content.getBytes("UTF-8"));
                fos.close();
            }
            if (!file.isFile() || file.length() == 0) {
                Toast.makeText(getContext(), "文件生成失败", Toast.LENGTH_SHORT).show();
                return;
            }
            launchShareIntent(file, mimeType);
        } catch (Exception e) {
            Toast.makeText(getContext(), "文件分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchShareIntent(java.io.File file, String mimeType) {
        android.net.Uri uri = cn.lineai.log.ShareFileProvider.uriFor(getContext(), file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.setClipData(ClipData.newRawUri("", uri));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getContext().startActivity(Intent.createChooser(shareIntent, "分享文件"));
        // 延迟6秒显示悬浮窗（等用户选好友发送完）
        pendingShareFile = file;
        pendingShareMime = mimeType;
        postDelayed(() -> showFloatingShareButton(file, mimeType), 6000);
    }

    private java.io.File pendingShareFile = null;
    private String pendingShareMime = null;
    private android.view.WindowManager floatingWindowManager = null;
    private android.view.View floatingView = null;

    private void showFloatingShareButton(java.io.File file, String mimeType) {
        removeFloatingButton();
        Context ctx = getContext();
        // 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(ctx)) {
            // 没权限，引导用户开启
            Toast.makeText(ctx, "请开启悬浮窗权限，分享后可在QQ/微信界面操作", Toast.LENGTH_LONG).show();
            try {
                Intent overlayIntent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.getPackageName()));
                overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(overlayIntent);
            } catch (Exception ignored) {}
            return;
        }
        floatingWindowManager = (android.view.WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);

        // 创建悬浮按钮
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(android.view.Gravity.CENTER);
        panel.setBackground(cn.lineai.ui.theme.LineTheme.rounded(ctx, 0xEE2A2A3E, 22));
        int p = cn.lineai.ui.theme.LineTheme.dp(ctx, 10);
        panel.setPadding(p * 2, p, p * 2, p);

        TextView btnAgain = new TextView(ctx);
        btnAgain.setText("📤 再发");
        btnAgain.setTextColor(0xFFFFFFFF);
        btnAgain.setTextSize(13);
        btnAgain.setPadding(p, p / 2, p, p / 2);
        btnAgain.setBackground(cn.lineai.ui.theme.LineTheme.rounded(ctx, 0xFF5B4FCF, 14));
        btnAgain.setOnClickListener(v -> {
            removeFloatingButton();
            launchShareIntent(file, mimeType);
        });
        panel.addView(btnAgain);

        TextView btnBack = new TextView(ctx);
        btnBack.setText("← 返回");
        btnBack.setTextColor(0xFFCCCCCC);
        btnBack.setTextSize(13);
        btnBack.setPadding(p, p / 2, p, p / 2);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = p;
        btnBack.setOnClickListener(v -> {
            removeFloatingButton();
            // 把APP拉回前台
            Intent bring = new Intent(ctx, ctx.getClass());
            bring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(bring);
        });
        panel.addView(btnBack, blp);

        TextView btnClose = new TextView(ctx);
        btnClose.setText("✕");
        btnClose.setTextColor(0xFF999999);
        btnClose.setTextSize(14);
        btnClose.setPadding(p, p / 2, 0, p / 2);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = p;
        btnClose.setOnClickListener(v -> removeFloatingButton());
        panel.addView(btnClose, clp);

        floatingView = panel;

        android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        params.y = cn.lineai.ui.theme.LineTheme.dp(ctx, 80);
        floatingWindowManager.addView(floatingView, params);
    }

    private void removeFloatingButton() {
        if (floatingView != null && floatingWindowManager != null) {
            try {
                floatingWindowManager.removeView(floatingView);
            } catch (Exception ignored) {}
            floatingView = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && pendingShareFile != null) {
            removeFloatingButton();
            pendingShareFile = null;
            pendingShareMime = null;
        }
    }

    private void showAfterShareDialog(java.io.File file, String mimeType) {
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("分享完成")
                .setMessage("还要发给其他人吗？")
                .setPositiveButton("再发一次", (d, w) -> launchShareIntent(file, mimeType))
                .setNeutralButton("留在那边聊", (d, w) -> {
                    // 不做任何事，用户可以通过任务切换回到分享目标APP
                })
                .setNegativeButton("返回对话", null)
                .show();
    }

    private void triggerTextSelection(android.view.View messageView, String content) {
        // Show content in a dialog with a selectable EditText (guaranteed to work on all devices)
        Context ctx = getContext();
        android.widget.EditText et = new android.widget.EditText(ctx);
        et.setText(content);
        et.setTextSize(15);
        et.setTextColor(0xFFFFFFFF);
        et.setBackgroundColor(0xFF1E1E2E);
        et.setPadding(30, 30, 30, 30);
        et.setFocusable(true);
        et.setFocusableInTouchMode(true);
        et.setKeyListener(null); // readonly but selectable
        et.setTextIsSelectable(true);
        // Select all by default
        et.selectAll();
        android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
        sv.addView(et);
        new android.app.AlertDialog.Builder(ctx)
                .setTitle("长按选中文字")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    private android.widget.TextView findFirstTextView(android.view.View view) {
        if (view instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) view;
            if (tv.getText().length() > 0) return tv;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.widget.TextView found = findFirstTextView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void shareMessageAsMarkdown(ChatMessage message) {
        String role = message.getRole() == ChatMessage.Role.USER ? "我" : "AI";
        String content = message.getContent();
        String[] options = {"对话截图(图片)", "PDF 文件", "Markdown 文件(.md)", "纯文本"};
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("分享格式")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        java.util.List<ChatMessage> single = new java.util.ArrayList<>();
                        single.add(message);
                        shareAsChatImage(single);
                    } else if (which == 1) {
                        java.util.List<ChatMessage> single = new java.util.ArrayList<>();
                        single.add(message);
                        String mdText = "## " + role + "\n\n" + content + "\n\n---\n*—— 来自 LineCode Pro*";
                        askFileNameAndShare("message_" + role, ".pdf", single, mdText, true);
                    } else if (which == 2) {
                        String text = "## " + role + "\n\n" + content + "\n\n---\n*—— 来自 LineCode Pro*";
                        askFileNameAndShare("message_" + role, ".md", null, text, false);
                    } else {
                        String text = role + "：\n" + content + "\n\n—— 来自 LineCode Pro";
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, role + "的消息 - LineCode Pro");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
                        getContext().startActivity(Intent.createChooser(shareIntent, "分享消息"));
                    }
                })
                .show();
    }

    @Override
    public void exportCurrentChat() {
        if (lastState == null || lastState.getMessages().isEmpty()) {
            Toast.makeText(getContext(), "当前没有对话内容", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder md = new StringBuilder();
        md.append("# 对话导出\n\n");
        for (ChatMessage msg : lastState.getMessages()) {
            String role = msg.getRole() == ChatMessage.Role.USER ? "我" : "AI";
            String content = msg.getContent() == null ? "" : msg.getContent();
            md.append("## ").append(role).append("\n\n");
            md.append(content).append("\n\n---\n\n");
        }
        String text = md.toString();
        // Save to Download folder
        try {
            java.io.File dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            String fileName = "chat_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(new java.util.Date()) + ".md";
            java.io.File file = new java.io.File(dlDir, fileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(text.getBytes("UTF-8"));
            fos.close();
            Toast.makeText(getContext(), "已保存到 Download/" + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            // ignore save error
        }
        // Also share as text
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "对话导出");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        getContext().startActivity(Intent.createChooser(shareIntent, "导出对话"));
    }

    @Override
    public void enterMessageSelectMode() {
        // Multi-select mode: delegate to exportCurrentChat for now
        exportCurrentChat();
    }

    @Override
    public void showTextSelectionTest() {
        Context ctx = getContext();
        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);
        scroll.addView(container);

        String testText = "这是一段测试文字，请长按试试能否选中。This is test text for selection.";

        // Test 1
        addTestLabel(container, ctx, "\u2460 setTextIsSelectable(true):");
        android.widget.TextView tv1 = new android.widget.TextView(ctx);
        tv1.setText(testText); tv1.setTextSize(16); tv1.setTextIsSelectable(true);
        tv1.setPadding(20,20,20,20); tv1.setBackgroundColor(0xFF2A2A3A); tv1.setTextColor(0xFFFFFFFF);
        container.addView(tv1, testLp());

        // Test 2
        addTestLabel(container, ctx, "\u2461 EditText (readonly):");
        android.widget.EditText tv2 = new android.widget.EditText(ctx);
        tv2.setText(testText); tv2.setTextSize(16); tv2.setFocusable(true); tv2.setFocusableInTouchMode(true);
        tv2.setKeyListener(null);
        tv2.setPadding(20,20,20,20); tv2.setBackgroundColor(0xFF2A2A3A); tv2.setTextColor(0xFFFFFFFF);
        container.addView(tv2, testLp());

        // Test 3
        addTestLabel(container, ctx, "\u2462 TextView + longClickable:");
        android.widget.TextView tv3 = new android.widget.TextView(ctx);
        tv3.setText(testText); tv3.setTextSize(16); tv3.setTextIsSelectable(true);
        tv3.setLongClickable(true); tv3.setFocusable(true); tv3.setFocusableInTouchMode(true);
        tv3.setPadding(20,20,20,20); tv3.setBackgroundColor(0xFF2A2A3A); tv3.setTextColor(0xFFFFFFFF);
        container.addView(tv3, testLp());

        // Test 4
        addTestLabel(container, ctx, "\u2463 WebView (HTML):");
        android.webkit.WebView wv = new android.webkit.WebView(ctx);
        wv.loadData("<p style='color:white;font-size:16px;user-select:text;-webkit-user-select:text;'>" + testText + "</p>", "text/html", "utf-8");
        wv.getSettings().setJavaScriptEnabled(false);
        wv.setBackgroundColor(0xFF2A2A3A);
        LinearLayout.LayoutParams wvp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200);
        wvp.topMargin = 10; wvp.bottomMargin = 30;
        container.addView(wv, wvp);

        // Test 5
        addTestLabel(container, ctx, "\u2464 Selectable + clickable=false:");
        android.widget.TextView tv5 = new android.widget.TextView(ctx);
        tv5.setText(testText); tv5.setTextSize(16); tv5.setTextIsSelectable(true);
        tv5.setClickable(false); tv5.setLongClickable(true);
        tv5.setPadding(20,20,20,20); tv5.setBackgroundColor(0xFF2A2A3A); tv5.setTextColor(0xFFFFFFFF);
        container.addView(tv5, testLp());

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("文本选中测试 - 长按每个区域")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void addTestLabel(LinearLayout container, Context ctx, String text) {
        android.widget.TextView tv = new android.widget.TextView(ctx);
        tv.setText(text); tv.setTextColor(0xFFAABBCC); tv.setTextSize(13); tv.setPadding(0,20,0,5);
        container.addView(tv);
    }

    private LinearLayout.LayoutParams testLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = 10; p.bottomMargin = 30;
        return p;
    }

    public void showBatchImportDialog(MainUiController controller) {
        Context context = getContext();
        android.widget.EditText urlInput = new android.widget.EditText(context);
        urlInput.setTextColor(cn.lineai.ui.theme.LineTheme.TEXT);
        urlInput.setHintTextColor(cn.lineai.ui.theme.LineTheme.TEXT_TERTIARY);
        urlInput.setHint("API Base URL, 如 http://10.0.2.2:8000");
        urlInput.setSingleLine(true);
        urlInput.setSelectAllOnFocus(true);
        cn.lineai.ui.theme.LineTheme.padding(urlInput, cn.lineai.ui.theme.LineTheme.MD, cn.lineai.ui.theme.LineTheme.SM, cn.lineai.ui.theme.LineTheme.MD, cn.lineai.ui.theme.LineTheme.SM);

        new android.app.AlertDialog.Builder(context)
                .setTitle("一键导入模型")
                .setMessage("输入 API 地址，自动拉取所有可用模型")
                .setView(urlInput)
                .setPositiveButton("导入", (d, w) -> {
                    String baseUrl = urlInput.getText().toString().trim();
                    if (baseUrl.length() == 0) return;
                    doBatchImport(baseUrl, controller);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doBatchImport(String baseUrl, MainUiController controller) {
        new Thread(() -> {
            try {
                String modelsUrl = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(modelsUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                java.io.InputStream is = conn.getInputStream();
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                is.close();
                org.json.JSONObject resp = new org.json.JSONObject(bos.toString("UTF-8"));
                org.json.JSONArray data = resp.getJSONArray("data");
                java.util.List<cn.lineai.model.ModelConfig> toImport = new java.util.ArrayList<>();
                java.util.List<cn.lineai.model.ModelConfig> existing = controller.getModels();
                java.util.Set<String> existingKeys = new java.util.HashSet<>();
                for (cn.lineai.model.ModelConfig m : existing) {
                    existingKeys.add(m.getBaseUrl() + "::" + m.getModelId());
                }
                for (int i = 0; i < data.length(); i++) {
                    String modelId = data.getJSONObject(i).getString("id");
                    if (existingKeys.contains(baseUrl + "::" + modelId)) continue;
                    cn.lineai.model.ModelConfig cfg = new cn.lineai.model.ModelConfig(
                            "", modelId, cn.lineai.model.ModelProtocolType.OPENAI_COMPATIBLE,
                            "ChatRelay", baseUrl, "any", modelId);
                    toImport.add(cfg);
                }
                final int count = toImport.size();
                // Save models on main thread
                post(() -> {
                    for (cn.lineai.model.ModelConfig cfg : toImport) {
                        controller.onModelSaved(cfg);
                    }
                    Toast.makeText(getContext(), "已导入 " + count + " 个模型", Toast.LENGTH_SHORT).show();
                    controller.onSettingsItemSelected("models");
                });
            } catch (Exception e) {
                post(() -> Toast.makeText(getContext(), "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
