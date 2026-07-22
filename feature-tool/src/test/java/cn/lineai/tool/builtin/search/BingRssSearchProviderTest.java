package cn.lineai.tool.builtin.search;

import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.builtin.SearchRequest;
import org.junit.Assert;
import org.junit.Test;

public final class BingRssSearchProviderTest {

    @Test
    public void providerIdIsBingRssFree() {
        Assert.assertEquals(WebSearchConfig.PROVIDER_BING_RSS_FREE, new BingRssSearchProvider().providerId());
    }

    @Test
    public void buildRequestUsesBingRssEndpointWithRequiredParameters() throws Exception {
        WebSearchConfig config = WebSearchConfig.defaultConfig(WebSearchConfig.PROVIDER_BING_RSS_FREE);
        SearchRequest request = new BingRssSearchProvider().buildRequest(config, "LineAI 编程", 7);

        Assert.assertNotNull(request.url);
        Assert.assertTrue("URL 应使用 https://www.bing.com/search?format=rss",
                request.url.startsWith("https://www.bing.com/search?format=rss"));
        Assert.assertTrue("URL 应包含 count=7", request.url.contains("count=7"));
        Assert.assertTrue("URL 应包含动态拼接的 mkt 参数（按系统语言）", request.url.matches(".*[?&]mkt=[A-Za-z-]+(&.*)?"));
        Assert.assertTrue("URL 应包含 safe=strict", request.url.contains("safe=strict"));
        Assert.assertTrue("URL 应对查询参数进行 URL 编码", request.url.contains("q=LineAI"));
        Assert.assertEquals("GET", request.method);
        Assert.assertTrue("应携带 User-Agent 头", request.headers.containsKey("User-Agent"));
    }

    @Test
    public void buildRequestHonoursCustomBaseUrlWhenProvided() throws Exception {
        WebSearchConfig config = new WebSearchConfig(
                WebSearchConfig.PROVIDER_BING_RSS_FREE,
                "https://example.com/rss",
                "", "", "q", "", "");
        SearchRequest request = new BingRssSearchProvider().buildRequest(config, "hello", 5);

        Assert.assertTrue(request.url.startsWith("https://example.com/rss"));
    }

    @Test
    public void buildRequestClampsLimitToTen() throws Exception {
        WebSearchConfig config = WebSearchConfig.defaultConfig(WebSearchConfig.PROVIDER_BING_RSS_FREE);
        SearchRequest request = new BingRssSearchProvider().buildRequest(config, "test", 999);

        Assert.assertTrue("count 应被限制为 10", request.url.contains("count=10"));
    }
}
