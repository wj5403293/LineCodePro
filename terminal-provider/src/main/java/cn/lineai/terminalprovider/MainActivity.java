package cn.lineai.terminalprovider;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.ITerminalProviderService;
import java.io.File;

public final class MainActivity extends Activity {
    private ITerminalProviderService service;
    private boolean bound = false;
    private TextView logView;
    private TextView statusView;
    private final StringBuilder logBuilder = new StringBuilder();

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ITerminalProviderService.Stub.asInterface(binder);
            bound = true;
            updateStatus();
            appendLog("服务已绑定");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            updateStatus();
            appendLog("服务已断开");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setPadding(0, 0, 0, padding);
        root.addView(statusView);

        Button bindButton = new Button(this);
        bindButton.setText("绑定本地服务");
        bindButton.setOnClickListener(v -> bindLocalService());
        root.addView(bindButton);

        Button testButton = new Button(this);
        testButton.setText("执行测试命令 (whoami)");
        testButton.setOnClickListener(v -> testShell("whoami"));
        root.addView(testButton);

        Button testLsButton = new Button(this);
        testLsButton.setText("执行测试命令 (ls /)");
        testLsButton.setOnClickListener(v -> testShell("ls /"));
        root.addView(testLsButton);

        Button testInfoButton = new Button(this);
        testInfoButton.setText("获取 Provider 信息");
        testInfoButton.setOnClickListener(v -> testProviderInfo());
        root.addView(testInfoButton);

        Button clearButton = new Button(this);
        clearButton.setText("清空日志");
        clearButton.setOnClickListener(v -> {
            logBuilder.setLength(0);
            logView.setText("");
        });
        root.addView(clearButton);

        TextView logLabel = new TextView(this);
        logLabel.setText("日志输出:");
        logLabel.setPadding(0, padding, 0, padding / 2);
        root.addView(logLabel);

        logView = new TextView(this);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextSize(13);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setBackgroundColor(0xFF1E1E1E);
        logView.setTextColor(0xFFD4D4D4);
        int logPadding = (int) (getResources().getDisplayMetrics().density * 8);
        logView.setPadding(logPadding, logPadding, logPadding, logPadding);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(logView, logParams);

        setContentView(scrollView);
        updateStatus();
        appendLog("Terminal Provider 测试 APP 已启动");
        appendLog("Shell: /system/bin/sh exists=" + new File("/system/bin/sh").exists());
        appendLog("Android API: " + Build.VERSION.SDK_INT);
        appendLog("包名: " + getPackageName());
    }

    private void bindLocalService() {
        if (bound) {
            appendLog("服务已绑定，无需重复绑定");
            return;
        }
        Intent intent = new Intent(this, TerminalProviderService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        appendLog("正在绑定本地服务...");
    }

    private void testShell(String command) {
        if (!bound || service == null) {
            appendLog("错误: 服务未绑定，请先绑定");
            return;
        }
        appendLog(">>> " + command);
        try {
            int exitCode = service.executeShell(command, "", 10000L, new ITerminalProviderCallback.Stub() {
                @Override
                public void onOutput(String content) {
                    runOnUiThread(() -> appendLog(content.trim()));
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> appendLog("[ERROR] " + error));
                }

                @Override
                public void onComplete(int exitCode) {
                    runOnUiThread(() -> appendLog("[exit=" + exitCode + "]"));
                }
            });
            if (exitCode != 0) {
                appendLog("命令退出码: " + exitCode);
            }
        } catch (RemoteException e) {
            appendLog("远程调用失败: " + e.getMessage());
        }
    }

    private void testProviderInfo() {
        if (!bound || service == null) {
            appendLog("错误: 服务未绑定，请先绑定");
            return;
        }
        try {
            String type = service.getProviderType();
            String info = service.getProviderInfo();
            boolean available = service.isAvailable();
            appendLog("ProviderType: " + type);
            appendLog("ProviderInfo: " + info);
            appendLog("Available: " + available);
        } catch (RemoteException e) {
            appendLog("远程调用失败: " + e.getMessage());
        }
    }

    private void updateStatus() {
        String status = bound ? "服务状态: 已绑定" : "服务状态: 未绑定";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            status += "\n版本: " + pi.versionName + " (" + pi.versionCode + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        statusView.setText(status);
    }

    private void appendLog(String message) {
        if (message == null || message.length() == 0) {
            return;
        }
        logBuilder.append(message).append('\n');
        logView.setText(logBuilder.toString());
        logView.post(() -> logView.scrollTo(0, logView.getHeight()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }
}
