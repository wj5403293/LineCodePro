package cn.lineai.tool.builtin;

import java.net.URLEncoder;
import java.util.LinkedHashMap;

public final class SearchRequest {
    public final String url;
    public final String method;
    public final String body;
    public final LinkedHashMap<String, String> headers = new LinkedHashMap<>();

    public SearchRequest(String url, String method, String body) {
        this.url = url;
        this.method = method;
        this.body = body;
    }

    public static String appendQuery(String baseUrl, LinkedHashMap<String, String> params) throws Exception {
        StringBuilder query = new StringBuilder();
        for (LinkedHashMap.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0 || entry.getValue() == null || entry.getValue().length() == 0) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        if (query.length() == 0) {
            return baseUrl;
        }
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + query;
    }
}
