package cn.lineai.workspace;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import cn.lineai.R;

public final class StoragePermissionManager {
    private final Context context;

    public StoragePermissionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasExternalStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean needsManageAllFilesPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager();
    }

    public String permissionDeniedMessage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return context.getString(R.string.permission_denied_manage_all);
        }
        return context.getString(R.string.permission_denied_read_write);
    }
}
