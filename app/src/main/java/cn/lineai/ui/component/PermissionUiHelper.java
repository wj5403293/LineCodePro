package cn.lineai.ui.component;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Encapsulates the runtime permission flows the chat workspace needs.
 *
 * <p>Modern Android (API 30+) routes the workspace's storage permission through the
 * "All files access" system settings screen, while older releases use the classic
 * {@code READ/WRITE_EXTERNAL_STORAGE} runtime permission. This helper wraps both code
 * paths and the resulting {@code onRequestPermissionsResult} dispatch, so the hosting
 * {@code Activity} can simply forward its callbacks here.</p>
 *
 * <p>Set an {@link OnPermissionResult} listener to be notified when a runtime permission
 * batch has been resolved. The helper does not own any UI of its own; it just issues the
 * platform intent and forwards the result.</p>
 */
public final class PermissionUiHelper {

    /** Request code used for legacy storage permission requests. */
    public static final int REQUEST_LEGACY_STORAGE = 7002;
    public static final int REQUEST_POST_NOTIFICATIONS = 7003;

    /** Listener notified when a runtime permission batch has been resolved. */
    public interface OnPermissionResult {
        void onPermissionResult(int requestCode, int[] grantResults);
    }

    private final Activity activity;
    private OnPermissionResult listener;

    public PermissionUiHelper(Activity activity) {
        this.activity = activity;
    }

    /**
     * Register a listener to be notified when a permission batch is resolved. Pass
     * {@code null} to clear.
     */
    public void setListener(OnPermissionResult listener) {
        this.listener = listener;
    }

    /**
     * Whether the platform recommends showing a rationale for {@code permission} before
     * requesting it.
     */
    public boolean shouldShowRequestPermissionRationale(String permission) {
        return activity.shouldShowRequestPermissionRationale(permission);
    }

    /**
     * Request legacy read/write storage permissions on API &lt; 30, or open the "All files
     * access" settings screen on API 30+. The latter is not a runtime permission and so
     * never produces a result through {@link #onRequestPermissionsResult}; the caller is
     * expected to refresh its state on the next {@code onResume}.
     */
    public void requestLegacyStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openManageAllFilesAccessSettings();
            return;
        }
        activity.requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, REQUEST_LEGACY_STORAGE);
    }

    public boolean hasPostNotificationsPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPostNotificationsPermission()) {
            return;
        }
        activity.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
    }

    /**
     * Open the system "All files access" settings screen on API 30+, falling back to the
     * app's details screen (and ultimately the system settings root) on failure.
     */
    public void openManageAllFilesAccessSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
        }
        try {
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    /**
     * Forward the host's {@code onRequestPermissionsResult} callback here. Listeners are
     * notified of the grant array verbatim; if no listener is registered the result is
     * dropped.
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (listener == null) {
            return;
        }
        listener.onPermissionResult(requestCode, grantResults);
    }

    /**
     * Convenience helper to translate a legacy storage grant array into a single
     * "everything granted" boolean.
     */
    public static boolean isLegacyStorageGranted(int[] grantResults) {
        if (grantResults == null) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return grantResults.length > 0;
    }
}
