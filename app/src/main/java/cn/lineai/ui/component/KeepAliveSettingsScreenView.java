package cn.lineai.ui.component;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import cn.lineai.data.repository.KeepAliveRepository;
import cn.lineai.service.KeepAliveService;

public final class KeepAliveSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onSettingsChanged();
    }

    private final KeepAliveRepository repository;
    private final Context context;

    public KeepAliveSettingsScreenView(Context context, Listener listener) {
        super(context, "保活设置", listener::onBack, null);
        this.context = context;
        this.repository = new KeepAliveRepository(context);
        LinearLayout content = getContent();

        KeepAliveRepository.KeepAliveSettings settings = repository.getSettings();

        SettingsSectionView coding = new SettingsSectionView(context, "编码任务保活");

        SwitchRowView wakeLockSwitch = new SwitchRowView(context, IconButtonView.ZAP, "Wake Lock", "对话生成和压缩时保持 CPU 与屏幕唤醒", settings.wakeLockEnabled, (buttonView, enabled) -> {
            repository.setWakeLockEnabled(enabled);
            updateService();
            listener.onSettingsChanged();
        });
        coding.addRow(wakeLockSwitch, true);

        SwitchRowView foregroundSwitch = new SwitchRowView(context, IconButtonView.BELL, "前台服务通知", "开启后常驻显示正在编码通知", settings.foregroundEnabled, (buttonView, enabled) -> {
            repository.setForegroundEnabled(enabled);
            updateService();
            listener.onSettingsChanged();
        });
        coding.addRow(foregroundSwitch, true);

        SwitchRowView fakeAudioSwitch = new SwitchRowView(context, IconButtonView.MUSIC, "假音乐播放", "后台任务期间启动静音 AudioTrack", settings.fakeAudioEnabled, (buttonView, enabled) -> {
            repository.setFakeAudioEnabled(enabled);
            updateService();
            listener.onSettingsChanged();
        });
        coding.addRow(fakeAudioSwitch, false);
        content.addView(coding, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView system = new SettingsSectionView(context, "系统白名单");
        SwitchRowView batterySwitch = new SwitchRowView(context, IconButtonView.BATTERY_CHARGING, "忽略电池优化", "打开 Android 白名单申请页面", isIgnoringBatteryOptimizations(), (buttonView, enabled) -> {
            if (enabled && !isIgnoringBatteryOptimizations()) {
                openBatteryOptimizationSettings();
            }
        });
        system.addRow(batterySwitch, false);
        content.addView(system, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return pm.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true;
    }

    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        }
    }

    private void updateService() {
        KeepAliveRepository.KeepAliveSettings settings = repository.getSettings();
        if (settings.wakeLockEnabled || settings.foregroundEnabled || settings.fakeAudioEnabled) {
            KeepAliveService.start(context, settings.wakeLockEnabled, settings.foregroundEnabled, settings.fakeAudioEnabled);
        } else {
            KeepAliveService.stop(context);
        }
    }

    public void updateStatus(String status) {
        KeepAliveService.updateStatus(context, status);
    }
}