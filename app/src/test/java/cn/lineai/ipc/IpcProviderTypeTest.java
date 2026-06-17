package cn.lineai.ipc;

import org.junit.Assert;
import org.junit.Test;

public final class IpcProviderTypeTest {
    @Test
    public void terminalTypeHasCorrectId() {
        Assert.assertEquals("terminal", IpcProviderType.TERMINAL.getId());
    }

    @Test
    public void terminalTypeHasCorrectIntentAction() {
        Assert.assertEquals("cn.lineai.action.IPC_TERMINAL_PROVIDER", IpcProviderType.TERMINAL.getIntentAction());
    }

    @Test
    public void terminalTypeHasCorrectPermissionName() {
        Assert.assertEquals("cn.lineai.permission.IPC_TERMINAL_PROVIDER", IpcProviderType.TERMINAL.getPermissionName());
    }

    @Test
    public void fromIdReturnsTerminalForTerminalId() {
        Assert.assertEquals(IpcProviderType.TERMINAL, IpcProviderType.fromId("terminal"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromIdThrowsForNullId() {
        IpcProviderType.fromId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromIdThrowsForUnknownId() {
        IpcProviderType.fromId("unknown_type");
    }
}
