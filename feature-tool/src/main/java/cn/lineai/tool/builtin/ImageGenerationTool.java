package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelStore;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ModelServiceProvider;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ImageGenerationTool extends BaseTool {
    public static final String NAME = "image_generation";
    private final ImageApiClient apiClient;
    private final ImageResponseParser responseParser;

    public ImageGenerationTool() {
        this(null);
    }

    public ImageGenerationTool(Context context) {
        apiClient = new ImageApiClient();
        responseParser = new ImageResponseParser(apiClient, new Base64ImageValidator());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String promptSupplement(String executionMode, boolean isSsh) {
        return "image_generation 由应用侧生图模型配置执行，不依赖"
                + (isSsh ? "SSH 主机环境" : "终端提供者环境")
                + "；结果会以内联 Markdown 图片返回。";
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
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.IMAGE_GENERATION;
    }

    @Override
    public String getActionName(Context context) {
        return context.getString(R.string.tool_call_image_generation);
    }

    @Override
    public int getActionIcon() {
        return ICON_SPARKLES;
    }

    @Override
    public boolean shouldHideOnSuccess() {
        return true;
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
        ToolSettingsStore settingsRepository = context != null ? context.getToolSettingsStore() : null;
        ModelStore modelRepository = context != null ? context.getModelRepository() : null;
        ModelServiceProvider modelServiceProvider = context != null ? context.getModelServiceProvider() : null;
        if (settingsRepository == null || modelRepository == null) {
            return error(context.getString(R.string.tool_img_gen_no_context));
        }
        String prompt = input == null ? "" : input.optString("prompt").trim();
        if (prompt.length() == 0) {
            return error(context.getString(R.string.tool_img_gen_prompt_empty));
        }
        ModelConfig model = selectedModel(settingsRepository, modelRepository);
        if (model == null) {
            return error(context.getString(R.string.tool_img_gen_no_model));
        }
        if (modelServiceProvider == null) {
            return error(context.getString(R.string.tool_img_gen_no_model_service));
        }
        if (!modelServiceProvider.supportsImageGeneration(model.getProtocolType())) {
            return error(context.getString(R.string.tool_img_gen_unsupported_protocol));
        }
        try {
            if (context != null) {
                context.reportToolProgress(getName(), context.getString(R.string.tool_img_gen_progress), false);
            }
            ImageResponseParser.GeneratedImage image = model.getProtocolType() == ModelProtocolType.CODEX_RESPONSES
                    ? generateWithResponsesApi(model, input, prompt)
                    : generateWithImagesApi(model, input, prompt, context);
            String displayMarkdown = "![" + markdownAlt(prompt, context) + "](" + image.dataUrl + ")";
            String modelContent = context.getString(R.string.tool_img_gen_result, trimForModel(prompt))
                    + (image.revisedPrompt.length() == 0 ? "" : context.getString(R.string.tool_img_gen_revised_prompt, trimForModel(image.revisedPrompt)));
            JSONObject result = new JSONObject()
                    .put("linecode_image_generation", true)
                    .put("display_markdown", displayMarkdown)
                    .put("model_content", modelContent)
                    .put("mime_type", image.mimeType)
                    .put("prompt", prompt)
                    .put("revised_prompt", image.revisedPrompt);
            return ok(result.toString());
        } catch (Exception e) {
            return error(context.getString(R.string.tool_img_gen_failed, e.getMessage()));
        }
    }

    private ModelConfig selectedModel(ToolSettingsStore settingsRepository, ModelStore modelRepository) {
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
        String id = ModelContextParser.apiModelId(model)
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        return !(id.startsWith("gpt-image-") || "chatgpt-image-latest".equals(id));
    }

    private String markdownAlt(String prompt, ToolContext context) {
        String value = prompt == null ? "" : prompt.replace('\n', ' ').replace('\r', ' ').replace('[', ' ').replace(']', ' ').trim();
        if (value.length() > 80) {
            value = value.substring(0, 77) + "...";
        }
        return value.length() == 0 ? context.getString(R.string.tool_img_gen_default_alt) : value;
    }

    private String trimForModel(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 500) {
            return text.substring(0, 497) + "...";
        }
        return text;
    }
}
