package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;
import cn.lineai.context.MemoryExtractionService;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.FileTreeStore;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.IpcFileTreeStore;
import cn.lineai.data.repository.IpcProviderStore;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.ProjectRecord;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.mvp.agent.AgentExecutionController;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import cn.lineai.model.SheetOption;
import cn.lineai.model.SshConfig;
import cn.lineai.ssh.SshService;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.builtin.HttpServerTool;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.StoragePermissionManager;
import java.util.ArrayList;
import java.util.List;

public final class MainCoordinator extends MainCoordinatorDelegates {
    private String agentTerminatedMessage() {
        return context.getString(R.string.message_agent_terminated);
    }

    private final Context context;

    private final ChatSessionStore chatSessionStore = new ChatSessionStore();
    private final ArrayList<ChatMessage> messages = chatSessionStore.mutableMessages();
    private final ScreenNavigationController screenNavigationController = new ScreenNavigationController();
    private final MainThreadDispatcher mainThread;
    private final BackgroundTaskRunner backgroundTasks;
    private final ChatUiStateAssembler chatUiStateAssembler;
    private final ToolMessageController toolMessageController;
    private final ToolReviewController toolReviewController;
    private final ConversationPersistenceController conversationPersistenceController;
    private final ExtensionDraftController extensionDraftController;
    private final ExtensionManagementController extensionManagementController;
    private final ModelPromptController modelPromptController;
    private final DirectoryPickerController directoryPickerController;
    private final StorageMaintenanceController storageMaintenanceController;
    private final ContextCompactionController contextCompactionController;
    private final IpcProviderController ipcProviderController;
    private final GenerationController generationController = new GenerationController();
    private final GenerationLifecycleController generationLifecycleController;
    private final GenerationFlowController generationFlowController;
    private final ChatInteractionController chatInteractionController;
    private final ModelInteractionController modelInteractionController;
    private final OverlayActionController overlayActionController;
    private final ModelManagementController modelManagementController;
    private final SettingsManagementController settingsManagementController;
    private final SshFileTreeController sshFileTreeController;
    private final IpcFileTreeController ipcFileTreeController;
    private FileTreeInteractionController fileTreeInteractionController;
    private final FileOperationController fileOperationController;
    private final PermissionModeController permissionModeController;
    private final ProjectWorkspaceController projectWorkspaceController;
    private final ModelStore modelRepository;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ChatModeRepository chatModeRepository;
    private final InputSettingsRepository inputSettingsRepository;
    private final OutputSettingsRepository outputSettingsRepository;
    private final ThemeSettingsRepository themeSettingsRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ConversationStore conversationRepository;
    private final ProjectStore projectRepository;
    private final LearningContextStore learningContextRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final ToolSettingsStore toolSettingsRepository;
    private final ExtensionStore extensionRepository;
    private final IpcProviderStore ipcProviderRepository;
    private final cn.lineai.ipc.IpcProviderManager ipcProviderManager;
    private final DiffStore diffRepository;
    private final FileTreeStore fileTreeRepository;
    private final IpcFileTreeStore ipcFileTreeRepository;
    private final SshService sshService;
    private final SshFileTreeStore sshFileTreeRepository;
    private final ContextManager contextManager;
    private final ContextCompactionService contextCompactionService;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ToolExecutionCoordinator toolExecutionCoordinator;
    private final SystemPromptProvider systemPromptProvider;
    private final StoragePermissionManager storagePermissionManager;
    private final SafPathResolver safPathResolver;
    private final LineCodeArchiveController lineCodeArchiveController;
    private final AgentExecutionController agentExecutionController;
    private final cn.lineai.state.TodoStateStore todoStateStore;
    private MainContract.View view;
    private final ScreenNavigationController.Host navigationHost = new ScreenNavigationController.Host() {
        @Override
        public void hideOverlays() {
            if (view != null) {
                view.hideOverlays();
            }
        }

        @Override
        public void showScreen(String screenId) {
            if (view != null) {
                view.showScreen(screenId);
            }
        }

        @Override
        public void showScreen(String screenId, boolean forward) {
            if (view instanceof cn.lineai.ui.MainChatView) {
                ((cn.lineai.ui.MainChatView) view).showScreen(screenId, forward);
            } else if (view != null) {
                view.showScreen(screenId);
            }
        }

        @Override
        public void showScreen(String screenId, boolean forward, boolean animate) {
            if (view instanceof cn.lineai.ui.MainChatView) {
                ((cn.lineai.ui.MainChatView) view).showScreen(screenId, forward, animate);
            } else if (view != null) {
                view.showScreen(screenId);
            }
        }

        @Override
        public void showChatScreen() {
            if (view != null) {
                view.showChatScreen();
            }
        }
    };
    private final ProjectRuntimeState projectState = new ProjectRuntimeState();
    private final AttachmentPickerCoordinator attachmentPickerController;

    public MainCoordinator(Context context) {
        this(new MainDependencies(context));
    }

    MainCoordinator(MainDependencies dependencies) {
        this.context = dependencies.context;
        modelRepository = dependencies.modelRepository;
        modelManagementController = new ModelManagementController(
                modelRepository,
                new ModelManagementController.Host() {
                    @Override
                    public void refreshModelsScreen() {
                        refreshVisibleScreen("models");
                    }

                    @Override
                    public void returnToModelsScreen() {
                        returnToScreen("models");
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        aiBehaviorSettingsRepository = dependencies.aiBehaviorSettingsRepository;
        chatModeRepository = dependencies.chatModeRepository;
        inputSettingsRepository = dependencies.inputSettingsRepository;
        outputSettingsRepository = dependencies.outputSettingsRepository;
        themeSettingsRepository = dependencies.themeSettingsRepository;
        promptTemplateRepository = dependencies.promptTemplateRepository;
        conversationRepository = dependencies.conversationRepository;
        projectRepository = dependencies.projectRepository;
        learningContextRepository = dependencies.learningContextRepository;
        memoryExtractionService = dependencies.memoryExtractionService;
        toolSettingsRepository = dependencies.toolSettingsRepository;
        extensionRepository = dependencies.extensionRepository;
        ipcProviderRepository = dependencies.ipcProviderRepository;
        ipcProviderManager = dependencies.ipcProviderManager;
        diffRepository = dependencies.diffRepository;
        fileTreeRepository = dependencies.fileTreeRepository;
        ipcFileTreeRepository = dependencies.ipcFileTreeRepository;
        sshService = dependencies.sshService;
        sshFileTreeRepository = dependencies.sshFileTreeRepository;
        contextManager = dependencies.contextManager;
        contextCompactionService = dependencies.contextCompactionService;
        modelClient = dependencies.modelClient;
        toolRegistry = dependencies.toolRegistry;
        toolExecutor = dependencies.toolExecutor;
        toolExecutionCoordinator = dependencies.toolExecutionCoordinator;
        systemPromptProvider = dependencies.systemPromptProvider;
        storagePermissionManager = dependencies.storagePermissionManager;
        safPathResolver = dependencies.safPathResolver;
        mainThread = dependencies.mainThreadDispatcher;
        backgroundTasks = dependencies.backgroundTaskRunner;
        generationLifecycleController = new GenerationLifecycleController(context, messages);
        sshFileTreeController = new SshFileTreeController(
                sshFileTreeRepository,
                new SshFileTreeController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public String projectLabel() {
                        return projectState.label();
                    }

                    @Override
                    public boolean isExpanded(String path) {
                        return fileTreeInteractionController.isExpanded(path);
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        fileTreeInteractionController.addExpandedPath(path);
                    }

                    @Override
                    public void setProjectPathFromSshRoot(String path) {
                        projectState.setPathFromRemoteRoot(path);
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        ipcFileTreeController = new IpcFileTreeController(
                ipcFileTreeRepository,
                new IpcFileTreeController.Host() {
                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public String projectLabel() {
                        return projectState.label();
                    }

                    @Override
                    public boolean isExpanded(String path) {
                        return fileTreeInteractionController.isExpanded(path);
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        fileTreeInteractionController.addExpandedPath(path);
                    }

                    @Override
                    public void setProjectPathFromIpcRoot(String path) {
                        projectState.setPathFromRemoteRoot(path);
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        fileTreeInteractionController = new FileTreeInteractionController(
                fileTreeRepository,
                sshFileTreeController,
                ipcFileTreeController,
                new FileTreeInteractionController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public String parentPath(String path) {
                        return MainCoordinator.this.parentPath(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        fileOperationController = new FileOperationController(
                fileTreeRepository,
                sshFileTreeRepository,
                ipcFileTreeRepository,
                new FileOperationController.Host() {
                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public void showInputDialog(String title, String message, String initialValue, String actionId) {
                        if (view != null) {
                            view.showInputDialog(title, message, initialValue, actionId);
                        }
                    }

                    @Override
                    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
                        if (view != null) {
                            view.showConfirmationDialog(title, message, confirmLabel, danger, actionId);
                        }
                    }

                    @Override
                    public void showFileActionDialog(String title, String subtitle, ArrayList<SheetOption> options) {
                        if (view != null) {
                            view.showFileActionDialog(title, subtitle, options);
                        }
                    }

                    @Override
                    public void addExpandedPath(String path) {
                        fileTreeInteractionController.addExpandedPath(path);
                    }

                    @Override
                    public void refreshSshDirectoryAfterFileOperation(String path) {
                        sshFileTreeController.refreshDirectoryAfterFileOperation(path);
                    }

                    @Override
                    public void refreshIpcDirectoryAfterFileOperation(String path) {
                        ipcFileTreeController.refreshDirectoryAfterFileOperation(path);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }

                    @Override
                    public String basename(String path) {
                        return MainCoordinator.this.basename(path);
                    }

                    @Override
                    public String parentPath(String path) {
                        return MainCoordinator.this.parentPath(path);
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        permissionModeController = new PermissionModeController(
                toolSettingsRepository,
                chatModeRepository,
                new PermissionModeController.Host() {
                    @Override
                    public boolean hasExternalStorageAccess() {
                        return storagePermissionManager.hasExternalStorageAccess();
                    }

                    @Override
                    public String storagePermissionMessage() {
                        return storagePermissionManager.permissionDeniedMessage();
                    }

                    @Override
                    public void showPermissionSheet(ArrayList<SheetOption> options) {
                        if (view != null) {
                            view.showSheet(context.getString(R.string.sheet_title_permissions), options);
                        }
                    }
                }
        );
        projectWorkspaceController = new ProjectWorkspaceController(
                context,
                projectRepository,
                toolSettingsRepository,
                sshFileTreeRepository,
                storagePermissionManager,
                safPathResolver,
                new ProjectWorkspaceController.Host() {
                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public boolean isTermuxSshHost() {
                        return MainCoordinator.this.isTermuxSshHost();
                    }

                    @Override
                    public void applyProject(ProjectRecord project) {
                        MainCoordinator.this.applyProject(project);
                    }

                    @Override
                    public void resetTodoState() {
                        MainCoordinator.this.resetTodoState();
                    }

                    @Override
                    public void requestSshFileTreeLoad(boolean force) {
                        MainCoordinator.this.requestSshFileTreeLoad(force);
                    }

                    @Override
                    public void showSheet(String title, List<SheetOption> options) {
                        if (view != null) {
                            view.showSheet(title, options);
                        }
                    }

                    @Override
                    public void showInputDialog(String title, String message, String initialValue, String actionId) {
                        if (view != null) {
                            view.showInputDialog(title, message, initialValue, actionId);
                        }
                    }

                    @Override
                    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
                        if (view != null) {
                            view.showConfirmationDialog(title, message, confirmLabel, danger, actionId);
                        }
                    }

                    @Override
                    public void hideOverlays() {
                        if (view != null) {
                            view.hideOverlays();
                        }
                    }

                    @Override
                    public void openExternalProjectPicker() {
                        if (view != null) {
                            view.openExternalProjectPicker();
                        }
                    }

                    @Override
                    public void openManageAllFilesPermissionSettings() {
                        if (view != null) {
                            view.openManageAllFilesPermissionSettings();
                        }
                    }

                    @Override
                    public void requestLegacyStoragePermissions() {
                        if (view != null) {
                            view.requestLegacyStoragePermissions();
                        }
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        settingsManagementController = new SettingsManagementController(
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                promptTemplateRepository,
                learningContextRepository,
                outputSettingsRepository,
                themeSettingsRepository,
                toolSettingsRepository,
                new SettingsManagementController.Host() {
                    @Override
                    public String currentProjectPath() {
                        return projectState.path();
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void recreateForTheme(String screenId) {
                        if (view != null) {
                            view.recreateForTheme(screenId);
                        }
                    }

                    @Override
                    public void afterMcpExecutionModeChanged(String executionMode) {
                        applyProject(projectRepository.ensureSelectedProjectPath(executionMode));
                        sshFileTreeController.invalidateFileTree();
                        ipcFileTreeController.invalidateFileTree();
                        requestSshFileTreeLoad(true);
                        requestIpcFileTreeLoad(true);
                        refreshVisibleScreen("mcp");
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void refreshMcpScreen() {
                        refreshVisibleScreen("mcp");
                    }

                    @Override
                    public void returnToToolSettings() {
                        returnToScreen("toolSettings");
                    }
                }
        );
        lineCodeArchiveController = new LineCodeArchiveController(
                dependencies.lineCodeArchiveService,
                new LineCodeArchiveController.Host() {
                    @Override
                    public void openExportPicker(String fileName) {
                        if (view != null) {
                            view.openLineCodeExportPicker(fileName);
                        }
                    }

                    @Override
                    public void persistBeforeExport() {
                        persistCurrentConversation();
                    }

                    @Override
                    public void openImportPicker() {
                        if (view != null) {
                            view.openLineCodeImportPicker();
                        }
                    }

                    @Override
                    public void showImportConfirmation(String sourceName) {
                        if (view != null) {
                            view.showConfirmationDialog(
                                    "覆盖导入 .linecode",
                                    "将从「" + sourceName + "」恢复数据库、聊天记录、配置和 .linecode 工作区文件。当前本机数据会被覆盖。",
                                    context.getString(R.string.common_confirm),
                                    true,
                                    "data:import_linecode"
                            );
                        }
                    }

                    @Override
                    public void beforeImport() {
                        generationLifecycleController.cancelActiveGeneration();
                        chatSessionStore.setStreaming(false);
                        generationLifecycleController.stopKeepAlive();
                    }

                    @Override
                    public void afterImport() {
                        reloadAfterLineCodeImport();
                    }

                    @Override
                    public void showNotice(String text) {
                        MainCoordinator.this.showNotice(text);
                    }
                },
                backgroundTasks::execute,
                mainThread::post
        );
        todoStateStore = dependencies.todoStateStore;
        chatUiStateAssembler = new ChatUiStateAssembler(
                modelRepository,
                aiBehaviorSettingsRepository,
                inputSettingsRepository,
                outputSettingsRepository,
                contextManager
        );
        modelInteractionController = new ModelInteractionController(
                context,
                modelRepository,
                modelClient,
                backgroundTasks,
                mainThread,
                new ModelInteractionController.Host() {
                    @Override
                    public boolean isStreaming() {
                        return chatSessionStore.isStreaming();
                    }

                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public void showTestResult(String message) {
                        if (view != null) {
                            view.showConfirmationDialog(
                                    context.getString(R.string.screen_model_add_test_result_title),
                                    message,
                                    context.getString(R.string.screen_model_add_test_result_confirm),
                                    false,
                                    "modelTestResult"
                            );
                        }
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        toolMessageController = new ToolMessageController(messages, this::nextId);
        toolReviewController = new ToolReviewController(
                diffRepository,
                toolMessageController,
                backgroundTasks,
                mainThread,
                new ToolReviewController.Host() {
                    @Override
                    public void refreshFileTreeAfterRevert(String filePath) {
                        MainCoordinator.this.refreshFileTreeAfterRevert(filePath);
                    }

                    @Override
                    public void persistCurrentConversation() {
                        MainCoordinator.this.persistCurrentConversation();
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        conversationPersistenceController = new ConversationPersistenceController(
                context,
                chatSessionStore,
                messages,
                conversationRepository,
                aiBehaviorSettingsRepository,
                learningContextRepository,
                new ConversationPersistenceController.Host() {
                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public String defaultConversationTitle(Context context) {
                        return context.getString(R.string.drawer_new_conversation);
                    }

                    @Override
                    public String interruptedGenerationMessage(Context context) {
                        return context.getString(R.string.message_generation_interrupted);
                    }
                }
        );
        extensionDraftController = new ExtensionDraftController(
                modelRepository,
                modelClient,
                toolRegistry,
                toolSettingsRepository,
                extensionRepository
        );
        extensionManagementController = new ExtensionManagementController(
                extensionRepository,
                ipcProviderRepository,
                toolRegistry,
                new ExtensionManagementController.Host() {
                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public void returnToScreen(String screenId) {
                        MainCoordinator.this.returnToScreen(screenId);
                    }

                    @Override
                    public void refreshVisibleScreen(String screenId) {
                        MainCoordinator.this.refreshVisibleScreen(screenId);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        modelPromptController = new ModelPromptController(
                messages,
                chatSessionStore,
                aiBehaviorSettingsRepository,
                chatModeRepository,
                promptTemplateRepository,
                learningContextRepository,
                contextManager,
                modelRepository,
                extensionRepository,
                systemPromptProvider,
                toolSettingsRepository,
                toolRegistry,
                todoStateStore,
                new ModelPromptController.Host() {
                    @Override
                    public String syncModePermission() {
                        return MainCoordinator.this.syncModePermission();
                    }

                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public String projectSource() {
                        return projectState.source();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String interruptedGenerationMessage() {
                        return context.getString(R.string.message_generation_interrupted);
                    }
                }
        );
        directoryPickerController = new DirectoryPickerController(
                fileTreeRepository,
                sshFileTreeRepository,
                backgroundTasks::execute,
                mainThread::post,
                new DirectoryPickerController.Host() {
                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public boolean isTermuxSshHost() {
                        return MainCoordinator.this.isTermuxSshHost();
                    }

                    @Override
                    public void applySelectedProject(String path, boolean ssh) {
                        projectWorkspaceController.applyDirectoryPickerProject(path, ssh);
                    }

                    @Override
                    public void hideDirectoryPicker() {
                        if (view != null) {
                            view.hideDirectoryPicker();
                        }
                    }

                    @Override
                    public void showDirectoryPicker(
                            String title,
                            String subtitle,
                            FileTreeNode tree,
                            String selectedPath,
                            boolean loading,
                            String message
                    ) {
                        if (view != null) {
                            view.showDirectoryPicker(title, subtitle, tree, selectedPath, loading, message);
                        }
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        storageMaintenanceController = new StorageMaintenanceController(
                context,
                messages,
                chatSessionStore,
                conversationRepository,
                new StorageMaintenanceController.Host() {
                    @Override
                    public void clearCurrentConversation() {
                        generationFlowController.clearSessionAutoToolConfirmations();
                        resetTodoState();
                    }

                    @Override
                    public void refreshStorageScreen() {
                        refreshVisibleScreen("storage");
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        contextCompactionController = new ContextCompactionController(
                context,
                messages,
                chatSessionStore,
                modelRepository,
                aiBehaviorSettingsRepository,
                contextCompactionService,
                contextManager,
                backgroundTasks,
                mainThread,
                new ContextCompactionController.Host() {
                    @Override
                    public String nextId() {
                        return MainCoordinator.this.nextId();
                    }

                    @Override
                    public void persistCurrentConversation() {
                        MainCoordinator.this.persistCurrentConversation();
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void showNotice(String message) {
                        MainCoordinator.this.showNotice(message);
                    }

                    @Override
                    public void startInitialModelRequest(
                            int generationId,
                            ModelConfig selectedModel,
                            ModelCancellationToken cancellationToken,
                            String userInput
                    ) {
                        generationFlowController.startInitialModelRequest(generationId, selectedModel, cancellationToken, userInput);
                    }

                    @Override
                    public void startGenerationKeepAlive() {
                        generationLifecycleController.startKeepAlive();
                    }

                    @Override
                    public void stopGenerationKeepAlive() {
                        generationLifecycleController.stopKeepAlive();
                    }

                    @Override
                    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
                        generationLifecycleController.setCurrentCancellationToken(cancellationToken);
                    }

                    @Override
                    public ModelCancellationToken currentCancellationToken() {
                        return generationLifecycleController.currentCancellationToken();
                    }

                    @Override
                    public void showSheet(String title, List<SheetOption> options) {
                        if (view != null) {
                            view.showSheet(title, new ArrayList<>(options));
                        }
                    }
                }
        );
        ipcProviderController = new IpcProviderController(
                context,
                ipcProviderRepository,
                dependencies.ipcProviderScanner,
                ipcProviderManager,
                new IpcProviderController.Host() {
                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public void applyTerminalProviderProjectPath(String path, String label) {
                        projectState.applyTerminalProviderPath(path, label);
                        fileTreeInteractionController.resetToProjectRoot();
                    }

                    @Override
                    public void clearTerminalProviderProjectPath() {
                        projectState.clearTerminalProviderPath();
                        fileTreeInteractionController.clearExpandedPaths();
                    }

                    @Override
                    public void requestIpcFileTreeLoad(boolean force) {
                        MainCoordinator.this.requestIpcFileTreeLoad(force);
                    }

                    @Override
                    public void refreshVisibleScreen(String screenId) {
                        MainCoordinator.this.refreshVisibleScreen(screenId);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        agentExecutionController = new AgentExecutionController(
                modelClient,
                aiBehaviorSettingsRepository,
                (cn.lineai.data.repository.ToolSettingsRepository) toolSettingsRepository,
                toolExecutor,
                toolRegistry,
                (cn.lineai.data.repository.ExtensionRepository) extensionRepository
        );
        generationFlowController = new GenerationFlowController(
                messages,
                chatSessionStore,
                modelClient,
                aiBehaviorSettingsRepository,
                memoryExtractionService,
                extensionRepository,
                toolRegistry,
                toolExecutor,
                toolExecutionCoordinator,
                toolSettingsRepository,
                toolMessageController,
                modelPromptController,
                generationController,
                agentExecutionController,
                todoStateStore,
                mainThread,
                backgroundTasks,
                new GenerationFlowController.Host() {
                    @Override
                    public String nextId() {
                        return MainCoordinator.this.nextId();
                    }

                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public String projectSource() {
                        return projectState.source();
                    }

                    @Override
                    public String currentConversationId() {
                        return chatSessionStore.getCurrentConversationId();
                    }

                    @Override
                    public String syncModePermission() {
                        return MainCoordinator.this.syncModePermission();
                    }

                    @Override
                    public void persistCurrentConversation() {
                        MainCoordinator.this.persistCurrentConversation();
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }

                    @Override
                    public void stopGenerationKeepAlive() {
                        generationLifecycleController.stopKeepAlive();
                    }

                    @Override
                    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
                        generationLifecycleController.setCurrentCancellationToken(cancellationToken);
                    }
                }
        );
        generationLifecycleController.setGenerationFlowController(generationFlowController);
        chatInteractionController = new ChatInteractionController(
                messages,
                chatSessionStore,
                conversationRepository,
                modelRepository,
                chatModeRepository,
                toolSettingsRepository,
                contextCompactionController,
                generationFlowController,
                new ChatInteractionController.Host() {
                    @Override
                    public String nextId() {
                        return MainCoordinator.this.nextId();
                    }

                    @Override
                    public String agentTerminatedMessage() {
                        return MainCoordinator.this.agentTerminatedMessage();
                    }

                    @Override
                    public String syncModePermission() {
                        return MainCoordinator.this.syncModePermission();
                    }

                    @Override
                    public void ensureCurrentConversation() {
                        MainCoordinator.this.ensureCurrentConversation();
                    }

                    @Override
                    public void persistCurrentConversation() {
                        MainCoordinator.this.persistCurrentConversation();
                    }

                    @Override
                    public void loadConversation(String id) {
                        MainCoordinator.this.loadConversation(id);
                    }

                    @Override
                    public void cancelActiveGeneration() {
                        generationLifecycleController.cancelActiveGeneration();
                    }

                    @Override
                    public void startGenerationKeepAlive() {
                        generationLifecycleController.startKeepAlive();
                    }

                    @Override
                    public void stopGenerationKeepAlive() {
                        generationLifecycleController.stopKeepAlive();
                    }

                    @Override
                    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
                        generationLifecycleController.setCurrentCancellationToken(cancellationToken);
                    }

                    @Override
                    public void markStreamingMessagesStopped() {
                        generationLifecycleController.markStreamingMessagesStopped();
                    }

                    @Override
                    public void resetTodoState() {
                        MainCoordinator.this.resetTodoState();
                    }

                    @Override
                    public void hideOverlays() {
                        if (view != null) {
                            view.hideOverlays();
                        }
                    }

                    @Override
                    public void showChatScreen() {
                        if (view != null) {
                            view.showChatScreen();
                        }
                    }

                    @Override
                    public void setComposerDraft(String text, ArrayList<InputAttachment> attachments) {
                        if (view != null) {
                            view.setComposerDraft(text, attachments);
                        }
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        overlayActionController = new OverlayActionController(
                context,
                projectWorkspaceController,
                fileOperationController,
                permissionModeController,
                contextCompactionController,
                chatInteractionController,
                lineCodeArchiveController,
                new OverlayActionController.Host() {
                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public void showSheet(String title, ArrayList<SheetOption> options) {
                        if (view != null) {
                            view.showSheet(title, options);
                        }
                    }

                    @Override
                    public void hideOverlays() {
                        if (view != null) {
                            view.hideOverlays();
                        }
                    }

                    @Override
                    public void showScreen(String screenId) {
                        MainCoordinator.this.showScreen(screenId);
                    }

                    @Override
                    public void render() {
                        MainCoordinator.this.render();
                    }
                }
        );
        attachmentPickerController = new AttachmentPickerCoordinator(
                fileTreeRepository,
                sshFileTreeRepository,
                ipcFileTreeRepository,
                backgroundTasks::execute,
                mainThread::post,
                new AttachmentPickerCoordinator.Host() {
                    @Override
                    public boolean isStreaming() {
                        return chatSessionStore.isStreaming();
                    }

                    @Override
                    public boolean isSshExecutionMode() {
                        return MainCoordinator.this.isSshExecutionMode();
                    }

                    @Override
                    public boolean isTerminalProviderExecutionMode() {
                        return MainCoordinator.this.isTerminalProviderExecutionMode();
                    }

                    @Override
                    public String projectPath() {
                        return projectState.path();
                    }

                    @Override
                    public String defaultHomePath() {
                        return projectRepository.getDefaultHomePath();
                    }

                    @Override
                    public boolean isViewAttached() {
                        return view != null;
                    }

                    @Override
                    public void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source) {
                        if (view != null) {
                            view.showAttachmentPicker(title, tree, loading, message, source);
                        }
                    }
                }
        );
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        fileTreeInteractionController.addExpandedPath(projectState.path());
        loadCurrentConversation();
    }

    @Override
    public void attachView(MainContract.View view) {
        this.view = view;
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        fileTreeInteractionController.addExpandedPath(projectState.path());
        render();
        requestSshFileTreeLoad(false);
        requestIpcFileTreeLoad(false);
        projectWorkspaceController.validateSelectedProjectAvailabilityOnStartup();
    }

    @Override
    public void detachView() {
        view = null;
    }

    @Override
    public void destroy() {
        ipcProviderManager.removeStateListener(ipcProviderController);
        detachView();
        generationLifecycleController.cancelActiveGeneration();
        generationLifecycleController.stopKeepAlive();
        HttpServerTool.stopActiveServer();
        backgroundTasks.shutdownNow();
    }

    @Override
    public void onMenuClick() {
        requestSshFileTreeLoad(false);
        requestIpcFileTreeLoad(false);
        if (view != null) {
            view.showDrawer();
        }
    }

    @Override
    public void onScreenBack() {
        navigateScreenBack("");
    }

    @Override
    public void onScreenBackFrom(String screenId) {
        navigateScreenBack(screenId);
    }

    private void navigateScreenBack(String visibleScreenId) {
        screenNavigationController.backFrom(visibleScreenId, navigationHost);
    }

    @Override
    protected MainContract.View delegateView() {
        return view;
    }

    @Override
    protected SettingsManagementController settingsDelegate() {
        return settingsManagementController;
    }

    @Override
    protected LineCodeArchiveController archiveDelegate() {
        return lineCodeArchiveController;
    }

    @Override
    protected ExtensionManagementController extensionManagementDelegate() {
        return extensionManagementController;
    }

    @Override
    protected ExtensionDraftController extensionDraftDelegate() {
        return extensionDraftController;
    }

    @Override
    protected IpcProviderController ipcProviderDelegate() {
        return ipcProviderController;
    }

    @Override
    protected ModelManagementController modelManagementDelegate() {
        return modelManagementController;
    }

    @Override
    protected ChatInteractionController chatInteractionDelegate() {
        return chatInteractionController;
    }

    @Override
    protected FileTreeInteractionController fileTreeInteractionDelegate() {
        return fileTreeInteractionController;
    }

    @Override
    protected FileOperationController fileOperationDelegate() {
        return fileOperationController;
    }

    @Override
    protected DirectoryPickerController directoryPickerDelegate() {
        return directoryPickerController;
    }

    @Override
    protected AttachmentPickerCoordinator attachmentPickerDelegate() {
        return attachmentPickerController;
    }

    @Override
    protected PermissionModeController permissionModeDelegate() {
        return permissionModeController;
    }

    @Override
    protected ProjectWorkspaceController projectWorkspaceDelegate() {
        return projectWorkspaceController;
    }

    @Override
    protected OverlayActionController overlayActionDelegate() {
        return overlayActionController;
    }

    @Override
    protected GenerationFlowController generationFlowDelegate() {
        return generationFlowController;
    }

    @Override
    protected ToolReviewController toolReviewDelegate() {
        return toolReviewController;
    }

    @Override
    protected ConversationStore conversationStoreDelegate() {
        return conversationRepository;
    }

    @Override
    protected ChatSessionStore chatSessionDelegate() {
        return chatSessionStore;
    }

    @Override
    protected ModelInteractionController modelInteractionDelegate() {
        return modelInteractionController;
    }

    @Override
    protected StorageMaintenanceController storageMaintenanceDelegate() {
        return storageMaintenanceController;
    }

    @Override
    protected void delegateShowScreen(String screenId) {
        showScreen(screenId);
    }

    @Override
    protected void delegateRefreshVisibleScreen(String screenId) {
        refreshVisibleScreen(screenId);
    }

    @Override
    protected void delegateRender() {
        render();
    }

    @Override
    protected void delegateReloadExtensions() {
        toolRegistry.reloadExtensions();
    }

    private void reloadAfterLineCodeImport() {
        toolRegistry.reloadExtensions();
        applyProject(projectRepository.ensureSelectedProjectPath(toolSettingsRepository.getExecutionMode()));
        sshFileTreeController.invalidateFileTree();
        ipcFileTreeController.invalidateFileTree();
        requestSshFileTreeLoad(true);
        requestIpcFileTreeLoad(true);
        ConversationRecord current = conversationRepository.getCurrentConversation();
        if (current == null) {
            chatSessionStore.clearCurrentConversation();
        } else {
            applyConversation(current);
        }
        refreshVisibleScreen("data");
        render();
    }

    private void showScreen(String screenId) {
        screenNavigationController.showScreen(screenId, navigationHost);
    }

    private void refreshVisibleScreen(String screenId) {
        if (view != null) {
            view.evictScreen(screenId);
        }
        screenNavigationController.refreshVisibleScreen(screenId, navigationHost);
    }

    private void returnToScreen(String screenId) {
        if (view != null) {
            view.evictScreen(screenId);
        }
        screenNavigationController.returnToScreen(screenId, navigationHost);
    }

    private void applyProject(ProjectRecord project) {
        if (project == null) {
            return;
        }
        projectState.apply(project, projectRepository);
        fileTreeInteractionController.resetToProjectRoot();
        sshFileTreeController.invalidateFileTree();
    }

    private void requestSshFileTreeLoad(boolean force) {
        sshFileTreeController.requestFileTreeLoad(force);
    }

    private void requestIpcFileTreeLoad(boolean force) {
        ipcFileTreeController.requestFileTreeLoad(force);
    }

    private boolean isSshExecutionMode() {
        return projectState.isSshExecutionMode(toolSettingsRepository);
    }

    private boolean isTerminalProviderExecutionMode() {
        return projectState.isTerminalProviderExecutionMode(toolSettingsRepository);
    }

    private boolean isTermuxSshHost() {
        SshConfig config = sshService.getConfig();
        String host = config == null ? "" : config.getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    private String basename(String path) {
        return projectState.basename(path);
    }

    private String parentPath(String path) {
        return projectState.parentPath(path);
    }

    private void showNotice(String text) {
        messages.add(new ChatMessage(nextId(), ChatMessage.Role.ASSISTANT, text, false));
        render();
    }

    private String syncModePermission() {
        String mode = chatModeRepository.getMode();
        chatModeRepository.applyMode(mode, toolSettingsRepository);
        return chatModeRepository.getMode();
    }

    private void render() {
        if (view == null) {
            return;
        }
        String activeChatMode = syncModePermission();
        view.render(chatUiStateAssembler.assemble(
                projectState.label(),
                projectState.source(),
                projectState.path(),
                chatSessionStore.getCurrentConversationId(),
                activeChatMode,
                chatSessionStore.isStreaming(),
                messages
        ));
    }

    private void resetTodoState() {
        if (todoStateStore != null) {
            todoStateStore.clear();
        }
    }

    private void refreshFileTreeAfterRevert(String filePath) {
        fileTreeInteractionController.refreshAfterRevert(filePath);
    }

    private void loadCurrentConversation() {
        conversationPersistenceController.loadCurrentConversation();
    }

    private void loadConversation(String id) {
        conversationPersistenceController.loadConversation(id);
        chatInteractionController.resetModelTracking();
    }

    private void applyConversation(ConversationRecord conversation) {
        conversationPersistenceController.applyConversation(conversation);
    }

    private void ensureCurrentConversation() {
        conversationPersistenceController.ensureCurrentConversation();
    }

    private void persistCurrentConversation() {
        conversationPersistenceController.persistCurrentConversation();
    }

    private String nextId() {
        return chatSessionStore.nextMessageId();
    }

    @Override
    public void onClearDiffCache() {
        storageMaintenanceController.clearDiffCache();
    }

    @Override
    public void onClearChatHistory() {
        storageMaintenanceController.clearChatHistory();
    }

    @Override
    public void onKeepAliveSettingsChanged() {
        storageMaintenanceController.applyKeepAliveSettings();
    }
}
