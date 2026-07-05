package cn.lineai.tool.builtin.search;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchResultItem;
import cn.lineai.tool.builtin.WebSearchProvider;
import java.util.List;
import org.json.JSONObject;

public class BingSearchProvider extends QueryCountSearchProvider {
    @Override
    public String providerId() {
        return WebSearchConfig.PROVIDER_BING;
    }

    @Override
    public List<SearchResultItem> normalizeResults(JSONObject response) {
        JSONObject webPages = response.optJSONObject("webPages");
        return SearchResultItem.arrayToResults(webPages == null ? null : webPages.optJSONArray("value"), "name", "url", "snippet", "dateLastCrawled");
    }
}
