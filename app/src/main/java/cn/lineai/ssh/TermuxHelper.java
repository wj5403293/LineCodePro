package cn.lineai.ssh;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import cn.lineai.R;
import cn.lineai.data.repository.SshConfigRepository;
import cn.lineai.model.SshConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Termux 集成辅助类，负责通过 Intent 调用 Termux 的 RunCommandService 来：
 * <ul>
 *   <li>打开 Termux 主界面</li>
 *   <li>在 Termux 中执行 OpenSSH 一键配置脚本（pkg install openssh、生成密钥、配置 sshd、注册公钥等）</li>
 * </ul>
 *
 * <p>设计上保持与原 {@link SshService} 中的 Termux 入口完全一致的对外契约，方便逐步迁移与回退。
 * 所有依赖 Android Context 的副作用（包检查、广播、Intent、Asset 读取）都在此类内部完成。</p>
 */
public final class TermuxHelper {
    public static final String TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
    public static final String TERMUX_ALLOW_EXTERNAL_APPS_COMMAND = "mkdir -p ~/.termux\n"
            + "properties_path=\"$HOME/.termux/termux.properties\"\n"
            + "touch \"$properties_path\"\n"
            + "grep -qxF 'allow-external-apps=true' \"$properties_path\" || printf '\\nallow-external-apps=true\\n' >> \"$properties_path\"\n"
            + "termux-reload-settings >/dev/null 2>&1 || true";

    private static final String TAG = "TermuxHelper";
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String TERMUX_ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String TERMUX_EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
    private static final String TERMUX_EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    private static final String TERMUX_EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN";
    private static final String TERMUX_EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    private static final String TERMUX_EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    private static final String TERMUX_EXTRA_RUNNER = "com.termux.RUN_COMMAND_RUNNER";
    private static final String TERMUX_EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    private static final String TERMUX_EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION";
    private static final String TERMUX_EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";
    private static final String TERMUX_RESULT_BUNDLE = "result";
    private static final String TERMUX_RESULT_STDOUT = "stdout";
    private static final String TERMUX_RESULT_STDERR = "stderr";
    private static final String TERMUX_RESULT_EXIT_CODE = "exitCode";
    private static final String TERMUX_RESULT_ERR = "err";
    private static final String TERMUX_RESULT_ERRMSG = "errmsg";
    @SuppressLint("SdCardPath")
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    @SuppressLint("SdCardPath")
    private static final String TERMUX_SH = "/data/data/com.termux/files/usr/bin/sh";
    private static final String TERMUX_SETUP_SCRIPT_ASSET = "termux_setup.sh";

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "LINEAI_PRIVATE_KEY_BEGIN\\n([\\s\\S]*?)\\nLINEAI_PRIVATE_KEY_END"
    );

    private final Context context;
    private final SshConfigRepository repository;

    public TermuxHelper(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        this.repository = this.context == null ? null : new SshConfigRepository(this.context);
    }

    public SshConfigRepository getRepository() {
        return repository;
    }

    public void openTermux() {
        ensureTermuxInstalled();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (launchIntent == null) {
            launchIntent = new Intent().setClassName(TERMUX_PACKAGE, "com.termux.app.TermuxActivity");
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launchIntent);
    }

    public TermuxSetupResult setupTermuxOpenSsh(int timeoutMs) throws Exception {
        ensureTermuxInstalled();
        if (context.checkSelfPermission(TERMUX_RUN_COMMAND_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException(context.getString(R.string.ssh_error_termux_permission));
        }
        int boundedTimeout = Math.max(60000, Math.min(timeoutMs <= 0 ? 900000 : timeoutMs, 900000));
        String action = context.getPackageName() + ".TERMUX_SETUP_RESULT." + System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> outputRef = new AtomicReference<>("");
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receivedContext, Intent intent) {
                try {
                    android.os.Bundle result = intent == null ? null : intent.getBundleExtra(TERMUX_RESULT_BUNDLE);
                    if (result == null) {
                        errorRef.set(new IllegalStateException(context.getString(R.string.ssh_error_termux_no_result)));
                        return;
                    }
                    String stdout = result.getString(TERMUX_RESULT_STDOUT, "");
                    String stderr = result.getString(TERMUX_RESULT_STDERR, "");
                    int exitCode = result.getInt(TERMUX_RESULT_EXIT_CODE, 0);
                    int err = result.getInt(TERMUX_RESULT_ERR, Activity.RESULT_OK);
                    String errmsg = result.getString(TERMUX_RESULT_ERRMSG, "");
                    String combined = combine(stdout, stderr, "");
                    if (err != Activity.RESULT_OK) {
                        errorRef.set(new IllegalStateException(errmsg.length() == 0 ? context.getString(R.string.ssh_error_termux_command_failed, err) : errmsg));
                    } else if (exitCode != 0) {
                        errorRef.set(new IllegalStateException("exit status " + exitCode + "\n" + combined));
                    } else {
                        outputRef.set(combined);
                    }
                } finally {
                    latch.countDown();
                }
            }
        };

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, new IntentFilter(action));
            }
            int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) (System.currentTimeMillis() & 0x7fffffff),
                    new Intent(action).setPackage(context.getPackageName()),
                    flags
            );
            Intent intent = new Intent()
                    .setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                    .setAction(TERMUX_ACTION_RUN_COMMAND)
                    .putExtra(TERMUX_EXTRA_COMMAND_PATH, TERMUX_SH)
                    .putExtra(TERMUX_EXTRA_ARGUMENTS, new String[] {"-s"})
                    .putExtra(TERMUX_EXTRA_STDIN, readTermuxSetupScript())
                    .putExtra(TERMUX_EXTRA_WORKDIR, TERMUX_HOME)
                    .putExtra(TERMUX_EXTRA_BACKGROUND, true)
                    .putExtra(TERMUX_EXTRA_RUNNER, "app-shell")
                    .putExtra(TERMUX_EXTRA_COMMAND_LABEL, "LineAI OpenSSH setup")
                    .putExtra(TERMUX_EXTRA_COMMAND_DESCRIPTION, "Install openssh, create a LineAI key, enable authorized_keys, add sshd autostart to shell rc, and start sshd.")
                    .putExtra(TERMUX_EXTRA_PENDING_INTENT, pendingIntent);
            context.startService(intent);
            boolean completed = latch.await(boundedTimeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                throw new IllegalStateException(context.getString(R.string.ssh_error_termux_setup_timeout));
            }
            if (errorRef.get() != null) {
                throw errorRef.get();
            }
            TermuxSetupResult result = parseTermuxSetupOutput(outputRef.get());
            if (repository != null) {
                repository.save(result.getConfig());
            }
            return result;
        } finally {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        }
    }

    public void ensureTermuxInstalled() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0));
            } else {
                context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(context.getString(R.string.ssh_error_termux_not_installed));
        }
    }

    public TermuxSetupResult parseTermuxSetupOutput(String output) {
        String username = readValue(output, "LINEAI_TERMUX_USERNAME");
        String host = readValue(output, "LINEAI_TERMUX_HOST");
        int port = parsePort(readValue(output, "LINEAI_TERMUX_PORT"));
        String shell = readValue(output, "LINEAI_TERMUX_SHELL");
        String rcPath = readValue(output, "LINEAI_TERMUX_RC");
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(output == null ? "" : output);
        String privateKey = matcher.find() ? matcher.group(1).trim() : "";
        if (username.length() == 0 || privateKey.length() == 0) {
            throw new IllegalStateException(context.getString(R.string.ssh_error_termux_parse_failed, redactPrivateKey(output)));
        }
        SshConfig config = new SshConfig(
                host.length() == 0 ? SshConfig.DEFAULT_HOST : host,
                port,
                username,
                "",
                privateKey,
                ""
        );
        return new TermuxSetupResult(config, output == null ? "" : output, shell, rcPath);
    }

    public String redactPrivateKey(String output) {
        return output == null ? "" : PRIVATE_KEY_PATTERN.matcher(output)
                .replaceAll(context.getString(R.string.ssh_redact_private_key));
    }

    private String readTermuxSetupScript() {
        try {
            InputStream input = context.getAssets().open(TERMUX_SETUP_SCRIPT_ASSET);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalStateException(
                    context.getString(R.string.ssh_error_read_termux_script, TERMUX_SETUP_SCRIPT_ASSET), e);
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

    private static String readValue(String output, String key) {
        if (output == null || key == null) {
            return "";
        }
        String prefix = key + "=";
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 ? port : SshConfig.DEFAULT_PORT;
        } catch (Exception ignored) {
            return SshConfig.DEFAULT_PORT;
        }
    }

    /**
     * Termux 集成结果：包含 OpenSSH 配置、设置过程输出、检测到的 shell 与 rc 文件路径。
     */
    public static final class TermuxSetupResult {
        private final SshConfig config;
        private final String output;
        private final String shell;
        private final String rcPath;

        TermuxSetupResult(SshConfig config, String output, String shell, String rcPath) {
            this.config = config == null ? SshConfig.defaultConfig() : config;
            this.output = output == null ? "" : output;
            this.shell = shell == null ? "" : shell;
            this.rcPath = rcPath == null ? "" : rcPath;
        }

        public SshConfig getConfig() {
            return config;
        }

        public String getOutput() {
            return output;
        }

        public String getShell() {
            return shell;
        }

        public String getRcPath() {
            return rcPath;
        }
    }
}
