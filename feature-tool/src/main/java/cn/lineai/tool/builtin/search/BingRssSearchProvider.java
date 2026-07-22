package cn.lineai.tool.builtin.search;

import android.util.Xml;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchRequest;
import cn.lineai.tool.builtin.SearchResultItem;
import cn.lineai.tool.builtin.WebSearchProvider;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/**
 * Bing 公开 RSS 端点的内置 provider。
 *
 * 端点：https://www.bing.com/search?format=rss&q=<URL编码>&count=10&mkt=<系统语言>&safe=strict
 * - 返回标准 RSS 2.0 XML，10 条结果
 * - 每个 {@code <item>} 含 title、link、description、pubDate
 * - 无反爬，无需 Cookie 或 API Key
 * - mkt 根据系统语言动态拼接（Locale.getDefault() 的 language tag，如 zh-CN、en-US）
 *
 * 用 Android 内置 {@link android.util.Xml} 解析，无需第三方依赖。
 */
public class BingRssSearchProvider implements WebSearchProvider {

    private static final String RSS_BASE_URL = "https://www.bing.com/search?format=rss";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    @Override
    public String providerId() {
        return WebSearchConfig.PROVIDER_BING_RSS_FREE;
    }

    @Override
    public SearchRequest buildRequest(WebSearchConfig config, String query, int limit) throws Exception {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put(config.getQueryParam(), query);
        params.put("count", String.valueOf(Math.max(1, Math.min(limit, 10))));
        params.put("mkt", resolveMarket());
        params.put("safe", "strict");
        String baseUrl = config.getBaseUrl().length() == 0 ? RSS_BASE_URL : config.getBaseUrl();
        String url = SearchRequest.appendQuery(baseUrl, params);
        SearchRequest request = new SearchRequest(url, "GET", null);
        request.headers.put("Accept", "application/rss+xml, application/xml, text/xml, */*");
        request.headers.put("User-Agent", USER_AGENT);
        return request;
    }

    /**
     * 根据系统语言解析 Bing market 参数（如 zh-CN、en-US）。
     * 使用 Locale.getDefault() 的 language tag；缺失时回退 en-US。
     */
    private static String resolveMarket() {
        Locale locale = Locale.getDefault();
        String tag = locale.toLanguageTag();
        return (tag == null || tag.trim().length() == 0) ? "en-US" : tag.trim();
    }

    @Override
    public List<SearchResultItem> normalizeResults(JSONObject response) {
        // RSS 走 parseRawResponse 路径，本方法不会被调用。
        return new ArrayList<>();
    }

    @Override
    public List<SearchResultItem> parseRawResponse(String body) throws Exception {
        return parseRss(body);
    }

    private List<SearchResultItem> parseRss(String xml) throws Exception {
        List<SearchResultItem> results = new ArrayList<>();
        if (xml == null || xml.length() == 0) {
            return results;
        }
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        int event = parser.getEventType();
        String title = "";
        String link = "";
        String description = "";
        String pubDate = "";
        boolean inItem = false;
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("item".equals(name)) {
                    inItem = true;
                    title = "";
                    link = "";
                    description = "";
                    pubDate = "";
                } else if (inItem) {
                    if ("title".equals(name)) {
                        title = safeText(parser.nextText());
                    } else if ("link".equals(name)) {
                        link = safeText(parser.nextText());
                    } else if ("description".equals(name)) {
                        description = stripHtml(safeText(parser.nextText()));
                    } else if ("pubDate".equals(name)) {
                        pubDate = safeText(parser.nextText());
                    }
                }
            } else if (event == XmlPullParser.END_TAG && "item".equals(parser.getName())) {
                inItem = false;
                if (link.length() > 0) {
                    results.add(new SearchResultItem(title, link, description, pubDate));
                }
            }
            event = parser.next();
        }
        return results;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Bing RSS 的 description 字段里可能包含 HTML 标签，做一次轻量清理。
     * 不引入第三方 HTML 解析器，仅去除标签和实体。
     */
    private static String stripHtml(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        String text = value.replaceAll("(?is)<[^>]+>", " ");
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return text.replaceAll("[ \\t]{2,}", " ").trim();
    }
}
