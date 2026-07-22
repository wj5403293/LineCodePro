package cn.lineai.data.repository;

public final class UserAgreementRepository {

    public static final int CURRENT_VERSION = 1;

    private static final String KEY_ACCEPTED = "@linecode_user_agreement_accepted";
    private static final String KEY_VERSION = "@linecode_user_agreement_version";

    private final SettingsRepository settings;

    public UserAgreementRepository(SettingsRepository settings) {
        this.settings = settings;
    }

    public boolean isAccepted() {
        return settings.getBoolean(KEY_ACCEPTED, false);
    }

    public void setAccepted(boolean accepted) {
        settings.setBoolean(KEY_ACCEPTED, accepted);
    }

    public int getVersion() {
        return (int) settings.getLong(KEY_VERSION, 0);
    }

    public void setVersion(int version) {
        settings.setLong(KEY_VERSION, version);
    }

    public boolean shouldShow() {
        return !isAccepted() || getVersion() < CURRENT_VERSION;
    }
}
