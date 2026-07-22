package cn.lineai.resource;

/**
 * Provides system configuration information without requiring Android Context.
 * Decouples data layer from Android framework.
 */
public interface SystemConfigProvider {
    boolean isDarkModeEnabled();
    int getSdkInt();
    String getFilesDirPath();
    String getDatabasePath(String name);
    String getExternalFilesDirPath();
}
