package cn.lineai.tool.builtin;

import cn.lineai.model.WebSearchConfig;
import java.util.List;
import org.json.JSONObject;

public interface WebSearchProvider {
    String providerId();
    SearchRequest buildRequest(WebSearchConfig config, String query, int limit) throws Exception;
    List<SearchResultItem> normalizeResults(JSONObject response);
}
