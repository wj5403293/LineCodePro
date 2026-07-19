package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.security.SimpleHttpClient;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ModelCatalogClient {
    private final EnumMap<ModelProtocolType, CatalogFetcher> registry = new EnumMap<>(ModelProtocolType.class);

    public ModelCatalogClient() {
        registry.put(ModelProtocolType.OPENAI_COMPATIBLE, new OpenAiCatalogFetcher());
        registry.put(ModelProtocolType.CODEX_RESPONSES, new CodexCatalogFetcher());
        registry.put(ModelProtocolType.ANTHROPIC_MESSAGES, new AnthropicCatalogFetcher());
    }

    /** 注册或替换指定协议类型的目录获取策略。 */
    public void register(ModelProtocolType type, CatalogFetcher fetcher) {
        if (type != null && fetcher != null) {
            registry.put(type, fetcher);
        }
    }

    public List<String> fetch(ModelProtocolType protocolType, String baseUrl, String apiKey) throws ModelCompletionException {
        CatalogFetcher fetcher = registry.get(protocolType);
        if (fetcher != null) {
            return fetcher.fetch(baseUrl, apiKey);
        }
        fetcher = registry.get(ModelProtocolType.OPENAI_COMPATIBLE);
        if (fetcher != null) {
            return fetcher.fetch(baseUrl, apiKey);
        }
        throw new ModelCompletionException("No catalog fetcher registered for " + protocolType);
    }

    // ---- Shared utility methods (package-private static) ----

    static List<String> parseModelIds(String raw) throws Exception {
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

    static String endpoint(String baseUrl, String suffix) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(suffix)) {
            return base;
        }
        return base + suffix;
    }

    static String appendQuery(String baseUrl, String key, String value) throws Exception {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + URLEncoder.encode(key, "UTF-8")
                + "="
                + URLEncoder.encode(value, "UTF-8");
    }

    static String rootOrigin(String baseUrl) throws Exception {
        String base = baseUrl == null || baseUrl.trim().length() == 0 ? "https://api.anthropic.com" : baseUrl.trim();
        if (!base.contains("://")) {
            base = "https://" + base;
        }
        java.net.URL url = new java.net.URL(base);
        return url.getProtocol() + "://" + url.getHost() + (url.getPort() >= 0 ? ":" + url.getPort() : "");
    }
}
