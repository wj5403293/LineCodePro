package cn.lineai.data.repository;

import cn.lineai.model.FileTreeNode;
import java.util.ArrayList;
import java.util.List;

/**
 * FileTree Repository 公共基类，集中维护路径操作与条目过滤等共享方法。
 */
public abstract class FileTreeBaseRepository {
    protected static final int MAX_CHILDREN_PER_DIR = 300;
    protected static final String[] IGNORED_ENTRIES = {".git", "node_modules", ".gradle", "build", "dist"};

    protected String cleanName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.length() == 0) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if (value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("Name cannot contain path separators");
        }
        return value;
    }

    protected boolean shouldSkipEntry(String name) {
        String value = name == null ? "" : name;
        if (value.length() == 0 || ".".equals(value) || "..".equals(value)) {
            return true;
        }
        for (String ignored : IGNORED_ENTRIES) {
            if (ignored.equals(value)) {
                return true;
            }
        }
        return false;
    }

    protected void appendLimited(ArrayList<FileTreeNode> target, List<FileTreeNode> source) {
        for (FileTreeNode node : source) {
            if (target.size() >= MAX_CHILDREN_PER_DIR) {
                return;
            }
            target.add(node);
        }
    }

    protected String basename(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        int index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    protected String parentPath(String path) {
        String value = path == null ? "" : path.trim();
        int index = value.lastIndexOf('/');
        if (index <= 0) {
            throw new IllegalArgumentException("Cannot resolve parent directory: " + path);
        }
        return value.substring(0, index);
    }

    protected String normalizePath(String path) {
        String value = path == null ? "" : path.trim();
        if (value.length() == 0 || "~".equals(value)) {
            return ".";
        }
        if (value.startsWith("~/")) {
            return "." + value.substring(1);
        }
        return value;
    }

    protected String shellQuote(String value) {
        return "'" + (value == null ? "" : value).replace("'", "'\\''") + "'";
    }
}
