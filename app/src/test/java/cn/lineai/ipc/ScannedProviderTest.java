package cn.lineai.ipc;

import org.junit.Assert;
import org.junit.Test;

public final class ScannedProviderTest {
    @Test
    public void constructorSetsAllFields() {
        ScannedProvider provider = new ScannedProvider(
                "com.example.provider",
                "com.example.provider.TerminalService",
                "Test Provider",
                "terminal"
        );
        Assert.assertEquals("com.example.provider", provider.getPackageName());
        Assert.assertEquals("com.example.provider.TerminalService", provider.getServiceClass());
        Assert.assertEquals("Test Provider", provider.getLabel());
        Assert.assertEquals("terminal", provider.getProviderType());
    }

    @Test
    public void constructorHandlesNullValues() {
        ScannedProvider provider = new ScannedProvider(null, null, null, null);
        Assert.assertEquals("", provider.getPackageName());
        Assert.assertEquals("", provider.getServiceClass());
        Assert.assertEquals("", provider.getLabel());
        Assert.assertEquals("", provider.getProviderType());
    }
}
