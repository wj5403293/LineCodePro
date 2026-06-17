package cn.lineai.tool.builtin;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.security.UrlPolicy;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 图片生成 API 客户端。负责端点解析、请求体构建、HTTP 调用与图片下载。
 */
final class ImageApiClient {
    static final int MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024;
    static final int MAX_RESPONSE_BYTES = 24 * 1024 * 1024;
    private static final String CODEX_PROTOCOL_VERSION = "0.120.0";
    private static final String CODEX_ORIGINATOR = "codex_cli_rs";

    String postJson(String url, JSONObject body, Map<String, String> headers) throws Exception {
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

    DownloadedImage downloadImage(String url) throws Exception {
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

    String imagesEndpoint(String baseUrl) {
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

    String responsesEndpoint(String baseUrl) {
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

    JSONObject imagesRequestBody(ModelConfig model, JSONObject input, String prompt, boolean requestBase64) throws Exception {
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

    JSONObject responsesRequestBody(ModelConfig model, JSONObject input, String prompt) throws Exception {
        return new JSONObject()
                .put("model", ModelContextParser.apiModelId(model.getModelId()))
                .put("input", prompt)
                .put("tools", new JSONArray().put(responsesImageGenerationTool(input)))
                .put("tool_choice", new JSONObject().put("type", "image_generation"))
                .put("store", isAzureResponsesEndpoint(model.getBaseUrl()));
    }

    JSONObject responsesImageGenerationTool(JSONObject input) throws Exception {
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

    Map<String, String> authHeaders(ModelConfig model) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + model.getApiKey());
        return headers;
    }

    Map<String, String> codexHeaders(ModelConfig model) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + model.getApiKey());
        headers.put("version", CODEX_PROTOCOL_VERSION);
        headers.put("originator", CODEX_ORIGINATOR);
        headers.put("User-Agent", CODEX_ORIGINATOR + "/" + CODEX_PROTOCOL_VERSION + " (Android; LineCode)");
        return headers;
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

    private void putIfPresent(JSONObject body, String key, String value) throws Exception {
        if (value != null && value.trim().length() > 0) {
            body.put(key, value.trim());
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

    static final class DownloadedImage {
        final String mimeType;
        final byte[] bytes;

        DownloadedImage(String mimeType, byte[] bytes) {
            this.mimeType = mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }
    }
}
