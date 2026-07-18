package cn.lineai.ai.protocol;

import org.json.JSONObject;

public interface ReasoningRequestStrategy {
    boolean matches(String baseUrl, String modelId);
    void apply(JSONObject body, ReasoningRequestContext context) throws Exception;
}
