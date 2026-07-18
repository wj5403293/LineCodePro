package cn.lineai.ai.protocol.reasoning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class ReasoningDeltaExtractor {
    private final List<DeltaStrategy> strategies;

    public ReasoningDeltaExtractor() {
        strategies = new ArrayList<>();
        strategies.add(new ReasoningContentStrategy());
        strategies.add(new ReasoningFieldStrategy());
        strategies.add(new ReasoningDetailsStrategy());
    }

    public String extract(JSONObject delta) {
        for (DeltaStrategy strategy : strategies) {
            String result = strategy.extract(delta);
            if (result.length() > 0) {
                return result;
            }
        }
        return "";
    }

    interface DeltaStrategy {
        String extract(JSONObject delta);
    }

    private static final class ReasoningContentStrategy implements DeltaStrategy {
        @Override
        public String extract(JSONObject delta) {
            if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                return delta.optString("reasoning_content");
            }
            return "";
        }
    }

    private static final class ReasoningFieldStrategy implements DeltaStrategy {
        @Override
        public String extract(JSONObject delta) {
            Object reasoning = delta.opt("reasoning");
            if (reasoning instanceof String) {
                return (String) reasoning;
            }
            if (reasoning instanceof JSONObject) {
                JSONObject object = (JSONObject) reasoning;
                if (object.has("content")) {
                    return object.optString("content");
                }
                if (object.has("text")) {
                    return object.optString("text");
                }
            }
            return "";
        }
    }

    private static final class ReasoningDetailsStrategy implements DeltaStrategy {
        @Override
        public String extract(JSONObject delta) {
            Object details = delta.opt("reasoning_details");
            if (details instanceof JSONObject) {
                return fromObject((JSONObject) details);
            }
            if (details instanceof JSONArray) {
                return fromArray((JSONArray) details);
            }
            return "";
        }

        private static String fromObject(JSONObject object) {
            if (object.has("content")) {
                return object.optString("content");
            }
            if (object.has("text")) {
                return object.optString("text");
            }
            if (object.has("reasoning_content")) {
                return object.optString("reasoning_content");
            }
            return "";
        }

        private static String fromArray(JSONArray array) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof String) {
                    builder.append((String) item);
                } else if (item instanceof JSONObject) {
                    builder.append(fromObject((JSONObject) item));
                }
            }
            return builder.toString();
        }
    }
}
