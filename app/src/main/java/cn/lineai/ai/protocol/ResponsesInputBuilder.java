package cn.lineai.ai.protocol;

import cn.lineai.ai.message.ModelMessage;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class ResponsesInputBuilder {
    private ResponsesInputBuilder() {
    }

    static JSONArray inputJson(List<ModelMessage> messages) throws Exception {
        JSONArray array = new JSONArray();
        for (ModelMessage message : messages) {
            if (appendRawInputItem(array, message.getRawInputJson())) {
                continue;
            }
            JSONObject object = new JSONObject();
            if ("tool".equals(message.getRole())) {
                object.put("role", "user");
                object.put("content", toolResultText(message));
            } else {
                object.put("role", message.getRole());
                object.put("content", message.getContent());
            }
            array.put(object);
        }
        return array;
    }

    private static boolean appendRawInputItem(JSONArray target, String rawInputJson) throws Exception {
        if (rawInputJson == null || rawInputJson.trim().length() == 0) {
            return false;
        }
        String raw = rawInputJson.trim();
        if (raw.startsWith("[")) {
            JSONArray items = new JSONArray(raw);
            for (int i = 0; i < items.length(); i++) {
                Object item = items.opt(i);
                if (item != null) {
                    target.put(item);
                }
            }
            return true;
        }
        target.put(new JSONObject(raw));
        return true;
    }

    private static String toolResultText(ModelMessage message) {
        return "<tool_result tool_call_id=\"" + escapeAttribute(message.getToolCallId())
                + "\" name=\"" + escapeAttribute(message.getToolName())
                + "\" is_error=\"" + message.isToolError()
                + "\"><![CDATA[" + escapeCdata(message.getContent()) + "]]></tool_result>";
    }

    private static String escapeAttribute(String value) {
        String text = value == null ? "" : value;
        return text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeCdata(String value) {
        String text = value == null ? "" : value;
        return text.replace("]]>", "]]]]><![CDATA[>");
    }
}
