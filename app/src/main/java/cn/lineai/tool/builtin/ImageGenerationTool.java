package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelRepository;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ImageGenerationTool extends BaseTool {
    private final ToolSettingsRepository settingsRepository;
    private final ModelRepository modelRepository;
    private final ImageApiClient apiClient;
    private final ImageResponseParser responseParser;

    public ImageGenerationTool() {
        this(null);
    }

    public ImageGenerationTool(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        settingsRepository = appContext == null ? null : new ToolSettingsRepository(appContext);
        modelRepository = appContext == null ? null : new ModelRepository(appContext);
        apiClient = new ImageApiClient();
        responseParser = new ImageResponseParser(apiClient, new Base64ImageValidator());
    }

    @Override
    public String getName() {
        return "image_generation";
    }

    @Override
    public String getDescription() {
        return "根据提示词调用工具设置里选择的生图模型生成图片，成功后返回可直接嵌入 Markdown 的图片。支持 OpenAI Images API 和 Responses image_generation 工具。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.GENERATE;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "图片生成提示词，包含主体、风格、构图、文字要求和限制"))
                        .put("size", new JSONObject()
                                .put("type", "string")
                                .put("description", "图片尺寸，默认 1024x1024；常见值: 1024x1024、1024x1536、1536x1024、auto"))
                        .put("quality", new JSONObject()
                                .put("type", "string")
                                .put("description", "质量选项，可留空；常见值: auto、low、medium、high、standard、hd"))
                        .put("background", new JSONObject()
                                .put("type", "string")
                                .put("description", "背景选项，可留空；常见值: auto、transparent、opaque")))
                .put("required", new JSONArray().put("prompt"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (settingsRepository == null || modelRepository == null) {
            return error("图片生成工具未接入应用上下文。");
        }
        String prompt = input == null ? "" : input.optString("prompt").trim();
        if (prompt.length() == 0) {
            return error("图片生成提示词不能为空。");
        }
        ModelConfig model = selectedModel();
        if (model == null) {
            return error("图片生成未选择模型。请在 设置 -> 工具设置 -> 图片操作 中选择生图模型。");
        }
        if (model.getProtocolType() != ModelProtocolType.OPENAI_COMPATIBLE
                && model.getProtocolType() != ModelProtocolType.CODEX_RESPONSES) {
            return error("图片生成当前仅支持 OpenAI 兼容或 Codex 协议模型。请添加或选择一个 Images API 兼容模型。");
        }
        try {
            if (context != null) {
                context.reportToolProgress(getName(), "正在生成图片...", false);
            }
            ImageResponseParser.GeneratedImage image = model.getProtocolType() == ModelProtocolType.CODEX_RESPONSES
                    ? generateWithResponsesApi(model, input, prompt)
                    : generateWithImagesApi(model, input, prompt, context);
            String displayMarkdown = "![" + markdownAlt(prompt) + "](" + image.dataUrl + ")";
            String modelContent = "图片已生成并已在对话中显示。提示词: " + trimForModel(prompt)
                    + (image.revisedPrompt.length() == 0 ? "" : "\n修订提示词: " + trimForModel(image.revisedPrompt));
            JSONObject result = new JSONObject()
                    .put("linecode_image_generation", true)
                    .put("display_markdown", displayMarkdown)
                    .put("model_content", modelContent)
                    .put("mime_type", image.mimeType)
                    .put("prompt", prompt)
                    .put("revised_prompt", image.revisedPrompt);
            return ok(result.toString());
        } catch (Exception e) {
            return error("图片生成失败: " + e.getMessage());
        }
    }

    private ModelConfig selectedModel() {
        String modelId = settingsRepository.getImageGenerationModelId();
        return modelId.length() == 0 ? null : modelRepository.getModel(modelId);
    }

    private ImageResponseParser.GeneratedImage generateWithImagesApi(ModelConfig model, JSONObject input, String prompt, ToolContext context) throws Exception {
        boolean requestBase64 = shouldRequestBase64Response(model);
        JSONObject body = apiClient.imagesRequestBody(model, input, prompt, requestBase64);
        String raw;
        try {
            raw = apiClient.postJson(apiClient.imagesEndpoint(model.getBaseUrl()), body, apiClient.authHeaders(model));
        } catch (Exception e) {
            if (!requestBase64) {
                throw e;
            }
            body = apiClient.imagesRequestBody(model, input, prompt, false);
            raw = apiClient.postJson(apiClient.imagesEndpoint(model.getBaseUrl()), body, apiClient.authHeaders(model));
        }
        return responseParser.parseImagesResponse(raw, context);
    }

    private ImageResponseParser.GeneratedImage generateWithResponsesApi(ModelConfig model, JSONObject input, String prompt) throws Exception {
        JSONObject body = apiClient.responsesRequestBody(model, input, prompt);
        String raw = apiClient.postJson(apiClient.responsesEndpoint(model.getBaseUrl()), body, apiClient.codexHeaders(model));
        return responseParser.parseResponsesImage(raw);
    }

    private String responsesEndpoint(String baseUrl) {
        return apiClient.responsesEndpoint(baseUrl);
    }

    private JSONObject responsesImageGenerationTool(JSONObject input) throws Exception {
        return apiClient.responsesImageGenerationTool(input);
    }

    private JSONObject responsesRequestBody(ModelConfig model, JSONObject input, String prompt) throws Exception {
        return apiClient.responsesRequestBody(model, input, prompt);
    }

    private JSONObject imagesRequestBody(ModelConfig model, JSONObject input, String prompt, boolean requestBase64) throws Exception {
        return apiClient.imagesRequestBody(model, input, prompt, requestBase64);
    }

    private ImageResponseParser.GeneratedImage parseResponsesImage(String raw) throws Exception {
        return responseParser.parseResponsesImage(raw);
    }

    private ImageResponseParser.GeneratedImage parseImagesResponse(String raw, ToolContext context) throws Exception {
        return responseParser.parseImagesResponse(raw, context);
    }

    private boolean shouldRequestBase64Response(ModelConfig model) {
        String id = ModelContextParser.apiModelId(model == null ? "" : model.getModelId())
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        return !(id.startsWith("gpt-image-") || "chatgpt-image-latest".equals(id));
    }

    private String markdownAlt(String prompt) {
        String value = prompt == null ? "" : prompt.replace('\n', ' ').replace('\r', ' ').replace('[', ' ').replace(']', ' ').trim();
        if (value.length() > 80) {
            value = value.substring(0, 77) + "...";
        }
        return value.length() == 0 ? "生成图片" : value;
    }

    private String trimForModel(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 500) {
            return text.substring(0, 497) + "...";
        }
        return text;
    }
}
