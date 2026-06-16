package cn.lineai.ui.component;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import cn.lineai.R;
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
        super(context, context.getString(R.string.screen_keep_alive_title), listener::onBack, null);
        this.context = context;
        this.repository = new KeepAliveRepository(context);
        LinearLayout content = getContent();

        KeepAliveRepository.KeepAliveSettings settings = repository.getSettings();

        SettingsSectionView coding = new SettingsSectionView(context, context.getString(R.string.screen_keep_alive_section_coding));

        SwitchRowView wakeLockSwitch = new SwitchRowView(context, IconButtonView.ZAP, context.getString(R.string.screen_keep_alive_wake_lock_label), context.getString(R.string.screen_keep_alive_wake_lock_desc), settings.wakeLockEnabled, (buttonView, enabled) -> {
            repository.setWakeLockEnabled(enabled);
            updateService();
            listener.onSettingsChanged();
        });
        coding.addRow(wakeLockSwitch, true);

        SwitchRowView foregroundSwitch = new SwitchRowView(context, IconButtonView.BELL, context.getString(R.string.screen_keep_alive_foreground_label), context.getString(R.string.screen_keep_alive_foreground_desc), settings.foregroundEnabled, (buttonView, enabled) -> {
            repository.setForegroundEnabled(enabled);
            updateService();
            listener.onSettingsChanged();
        });
        coding.addRow(foregroundSwitch, true);

        SwitchRowView fakeAudioSwitch = new SwitchRowView(context, IconButtonView.MUSIC, context.getString(R.string.screen_keep_alive_fake_music_label), context.getString(R.string.screen_keep_alive_fake_music_desc), settings.fakeAudioEnabled, (buttonView, enabled) -> {
            repository.setFakeAudioEnabled(enabled);
            updateService();
            listener.onSettingsChanged();
        });
        coding.addRow(fakeAudioSwitch, false);
        content.addView(coding, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView system = new SettingsSectionView(context, context.getString(R.string.screen_keep_alive_section_system));
        SwitchRowView batterySwitch = new SwitchRowView(context, IconButtonView.BATTERY_CHARGING, context.getString(R.string.screen_keep_alive_ignore_battery_label), context.getString(R.string.screen_keep_alive_ignore_battery_desc), isIgnoringBatteryOptimizations(), (buttonView, enabled) -> {
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