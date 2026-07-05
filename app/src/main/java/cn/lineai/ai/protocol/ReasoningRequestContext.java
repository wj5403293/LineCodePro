package cn.lineai.ai.protocol;

public final class ReasoningRequestContext {
    private final boolean enabled;
    private final String effort;
    private final boolean preserveReasoning;
    private final String baseUrl;
    private final String modelId;
    private final int thinkingBudget;

    public ReasoningRequestContext(boolean enabled, String effort, boolean preserveReasoning,
                                   String baseUrl, String modelId, int thinkingBudget) {
        this.enabled = enabled;
        this.effort = effort;
        this.preserveReasoning = preserveReasoning;
        this.baseUrl = baseUrl;
        this.modelId = modelId;
        this.thinkingBudget = thinkingBudget;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEffort() {
        return effort;
    }

    public boolean isPreserveReasoning() {
        return preserveReasoning;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelId() {
        return modelId;
    }

    public int getThinkingBudget() {
        return thinkingBudget;
    }
}
