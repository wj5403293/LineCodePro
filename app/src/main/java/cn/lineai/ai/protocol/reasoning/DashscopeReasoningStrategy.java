package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import org.json.JSONObject;

public final class DashscopeReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("dashscope") || baseUrl.contains("aliyuncs") || modelId.contains("qwen");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        body.put("enable_thinking", context.isEnabled());
        if (context.isEnabled()) {
            body.put("thinking_budget", context.getThinkingBudget());
        }
        if (context.isPreserveReasoning()) {
            body.put("preserve_thinking", true);
        }
    }
}
