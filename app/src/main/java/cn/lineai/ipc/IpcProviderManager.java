package cn.lineai.ipc;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IpcProviderManager {
    private final Map<String, BaseIpcProvider> activeProviders = new ConcurrentHashMap<>();
    private final Context context;

    public IpcProviderManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public BaseIpcProvider registerAndBind(IpcProviderConfig config) {
        BaseIpcProvider existing = activeProviders.get(config.getId());
        if (existing != null) {
            existing.unbind(context);
        }
        BaseIpcProvider provider = createProvider(config);
        provider.bind(context);
        activeProviders.put(config.getId(), provider);
        return provider;
    }

    public void unregisterAndUnbind(String providerId) {
        BaseIpcProvider provider = activeProviders.remove(providerId);
        if (provider != null) {
            provider.unbind(context);
        }
    }

    public void unregisterAll() {
        for (BaseIpcProvider provider : new ArrayList<>(activeProviders.values())) {
            provider.unbind(context);
        }
        activeProviders.clear();
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseIpcProvider> T getProvider(String providerId, Class<T> type) {
        BaseIpcProvider provider = activeProviders.get(providerId);
        if (provider == null || !type.isInstance(provider)) {
            return null;
        }
        return type.cast(provider);
    }

    public BaseIpcProvider getProviderByType(IpcProviderType type) {
        for (BaseIpcProvider provider : activeProviders.values()) {
            if (provider.getProviderType() == type && provider.isBound()) {
                return provider;
            }
        }
        return null;
    }

    public List<BaseIpcProvider> getProvidersByType(IpcProviderType type) {
        List<BaseIpcProvider> result = new ArrayList<>();
        for (BaseIpcProvider provider : activeProviders.values()) {
            if (provider.getProviderType() == type) {
                result.add(provider);
            }
        }
        return result;
    }

    private BaseIpcProvider createProvider(IpcProviderConfig config) {
        IpcProviderType type = IpcProviderType.fromId(config.getProviderType());
        switch (type) {
            case TERMINAL:
                return new cn.lineai.ipc.terminal.TerminalIpcProvider(config);
            default:
                throw new IllegalArgumentException("未知的 IPC 提供者类型: " + config.getProviderType());
        }
    }
}
