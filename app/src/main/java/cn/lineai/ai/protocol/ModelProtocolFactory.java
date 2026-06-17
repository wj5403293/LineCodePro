package cn.lineai.ai.protocol;

import cn.lineai.model.ModelProtocolType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModelProtocolFactory {
    private final Map<ModelProtocolType, Supplier<ModelProtocol>> registry = new HashMap<>();

    public ModelProtocolFactory() {
        register(ModelProtocolType.CODEX_RESPONSES, CodexResponsesProtocol::new);
        register(ModelProtocolType.ANTHROPIC_MESSAGES, AnthropicMessagesProtocol::new);
        register(ModelProtocolType.LOCAL_GGUF, LocalGgufProtocol::new);
        register(ModelProtocolType.OPENAI_COMPATIBLE, OpenAiCompatibleProtocol::new);
    }

    public void register(ModelProtocolType type, Supplier<ModelProtocol> supplier) {
        if (type == null || supplier == null) {
            return;
        }
        registry.put(type, supplier);
    }

    public ModelProtocol create(ModelProtocolType type) {
        Supplier<ModelProtocol> supplier = registry.get(type);
        if (supplier != null) {
            return supplier.get();
        }
        return new OpenAiCompatibleProtocol();
    }
}
