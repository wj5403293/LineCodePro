package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CodexResponsesProtocol extends AbstractHttpModelProtocol {
    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("input", ResponsesInputBuilder.inputJson(messages));

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());
            String raw = postJson(endpoint(config.getBaseUrl(), "/responses"), body, headers);
            JSONObject response = new JSONObject(raw);
            String directText = response.optString("output_text");
            if (directText.length() > 0) {
                return new ModelCompletionResponse(directText);
            }
            return new ModelCompletionResponse(extractOutputText(response.optJSONArray("output")));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex Responses 协议解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        try {
            ModelRequestOptions requestOptions = options == null ? ModelRequestOptions.defaults() : options;
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("input", ResponsesInputBuilder.inputJson(messages));
            body.put("stream", true);
            if (!AiBehaviorSettings.REASONING_OFF.equals(requestOptions.getReasoningEffort())) {
                body.put("reasoning", new JSONObject()
                        .put("effort", AiBehaviorSettings.REASONING_MAX.equals(requestOptions.getReasoningEffort()) ? "high" : requestOptions.getReasoningEffort())
                        .put("summary", "auto"));
            }

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();

            postJsonSse(endpoint(config.getBaseUrl(), "/responses"), body, headers, cancellationToken, (eventType, data) -> {
                if ("[DONE]".equals(data.trim())) {
                    return;
                }
                JSONObject event = new JSONObject(data);
                if (event.has("error")) {
                    throw new ModelCompletionException("Codex 流式错误: " + event.opt("error"));
                }
                String type = eventType == null || eventType.length() == 0 ? event.optString("type") : eventType;

                if ("response.output_text.delta".equals(type) && event.has("delta")) {
                    String delta = event.optString("delta");
                    text.append(delta);
                    if (callback != null) {
                        callback.onTextDelta(delta);
                    }
                    return;
                }

                if (("response.reasoning_summary_text.delta".equals(type) || "response.reasoning_text.delta".equals(type))
                        && event.has("delta")) {
                    String delta = event.optString("delta");
                    reasoning.append(delta);
                    if (callback != null) {
                        callback.onReasoningDelta(delta);
                    }
                    return;
                }

                if ("response.reasoning_summary_part.added".equals(type) && reasoning.length() > 0) {
                    reasoning.append('\n');
                    if (callback != null) {
                        callback.onReasoningDelta("\n");
                    }
                    return;
                }

                if (("response.completed".equals(type) || "response.output_item.done".equals(type)) && event.has("response")) {
                    JSONObject response = event.optJSONObject("response");
                    if (response != null) {
                        mergeOutputArray(response.optJSONArray("output"), text, reasoning, callback);
                    }
                } else if (("response.output_item.added".equals(type) || "response.output_item.done".equals(type)) && event.has("item")) {
                    mergeOutputItem(event.optJSONObject("item"), text, reasoning, callback);
                }
            });

            return new ModelCompletionResponse(text.toString(), reasoning.toString());
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex Responses 协议流式解析失败: " + e.getMessage(), e);
        }
    }

    private String extractOutputText(JSONArray output) {
        if (output == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONArray content = item.optJSONArray("content");
            if (content == null) {
                continue;
            }
            for (int j = 0; j < content.length(); j++) {
                JSONObject block = content.optJSONObject(j);
                if (block != null) {
                    String text = block.optString("text");
                    if (text.length() > 0) {
                        builder.append(text);
                    }
                }
            }
        }
        return builder.toString();
    }

    private void mergeOutputArray(
            JSONArray output,
            StringBuilder text,
            StringBuilder reasoning,
            ModelStreamCallback callback
    ) {
        if (output == null) {
            return;
        }
        for (int i = 0; i < output.length(); i++) {
            mergeOutputItem(output.optJSONObject(i), text, reasoning, callback);
        }
    }

    private void mergeOutputItem(
            JSONObject item,
            StringBuilder text,
            StringBuilder reasoning,
            ModelStreamCallback callback
    ) {
        if (item == null) {
            return;
        }
        if ("reasoning".equals(item.optString("type"))) {
            String next = extractReasoningText(item);
            appendFinalIfMissing(reasoning, next, true, callback);
            return;
        }
        JSONArray content = item.optJSONArray("content");
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) {
                continue;
            }
            String type = block.optString("type");
            if ("output_text".equals(type) || "text".equals(type)) {
                appendFinalIfMissing(text, block.optString("text"), false, callback);
            } else if ("reasoning_text".equals(type) || "summary_text".equals(type)) {
                appendFinalIfMissing(reasoning, block.optString("text"), true, callback);
            }
        }
    }

    private String extractReasoningText(JSONObject item) {
        StringBuilder builder = new StringBuilder();
        JSONArray summary = item.optJSONArray("summary");
        if (summary != null) {
            for (int i = 0; i < summary.length(); i++) {
                JSONObject part = summary.optJSONObject(i);
                if (part != null) {
                    builder.append(part.optString("text"));
                }
            }
        }
        if (builder.length() == 0) {
            builder.append(item.optString("content"));
        }
        return builder.toString();
    }

    private void appendFinalIfMissing(
            StringBuilder target,
            String next,
            boolean thinking,
            ModelStreamCallback callback
    ) {
        if (next == null || next.length() == 0 || target.indexOf(next) >= 0) {
            return;
        }
        target.append(next);
        if (callback == null) {
            return;
        }
        if (thinking) {
            callback.onReasoningDelta(next);
        } else {
            callback.onTextDelta(next);
        }
    }
}
