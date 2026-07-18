package cn.lineai.tool.builtin.search;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchRequest;
import cn.lineai.tool.builtin.SearchResultItem;
import cn.lineai.tool.builtin.WebSearchProvider;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class DefaultSearchProvider implements WebSearchProvider {
    @Override
    public String providerId() {
        return "default";
    }

    @Override
    public SearchRequest buildRequest(WebSearchConfig config, String query, int limit) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put(config.getQueryParam(), query);
        if (config.getModel().length() > 0) {
            params.put("model", config.getModel());
        }
        if (config.getApiKeyParam().length() > 0) {
            params.put(config.getApiKeyParam(), config.getApiKey());
        }
        params.put("limit", String.valueOf(limit));
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
        JSONArray candidates = response.optJSONArray("results");
        if (candidates == null) candidates = response.optJSONArray("items");
        if (candidates == null) candidates = response.optJSONArray("data");
        if (candidates == null && response.optJSONObject("web") != null) {
            candidates = response.optJSONObject("web").optJSONArray("results");
        }
        if (candidates == null) candidates = response.optJSONArray("organic_results");
        return SearchResultItem.arrayToResults(candidates, "title", "url", "snippet", "publishedDate");
    }
}
