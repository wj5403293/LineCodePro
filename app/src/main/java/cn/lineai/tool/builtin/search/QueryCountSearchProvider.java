package cn.lineai.tool.builtin.search;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchRequest;
import cn.lineai.tool.builtin.WebSearchProvider;
import java.util.LinkedHashMap;

abstract class QueryCountSearchProvider implements WebSearchProvider {
    @Override
    public SearchRequest buildRequest(WebSearchConfig config, String query, int limit) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put(config.getQueryParam(), query);
        params.put("count", String.valueOf(limit));
        SearchRequest request = new SearchRequest(SearchRequest.appendQuery(config.getBaseUrl(), params), "GET", null);
        request.headers.put("Accept", "application/json");
        if (config.getApiKeyHeader().length() > 0) {
            String header = config.getApiKeyHeader();
            request.headers.put(header, "authorization".equalsIgnoreCase(header) ? "Bearer " + config.getApiKey() : config.getApiKey());
        }
        return request;
    }
}
