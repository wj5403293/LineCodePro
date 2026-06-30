package cn.lineai.ssh;

import cn.lineai.model.SshConfig;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public final class SshConnectionPoolTest {
    @Test
    public void releaseNewSessionDoesNotThrowIllegalMonitorStateException() throws Exception {
        SshConfig config = config();
        SshConnectionPool pool = new SshConnectionPool((ignored, timeoutMs) -> newSession());

        Session session = pool.getOrCreate(config, 1000);
        pool.release(session, config);

        Assert.assertEquals(0, pool.size());
        pool.closeAll();
    }

    @Test
    public void discardNewSessionAllowsReborrow() throws Exception {
        SshConfig config = config();
        AtomicInteger createCount = new AtomicInteger();
        SshConnectionPool pool = new SshConnectionPool((ignored, timeoutMs) -> {
            createCount.incrementAndGet();
            return newSession();
        });

        Session first = pool.getOrCreate(config, 1000);
        pool.discard(first, config);
        Session second = pool.getOrCreate(config, 1000);
        pool.discard(second, config);

        Assert.assertEquals(2, createCount.get());
        Assert.assertEquals(0, pool.size());
        pool.closeAll();
    }

    @Test
    public void stalePooledSessionIsRemovedBeforeNewSession() throws Exception {
        SshConfig config = config();
        AtomicInteger createCount = new AtomicInteger();
        SshConnectionPool pool = new SshConnectionPool((ignored, timeoutMs) -> {
            createCount.incrementAndGet();
            return newSession();
        });

        Session first = pool.getOrCreate(config, 1000);
        setConnected(first, true);
        pool.release(first, config);
        setConnected(first, false);
        Session second = pool.getOrCreate(config, 1000);
        pool.discard(second, config);

        Assert.assertNotSame(first, second);
        Assert.assertEquals(2, createCount.get());
        Assert.assertEquals(0, pool.size());
        pool.closeAll();
    }

    private static SshConfig config() {
        return new SshConfig("example.com", 22, "line", "password", "", "");
    }

    private static Session newSession() throws Exception {
        return new JSch().getSession("line", "example.com", 22);
    }

    private static void setConnected(Session session, boolean connected) throws Exception {
        Field field = Session.class.getDeclaredField("isConnected");
        field.setAccessible(true);
        field.setBoolean(session, connected);
    }
}
