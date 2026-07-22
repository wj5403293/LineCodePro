package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OpenAiResponsesCompactionProtocol extends AbstractHttpModelProtocol {
    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        throw new ModelCompletionException("OpenAI Responses compact protocol can only be used for context compaction");
    }

    @Override
    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        throw new ModelCompletionException("OpenAI Responses compact protocol can only be used for context compaction");
    }

    public String compact(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        String raw = "";
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getEffectiveCompressionModelId()));
            body.put("input", ResponsesInputBuilder.inputJson(messages));

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());
            raw = postJson(endpoint(config.getBaseUrl(), "/responses/compact"), body, headers, cancellationToken);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return "";
            }
            JSONObject response = new JSONObject(raw);
            String item = findCompactionItem(response);
            if (item.length() == 0) {
                throw new ModelCompletionException("OpenAI compaction API did not return a compaction item");
            }
            return item;
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            logParseError("parse_openai_compact", raw, e);
            throw new ModelCompletionException("OpenAI compaction API parse failed: " + e.getMessage(), e);
        }
    }

    private String findCompactionItem(JSONObject response) {
        String direct = firstCompactionItem(response.optJSONArray("output"));
        if (direct.length() > 0) {
            return direct;
        }
        JSONObject compaction = response.optJSONObject("compaction");
        if (compaction == null) {
            return "";
        }
        String nested = firstCompactionItem(compaction.optJSONArray("output"));
        if (nested.length() > 0) {
            return nested;
        }
        if ("compaction".equals(compaction.optString("type"))) {
            return compaction.toString();
        }
        return "";
    }

    private String firstCompactionItem(JSONArray output) {
        if (output == null) {
            return "";
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if ("compaction".equals(item.optString("type"))) {
                return item.toString();
            }
        }
        return "";
    }
}
