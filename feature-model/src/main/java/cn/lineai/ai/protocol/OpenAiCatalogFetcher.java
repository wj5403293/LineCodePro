package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.security.SimpleHttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiCatalogFetcher implements CatalogFetcher {
    @Override
    public List<String> fetch(String baseUrl, String apiKey) throws ModelCompletionException {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Authorization", "Bearer " + apiKey);
            String body = SimpleHttpClient.get(ModelCatalogClient.endpoint(baseUrl, "/models"), 20000, 30000, headers);
            return ModelCatalogClient.parseModelIds(body);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Model list query failed: " + e.getMessage(), e);
        }
    }
}
