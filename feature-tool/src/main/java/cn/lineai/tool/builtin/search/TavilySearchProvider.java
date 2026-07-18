package cn.lineai.tool.builtin.search;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchRequest;
import cn.lineai.tool.builtin.SearchResultItem;
import cn.lineai.tool.builtin.WebSearchProvider;
import java.util.List;
import org.json.JSONObject;

public class TavilySearchProvider implements WebSearchProvider {
    @Override
    public String providerId() {
        return WebSearchConfig.PROVIDER_TAVILY;
    }

    @Override
    public SearchRequest buildRequest(WebSearchConfig config, String query, int limit) throws Exception {
        JSONObject body = new JSONObject()
                .put("query", query)
                .put("max_results", limit)
                .put("search_depth", config.getModel().length() == 0 ? "basic" : config.getModel())
                .put("include_answer", false);
        SearchRequest request = new SearchRequest(config.getBaseUrl(), "POST", body.toString());
        request.headers.put("Content-Type", "application/json");
        request.headers.put("Authorization", "Bearer " + config.getApiKey());
        return request;
    }

    @Override
    public List<SearchResultItem> normalizeResults(JSONObject response) {
        return SearchResultItem.arrayToResults(response.optJSONArray("results"), "title", "url", "content", "published_date");
    }
}
