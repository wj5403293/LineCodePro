package cn.lineai.mvp;

import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.model.FileTreeNode;

public final class SshFileTreeController extends RemoteFileTreeController {
    public interface Host {
        boolean isSshExecutionMode();

        String projectPath();

        String projectLabel();

        boolean isExpanded(String path);

        void addExpandedPath(String path);

        void setProjectPathFromSshRoot(String path);

        String basename(String path);

        void render();
    }

    private static final class RepositoryDirectoryStore implements DirectoryStore {
        private final SshFileTreeStore repository;

        RepositoryDirectoryStore(SshFileTreeStore repository) {
            this.repository = repository;
        }

        @Override
        public FileTreeNode listDirectory(String path) throws Exception {
            return repository.listDirectory(path);
        }
    }

    private final Host host;

    public SshFileTreeController(
            SshFileTreeStore repository,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this(new RepositoryDirectoryStore(repository), host, backgroundRunner, uiDispatcher);
    }

    SshFileTreeController(
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
        return host.isSshExecutionMode();
    }

    @Override
    protected String loadingLabel() {
        return "正在读取 SSH 目录...";
    }

    @Override
    protected String shortLabel() {
        return "SSH";
    }

    @Override
    protected String directoryIndexTaskName() {
        return "linecode-ssh-directory-index";
    }

    @Override
    protected String indexPrefetchTaskName() {
        return "linecode-ssh-index-prefetch";
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
        host.setProjectPathFromSshRoot(path);
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
