package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.ModelConfig;
import java.util.List;

public final class LocalGgufProtocol implements ModelProtocol {
    @Override
    public boolean supportsImageUnderstanding() {
        return false;
    }

    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        throw new ModelCompletionException("本地 GGUF 推理接口还没有接入。请先使用 OpenAI 兼容、Codex 或 Anthropic 模型。");
    }

    @Override
    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        throw new ModelCompletionException("本地 GGUF 推理接口还没有接入。请先使用 OpenAI 兼容、Codex 或 Anthropic 模型。");
    }
}
