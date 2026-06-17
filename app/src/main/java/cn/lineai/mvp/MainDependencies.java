package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.prompt.SystemPromptProvider;
import cn.lineai.ai.protocol.CodexResponsesProtocol;
import cn.lineai.ai.protocol.OpenAiResponsesCompactionProtocol;
import cn.lineai.context.ContextCompactionService;
import cn.lineai.context.ContextManager;
import cn.lineai.context.MemoryExtractionService;
import cn.lineai.data.importer.LineCodeArchiveService;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationRepository;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.repository.ExtensionRepository;
import cn.lineai.data.repository.FileTreeRepository;
import cn.lineai.data.repository.InputSettingsRepository;
import cn.lineai.data.repository.IpcFileTreeRepository;
import cn.lineai.data.repository.IpcProviderRepository;
import cn.lineai.data.repository.LearningContextRepository;
import cn.lineai.data.repository.OutputSettingsRepository;
import cn.lineai.data.repository.ProjectRepository;
import cn.lineai.data.repository.ProjectStore;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeRepository;
import cn.lineai.data.repository.ThemeSettingsRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderScanner;
import cn.lineai.model.ModelRepository;
import cn.lineai.model.ModelStore;
import cn.lineai.ssh.SshService;
import cn.lineai.state.TodoStateStore;
import cn.lineai.tool.ToolExecutionCoordinator;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.workspace.SafPathResolver;
import cn.lineai.workspace.StoragePermissionManager;

public final class MainDependencies {
    final Context context;
    final ModelStore modelRepository;
    final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    final ChatModeRepository chatModeRepository;
    final InputSettingsRepository inputSettingsRepository;
    final OutputSettingsRepository outputSettingsRepository;
    final ThemeSettingsRepository themeSettingsRepository;
    final PromptTemplateRepository promptTemplateRepository;
    final ConversationStore conversationRepository;
    final ProjectStore projectRepository;
    final LearningContextRepository learningContextRepository;
    final MemoryExtractionService memoryExtractionService;
    final ToolSettingsRepository toolSettingsRepository;
    final ExtensionRepository extensionRepository;
    final IpcProviderRepository ipcProviderRepository;
    final IpcProviderScanner ipcProviderScanner;
    final IpcProviderManager ipcProviderManager;
    final DiffStore diffRepository;
    final FileTreeRepository fileTreeRepository;
    final IpcFileTreeRepository ipcFileTreeRepository;
    final SshService sshService;
    final SshFileTreeRepository sshFileTreeRepository;
    final ContextManager contextManager;
    final ContextCompactionService contextCompactionService;
    final ModelClient modelClient;
    final ToolRegistry toolRegistry;
    final ToolExecutor toolExecutor;
    final ToolExecutionCoordinator toolExecutionCoordinator;
    final SystemPromptProvider systemPromptProvider;
    final StoragePermissionManager storagePermissionManager;
    final SafPathResolver safPathResolver;
    final MainThreadDispatcher mainThreadDispatcher;
    final BackgroundTaskRunner backgroundTaskRunner;
    final LineCodeArchiveService lineCodeArchiveService;
    final TodoStateStore todoStateStore;

    public MainDependencies(Context context) {
        this.context = context;
        modelRepository = new ModelRepository(context);
        aiBehaviorSettingsRepository = new AiBehaviorSettingsRepository(context);
        chatModeRepository = new ChatModeRepository(context);
        inputSettingsRepository = new InputSettingsRepository(context);
        outputSettingsRepository = new OutputSettingsRepository(context);
        themeSettingsRepository = new ThemeSettingsRepository(context);
        promptTemplateRepository = new PromptTemplateRepository(context);
        themeSettingsRepository.applyCurrentTheme();
        conversationRepository = new ConversationRepository(context);
        projectRepository = new ProjectRepository(context);
        learningContextRepository = new LearningContextRepository(context);
        memoryExtractionService = new MemoryExtractionService(context, learningContextRepository);
        toolSettingsRepository = new ToolSettingsRepository(context);
        extensionRepository = new ExtensionRepository(context);
        ipcProviderRepository = new IpcProviderRepository(context);
        ipcProviderScanner = new IpcProviderScanner();
        ipcProviderManager = new IpcProviderManager(context);
        diffRepository = new DiffRepository(context);
        fileTreeRepository = new FileTreeRepository();
        sshService = new SshService(context);
        sshFileTreeRepository = new SshFileTreeRepository(sshService);
        ipcFileTreeRepository = new IpcFileTreeRepository(ipcProviderManager);
        contextManager = new ContextManager();
        modelClient = new ModelClient();
        contextCompactionService = new ContextCompactionService(
                context,
                modelClient,
                new OpenAiResponsesCompactionProtocol(),
                new CodexResponsesProtocol(),
                promptTemplateRepository);
        toolRegistry = new ToolRegistry(context, ipcProviderManager);
        toolExecutor = new ToolExecutor(toolRegistry, toolSettingsRepository, diffRepository);
        toolExecutionCoordinator = new ToolExecutionCoordinator(toolRegistry);
        systemPromptProvider = new SystemPromptProvider(context, promptTemplateRepository);
        storagePermissionManager = new StoragePermissionManager(context);
        safPathResolver = new SafPathResolver();
        mainThreadDispatcher = new MainThreadDispatcher();
        backgroundTaskRunner = new BackgroundTaskRunner();
        lineCodeArchiveService = new LineCodeArchiveService(context);
        todoStateStore = new TodoStateStore();
        chatModeRepository.initialize(toolSettingsRepository);
    }
}
