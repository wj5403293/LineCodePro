package cn.lineai.resource;

import java.io.InputStream;

/**
 * Provides access to application resources without requiring Android Context.
 * Decouples data layer from Android framework.
 */
public interface ResourceProvider {
    InputStream openAsset(String path);
    String getString(int resId);
    String getString(int resId, Object... formatArgs);
}
