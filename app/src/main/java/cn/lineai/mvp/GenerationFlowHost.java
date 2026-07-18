package cn.lineai.mvp;

import cn.lineai.ai.ModelCancellationToken;

class GenerationFlowHost implements GenerationFlowController.Host {
    private final MainCoordinator coordinator;

    GenerationFlowHost(MainCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public String nextId() {
        return coordinator.nextId();
    }

    @Override
    public String projectPath() {
        return coordinator.projectState().path();
    }

    @Override
    public String projectSource() {
        return coordinator.projectState().source();
    }

    @Override
    public String currentConversationId() {
        return coordinator.chatSessionStore().getCurrentConversationId();
    }

    @Override
    public String syncModePermission() {
        return coordinator.syncModePermission();
    }

    @Override
    public void persistCurrentConversation() {
        coordinator.persistCurrentConversation();
    }

    @Override
    public void render() {
        coordinator.render();
    }

    @Override
    public void stopGenerationKeepAlive() {
        coordinator.generationLifecycleController.stopKeepAlive();
    }

    @Override
    public void setCurrentCancellationToken(ModelCancellationToken cancellationToken) {
        coordinator.generationLifecycleController.setCurrentCancellationToken(cancellationToken);
    }

    @Override
    public boolean isSshExecutionMode() {
        return coordinator.isSshExecutionMode();
    }

    @Override
    public boolean isTerminalProviderExecutionMode() {
        return coordinator.isTerminalProviderExecutionMode();
    }
}
