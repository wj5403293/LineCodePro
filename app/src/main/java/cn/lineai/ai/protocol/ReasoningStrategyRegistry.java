package cn.lineai.ai.protocol;

import java.util.ArrayList;
import java.util.List;

public class ReasoningStrategyRegistry {
    private final List<ReasoningRequestStrategy> strategies = new ArrayList<>();

    public void register(ReasoningRequestStrategy strategy) {
        strategies.add(strategy);
    }

    public ReasoningRequestStrategy find(String baseUrl, String modelId) {
        for (ReasoningRequestStrategy strategy : strategies) {
            if (strategy.matches(baseUrl, modelId)) {
                return strategy;
            }
        }
        return null;
    }
}
