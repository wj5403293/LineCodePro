package cn.lineai.tool.builtin;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.security.UrlPolicy;
import cn.lineai.tool.builtin.search.BingSearchProvider;
import cn.lineai.tool.builtin.search.BraveSearchProvider;
import cn.lineai.tool.builtin.search.DefaultSearchProvider;
import cn.lineai.tool.builtin.search.SerpApiSearchProvider;
import cn.lineai.tool.builtin.search.TavilySearchProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
        providerRegistry.register(new TavilySearchProvider());
        providerRegistry.register(new SerpApiSearchProvider());
        providerRegistry.register(new BingSearchProvider());
        providerRegistry.register(new BraveSearchProvider());
    }

    List<SearchResultItem> search(WebSearchConfig config, String query, int limit) throws Exception {
        WebSearchConfig value = config == null ? WebSearchConfig.defaultConfig() : config;
        if (value.getBaseUrl().length() == 0 || value.getApiKey().length() == 0) {
            throw new IllegalStateException("网页搜索未配置。请在 MCP 工具设置中填写搜索 API、模型/搜索源和密钥。");
        }
        int maxResults = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 10));
        String providerId = WebSearchConfig.normalizeProvider(value.getProvider());
        WebSearchProvider provider = providerRegistry.get(providerId);
        SearchRequest searchRequest = provider.buildRequest(value, query, maxResults);
        HttpRequest httpRequest = toHttpRequest(searchRequest);
        HttpResponse response = request(httpRequest);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new IllegalStateException("搜索 API " + response.statusCode + ": " + extractErrorText(response.body));
        }
        JSONObject json = new JSONObject(response.body);
        List<SearchResultItem> results = provider.normalizeResults(json);
        return results.size() > maxResults ? new ArrayList<>(results.subList(0, maxResults)) : results;
    }

    String fetchPage(String url, int maxChars) throws Exception {
        String trimmedUrl = url == null ? "" : url.trim();
        String safeUrl = UrlPolicy.requireHttpOrLocalCleartextUrl(trimmedUrl, "URL");
        int limit = Math.max(1000, Math.min(maxChars <= 0 ? 12000 : maxChars, 30000));
        HttpRequest request = new HttpRequest(safeUrl, "GET", null);
        request.headers.put("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.6");
        request.headers.put("User-Agent", "LineCode/1.0");
        HttpResponse response = request(request);
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new IllegalStateException("网页请求失败 " + response.statusCode + ": " + response.message);
        }
        String normalized = response.contentType.toLowerCase(java.util.Locale.ROOT).contains("html") ? htmlToText(response.body) : response.body;
        String compact = normalized.replaceAll("\\n{3,}", "\n\n").trim();
        if (compact.length() == 0) {
            return "网页内容为空或无法提取正文。";
        }
        if (compact.length() > limit) {
            return compact.substring(0, limit) + "\n\n[内容已截断，原始长度约 " + compact.length() + " 字符]";
        }
        return compact;
    }

    private HttpRequest toHttpRequest(SearchRequest searchRequest) {
        HttpRequest httpRequest = new HttpRequest(searchRequest.url, searchRequest.method, searchRequest.body);
        httpRequest.headers.putAll(searchRequest.headers);
        return httpRequest;
    }

    private HttpResponse request(HttpRequest request) throws Exception {
        String safeUrl = UrlPolicy.requireHttpOrLocalCleartextUrl(request.url, "URL");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(safeUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod(request.method);
            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if (request.body != null) {
                connection.setDoOutput(true);
                byte[] bytes = request.body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(bytes);
                } finally {
                    output.close();
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new HttpResponse(code, connection.getResponseMessage(), safe(connection.getContentType()), read(stream));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String read(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
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
            return "请求失败";
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

    private static final class HttpRequest {
        private final String url;
        private final String method;
        private final String body;
        private final LinkedHashMap<String, String> headers = new LinkedHashMap<>();

        HttpRequest(String url, String method, String body) {
            this.url = url;
            this.method = method;
            this.body = body;
        }
    }

    private static final class HttpResponse {
        private final int statusCode;
        private final String message;
        private final String contentType;
        private final String body;

        HttpResponse(int statusCode, String message, String contentType, String body) {
            this.statusCode = statusCode;
            this.message = message == null ? "" : message;
            this.contentType = contentType == null ? "" : contentType;
            this.body = body == null ? "" : body;
        }
    }
}
