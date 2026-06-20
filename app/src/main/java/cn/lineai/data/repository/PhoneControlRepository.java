package cn.lineai.data.repository;

import android.content.Context;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import cn.lineai.service.LineCodeAccessibilityService;
import java.util.List;

public final class PhoneControlRepository {

    private static final String KEY_DISCLAIMER_ACCEPTED = "@linecode_phone_control_disclaimer_accepted";
    private static final String KEY_ENABLED = "@linecode_phone_control_enabled";
    private static final String KEY_PERMISSION_PREFIX = "@linecode_phone_control_permission_";

    public static final String PERMISSION_SCREENSHOT = "screenshot";
    public static final String PERMISSION_CLICK = "click";
    public static final String PERMISSION_SWIPE = "swipe";
    public static final String PERMISSION_LONG_PRESS = "longPress";
    public static final String PERMISSION_VIEW_HIERARCHY = "viewHierarchy";
    public static final String PERMISSION_VIEW_ACTION = "viewAction";

    private static final String[] PERMISSION_IDS = new String[] {
            PERMISSION_SCREENSHOT,
            PERMISSION_CLICK,
            PERMISSION_SWIPE,
            PERMISSION_LONG_PRESS,
            PERMISSION_VIEW_HIERARCHY,
            PERMISSION_VIEW_ACTION
    };

    private final Context context;
    private final SettingsRepository settingsRepository;

    public PhoneControlRepository(Context context) {
        this.context = context.getApplicationContext();
        this.settingsRepository = new SettingsRepository(this.context);
    }

    public boolean isDisclaimerAccepted() {
        return settingsRepository.getBoolean(KEY_DISCLAIMER_ACCEPTED, false);
    }

    public void setDisclaimerAccepted(boolean accepted) {
        settingsRepository.setBoolean(KEY_DISCLAIMER_ACCEPTED, accepted);
    }

    public boolean isPhoneControlEnabled() {
        return isDisclaimerAccepted() && isAccessibilityEnabled();
    }

    public boolean isAccessibilityEnabled() {
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        if (services == null) {
            return false;
        }
        String target = LineCodeAccessibilityService.class.getName();
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() != null
                    && info.getResolveInfo().serviceInfo != null
                    && target.equals(info.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    public boolean isExplicitlyEnabled() {
        return settingsRepository.getBoolean(KEY_ENABLED, false);
    }

    public void setExplicitlyEnabled(boolean enabled) {
        settingsRepository.setBoolean(KEY_ENABLED, enabled);
    }

    public boolean isPermissionEnabled(String permissionId) {
        return settingsRepository.getBoolean(KEY_PERMISSION_PREFIX + permissionId, true);
    }

    public void setPermissionEnabled(String permissionId, boolean enabled) {
        if (permissionId == null || permissionId.length() == 0) {
            return;
        }
        settingsRepository.setBoolean(KEY_PERMISSION_PREFIX + permissionId, enabled);
    }

    public String[] getPermissionIds() {
        return PERMISSION_IDS.clone();
    }
}
