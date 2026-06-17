package cn.lineai.ipc.terminal;

import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderType;
import org.junit.Assert;
import org.junit.Test;

public final class TerminalIpcProviderTest {
    private static IpcProviderConfig sampleConfig() {
        return IpcProviderConfig.builder()
                .id("test-id")
                .enabled(true)
                .providerType("terminal")
                .name("Test Terminal Provider")
                .packageName("com.example.terminal")
                .serviceClass("com.example.terminal.TerminalService")
                .build();
    }

    @Test
    public void getProviderTypeReturnsTerminal() {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        Assert.assertEquals(IpcProviderType.TERMINAL, provider.getProviderType());
    }

    @Test
    public void requiresConfirmationReturnsTrue() {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        Assert.assertTrue(provider.requiresConfirmation());
    }

    @Test
    public void isBoundDefaultsToFalse() {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        Assert.assertFalse(provider.isBound());
    }

    @Test
    public void getConfigReturnsConstructorConfig() {
        IpcProviderConfig config = sampleConfig();
        TerminalIpcProvider provider = new TerminalIpcProvider(config);
        Assert.assertSame(config, provider.getConfig());
    }

    @Test
    public void isBaseIpcProviderSubclass() {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        Assert.assertTrue(provider instanceof BaseIpcProvider);
    }

    @Test(expected = IllegalStateException.class)
    public void executeShellThrowsWhenNotBound() throws Exception {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        provider.executeShell("ls", "/", 1000L, null);
    }

    @Test(expected = IllegalStateException.class)
    public void readFileThrowsWhenNotBound() throws Exception {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        provider.readFile("/test/path");
    }

    @Test(expected = IllegalStateException.class)
    public void writeFileThrowsWhenNotBound() throws Exception {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        provider.writeFile("/test/path", new byte[]{1, 2, 3});
    }

    @Test(expected = IllegalStateException.class)
    public void deleteFileThrowsWhenNotBound() throws Exception {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        provider.deleteFile("/test/path");
    }

    @Test(expected = IllegalStateException.class)
    public void listDirThrowsWhenNotBound() throws Exception {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        provider.listDir("/test/path");
    }

    @Test(expected = IllegalStateException.class)
    public void fileExistsThrowsWhenNotBound() throws Exception {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        provider.fileExists("/test/path");
    }

    @Test(expected = IllegalStateException.class)
    public void fileSizeThrowsWhenNotBound() throws Exception {
        TerminalIpcProvider provider = new TerminalIpcProvider(sampleConfig());
        provider.fileSize("/test/path");
    }
}
