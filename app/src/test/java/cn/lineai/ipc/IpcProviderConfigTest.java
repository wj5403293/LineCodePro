package cn.lineai.ipc;

import org.junit.Assert;
import org.junit.Test;

public final class IpcProviderConfigTest {
    @Test
    public void builderSetsAllFields() {
        long now = System.currentTimeMillis();
        IpcProviderConfig config = IpcProviderConfig.builder()
                .id("test-id")
                .enabled(true)
                .providerType("terminal")
                .name("Test Provider")
                .packageName("com.example.provider")
                .serviceClass("com.example.provider.TerminalService")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Assert.assertEquals("test-id", config.getId());
        Assert.assertTrue(config.isEnabled());
        Assert.assertEquals("terminal", config.getProviderType());
        Assert.assertEquals("Test Provider", config.getName());
        Assert.assertEquals("com.example.provider", config.getPackageName());
        Assert.assertEquals("com.example.provider.TerminalService", config.getServiceClass());
        Assert.assertEquals(now, config.getCreatedAt());
        Assert.assertEquals(now, config.getUpdatedAt());
    }

    @Test
    public void builderDefaultsToEnabledTrue() {
        IpcProviderConfig config = IpcProviderConfig.builder()
                .providerType("terminal")
                .name("Default Enabled")
                .packageName("com.example")
                .serviceClass("com.example.Service")
                .build();
        Assert.assertTrue(config.isEnabled());
    }

    @Test
    public void builderDefaultsEmptyStringsToEmpty() {
        IpcProviderConfig config = IpcProviderConfig.builder().build();
        Assert.assertEquals("", config.getId());
        Assert.assertEquals("", config.getProviderType());
        Assert.assertEquals("", config.getName());
        Assert.assertEquals("", config.getPackageName());
        Assert.assertEquals("", config.getServiceClass());
    }

    @Test
    public void builderDefaultsTimestampsToCurrentTime() {
        long before = System.currentTimeMillis();
        IpcProviderConfig config = IpcProviderConfig.builder().build();
        long after = System.currentTimeMillis();
        Assert.assertTrue(config.getCreatedAt() >= before);
        Assert.assertTrue(config.getCreatedAt() <= after);
        Assert.assertTrue(config.getUpdatedAt() >= before);
        Assert.assertTrue(config.getUpdatedAt() <= after);
    }

    @Test
    public void builderCanDisableProvider() {
        IpcProviderConfig config = IpcProviderConfig.builder()
                .enabled(false)
                .build();
        Assert.assertFalse(config.isEnabled());
    }

    @Test
    public void directConstructorHandlesNullValues() {
        IpcProviderConfig config = new IpcProviderConfig(null, false, null, null, null, null, 0, 0);
        Assert.assertEquals("", config.getId());
        Assert.assertEquals("", config.getProviderType());
        Assert.assertEquals("", config.getName());
        Assert.assertEquals("", config.getPackageName());
        Assert.assertEquals("", config.getServiceClass());
    }
}
