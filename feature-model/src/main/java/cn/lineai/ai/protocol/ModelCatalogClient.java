package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.security.SimpleHttpClient;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ModelCatalogClient {
    public List<String> fetch(ModelProtocolType protocolType, String baseUrl, String apiKey) throws ModelCompletionException {
        if (protocolType == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return fetchAnthropic(baseUrl, apiKey);
        }
        if (protocolType == ModelProtocolType.CODEX_RESPONSES) {
            return fetchCodex(baseUrl, apiKey);
        }
        return fetchOpenAiCompatible(baseUrl, apiKey);
    }

    private List<String> fetchOpenAiCompatible(String baseUrl, String apiKey) throws ModelCompletionException {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Authorization", "Bearer " + apiKey);
            String body = SimpleHttpClient.get(endpoint(baseUrl, "/models"), 20000, 30000, headers);
            return parseModelIds(body);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("模型列表查询失败: " + e.getMessage(), e);
        }
    }

    private List<String> fetchCodex(String baseUrl, String apiKey) throws ModelCompletionException {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("version", CodexResponsesProtocol.CODEX_PROTOCOL_VERSION);
            headers.put("originator", CodexResponsesProtocol.CODEX_ORIGINATOR);
            headers.put("User-Agent", CodexResponsesProtocol.codexUserAgent());
            String url = appendQuery(endpoint(baseUrl, "/models"),
                    "client_version", CodexResponsesProtocol.CODEX_PROTOCOL_VERSION);
            String body = SimpleHttpClient.get(url, 20000, 30000, headers);
            return parseModelIds(body);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex 模型列表查询失败: " + e.getMessage(), e);
        }
    }

    private List<String> fetchAnthropic(String baseUrl, String apiKey) throws ModelCompletionException {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", "2023-06-01");
            String body = SimpleHttpClient.get(endpoint(rootOrigin(baseUrl), "/v1/models"), 20000, 30000, headers);
            return parseModelIds(body);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Anthropic 模型列表查询失败: " + e.getMessage(), e);
        }
    }

    private List<String> parseModelIds(String raw) throws Exception {
        JSONObject body = new JSONObject(raw);
        JSONArray data = body.optJSONArray("data");
        ArrayList<String> ids = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id");
                if (id.length() > 0) {
                    ids.add(id);
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    private String endpoint(String baseUrl, String suffix) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(suffix)) {
            return base;
        }
        return base + suffix;
    }

    private String appendQuery(String baseUrl, String key, String value) throws Exception {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + URLEncoder.encode(key, "UTF-8")
                + "="
                + URLEncoder.encode(value, "UTF-8");
    }

    private String rootOrigin(String baseUrl) throws Exception {
        String base = baseUrl == null || baseUrl.trim().length() == 0 ? "https://api.anthropic.com" : baseUrl.trim();
        if (!base.contains("://")) {
            base = "https://" + base;
        }
        java.net.URL url = new java.net.URL(base);
        return url.getProtocol() + "://" + url.getHost() + (url.getPort() >= 0 ? ":" + url.getPort() : "");
    }
}
