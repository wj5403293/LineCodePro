package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.ai.stream.ThinkTagParser;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.ai.protocol.reasoning.DashscopeReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.DeepseekReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.DefaultReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.MinimaxReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.MoonshotReasoningStrategy;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OpenAiCompatibleProtocol extends AbstractHttpModelProtocol {

    private final ReasoningStrategyRegistry reasoningStrategyRegistry = createDefaultRegistry();

    private static ReasoningStrategyRegistry createDefaultRegistry() {
        ReasoningStrategyRegistry registry = new ReasoningStrategyRegistry();
        registry.register(new DashscopeReasoningStrategy());
        registry.register(new MinimaxReasoningStrategy());
        registry.register(new DeepseekReasoningStrategy());
        registry.register(new MoonshotReasoningStrategy());
        registry.register(new DefaultReasoningStrategy());
        return registry;
    }

    @Override
    public boolean supportsNativeTools(ModelConfig model) {
        return OpenAiCompatibleCapabilities.supportsNativeTools(model);
    }

    @Override
    public boolean supportsDedicatedCompression() {
        return true;
    }

    @Override
    public boolean supportsImageGeneration() {
        return true;
    }

    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        String raw = "";
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("messages", messagesJson(messages));
            body.put("temperature", 0.2);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());
            raw = postJson(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers);
            JSONObject response = new JSONObject(raw);
            String text = response
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content");
            return new ModelCompletionResponse(text);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            logParseError("parse_openai_complete", raw, e);
            throw new ModelCompletionException("OpenAI 兼容协议解析失败: " + e.getMessage(), e);
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
            body.put("messages", messagesJson(messages, requestOptions.isPreserveReasoning()));
            body.put("temperature", 0.2);
            body.put("stream", true);
            if (!requestOptions.getTools().isEmpty()) {
                body.put("tools", ToolRegistry.toJsonArray(requestOptions.getTools()));
                body.put("tool_choice", "auto");
            }
            applyReasoningRequest(config, body, requestOptions);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            ThinkTagParser thinkTagParser = new ThinkTagParser();
            HashMap<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();

            postJsonSse(endpoint(config.getBaseUrl(), "/chat/completions"), body, headers, cancellationToken, (eventType, data) -> {
                if ("[DONE]".equals(data.trim())) {
                    return;
                }
                JSONObject event = new JSONObject(data);
                if (event.has("error")) {
                    throw new ModelCompletionException("OpenAI 流式错误: " + describeError(event.opt("error")));
                }
                JSONArray choices = event.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    return;
                }
                JSONObject choice = choices.optJSONObject(0);
                if (choice == null) {
                    return;
                }
                if ("content_filter".equals(choice.optString("finish_reason"))) {
                    throw new ModelCompletionException("OpenAI 流式错误: 输出被内容安全策略拦截");
                }
                JSONObject delta = choice.optJSONObject("delta");
                if (delta == null) {
                    return;
                }

                JSONArray toolCalls = delta.optJSONArray("tool_calls");
                if (toolCalls != null) {
                    appendToolCallDeltas(toolCallBuilders, toolCalls);
                }

                String reasoningDelta = extractReasoningDelta(delta);
                if (reasoningDelta.length() > 0) {
                    reasoning.append(reasoningDelta);
                    if (callback != null) {
                        callback.onReasoningDelta(reasoningDelta);
                    }
                }

                if (delta.has("content") && !delta.isNull("content")) {
                    ThinkTagParser.Result parsed = thinkTagParser.append(delta.optString("content"));
                    appendParsedDelta(text, reasoning, parsed, callback);
                }
            });

            appendParsedDelta(text, reasoning, thinkTagParser.flush(), callback);
            return new ModelCompletionResponse(text.toString(), reasoning.toString(), buildToolCalls(toolCallBuilders));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("OpenAI 兼容协议流式解析失败: " + e.getMessage(), e);
        }
    }

    private JSONArray messagesJson(List<ModelMessage> messages) throws Exception {
        return messagesJson(messages, false);
    }

    private JSONArray messagesJson(List<ModelMessage> messages, boolean preserveReasoning) throws Exception {
        JSONArray array = new JSONArray();
        for (ModelMessage message : messages) {
            JSONObject object = new JSONObject();
            object.put("role", message.getRole());
            if ("tool".equals(message.getRole())) {
                object.put("tool_call_id", message.getToolCallId());
                object.put("content", message.getContent());
                array.put(object);
                continue;
            }
            if ("assistant".equals(message.getRole()) && !message.getToolCalls().isEmpty()) {
                object.put("content", message.getContent().length() == 0 ? JSONObject.NULL : message.getContent());
                JSONArray toolCalls = new JSONArray();
                for (ToolCall call : message.getToolCalls()) {
                    JSONObject function = new JSONObject()
                            .put("name", call.getName())
                            .put("arguments", call.getArguments());
                    toolCalls.put(new JSONObject()
                            .put("id", call.getId())
                            .put("type", "function")
                            .put("function", function));
                }
                object.put("tool_calls", toolCalls);
            } else if ("user".equals(message.getRole()) && ImageInputPayload.fromRawInputJson(message.getRawInputJson()) != null) {
                object.put("content", imageContent(message));
            } else {
                object.put("content", message.getContent());
            }
            if (preserveReasoning
                    && "assistant".equals(message.getRole())
                    && message.getReasoningContent().length() > 0) {
                object.put("reasoning_content", message.getReasoningContent());
            }
            array.put(object);
        }
        return array;
    }

    JSONArray messagesJsonForTest(List<ModelMessage> messages) throws Exception {
        return messagesJson(messages);
    }

    private JSONArray imageContent(ModelMessage message) throws Exception {
        ImageInputPayload.Payload payload = ImageInputPayload.fromRawInputJson(message.getRawInputJson());
        if (payload == null) {
            return new JSONArray().put(new JSONObject().put("type", "text").put("text", message.getContent()));
        }
        String prompt = payload.getPrompt().length() == 0 ? message.getContent() : payload.getPrompt();
        return new JSONArray()
                .put(new JSONObject()
                        .put("type", "text")
                        .put("text", prompt == null ? "" : prompt))
                .put(new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", new JSONObject()
                                .put("url", payload.dataUrl())));
    }

    private void appendToolCallDeltas(Map<Integer, ToolCallBuilder> builders, JSONArray toolCalls) {
        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject item = toolCalls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            int index = item.optInt("index", i);
            ToolCallBuilder builder = builders.get(index);
            if (builder == null) {
                builder = new ToolCallBuilder();
                builders.put(index, builder);
            }
            if (item.has("id") && !item.isNull("id")) {
                String id = item.optString("id");
                if (id.length() > 0) {
                    builder.id = id;
                }
            }
            JSONObject function = item.optJSONObject("function");
            if (function == null) {
                continue;
            }
            if (function.has("name") && !function.isNull("name")) {
                String name = function.optString("name");
                if (name.length() > 0) {
                    builder.name = name;
                }
            }
            if (function.has("arguments") && !function.isNull("arguments")) {
                builder.arguments.append(function.optString("arguments"));
            }
        }
    }

    private List<ToolCall> buildToolCalls(Map<Integer, ToolCallBuilder> builders) {
        ArrayList<Integer> indexes = new ArrayList<>(builders.keySet());
        indexes.sort(Integer::compareTo);
        ArrayList<ToolCall> calls = new ArrayList<>();
        for (Integer index : indexes) {
            ToolCallBuilder builder = builders.get(index);
            if (builder == null || builder.name.length() == 0) {
                continue;
            }
            String id = builder.id.length() == 0 ? "call_" + index + "_" + System.currentTimeMillis() : builder.id;
            String args = builder.arguments.length() == 0 ? "{}" : builder.arguments.toString();
            calls.add(new ToolCall(id, builder.name, args));
        }
        return calls;
    }

    private static final class ToolCallBuilder {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();
    }

    private void appendParsedDelta(
            StringBuilder text,
            StringBuilder reasoning,
            ThinkTagParser.Result parsed,
            ModelStreamCallback callback
    ) {
        if (parsed.getThinking().length() > 0) {
            reasoning.append(parsed.getThinking());
            if (callback != null) {
                callback.onReasoningDelta(parsed.getThinking());
            }
        }
        if (parsed.getText().length() > 0) {
            text.append(parsed.getText());
            if (callback != null) {
                callback.onTextDelta(parsed.getText());
            }
        }
    }

    private String extractReasoningDelta(JSONObject delta) {
        if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
            return delta.optString("reasoning_content");
        }
        Object reasoning = delta.opt("reasoning");
        if (reasoning instanceof String) {
            return (String) reasoning;
        }
        if (reasoning instanceof JSONObject) {
            JSONObject object = (JSONObject) reasoning;
            if (object.has("content")) {
                return object.optString("content");
            }
            if (object.has("text")) {
                return object.optString("text");
            }
        }
        Object details = delta.opt("reasoning_details");
        if (details instanceof JSONObject) {
            JSONObject object = (JSONObject) details;
            if (object.has("content")) {
                return object.optString("content");
            }
            if (object.has("text")) {
                return object.optString("text");
            }
            if (object.has("reasoning_content")) {
                return object.optString("reasoning_content");
            }
        }
        if (details instanceof JSONArray) {
            JSONArray array = (JSONArray) details;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof String) {
                    builder.append((String) item);
                } else if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    if (object.has("content")) {
                        builder.append(object.optString("content"));
                    } else if (object.has("text")) {
                        builder.append(object.optString("text"));
                    } else if (object.has("reasoning_content")) {
                        builder.append(object.optString("reasoning_content"));
                    }
                }
            }
            return builder.toString();
        }
        return "";
    }

    private void applyReasoningRequest(ModelConfig config, JSONObject body, ModelRequestOptions options) throws Exception {
        if (!OpenAiCompatibleCapabilities.supportsReasoningRequestParameters(config)) {
            return;
        }
        String base = config.getBaseUrl().toLowerCase(java.util.Locale.ROOT);
        String model = ModelContextParser.apiModelId(config.getModelId()).toLowerCase(java.util.Locale.ROOT);
        String effort = options.getReasoningEffort();
        boolean enabled = !AiBehaviorSettings.REASONING_OFF.equals(effort);
        ReasoningRequestContext context = new ReasoningRequestContext(
                enabled, effort, options.isPreserveReasoning(), base, model, thinkingBudget(effort));
        ReasoningRequestStrategy strategy = reasoningStrategyRegistry.find(base, model);
        if (strategy != null) {
            strategy.apply(body, context);
        }
    }

    JSONObject reasoningRequestBodyForTest(ModelConfig config, ModelRequestOptions options) throws Exception {
        JSONObject body = new JSONObject();
        applyReasoningRequest(config, body, options == null ? ModelRequestOptions.defaults() : options);
        return body;
    }

    /**
     * 把 SSE 错误字段可读地转为文本。{@code error} 可能是字符串或 JSON 对象：
     * 对象时直接 {@code JSONObject.toString()} 会把中文转义成 {@code \\uXXXX}，
     * 因此优先读取 {@code message}/{@code type} 字段，最后统一做一次 Unicode 转义解码。
     */
    private static String describeError(Object error) {
        if (error == null) {
            return "";
        }
        if (error instanceof JSONObject) {
            JSONObject obj = (JSONObject) error;
            String message = obj.optString("message");
            if (message != null && message.length() > 0) {
                return StringUtils.decodeUnicodeEscapes(message);
            }
            String type = obj.optString("type");
            if (type != null && type.length() > 0) {
                return StringUtils.decodeUnicodeEscapes(type);
            }
            return StringUtils.decodeUnicodeEscapes(obj.optString("code"));
        }
        return StringUtils.decodeUnicodeEscapes(error.toString());
    }
}
