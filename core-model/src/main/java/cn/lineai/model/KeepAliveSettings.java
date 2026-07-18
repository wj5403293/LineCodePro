package cn.lineai.model;

/**
 * UI layer representation of keep-alive settings, decoupled from KeepAliveRepository.
 */
public final class KeepAliveSettings {
    public final boolean wakeLockEnabled;
    public final boolean foregroundEnabled;
    public final boolean fakeAudioEnabled;

    public KeepAliveSettings(boolean wakeLockEnabled, boolean foregroundEnabled, boolean fakeAudioEnabled) {
        this.wakeLockEnabled = wakeLockEnabled;
        this.foregroundEnabled = foregroundEnabled;
        this.fakeAudioEnabled = fakeAudioEnabled;
    }
}
