package cn.lineai.tool.builtin;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SearchResultItem {
    public final String title;
    public final String url;
    public final String snippet;
    public final String publishedDate;

    public SearchResultItem(String title, String url, String snippet, String publishedDate) {
        this.title = title == null || title.length() == 0 ? "Untitled" : title;
        this.url = url == null ? "" : url;
        this.snippet = snippet == null ? "" : snippet;
        this.publishedDate = publishedDate == null ? "" : publishedDate;
    }

    public static List<SearchResultItem> arrayToResults(JSONArray array, String titleKey, String urlKey, String snippetKey, String dateKey) {
        ArrayList<SearchResultItem> results = new ArrayList<>();
        if (array == null) {
            return results;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String url = first(item, urlKey, "link", "href");
            if (url.length() == 0) {
                continue;
            }
            results.add(new SearchResultItem(
                    first(item, titleKey, "name", "title", url),
                    url,
                    first(item, snippetKey, "description", "content"),
                    first(item, dateKey)
            ));
        }
        return results;
    }

    static String first(JSONObject item, String... keys) {
        for (String key : keys) {
            if (key == null || key.length() == 0) {
                continue;
            }
            String value = item.optString(key, "").trim();
            if (value.length() > 0) {
                return value;
            }
        }
        return "";
    }
}
