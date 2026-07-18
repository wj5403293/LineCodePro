package cn.lineai.ai;

import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.protocol.ModelProtocol;
import cn.lineai.ai.protocol.ModelProtocolFactory;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.tool.ModelServiceProvider;

import java.util.ArrayList;

/**
 * ModelServiceProvider 的默认实现，委托给 AI 模块的 ModelClient 和 ModelProtocolFactory。
 */
public final class DefaultModelServiceProvider implements ModelServiceProvider {
    private final ModelProtocolFactory protocolFactory;
    private final ModelClient modelClient;

    public DefaultModelServiceProvider() {
        this(new ModelProtocolFactory(), new ModelClient());
    }

    public DefaultModelServiceProvider(ModelProtocolFactory protocolFactory, ModelClient modelClient) {
        this.protocolFactory = protocolFactory != null ? protocolFactory : new ModelProtocolFactory();
        this.modelClient = modelClient != null ? modelClient : new ModelClient();
    }

    @Override
    public boolean supportsImageUnderstanding(ModelProtocolType protocolType) {
        return protocolFactory.create(protocolType).supportsImageUnderstanding();
    }

    @Override
    public boolean supportsImageGeneration(ModelProtocolType protocolType) {
        return protocolFactory.create(protocolType).supportsImageGeneration();
    }

    @Override
    public String completeImageUnderstanding(ModelConfig config, String systemPrompt, String userPrompt, String rawInputJson) throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new SystemModelMessage(systemPrompt));
        messages.add(new UserModelMessage(userPrompt, rawInputJson));
        ModelCompletionResponse response = modelClient.complete(config, messages);
        return response.getText();
    }
}
