package cn.lineai.tool.builtin;

import java.util.HashMap;
import java.util.Map;

public class WebSearchProviderRegistry {
    private final Map<String, WebSearchProvider> providers = new HashMap<>();
    private final WebSearchProvider defaultProvider;

    public WebSearchProviderRegistry(WebSearchProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public void register(WebSearchProvider provider) {
        providers.put(provider.providerId(), provider);
    }

    public WebSearchProvider get(String providerId) {
        return providers.getOrDefault(providerId, defaultProvider);
    }
}
