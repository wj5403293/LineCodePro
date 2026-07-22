package cn.lineai.data.repository;

import android.content.SharedPreferences;

public final class KeepAliveRepository {
    private static final String PREFS_NAME = "linecode_keep_alive";
    private static final String KEY_WAKE_LOCK = "wake_lock_enabled";
    private static final String KEY_FOREGROUND = "foreground_enabled";
    private static final String KEY_FAKE_AUDIO = "fake_audio_enabled";

    private final SharedPreferences prefs;

    public KeepAliveRepository(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public boolean isWakeLockEnabled() {
        return prefs.getBoolean(KEY_WAKE_LOCK, true);
    }

    public void setWakeLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WAKE_LOCK, enabled).apply();
    }

    public boolean isForegroundEnabled() {
        return prefs.getBoolean(KEY_FOREGROUND, false);
    }

    public void setForegroundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FOREGROUND, enabled).apply();
    }

    public boolean isFakeAudioEnabled() {
        return prefs.getBoolean(KEY_FAKE_AUDIO, false);
    }

    public void setFakeAudioEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FAKE_AUDIO, enabled).apply();
    }

    public KeepAliveSettings getSettings() {
        return new KeepAliveSettings(
                isWakeLockEnabled(),
                isForegroundEnabled(),
                isFakeAudioEnabled()
        );
    }

    public void saveSettings(KeepAliveSettings settings) {
        prefs.edit()
                .putBoolean(KEY_WAKE_LOCK, settings.wakeLockEnabled)
                .putBoolean(KEY_FOREGROUND, settings.foregroundEnabled)
                .putBoolean(KEY_FAKE_AUDIO, settings.fakeAudioEnabled)
                .apply();
    }

    public static final class KeepAliveSettings {
        public final boolean wakeLockEnabled;
        public final boolean foregroundEnabled;
        public final boolean fakeAudioEnabled;

        public KeepAliveSettings(boolean wakeLockEnabled, boolean foregroundEnabled, boolean fakeAudioEnabled) {
            this.wakeLockEnabled = wakeLockEnabled;
            this.foregroundEnabled = foregroundEnabled;
            this.fakeAudioEnabled = fakeAudioEnabled;
        }
    }
}