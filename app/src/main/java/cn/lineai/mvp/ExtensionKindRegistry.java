package cn.lineai.mvp;

import java.util.HashMap;
import java.util.Map;

/**
 * ExtensionKindDescriptor 策略注册表。
 * 将 kind 字符串映射到对应的描述符实现，消除 if-else 分发。
 * 使用静态单例，因为描述符是无状态的。
 */
public final class ExtensionKindRegistry {

    private static final ExtensionKindRegistry INSTANCE = new ExtensionKindRegistry();

    private final Map<String, ExtensionKindDescriptor> descriptors = new HashMap<>();

    private ExtensionKindRegistry() {
        register(new AgentKindDescriptor());
        register(new McpKindDescriptor());
        register(new SkillsKindDescriptor());
        register(new LinecodeKindDescriptor());
    }

    public static ExtensionKindRegistry getInstance() {
        return INSTANCE;
    }

    private void register(ExtensionKindDescriptor descriptor) {
        descriptors.put(descriptor.kind(), descriptor);
    }

    /** 根据 kind 字符串返回对应描述符，未知 kind 返回 null */
    public ExtensionKindDescriptor get(String kind) {
        return descriptors.get(kind);
    }
}
