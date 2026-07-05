package cn.lineai.ai.protocol.reasoning;

import cn.lineai.ai.protocol.ReasoningRequestContext;
import cn.lineai.ai.protocol.ReasoningRequestStrategy;
import org.json.JSONObject;

public final class MinimaxReasoningStrategy implements ReasoningRequestStrategy {
    @Override
    public boolean matches(String baseUrl, String modelId) {
        return baseUrl.contains("minimax") || modelId.contains("minimax")
                || modelId.contains("abab") || modelId.contains("m2");
    }

    @Override
    public void apply(JSONObject body, ReasoningRequestContext context) throws Exception {
        body.put("reasoning_split", context.isEnabled());
    }
}
