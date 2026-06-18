package cn.lineai.ssh;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import cn.lineai.R;
import cn.lineai.data.repository.SshConfigRepository;
import cn.lineai.model.SshConfig;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public final class SshService {
    public interface OutputListener {
        void onOutput(String output);
    }

    public interface SftpOperation<T> {
        T run(ChannelSftp sftp) throws Exception;
    }

    public static final String TERMUX_RUN_COMMAND_PERMISSION = TermuxHelper.TERMUX_RUN_COMMAND_PERMISSION;
    public static final String TERMUX_ALLOW_EXTERNAL_APPS_COMMAND = TermuxHelper.TERMUX_ALLOW_EXTERNAL_APPS_COMMAND;

    private static final String TAG = "SshService";

    private final Context context;
    private final SshConfigRepository repository;
    private final TermuxHelper termuxHelper;
    private final SshConnectionPool connectionPool;
    private final boolean ownsConnectionPool;

    public SshService(Context context) {
        this(context, null);
    }

    /**
     * 可注入连接池的构造方法，便于共享池或测试。
     */
    public SshService(Context context, SshConnectionPool sharedPool) {
        this.context = context.getApplicationContext();
        repository = new SshConfigRepository(this.context);
        termuxHelper = new TermuxHelper(this.context);
        if (sharedPool != null) {
            connectionPool = sharedPool;
            ownsConnectionPool = false;
        } else {
            connectionPool = new SshConnectionPool(this::createRawSession);
            ownsConnectionPool = true;
        }
    }

    public SshConfig getConfig() {
        return repository.get();
    }

    public void saveConfig(SshConfig config) {
        repository.save(config);
    }

    public String testConnection(SshConfig config) throws Exception {
        return executeCommand("echo LineAI SSH OK && whoami && pwd", 10000, config);
    }

    public String executeCommand(String command, int timeoutMs) throws Exception {
        return executeCommand(command, timeoutMs, repository.get());
    }

    public String executeCommand(String command, int timeoutMs, SshConfig config) throws Exception {
        return executeCommand(command, timeoutMs, config, null);
    }

    public String executeCommand(String command, int timeoutMs, SshConfig config, OutputListener listener) throws Exception {
        String safeCommand = command == null ? "" : command.trim();
        if (safeCommand.length() == 0) {
            throw new IllegalArgumentException(context.getString(R.string.ssh_error_command_empty));
        }
        SshConfig safeConfig = config == null ? repository.get() : config;
        if (!safeConfig.isConfigured()) {
            throw new IllegalStateException(context.getString(R.string.ssh_error_not_configured));
        }
        int boundedTimeout = Math.max(1000, Math.min(timeoutMs <= 0 ? 30000 : timeoutMs, 300000));
        return executeInternal(safeCommand, boundedTimeout, safeConfig, listener);
    }

    public <T> T withSftp(SftpOperation<T> operation, int timeoutMs) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException(context.getString(R.string.ssh_error_sftp_empty));
        }
        SshConfig config = repository.get();
        if (!config.isConfigured()) {
            throw new IllegalStateException(context.getString(R.string.ssh_error_not_configured));
        }
        int boundedTimeout = Math.max(1000, Math.min(timeoutMs <= 0 ? 30000 : timeoutMs, 300000));
        Session session = null;
        ChannelSftp channel = null;
        boolean success = false;
        try {
            session = connectionPool.getOrCreate(config, boundedTimeout);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(boundedTimeout);
            T result = operation.run(channel);
            success = true;
            return result;
        } catch (Exception e) {
            if (session != null) {
                connectionPool.discard(session, config);
                session = null;
            }
            throw e;
        } finally {
            if (channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                }
            }
            if (session != null) {
                if (success) {
                    connectionPool.release(session, config);
                } else {
                    connectionPool.discard(session, config);
                }
            }
        }
    }

    public void openTermux() {
        termuxHelper.openTermux();
    }

    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public TermuxHelper.TermuxSetupResult setupTermuxOpenSsh(int timeoutMs) throws Exception {
        return termuxHelper.setupTermuxOpenSsh(timeoutMs);
    }

    /**
     * 关闭底层连接池。一般在进程退出或测试中调用，外部业务无需关心。
     */
    public void close() {
        if (ownsConnectionPool) {
            connectionPool.closeAll();
        }
    }

    private String executeInternal(String command, int timeoutMs, SshConfig config, OutputListener listener) throws Exception {
        Session session = null;
        ChannelExec channel = null;
        boolean success = false;
        try {
            session = connectionPool.getOrCreate(config, timeoutMs);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            channel.connect(timeoutMs);
            long startedAt = System.currentTimeMillis();
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            // 启动两个独立读线程把 stdout/stderr 抽到 StringBuilder。
            // 通道关闭时 jsch 会让 InputStream 读到 EOF，线程自然退出。
            CountDownLatch readersDone = new CountDownLatch(2);
            Thread stdoutReader = startDrainThread("linecode-ssh-stdout", stdout, output, readersDone);
            Thread stderrReader = startDrainThread("linecode-ssh-stderr", stderr, error, readersDone);

            // 主线程不再使用 Thread.sleep(100) 主动轮询输出，仅周期性地把累积的缓冲推给监听器，
            // 并在通道关闭/超时后 join 读线程。
            long lastEmitAt = 0L;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() - startedAt > timeoutMs) {
                    throw new IllegalStateException(context.getString(R.string.ssh_error_command_timeout));
                }
                if (listener != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastEmitAt >= 80L) {
                        lastEmitAt = now;
                        String combined = combineRaw(output.toString(), error.toString());
                        if (combined.length() > 0) {
                            listener.onOutput(combined);
                        }
                    }
                }
                // 短轮询通道状态；读线程负责非阻塞式排空流，避免主线程长期 sleep。
                try {
                    channel.getExitStatus();
                } catch (Exception ignored) {
                }
                Thread.sleep(20L);
            }

            // 等待读线程把剩余字节抽完
            readersDone.await(2L, TimeUnit.SECONDS);
            try {
                stdoutReader.join(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            try {
                stderrReader.join(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (listener != null) {
                String combined = combineRaw(output.toString(), error.toString());
                if (combined.length() > 0) {
                    listener.onOutput(combined);
                }
            }
            int exitStatus = channel.getExitStatus();
            String combined = combine(output.toString(), error.toString(), "exit status: " + exitStatus);
            success = true;
            if (exitStatus == 0) {
                return combined;
            }
            throw new IllegalStateException("exit status " + exitStatus + "\n" + combined);
        } catch (Exception e) {
            if (session != null) {
                connectionPool.discard(session, config);
                session = null;
            }
            throw e;
        } finally {
            if (channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                }
            }
            if (session != null) {
                if (success) {
                    connectionPool.release(session, config);
                } else {
                    connectionPool.discard(session, config);
                }
            }
        }
    }

    private Thread startDrainThread(String name, InputStream stream, StringBuilder target, CountDownLatch done) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    if (read > 0) {
                        synchronized (target) {
                            target.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (Exception ignored) {
                // 流在通道断开时可能抛 IOException，属于正常生命周期，忽略即可
            } finally {
                done.countDown();
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private Session createRawSession(SshConfig config, int timeoutMs) throws Exception {
        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHostsFile().getAbsolutePath());
        if (config.getPrivateKey().trim().length() > 0) {
            byte[] passphrase = config.getPassphrase().length() > 0
                    ? config.getPassphrase().getBytes(StandardCharsets.UTF_8)
                    : null;
            jsch.addIdentity(
                    "linecode-key",
                    config.getPrivateKey().getBytes(StandardCharsets.UTF_8),
                    null,
                    passphrase
            );
        }
        Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        if (config.getPassword().length() > 0) {
            session.setPassword(config.getPassword());
        }
        Properties properties = new Properties();
        properties.put("StrictHostKeyChecking", "ask");
        properties.put("IdentitiesOnly", "yes");
        properties.put("PreferredAuthentications", config.getPrivateKey().trim().length() > 0
                ? "publickey,password,keyboard-interactive"
                : "password,keyboard-interactive,publickey");
        session.setConfig(properties);
        session.setUserInfo(new TrustOnFirstUseUserInfo());
        session.connect(timeoutMs);
        return session;
    }

    private File knownHostsFile() throws Exception {
        File dir = new File(context.getFilesDir(), "ssh");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException(context.getString(R.string.ssh_error_create_config_dir, dir.getPath()));
        }
        File file = new File(dir, "known_hosts");
        if (!file.exists() && !file.createNewFile()) {
            throw new IllegalStateException(context.getString(R.string.ssh_error_create_known_hosts, file.getPath()));
        }
        return file;
    }

    private static final class TrustOnFirstUseUserInfo implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
            if (text.contains("has changed") || text.contains("offending key")) {
                return false;
            }
            Log.w(TAG, "TOFU: auto-accepting host key prompt: " + message);
            return true;
        }

        @Override
        public void showMessage(String message) {
        }
    }

    private static String combine(String output, String error, String fallback) {
        StringBuilder builder = new StringBuilder();
        if (output != null && output.trim().length() > 0) {
            builder.append(output.trim());
        }
        if (error != null && error.trim().length() > 0) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(error.trim());
        }
        return builder.length() == 0 ? fallback : builder.toString();
    }

    private static String combineRaw(String output, String error) {
        StringBuilder builder = new StringBuilder();
        if (output != null && output.length() > 0) {
            builder.append(output);
        }
        if (error != null && error.length() > 0) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(error);
        }
        return builder.toString();
    }
}
