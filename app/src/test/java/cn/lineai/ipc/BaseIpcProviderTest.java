package cn.lineai.ipc;

import org.junit.Assert;
import org.junit.Test;

public final class BaseIpcProviderTest {
    private static IpcProviderConfig sampleConfig() {
        return IpcProviderConfig.builder()
                .id("test-id")
                .enabled(true)
                .providerType("terminal")
                .name("Test Provider")
                .packageName("com.example.provider")
                .serviceClass("com.example.provider.TerminalService")
                .build();
    }

    private static BaseIpcProvider createProvider(IpcProviderConfig config) {
        return new BaseIpcProvider(config) {
            @Override
            public IpcProviderType getProviderType() {
                return IpcProviderType.TERMINAL;
            }
        };
    }

    @Test
    public void getConfigReturnsConstructorConfig() {
        IpcProviderConfig config = sampleConfig();
        BaseIpcProvider provider = createProvider(config);
        Assert.assertSame(config, provider.getConfig());
    }

    @Test
    public void isBoundDefaultsToFalse() {
        BaseIpcProvider provider = createProvider(sampleConfig());
        Assert.assertFalse(provider.isBound());
    }

    @Test
    public void requiresConfirmationDefaultsToFalse() {
        BaseIpcProvider provider = createProvider(sampleConfig());
        Assert.assertFalse(provider.requiresConfirmation());
    }

    @Test
    public void getServiceBinderDefaultsToNull() {
        BaseIpcProvider provider = createProvider(sampleConfig());
        Assert.assertNull(provider.getServiceBinder());
    }

    @Test
    public void unbindWhenNotBoundIsNoop() {
        BaseIpcProvider provider = createProvider(sampleConfig());
        provider.unbind(null);
        Assert.assertFalse(provider.isBound());
        Assert.assertNull(provider.getServiceBinder());
    }
}
