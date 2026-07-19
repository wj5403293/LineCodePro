package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.security.SimpleHttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AnthropicCatalogFetcher implements CatalogFetcher {
    @Override
    public List<String> fetch(String baseUrl, String apiKey) throws ModelCompletionException {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", "2023-06-01");
            String body = SimpleHttpClient.get(
                    ModelCatalogClient.endpoint(ModelCatalogClient.rootOrigin(baseUrl), "/v1/models"),
                    20000, 30000, headers);
            return ModelCatalogClient.parseModelIds(body);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Anthropic model list query failed: " + e.getMessage(), e);
        }
    }
}
