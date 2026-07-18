package cn.lineai.tool;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;

/**
 * 模型服务提供者接口，供工具层调用模型能力而不依赖 AI 模块具体类。
 * 实现类在 AI 模块中提供，工具层通过 ToolContext 获取。
 */
public interface ModelServiceProvider {
    /** 检查协议是否支持图片理解 */
    boolean supportsImageUnderstanding(ModelProtocolType protocolType);

    /** 检查协议是否支持图片生成 */
    boolean supportsImageGeneration(ModelProtocolType protocolType);

    /**
     * 调用视觉模型完成图片理解。
     *
     * @param config       视觉模型配置
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param rawInputJson 图片原始输入 JSON（含 base64 数据）
     * @return 模型返回的文本内容
     */
    String completeImageUnderstanding(ModelConfig config, String systemPrompt, String userPrompt, String rawInputJson) throws Exception;
}
