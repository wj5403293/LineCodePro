package cn.lineai.ssh;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import cn.lineai.ssh.R;
import cn.lineai.data.repository.SshConfigRepository;
import cn.lineai.model.SshConfig;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

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
    private final SshCommandExecutor commandExecutor;

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
        commandExecutor = new SshCommandExecutor(this.context, connectionPool);
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
        SshConfig safeConfig = config == null ? repository.get() : config;
        return commandExecutor.executeCommand(command, timeoutMs, safeConfig, listener == null ? null : listener::onOutput);
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

}
