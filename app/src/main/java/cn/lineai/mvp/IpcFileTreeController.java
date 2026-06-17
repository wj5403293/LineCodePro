package cn.lineai.mvp;

import cn.lineai.data.repository.IpcFileTreeRepository;
import cn.lineai.model.FileTreeNode;

public final class IpcFileTreeController extends RemoteFileTreeController {
    public interface Host {
        boolean isTerminalProviderExecutionMode();

        String projectPath();

        String projectLabel();

        boolean isExpanded(String path);

        void addExpandedPath(String path);

        void setProjectPathFromIpcRoot(String path);

        String basename(String path);

        void render();
    }

    private static final class RepositoryDirectoryStore implements DirectoryStore {
        private final IpcFileTreeRepository repository;

        RepositoryDirectoryStore(IpcFileTreeRepository repository) {
            this.repository = repository;
        }

        @Override
        public FileTreeNode listDirectory(String path) throws Exception {
            return repository.listDirectory(path);
        }
    }

    private final Host host;

    public IpcFileTreeController(
            IpcFileTreeRepository repository,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this(new RepositoryDirectoryStore(repository), host, backgroundRunner, uiDispatcher);
    }

    IpcFileTreeController(
            DirectoryStore directoryStore,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        super(directoryStore, backgroundRunner, uiDispatcher);
        this.host = host;
    }

    @Override
    protected boolean isExecutionMode() {
        return host.isTerminalProviderExecutionMode();
    }

    @Override
    protected String loadingLabel() {
        return "正在读取终端提供者目录...";
    }

    @Override
    protected String shortLabel() {
        return "终端提供者";
    }

    @Override
    protected String directoryIndexTaskName() {
        return "linecode-ipc-directory-index";
    }

    @Override
    protected String indexPrefetchTaskName() {
        return "linecode-ipc-index-prefetch";
    }

    @Override
    protected String hostProjectPath() {
        return host.projectPath();
    }

    @Override
    protected String hostProjectLabel() {
        return host.projectLabel();
    }

    @Override
    protected boolean hostIsExpanded(String path) {
        return host.isExpanded(path);
    }

    @Override
    protected void hostAddExpandedPath(String path) {
        host.addExpandedPath(path);
    }

    @Override
    protected void hostSetProjectPathFromRemoteRoot(String path) {
        host.setProjectPathFromIpcRoot(path);
    }

    @Override
    protected String hostBasename(String path) {
        return host.basename(path);
    }

    @Override
    protected void hostRender() {
        host.render();
    }
}
