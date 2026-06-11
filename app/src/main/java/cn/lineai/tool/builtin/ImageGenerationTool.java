package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelRepository;
import cn.lineai.security.UrlPolicy;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ImageGenerationTool extends BaseTool {
    private static final int MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 24 * 1024 * 1024;
    private static final String CODEX_PROTOCOL_VERSION = "0.120.0";
    private static final String CODEX_ORIGINATOR = "codex_cli_rs";

    private final ToolSettingsRepository settingsRepository;
    private final ModelRepository modelRepository;

    public ImageGenerationTool() {
        this(null);
    }

    public ImageGenerationTool(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        settingsRepository = appContext == null ? null : new ToolSettingsRepository(appContext);
        modelRepository = appContext == null ? null : new ModelRepository(appContext);
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
            GeneratedImage image = model.getProtocolType() == ModelProtocolType.CODEX_RESPONSES
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

    private GeneratedImage generateWithImagesApi(ModelConfig model, JSONObject input, String prompt, ToolContext context) throws Exception {
        boolean requestBase64 = shouldRequestBase64Response(model);
        JSONObject body = imagesRequestBody(model, input, prompt, requestBase64);
        String raw;
        try {
            raw = postJson(imagesEndpoint(model.getBaseUrl()), body, authHeaders(model));
        } catch (Exception e) {
            if (!requestBase64) {
                throw e;
            }
            body = imagesRequestBody(model, input, prompt, false);
            raw = postJson(imagesEndpoint(model.getBaseUrl()), body, authHeaders(model));
        }
        return parseImagesResponse(raw, context);
    }

    private GeneratedImage generateWithResponsesApi(ModelConfig model, JSONObject input, String prompt) throws Exception {
        JSONObject body = responsesRequestBody(model, input, prompt);
        String raw = postJson(responsesEndpoint(model.getBaseUrl()), body, codexHeaders(model));
        return parseResponsesImage(raw);
    }

    private JSONObject responsesRequestBody(ModelConfig model, JSONObject input, String prompt) throws Exception {
        return new JSONObject()
                .put("model", ModelContextParser.apiModelId(model.getModelId()))
                .put("input", prompt)
                .put("tools", new JSONArray().put(responsesImageGenerationTool(input)))
                .put("tool_choice", new JSONObject().put("type", "image_generation"))
                .put("store", isAzureResponsesEndpoint(model.getBaseUrl()));
    }

    private JSONObject imagesRequestBody(ModelConfig model, JSONObject input, String prompt, boolean requestBase64) throws Exception {
        JSONObject body = new JSONObject()
                .put("model", ModelContextParser.apiModelId(model.getModelId()))
                .put("prompt", prompt)
                .put("n", 1);
        String size = input == null ? "" : input.optString("size").trim();
        body.put("size", size.length() == 0 ? "1024x1024" : size);
        putIfPresent(body, "quality", input == null ? "" : input.optString("quality").trim());
        putIfPresent(body, "background", input == null ? "" : input.optString("background").trim());
        if (requestBase64) {
            body.put("response_format", "b64_json");
        }
        return body;
    }

    private JSONObject responsesImageGenerationTool(JSONObject input) throws Exception {
        JSONObject tool = new JSONObject()
                .put("type", "image_generation")
                .put("action", "generate");
        String size = input == null ? "" : input.optString("size").trim();
        if (size.length() > 0) {
            tool.put("size", size);
        }
        putIfPresent(tool, "quality", input == null ? "" : input.optString("quality").trim());
        putIfPresent(tool, "background", input == null ? "" : input.optString("background").trim());
        return tool;
    }

    private void putIfPresent(JSONObject body, String key, String value) throws Exception {
        if (value != null && value.trim().length() > 0) {
            body.put(key, value.trim());
        }
    }

    private Map<String, String> authHeaders(ModelConfig model) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + model.getApiKey());
        return headers;
    }

    private Map<String, String> codexHeaders(ModelConfig model) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + model.getApiKey());
        headers.put("version", CODEX_PROTOCOL_VERSION);
        headers.put("originator", CODEX_ORIGINATOR);
        headers.put("User-Agent", CODEX_ORIGINATOR + "/" + CODEX_PROTOCOL_VERSION + " (Android; LineCode)");
        return headers;
    }

    private String postJson(String url, JSONObject body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    UrlPolicy.requireHttpOrLocalCleartextUrl(url, "图片生成 API 地址")
            ).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(180000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getValue() != null && header.getValue().length() > 0) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(body.toString());
            writer.flush();
            writer.close();
            int code = connection.getResponseCode();
            String response = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(), MAX_RESPONSE_BYTES);
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + response);
            }
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private GeneratedImage parseImagesResponse(String raw, ToolContext context) throws Exception {
        JSONObject response = new JSONObject(raw);
        JSONArray data = response.optJSONArray("data");
        if (data == null || data.length() == 0 || data.optJSONObject(0) == null) {
            throw new Exception("接口没有返回图片数据。");
        }
        JSONObject item = data.getJSONObject(0);
        String revisedPrompt = item.optString("revised_prompt").trim();
        boolean invalidImageData = false;
        String b64 = optNonNullString(item, "b64_json");
        String mimeType = optNonNullString(item, "mime_type");
        if (mimeType.length() == 0 || isNullLikeString(mimeType)) {
            mimeType = "image/png";
        }
        if (b64.length() > 0) {
            if (isUsableBase64ImagePayload(b64)) {
                return new GeneratedImage(mimeType, "data:" + mimeType + ";base64," + b64, revisedPrompt);
            }
            invalidImageData = true;
        }
        String dataUrl = optNonNullString(item, "data_url");
        if (isImageDataUrl(dataUrl)) {
            if (isUsableImageDataUrl(dataUrl)) {
                return new GeneratedImage(mimeFromDataUrl(dataUrl), dataUrl, revisedPrompt);
            }
            invalidImageData = true;
        }
        String url = optNonNullString(item, "url");
        if (isNullLikeString(url)) {
            invalidImageData = true;
            url = "";
        }
        if (url.length() == 0) {
            if (invalidImageData) {
                throw new Exception("接口返回了无效的图片数据。");
            }
            throw new Exception("接口没有返回 b64_json、data_url 或 url。");
        }
        if (context != null) {
            context.reportToolProgress(getName(), "正在读取生成图片...", false);
        }
        DownloadedImage downloaded = downloadImage(url);
        return new GeneratedImage(downloaded.mimeType,
                "data:" + downloaded.mimeType + ";base64," + android.util.Base64.encodeToString(downloaded.bytes, android.util.Base64.NO_WRAP),
                revisedPrompt);
    }

    private GeneratedImage parseResponsesImage(String raw) throws Exception {
        JSONObject response = new JSONObject(raw);
        JSONObject error = response.optJSONObject("error");
        if (error != null) {
            String message = error.optString("message").trim();
            throw new Exception(message.length() == 0 ? error.toString() : message);
        }
        JSONArray output = response.optJSONArray("output");
        if (output == null || output.length() == 0) {
            throw new Exception("Responses API 没有返回 output。");
        }
        boolean invalidImageData = false;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null || !"image_generation_call".equals(item.optString("type"))) {
                continue;
            }
            String result = optNonNullString(item, "result");
            if (isImageDataUrl(result)) {
                if (isUsableImageDataUrl(result)) {
                    return new GeneratedImage(mimeFromDataUrl(result), result, item.optString("revised_prompt").trim());
                }
                invalidImageData = true;
                continue;
            }
            if (result.length() > 0) {
                if (isUsableBase64ImagePayload(result)) {
                    return new GeneratedImage("image/png", "data:image/png;base64," + result, item.optString("revised_prompt").trim());
                }
                invalidImageData = true;
                continue;
            }
            String status = item.optString("status").trim();
            if ("failed".equals(status)) {
                throw new Exception("Responses API 图片生成失败。");
            }
        }
        if (invalidImageData) {
            throw new Exception("Responses API 返回了无效的图片数据。");
        }
        throw new Exception("Responses API 没有返回 image_generation_call.result。");
    }

    private DownloadedImage downloadImage(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    UrlPolicy.requireHttpOrLocalCleartextUrl(url, "生成图片地址")
            ).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(120000);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("下载生成图片失败: HTTP " + code);
            }
            String mimeType = connection.getContentType();
            if (mimeType == null || !mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                mimeType = "image/png";
            } else {
                int semicolon = mimeType.indexOf(';');
                if (semicolon > 0) {
                    mimeType = mimeType.substring(0, semicolon).trim();
                }
            }
            return new DownloadedImage(mimeType, readBytes(connection.getInputStream(), MAX_DOWNLOAD_BYTES));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream input, int maxBytes) throws Exception {
        return new String(readBytes(input, maxBytes), StandardCharsets.UTF_8);
    }

    private byte[] readBytes(InputStream input, int maxBytes) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new Exception("图片数据过大，当前上限为 " + (maxBytes / 1024 / 1024) + " MB。");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private String imagesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/images/generations";
        }
        if (base.endsWith("/responses")) {
            return base.substring(0, base.length() - "/responses".length()) + "/images/generations";
        }
        if (base.endsWith("/images/generations")) {
            return base;
        }
        return base + "/images/generations";
    }

    private String responsesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/responses";
        }
        if (base.endsWith("/images/generations")) {
            return base.substring(0, base.length() - "/images/generations".length()) + "/responses";
        }
        if (base.endsWith("/responses")) {
            return base;
        }
        return base + "/responses";
    }

    private boolean shouldRequestBase64Response(ModelConfig model) {
        String id = ModelContextParser.apiModelId(model == null ? "" : model.getModelId())
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        return !(id.startsWith("gpt-image-") || "chatgpt-image-latest".equals(id));
    }

    private boolean isAzureResponsesEndpoint(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        String normalized = baseUrl.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("openai.azure.")
                || normalized.contains("cognitiveservices.azure.")
                || normalized.contains("aoai.azure.")
                || normalized.contains("azure-api.")
                || normalized.contains("azurefd.")
                || normalized.contains("windows.net/openai");
    }

    private String mimeFromDataUrl(String dataUrl) {
        int colon = dataUrl.indexOf(':');
        int semicolon = dataUrl.indexOf(';');
        if (colon >= 0 && semicolon > colon) {
            return dataUrl.substring(colon + 1, semicolon);
        }
        return "image/png";
    }

    private boolean isImageDataUrl(String value) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).startsWith("data:image/");
    }

    private boolean isUsableImageDataUrl(String value) {
        String dataUrl = value == null ? "" : value.trim();
        if (!isImageDataUrl(dataUrl)) {
            return false;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return false;
        }
        String metadata = dataUrl.substring(0, comma).toLowerCase(java.util.Locale.ROOT);
        return metadata.contains(";base64")
                && isUsableBase64ImagePayload(dataUrl.substring(comma + 1));
    }

    private boolean isUsableBase64ImagePayload(String value) {
        String payload = value == null ? "" : value.trim();
        if (payload.length() == 0 || isNullLikeString(payload)) {
            return false;
        }
        return hasKnownImageSignature(decodedBase64Prefix(payload, 16));
    }

    private byte[] decodedBase64Prefix(String value, int maxBytes) {
        byte[] output = new byte[Math.max(0, maxBytes)];
        int count = 0;
        int buffer = 0;
        int bits = 0;
        boolean sawPadding = false;
        for (int i = 0; i < value.length() && count < output.length; i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '=') {
                sawPadding = true;
                continue;
            }
            int sixBits = base64Value(c);
            if (sixBits < 0 || sawPadding) {
                return new byte[0];
            }
            buffer = (buffer << 6) | sixBits;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                output[count] = (byte) ((buffer >> bits) & 0xff);
                count++;
            }
        }
        return Arrays.copyOf(output, count);
    }

    private int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == '+' || c == '-') {
            return 62;
        }
        if (c == '/' || c == '_') {
            return 63;
        }
        return -1;
    }

    private boolean hasKnownImageSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return false;
        }
        return startsWith(bytes, new int[] {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})
                || startsWith(bytes, new int[] {0xff, 0xd8, 0xff})
                || startsWith(bytes, new int[] {'G', 'I', 'F', '8'})
                || startsWith(bytes, new int[] {'B', 'M'})
                || (bytes.length >= 12
                && startsWith(bytes, new int[] {'R', 'I', 'F', 'F'})
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P')
                || (bytes.length >= 12
                && bytes[4] == 'f'
                && bytes[5] == 't'
                && bytes[6] == 'y'
                && bytes[7] == 'p'
                && bytes[8] == 'a'
                && bytes[9] == 'v'
                && bytes[10] == 'i'
                && bytes[11] == 'f');
    }

    private boolean startsWith(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xff) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String optNonNullString(JSONObject object, String key) {
        if (object == null) {
            return "";
        }
        String value = object.optString(key, "").trim();
        return value;
    }

    private boolean isNullLikeString(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return "null".equals(normalized) || "undefined".equals(normalized);
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

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }

    private static final class GeneratedImage {
        private final String mimeType;
        private final String dataUrl;
        private final String revisedPrompt;

        private GeneratedImage(String mimeType, String dataUrl, String revisedPrompt) {
            this.mimeType = mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType;
            this.dataUrl = dataUrl == null ? "" : dataUrl;
            this.revisedPrompt = revisedPrompt == null ? "" : revisedPrompt;
        }
    }

    private static final class DownloadedImage {
        private final String mimeType;
        private final byte[] bytes;

        private DownloadedImage(String mimeType, byte[] bytes) {
            this.mimeType = mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }
    }
}
