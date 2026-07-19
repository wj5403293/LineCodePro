package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.security.SimpleHttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CodexCatalogFetcher implements CatalogFetcher {
    @Override
    public List<String> fetch(String baseUrl, String apiKey) throws ModelCompletionException {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("version", CodexResponsesProtocol.CODEX_PROTOCOL_VERSION);
            headers.put("originator", CodexResponsesProtocol.CODEX_ORIGINATOR);
            headers.put("User-Agent", CodexResponsesProtocol.codexUserAgent());
            String url = ModelCatalogClient.appendQuery(
                    ModelCatalogClient.endpoint(baseUrl, "/models"),
                    "client_version", CodexResponsesProtocol.CODEX_PROTOCOL_VERSION);
            String body = SimpleHttpClient.get(url, 20000, 30000, headers);
            return ModelCatalogClient.parseModelIds(body);
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Codex model list query failed: " + e.getMessage(), e);
        }
    }
}
