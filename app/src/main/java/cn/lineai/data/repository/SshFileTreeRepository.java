package cn.lineai.data.repository;

import cn.lineai.model.FileTreeNode;
import cn.lineai.ssh.SshService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class SshFileTreeRepository extends FileTreeBaseRepository implements SshFileTreeStore {
    private static final int MAX_SFTP_SCAN_ITEMS = 1200;

    private final SshService sshService;

    public SshFileTreeRepository(SshService sshService) {
        this.sshService = sshService;
    }

    @Override
    public FileTreeNode buildTree(String rootPath, Set<String> expandedPaths) throws Exception {
        return sshService.withSftp(sftp -> {
            sftp.cd(normalizePath(rootPath));
            String absolutePath = sftp.pwd();
            return buildSftpTreeNode(sftp, absolutePath, true,
                    expandedPaths == null ? Collections.emptySet() : expandedPaths);
        }, 120000);
    }

    @Override
    public FileTreeNode listDirectory(String directoryPath) throws Exception {
        return listDirectoryWithSftp(directoryPath);
    }

    @Override
    public boolean directoryExists(String directoryPath) throws Exception {
        return sshService.withSftp(sftp -> {
            try {
                sftp.cd(normalizePath(directoryPath));
                return true;
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    return false;
                }
                throw e;
            }
        }, 30000);
    }

    @Override
    public byte[] readFileBytes(String path, long maxBytes) throws Exception {
        return sshService.withSftp(sftp -> {
            String cleanPath = normalizePath(path);
            if (sftp.lstat(cleanPath).isDir()) {
                throw new IllegalStateException("路径是目录，无法读取文件: " + path);
            }
            long size = sftp.lstat(cleanPath).getSize();
            if (maxBytes > 0 && size > maxBytes) {
                throw new IllegalStateException("文件过大，当前上限为 " + (maxBytes / 1024L / 1024L) + " MB: " + path);
            }
            InputStream input = sftp.get(cleanPath);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (maxBytes > 0 && total > maxBytes) {
                        throw new IllegalStateException("文件过大，当前上限为 " + (maxBytes / 1024L / 1024L) + " MB: " + path);
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            } finally {
                input.close();
            }
        }, 120000);
    }

    private FileTreeNode listDirectoryWithSftp(String directoryPath) throws Exception {
        return sshService.withSftp(sftp -> {
            sftp.cd(normalizePath(directoryPath));
            String absolutePath = sftp.pwd();
            String name = basename(absolutePath);
            if (name.length() == 0) {
                name = absolutePath;
            }
            return new FileTreeNode(name, absolutePath, true, true,
                    listChildrenWithSftp(sftp, absolutePath, Collections.emptySet()));
        }, 30000);
    }

    @Override
    public void createFile(String parentPath, String name) throws Exception {
        String cleanName = cleanName(name);
        sshService.withSftp(sftp -> {
            sftp.cd(normalizePath(parentPath));
            if (exists(sftp, cleanName)) {
                throw new IllegalStateException("文件已存在: " + cleanName);
            }
            sftp.put(new ByteArrayInputStream(new byte[0]), cleanName, ChannelSftp.OVERWRITE);
            return null;
        }, 30000);
    }

    @Override
    public void createDirectory(String parentPath, String name) throws Exception {
        String cleanName = cleanName(name);
        sshService.withSftp(sftp -> {
            sftp.cd(normalizePath(parentPath));
            if (exists(sftp, cleanName)) {
                throw new IllegalStateException("目录已存在: " + cleanName);
            }
            sftp.mkdir(cleanName);
            return null;
        }, 30000);
    }

    @Override
    public String rename(String path, String newName) throws Exception {
        String cleanName = cleanName(newName);
        String parentPath = parentPath(path);
        String targetPath = join(parentPath, cleanName);
        sshService.withSftp(sftp -> {
            if (exists(sftp, targetPath)) {
                throw new IllegalStateException("目标已存在: " + cleanName);
            }
            sftp.rename(normalizePath(path), normalizePath(targetPath));
            return null;
        }, 30000);
        return targetPath;
    }

    @Override
    public void delete(String path) throws Exception {
        sshService.withSftp(sftp -> {
            deleteRecursively(sftp, normalizePath(path));
            return null;
        }, 120000);
    }

    @Override
    public String copyInto(String sourcePath, String targetDirectoryPath) throws Exception {
        String targetPath = join(targetDirectoryPath, basename(sourcePath));
        sshService.executeCommand("test ! -e " + shellQuote(targetPath) + " && cp -R " + shellQuote(sourcePath)
                + " " + shellQuote(targetPath), 120000);
        return targetPath;
    }

    @Override
    public String createManagedProject(String name) throws Exception {
        String cleanName = cleanName(name);
        String output = sshService.executeCommand("mkdir -p ~/.linecode/project/" + shellQuote(cleanName)
                + " && cd ~/.linecode/project/" + shellQuote(cleanName) + " && pwd", 30000);
        return firstOutputLine(output);
    }

    private FileTreeNode buildSftpTreeNode(
            ChannelSftp sftp,
            String path,
            boolean forceExpanded,
            Set<String> expandedPaths
    ) throws Exception {
        String cleanPath = normalizePath(path);
        boolean directory = sftp.lstat(cleanPath).isDir();
        boolean expanded = directory && (forceExpanded || isExpandedPath(expandedPaths, cleanPath));
        ArrayList<FileTreeNode> children = new ArrayList<>();
        if (expanded) {
            children.addAll(listChildrenWithSftp(sftp, cleanPath, expandedPaths));
        }
        String name = basename(cleanPath);
        if (name.length() == 0) {
            name = cleanPath;
        }
        return new FileTreeNode(name, cleanPath, directory, expanded, children);
    }

    private List<FileTreeNode> listChildrenWithSftp(
            ChannelSftp sftp,
            String directoryPath,
            Set<String> expandedPaths
    ) throws Exception {
        ArrayList<ChannelSftp.LsEntry> entries = new ArrayList<>();
        sftp.ls(normalizePath(directoryPath), entry -> {
            String name = entry == null ? "" : entry.getFilename();
            if (shouldSkipEntry(name)) {
                return ChannelSftp.LsEntrySelector.CONTINUE;
            }
            entries.add(entry);
            return entries.size() >= MAX_SFTP_SCAN_ITEMS
                    ? ChannelSftp.LsEntrySelector.BREAK
                    : ChannelSftp.LsEntrySelector.CONTINUE;
        });

        ArrayList<FileTreeNode> directories = new ArrayList<>();
        ArrayList<FileTreeNode> files = new ArrayList<>();
        for (ChannelSftp.LsEntry entry : entries) {
            try {
                FileTreeNode child = nodeFromEntry(sftp, directoryPath, entry, expandedPaths);
                if (child.isDirectory()) {
                    directories.add(child);
                } else {
                    files.add(child);
                }
            } catch (Exception ignored) {
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

    private FileTreeNode nodeFromEntry(
            ChannelSftp sftp,
            String parentPath,
            ChannelSftp.LsEntry entry,
            Set<String> expandedPaths
    ) throws Exception {
        String name = entry.getFilename();
        String path = joinRemotePath(parentPath, name);
        boolean directory = entry.getAttrs() != null && entry.getAttrs().isDir();
        boolean expanded = directory && isExpandedPath(expandedPaths, path);
        ArrayList<FileTreeNode> children = new ArrayList<>();
        if (expanded) {
            children.addAll(listChildrenWithSftp(sftp, path, expandedPaths));
        }
        return new FileTreeNode(name, path, directory, expanded, children);
    }

    private boolean isExpandedPath(Set<String> expandedPaths, String path) {
        if (expandedPaths == null || expandedPaths.isEmpty()) {
            return false;
        }
        String cleanPath = normalizePath(path);
        return expandedPaths.contains(cleanPath) || expandedPaths.contains(path);
    }

    private boolean exists(ChannelSftp sftp, String path) throws Exception {
        try {
            sftp.lstat(normalizePath(path));
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw e;
        }
    }

    private void deleteRecursively(ChannelSftp sftp, String path) throws Exception {
        if (path == null || path.trim().length() == 0 || ".".equals(path.trim()) || "~".equals(path.trim())) {
            throw new IllegalArgumentException("不能删除 SSH 根目录");
        }
        String cleanPath = normalizePath(path);
        if (!exists(sftp, cleanPath)) {
            throw new IllegalStateException("路径不存在: " + path);
        }
        if (sftp.lstat(cleanPath).isDir()) {
            ArrayList<String> children = new ArrayList<>();
            sftp.ls(cleanPath, entry -> {
                String name = entry == null ? "" : entry.getFilename();
                if (!shouldSkipEntry(name)) {
                    children.add(joinRemotePath(cleanPath, name));
                }
                return ChannelSftp.LsEntrySelector.CONTINUE;
            });
            for (String child : children) {
                deleteRecursively(sftp, child);
            }
            sftp.rmdir(cleanPath);
            return;
        }
        sftp.rm(cleanPath);
    }

    private String joinRemotePath(String parentPath, String name) {
        String parent = normalizePath(parentPath);
        String child = name == null ? "" : name;
        while (parent.length() > 1 && parent.endsWith("/")) {
            parent = parent.substring(0, parent.length() - 1);
        }
        if (".".equals(parent)) {
            return child;
        }
        if ("/".equals(parent)) {
            return "/" + child;
        }
        return parent + "/" + child;
    }

    private String join(String parentPath, String name) {
        String parent = directoryOrHome(parentPath);
        while (parent.length() > 1 && parent.endsWith("/")) {
            parent = parent.substring(0, parent.length() - 1);
        }
        return parent + "/" + name;
    }

    private String firstOutputLine(String output) {
        String[] lines = (output == null ? "" : output).split("\\r?\\n");
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (value.length() > 0 && !value.startsWith("exit status")) {
                return value;
            }
        }
        return "";
    }

    private String directoryOrHome(String path) {
        String value = path == null ? "" : path.trim();
        return value.length() == 0 ? "~" : value;
    }
}
