package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import org.json.JSONObject;

public final class DeepseekReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("deepseek") || modelId.contains("deepseek");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        body.put("thinking", new JSONObject().put("type", context.isEnabled() ? "enabled" : "disabled"));
        if (context.isEnabled()) {
            body.put("reasoning_effort", context.getEffort());
        }
    }
}
