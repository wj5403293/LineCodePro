package cn.lineai.tool.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.lang.reflect.Method;
import org.json.JSONObject;
import org.junit.Test;

public final class ImageGenerationToolTest {
    @Test
    public void responsesEndpointDerivesFromCommonBaseUrls() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();

        assertEquals("https://api.openai.com/v1/responses",
                invokeString(tool, "responsesEndpoint", "https://api.openai.com/v1"));
        assertEquals("https://api.openai.com/v1/responses",
                invokeString(tool, "responsesEndpoint", "https://api.openai.com/v1/chat/completions"));
        assertEquals("https://api.openai.com/v1/responses",
                invokeString(tool, "responsesEndpoint", "https://api.openai.com/v1/images/generations"));
    }

    @Test
    public void responsesImageToolForcesGenerationAction() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();
        JSONObject input = new JSONObject()
                .put("size", "1024x1536")
                .put("quality", "high")
                .put("background", "transparent");

        JSONObject imageTool = invokeJson(tool, "responsesImageGenerationTool", input);

        assertEquals("image_generation", imageTool.getString("type"));
        assertEquals("generate", imageTool.getString("action"));
        assertEquals("1024x1536", imageTool.getString("size"));
        assertEquals("high", imageTool.getString("quality"));
        assertEquals("transparent", imageTool.getString("background"));
    }

    @Test
    public void responsesRequestBodyForcesImageGenerationToolChoice() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();

        JSONObject body = invokeJson(tool, "responsesRequestBody",
                model("gpt-5"), new JSONObject(), "draw a cat");
        JSONObject toolChoice = body.getJSONObject("tool_choice");
        JSONObject imageTool = body.getJSONArray("tools").getJSONObject(0);

        assertEquals("image_generation", toolChoice.getString("type"));
        assertEquals("image_generation", imageTool.getString("type"));
        assertEquals("generate", imageTool.getString("action"));
    }

    @Test
    public void gptImageRequestAvoidsDeprecatedResponseFormat() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();
        JSONObject input = new JSONObject().put("size", "1024x1024");

        JSONObject body = invokeJson(tool, "imagesRequestBody",
                model("gpt-image-1"), input, "draw a cat", false);

        assertFalse(body.has("response_format"));
    }

    @Test
    public void legacyImageRequestCanAskForBase64() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();
        JSONObject input = new JSONObject().put("size", "1024x1024");

        JSONObject body = invokeJson(tool, "imagesRequestBody",
                model("dall-e-3"), input, "draw a cat", true);

        assertTrue(body.has("response_format"));
        assertEquals("b64_json", body.getString("response_format"));
    }

    private static ModelConfig model(String modelId) {
        return new ModelConfig("id", "name", ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI", "https://api.openai.com/v1", "key", modelId);
    }

    private static String invokeString(ImageGenerationTool tool, String methodName, String value) throws Exception {
        Method method = ImageGenerationTool.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        return (String) method.invoke(tool, value);
    }

    private static JSONObject invokeJson(ImageGenerationTool tool, String methodName, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] instanceof Boolean ? boolean.class : args[i].getClass();
        }
        Method method = ImageGenerationTool.class.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return (JSONObject) method.invoke(tool, args);
    }
}
