package cn.lineai.tool.builtin.search;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchRequest;
import cn.lineai.tool.builtin.SearchResultItem;
import cn.lineai.tool.builtin.WebSearchProvider;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONObject;

public class SerpApiSearchProvider implements WebSearchProvider {
    @Override
    public String providerId() {
        return WebSearchConfig.PROVIDER_SERPAPI;
    }

    @Override
    public SearchRequest buildRequest(WebSearchConfig config, String query, int limit) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put(config.getQueryParam(), query);
        params.put("engine", config.getModel().length() == 0 ? "google" : config.getModel());
        params.put(config.getApiKeyParam().length() == 0 ? "api_key" : config.getApiKeyParam(), config.getApiKey());
        params.put("num", String.valueOf(limit));
        SearchRequest request = new SearchRequest(SearchRequest.appendQuery(config.getBaseUrl(), params), "GET", null);
        request.headers.put("Accept", "application/json");
        if (config.getApiKeyHeader().length() > 0) {
            String header = config.getApiKeyHeader();
            request.headers.put(header, "authorization".equalsIgnoreCase(header) ? "Bearer " + config.getApiKey() : config.getApiKey());
        }
        return request;
    }

    @Override
    public List<SearchResultItem> normalizeResults(JSONObject response) {
        return SearchResultItem.arrayToResults(response.optJSONArray("organic_results"), "title", "link", "snippet", "date");
    }
}
