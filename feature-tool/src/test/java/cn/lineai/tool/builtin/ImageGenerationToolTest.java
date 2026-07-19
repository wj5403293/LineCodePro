package cn.lineai.tool.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.tool.ToolContext;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class ImageGenerationToolTest {
    private static final String PNG_1X1_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

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

    @Test
    public void responsesParserRejectsNullStringResult() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();
        String raw = new JSONObject()
                .put("output", new JSONArray().put(new JSONObject()
                        .put("type", "image_generation_call")
                        .put("result", "null")))
                .toString();

        Exception error = invokeFailure(tool, "parseResponsesImage",
                new Class<?>[] {String.class}, raw);

        assertTrue(error.getMessage().contains("无效的图片数据"));
    }

    @Test
    public void imagesParserRejectsNullStringFields() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();
        String raw = new JSONObject()
                .put("data", new JSONArray().put(new JSONObject()
                        .put("b64_json", "null")
                        .put("data_url", "data:image/png;base64,null")
                        .put("url", "null")))
                .toString();

        Exception error = invokeFailure(tool, "parseImagesResponse",
                new Class<?>[] {String.class, ToolContext.class}, raw, null);

        assertTrue(error.getMessage().contains("无效的图片数据"));
    }

    @Test
    public void responsesParserAcceptsPngBase64Result() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();
        String raw = new JSONObject()
                .put("output", new JSONArray().put(new JSONObject()
                        .put("type", "image_generation_call")
                        .put("result", PNG_1X1_BASE64)))
                .toString();

        Object image = invokeObject(tool, "parseResponsesImage",
                new Class<?>[] {String.class}, raw);

        assertEquals("data:image/png;base64," + PNG_1X1_BASE64, fieldString(image, "dataUrl"));
    }

    @Test
    public void imagesParserAcceptsPngBase64Result() throws Exception {
        ImageGenerationTool tool = new ImageGenerationTool();
        String raw = new JSONObject()
                .put("data", new JSONArray().put(new JSONObject()
                        .put("b64_json", PNG_1X1_BASE64)
                        .put("mime_type", "image/png")))
                .toString();

        Object image = invokeObject(tool, "parseImagesResponse",
                new Class<?>[] {String.class, ToolContext.class}, raw, null);

        assertEquals("data:image/png;base64," + PNG_1X1_BASE64, fieldString(image, "dataUrl"));
    }

    private static ModelConfig model(String modelId) {
        return ModelConfig.builder("id", "name", ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI", "https://api.openai.com/v1", "key", modelId).build();
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

    private static Object invokeObject(
            ImageGenerationTool tool,
            String methodName,
            Class<?>[] types,
            Object... args
    ) throws Exception {
        Method method = ImageGenerationTool.class.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return method.invoke(tool, args);
    }

    private static Exception invokeFailure(
            ImageGenerationTool tool,
            String methodName,
            Class<?>[] types,
            Object... args
    ) throws Exception {
        try {
            invokeObject(tool, methodName, types, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                return (Exception) cause;
            }
            throw e;
        }
        throw new AssertionError("Expected " + methodName + " to fail");
    }

    private static String fieldString(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(target);
    }
}
