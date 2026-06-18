package cn.lineai.data.repository;

import cn.lineai.model.FileTreeNode;
import java.util.Set;

/**
 * SSH 文件树仓库接口，定义 SshFileTreeRepository 的公开契约。
 */
public interface SshFileTreeStore {

    /**
     * 构造 SSH 根目录的文件树。
     */
    FileTreeNode buildTree(String rootPath, Set<String> expandedPaths) throws Exception;

    /**
     * 列出 SSH 目录的子项。
     */
    FileTreeNode listDirectory(String directoryPath) throws Exception;

    /**
     * 判断 SSH 目录是否存在。
     */
    boolean directoryExists(String directoryPath) throws Exception;

    /**
     * 读取 SSH 路径下的文件字节。
     */
    byte[] readFileBytes(String path, long maxBytes) throws Exception;

    /**
     * 在 SSH 父目录下创建空文件。
     */
    void createFile(String parentPath, String name) throws Exception;

    /**
     * 在 SSH 父目录下创建子目录。
     */
    void createDirectory(String parentPath, String name) throws Exception;

    /**
     * 重命名 SSH 路径。
     */
    String rename(String path, String newName) throws Exception;

    /**
     * 递归删除 SSH 路径。
     */
    void delete(String path) throws Exception;

    /**
     * 复制到目标目录。
     */
    String copyInto(String sourcePath, String targetDirectoryPath) throws Exception;

    /**
     * 在 SSH 主目录创建托管项目，返回新项目路径。
     */
    String createManagedProject(String name) throws Exception;
}
