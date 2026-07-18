package cn.lineai.tool.builtin.search;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchResultItem;
import cn.lineai.tool.builtin.WebSearchProvider;
import java.util.List;
import org.json.JSONObject;

public class BraveSearchProvider extends QueryCountSearchProvider {
    @Override
    public String providerId() {
        return WebSearchConfig.PROVIDER_BRAVE;
    }

    @Override
    public List<SearchResultItem> normalizeResults(JSONObject response) {
        JSONObject web = response.optJSONObject("web");
        return SearchResultItem.arrayToResults(web == null ? null : web.optJSONArray("results"), "title", "url", "description", "age");
    }
}
