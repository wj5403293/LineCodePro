package cn.lineai.ipc.terminal;

import org.junit.Assert;
import org.junit.Test;

public final class TerminalShellResultTest {
    @Test
    public void zeroExitCodeIsSuccess() {
        TerminalShellResult result = new TerminalShellResult(0);
        Assert.assertEquals(0, result.getExitCode());
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void nonZeroExitCodeIsNotSuccess() {
        TerminalShellResult result = new TerminalShellResult(1);
        Assert.assertEquals(1, result.getExitCode());
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void negativeExitCodeIsNotSuccess() {
        TerminalShellResult result = new TerminalShellResult(-1);
        Assert.assertEquals(-1, result.getExitCode());
        Assert.assertFalse(result.isSuccess());
    }
}
