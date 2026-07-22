package cn.lineai.tool.builtin;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.security.SimpleHttpClient;
import cn.lineai.tool.builtin.search.BingRssSearchProvider;
import cn.lineai.tool.builtin.search.BingSearchProvider;
import cn.lineai.tool.builtin.search.BraveSearchProvider;
import cn.lineai.tool.builtin.search.DefaultSearchProvider;
import cn.lineai.tool.builtin.search.SerpApiSearchProvider;
import cn.lineai.tool.builtin.search.TavilySearchProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

final class WebSearchService {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;

    private final WebSearchProviderRegistry providerRegistry;

    WebSearchService() {
        DefaultSearchProvider defaultProvider = new DefaultSearchProvider();
        providerRegistry = new WebSearchProviderRegistry(defaultProvider);
        providerRegistry.register(new BingRssSearchProvider());
        providerRegistry.register(new TavilySearchProvider());
        providerRegistry.register(new SerpApiSearchProvider());
        providerRegistry.register(new BingSearchProvider());
        providerRegistry.register(new BraveSearchProvider());
    }

    List<SearchResultItem> search(WebSearchConfig config, String query, int limit) throws Exception {
        WebSearchConfig value = config == null ? WebSearchConfig.defaultConfig() : config;
        if (value.requiresApiKey()) {
            if (value.getBaseUrl().length() == 0 || value.getApiKey().length() == 0) {
                throw new IllegalStateException("Web search not configured. Please fill in the search API, model/search source and key in MCP tool settings.");
            }
        } else if (value.getBaseUrl().length() == 0 && !WebSearchConfig.PROVIDER_BING_RSS_FREE.equals(value.getProvider())) {
            throw new IllegalStateException("Web search not configured. Please fill in the search API URL in MCP tool settings.");
        }
        int maxResults = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 10));
        String providerId = WebSearchConfig.normalizeProvider(value.getProvider());
        WebSearchProvider provider = providerRegistry.get(providerId);
        SearchRequest searchRequest = provider.buildRequest(value, query, maxResults);
        SimpleHttpClient.Request httpRequest = new SimpleHttpClient.Request(searchRequest.url, searchRequest.method, searchRequest.body);
        httpRequest.connectTimeoutMs = CONNECT_TIMEOUT_MS;
        httpRequest.readTimeoutMs = READ_TIMEOUT_MS;
        httpRequest.headers.putAll(searchRequest.headers);
        SimpleHttpClient.Response response = SimpleHttpClient.execute(httpRequest);
        if (response.code < 200 || response.code >= 300) {
            throw new IllegalStateException("Search API " + response.code + ": " + extractErrorText(response.body));
        }
        List<SearchResultItem> results = parseResults(provider, response.body);
        return results.size() > maxResults ? new ArrayList<>(results.subList(0, maxResults)) : results;
    }

    private List<SearchResultItem> parseResults(WebSearchProvider provider, String body) throws Exception {
        List<SearchResultItem> raw = provider.parseRawResponse(body);
        if (raw != null) {
            return raw;
        }
        JSONObject json = new JSONObject(body);
        return provider.normalizeResults(json);
    }

    String fetchPage(String url, int maxChars) throws Exception {
        String trimmedUrl = url == null ? "" : url.trim();
        int limit = Math.max(1000, Math.min(maxChars <= 0 ? 12000 : maxChars, 30000));
        SimpleHttpClient.Request request = new SimpleHttpClient.Request(trimmedUrl, "GET", null);
        request.connectTimeoutMs = CONNECT_TIMEOUT_MS;
        request.readTimeoutMs = READ_TIMEOUT_MS;
        request.headers.put("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.6");
        request.headers.put("User-Agent", "LineCode/1.0");
        SimpleHttpClient.Response response = SimpleHttpClient.execute(request);
        if (response.code < 200 || response.code >= 300) {
            throw new IllegalStateException("Web request failed " + response.code + ": " + response.message);
        }
        String normalized = response.contentType.toLowerCase(java.util.Locale.ROOT).contains("html") ? htmlToText(response.body) : response.body;
        String compact = normalized.replaceAll("\\n{3,}", "\n\n").trim();
        if (compact.length() == 0) {
            return "Web page content is empty or could not extract body text.";
        }
        if (compact.length() > limit) {
            return compact.substring(0, limit) + "\n\n[Content truncated, original length approx. " + compact.length() + " characters]";
        }
        return compact;
    }

    private String htmlToText(String html) {
        String value = html == null ? "" : html;
        value = value.replaceAll("(?is)<script[\\s\\S]*?</script>", " ");
        value = value.replaceAll("(?is)<style[\\s\\S]*?</style>", " ");
        value = value.replaceAll("(?is)<noscript[\\s\\S]*?</noscript>", " ");
        value = value.replaceAll("(?is)</(p|div|section|article|header|footer|li|h[1-6]|tr)\\s*>", "\n");
        value = value.replaceAll("(?is)<br\\s*/?>", "\n");
        value = value.replaceAll("(?is)<[^>]+>", " ");
        value = value.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return value.replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n[ \\t]+", "\n")
                .trim();
    }

    private String extractErrorText(String text) {
        if (text == null || text.length() == 0) {
            return "Request failed";
        }
        try {
            JSONObject object = new JSONObject(text);
            JSONObject error = object.optJSONObject("error");
            if (error != null && error.optString("message").length() > 0) {
                return error.optString("message");
            }
            if (object.optString("message").length() > 0) {
                return object.optString("message");
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
