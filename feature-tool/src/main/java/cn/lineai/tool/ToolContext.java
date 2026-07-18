package cn.lineai.tool;

import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ModelStore;
import cn.lineai.state.TodoStateStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

public final class ToolContext {
    public interface AgentRunner {
        ToolResult runAgent(JSONObject input, ToolContext context);

        ToolResult runAgentPipeline(JSONObject input, ToolContext context);
    }

    public interface ProgressListener {
        void onToolProgress(String toolCallId, String toolName, String content, boolean error);
    }

    private final String homePath;
    private final List<String> extraWriteRoots;
    private final AgentRunner agentRunner;
    private final String toolCallId;
    private final ProgressListener progressListener;
    private final TodoStateStore todoStateStore;
    private final ToolSettingsStore toolSettingsStore;
    private final ModelStore modelRepository;
    private final SshFileTreeStore sshFileTreeRepository;
    private final ModelServiceProvider modelServiceProvider;
    private final PromptTemplateRepository promptTemplateRepository;

    private ToolContext(
            String homePath,
            List<String> extraWriteRoots,
            AgentRunner agentRunner,
            String toolCallId,
            ProgressListener progressListener,
            TodoStateStore todoStateStore,
            ToolSettingsStore toolSettingsStore,
            ModelStore modelRepository,
            SshFileTreeStore sshFileTreeRepository,
            ModelServiceProvider modelServiceProvider,
            PromptTemplateRepository promptTemplateRepository
    ) {
        this.homePath = homePath == null ? "" : homePath;
        this.extraWriteRoots = immutableRoots(extraWriteRoots);
        this.agentRunner = agentRunner;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.progressListener = progressListener;
        this.todoStateStore = todoStateStore;
        this.toolSettingsStore = toolSettingsStore;
        this.modelRepository = modelRepository;
        this.sshFileTreeRepository = sshFileTreeRepository;
        this.modelServiceProvider = modelServiceProvider;
        this.promptTemplateRepository = promptTemplateRepository;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getHomePath() {
        return homePath;
    }

    public List<String> getExtraWriteRoots() {
        return extraWriteRoots;
    }

    public AgentRunner getAgentRunner() {
        return agentRunner;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public ToolContext withToolCallId(String nextToolCallId) {
        return new ToolContext(homePath, extraWriteRoots, agentRunner, nextToolCallId, progressListener, todoStateStore, toolSettingsStore, modelRepository, sshFileTreeRepository, modelServiceProvider, promptTemplateRepository);
    }

    public static final class Builder {
        private String homePath;
        private List<String> extraWriteRoots;
        private AgentRunner agentRunner;
        private String toolCallId;
        private ProgressListener progressListener;
        private TodoStateStore todoStateStore;
        private ToolSettingsStore toolSettingsStore;
        private ModelStore modelRepository;
        private SshFileTreeStore sshFileTreeRepository;
        private ModelServiceProvider modelServiceProvider;
        private PromptTemplateRepository promptTemplateRepository;

        public Builder homePath(String v) { this.homePath = v; return this; }
        public Builder extraWriteRoots(List<String> v) { this.extraWriteRoots = v; return this; }
        public Builder agentRunner(AgentRunner v) { this.agentRunner = v; return this; }
        public Builder toolCallId(String v) { this.toolCallId = v; return this; }
        public Builder progressListener(ProgressListener v) { this.progressListener = v; return this; }
        public Builder todoStateStore(TodoStateStore v) { this.todoStateStore = v; return this; }
        public Builder toolSettingsStore(ToolSettingsStore v) { this.toolSettingsStore = v; return this; }
        public Builder modelRepository(ModelStore v) { this.modelRepository = v; return this; }
        public Builder sshFileTreeRepository(SshFileTreeStore v) { this.sshFileTreeRepository = v; return this; }
        public Builder modelServiceProvider(ModelServiceProvider v) { this.modelServiceProvider = v; return this; }
        public Builder promptTemplateRepository(PromptTemplateRepository v) { this.promptTemplateRepository = v; return this; }

        public ToolContext build() {
            return new ToolContext(homePath, extraWriteRoots, agentRunner, toolCallId,
                    progressListener, todoStateStore, toolSettingsStore, modelRepository,
                    sshFileTreeRepository, modelServiceProvider, promptTemplateRepository);
        }
    }

    public void reportToolProgress(String toolName, String content, boolean error) {
        if (progressListener != null && toolCallId.length() > 0) {
            progressListener.onToolProgress(toolCallId, toolName, content == null ? "" : content, error);
        }
    }

    public TodoStateStore getTodoStateStore() {
        return todoStateStore;
    }

    public ToolSettingsStore getToolSettingsStore() {
        return toolSettingsStore;
    }

    public ModelStore getModelRepository() {
        return modelRepository;
    }

    public SshFileTreeStore getSshFileTreeRepository() {
        return sshFileTreeRepository;
    }

    public ModelServiceProvider getModelServiceProvider() {
        return modelServiceProvider;
    }

    public PromptTemplateRepository getPromptTemplateRepository() {
        return promptTemplateRepository;
    }

    private List<String> immutableRoots(List<String> roots) {
        ArrayList<String> values = new ArrayList<>();
        if (roots != null) {
            for (String root : roots) {
                if (root != null && root.trim().length() > 0) {
                    values.add(root.trim());
                }
            }
        }
        return Collections.unmodifiableList(values);
    }
}
