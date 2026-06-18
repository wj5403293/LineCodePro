package cn.lineai.data.repository;

import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.ipc.terminal.TerminalShellResult;
import cn.lineai.model.FileTreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class IpcFileTreeRepository implements IpcFileTreeStore {
    private static final int MAX_CHILDREN_PER_DIR = 300;
    private static final String[] IGNORED_ENTRIES = {".git", "node_modules", ".gradle", "build", "dist"};

    private final IpcProviderManager ipcProviderManager;

    public IpcFileTreeRepository(IpcProviderManager ipcProviderManager) {
        this.ipcProviderManager = ipcProviderManager;
    }

    @Override
    public FileTreeNode buildTree(String rootPath, Set<String> expandedPaths) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        String cleanPath = normalizePath(rootPath);
        return buildNode(provider, cleanPath, true,
                expandedPaths == null ? Collections.emptySet() : expandedPaths);
    }

    @Override
    public FileTreeNode listDirectory(String directoryPath) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        String cleanPath = normalizePath(directoryPath);
        String name = basename(cleanPath);
        if (name.length() == 0) {
            name = cleanPath;
        }
        return new FileTreeNode(name, cleanPath, true, true,
                listChildren(provider, cleanPath, Collections.emptySet()));
    }

    @Override
    public byte[] readFileBytes(String path, long maxBytes) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        String cleanPath = normalizePath(path);
        long actualSize = provider.getFileSize(cleanPath);
        if (maxBytes > 0 && actualSize > maxBytes) {
            throw new IllegalStateException(
                    "文件过大，文件大小 " + actualSize + " 字节超出上限 " + maxBytes + " 字节: " + path);
        }
        byte[] data = provider.readFile(cleanPath);
        if (maxBytes > 0 && data.length > maxBytes) {
            throw new IllegalStateException(
                    "文件过大，文件大小 " + data.length + " 字节超出上限 " + maxBytes + " 字节: " + path);
        }
        return data;
    }

    @Override
    public void createFile(String parentPath, String name) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        String cleanName = cleanName(name);
        String targetPath = join(parentPath, cleanName);
        if (provider.fileExists(targetPath)) {
            throw new IllegalStateException("文件已存在: " + cleanName);
        }
        if (!provider.writeFile(targetPath, new byte[0])) {
            throw new IllegalStateException("无法创建文件: " + cleanName);
        }
    }

    @Override
    public void createDirectory(String parentPath, String name) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        String cleanName = cleanName(name);
        String targetPath = join(parentPath, cleanName);
        if (provider.fileExists(targetPath)) {
            throw new IllegalStateException("目录已存在: " + cleanName);
        }
        TerminalShellResult result = provider.executeShell("mkdir -p " + shellQuote(targetPath), null, 30000, null);
        if (!result.isSuccess()) {
            throw new IllegalStateException("无法创建目录: " + cleanName);
        }
    }

    @Override
    public String rename(String path, String newName) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        String cleanName = cleanName(newName);
        String parent = parentPath(path);
        String targetPath = join(parent, cleanName);
        if (provider.fileExists(targetPath)) {
            throw new IllegalStateException("目标已存在: " + cleanName);
        }
        TerminalShellResult result = provider.executeShell(
                "mv " + shellQuote(path) + " " + shellQuote(targetPath), null, 30000, null);
        if (!result.isSuccess()) {
            throw new IllegalStateException("重命名失败: " + path);
        }
        return targetPath;
    }

    @Override
    public void delete(String path) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        if (!provider.fileExists(path)) {
            throw new IllegalStateException("路径不存在: " + path);
        }
        TerminalShellResult result = provider.executeShell(
                "rm -rf " + shellQuote(path), null, 120000, null);
        if (!result.isSuccess()) {
            throw new IllegalStateException("删除失败: " + path);
        }
    }

    @Override
    public String copyInto(String sourcePath, String targetDirectoryPath) throws Exception {
        TerminalIpcProvider provider = requireProvider();
        String targetPath = join(targetDirectoryPath, basename(sourcePath));
        TerminalShellResult result = provider.executeShell(
                "cp -R " + shellQuote(sourcePath) + " " + shellQuote(targetPath), null, 120000, null);
        if (!result.isSuccess()) {
            throw new IllegalStateException("复制失败: " + sourcePath);
        }
        return targetPath;
    }

    private FileTreeNode buildNode(
            TerminalIpcProvider provider,
            String path,
            boolean forceExpanded,
            Set<String> expandedPaths
    ) throws Exception {
        boolean directory = provider.fileExists(path);
        boolean expanded = directory && (forceExpanded || isExpandedPath(expandedPaths, path));
        ArrayList<FileTreeNode> children = new ArrayList<>();
        if (expanded) {
            children.addAll(listChildren(provider, path, expandedPaths));
        }
        String name = basename(path);
        if (name.length() == 0) {
            name = path;
        }
        return new FileTreeNode(name, path, directory, expanded, children);
    }

    private List<FileTreeNode> listChildren(
            TerminalIpcProvider provider,
            String directoryPath,
            Set<String> expandedPaths
    ) throws Exception {
        String json = provider.listDirDetailed(directoryPath);
        JSONArray array = new JSONArray(json);
        ArrayList<FileTreeNode> directories = new ArrayList<>();
        ArrayList<FileTreeNode> files = new ArrayList<>();
        for (int i = 0; i < array.length() && i < MAX_CHILDREN_PER_DIR * 2; i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String name = entry.optString("name", "");
            if (shouldSkipEntry(name)) {
                continue;
            }
            boolean isDir = entry.optBoolean("dir", false);
            String childPath = joinPath(directoryPath, name);
            boolean expanded = isDir && isExpandedPath(expandedPaths, childPath);
            ArrayList<FileTreeNode> grandchildren = new ArrayList<>();
            if (expanded) {
                grandchildren.addAll(listChildren(provider, childPath, expandedPaths));
            }
            FileTreeNode child = new FileTreeNode(name, childPath, isDir, expanded, grandchildren);
            if (isDir) {
                directories.add(child);
            } else {
                files.add(child);
            }
        }
        Comparator<FileTreeNode> byName = (left, right) -> left.getName().compareToIgnoreCase(right.getName());
        Collections.sort(directories, byName);
        Collections.sort(files, byName);
        ArrayList<FileTreeNode> children = new ArrayList<>();
        appendLimited(children, directories);
        appendLimited(children, files);
        return children;
    }

    private TerminalIpcProvider requireProvider() {
        TerminalIpcProvider provider = (TerminalIpcProvider) ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL);
        if (provider == null) {
            throw new IllegalStateException("终端提供者服务未绑定");
        }
        return provider;
    }

    private boolean isExpandedPath(Set<String> expandedPaths, String path) {
        if (expandedPaths == null || expandedPaths.isEmpty()) {
            return false;
        }
        return expandedPaths.contains(path);
    }

    private void appendLimited(ArrayList<FileTreeNode> target, List<FileTreeNode> source) {
        for (FileTreeNode node : source) {
            if (target.size() >= MAX_CHILDREN_PER_DIR) {
                return;
            }
            target.add(node);
        }
    }

    private boolean shouldSkipEntry(String name) {
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

    private String normalizePath(String path) {
        String value = path == null ? "" : path.trim();
        if (value.length() == 0 || "~".equals(value)) {
            return ".";
        }
        if (value.startsWith("~/")) {
            return "." + value.substring(1);
        }
        return value;
    }

    private String joinPath(String parentPath, String name) {
        String parent = normalizePath(parentPath);
        while (parent.length() > 1 && parent.endsWith("/")) {
            parent = parent.substring(0, parent.length() - 1);
        }
        if (".".equals(parent)) {
            return name;
        }
        if ("/".equals(parent)) {
            return "/" + name;
        }
        return parent + "/" + name;
    }

    private String join(String parentPath, String name) {
        String parent = normalizePath(parentPath);
        while (parent.length() > 1 && parent.endsWith("/")) {
            parent = parent.substring(0, parent.length() - 1);
        }
        if (".".equals(parent)) {
            return name;
        }
        if ("/".equals(parent)) {
            return "/" + name;
        }
        return parent + "/" + name;
    }

    private String basename(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        int index = value.lastIndexOf('/');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private String parentPath(String path) {
        String value = path == null ? "" : path.trim();
        int index = value.lastIndexOf('/');
        if (index <= 0) {
            throw new IllegalArgumentException("无法解析父目录: " + path);
        }
        return value.substring(0, index);
    }

    private String cleanName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.length() == 0) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("名称不能包含路径分隔符");
        }
        return value;
    }

    private String shellQuote(String value) {
        return "'" + (value == null ? "" : value).replace("'", "'\\''") + "'";
    }
}
