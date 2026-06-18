package cn.lineai.data.repository;

import cn.lineai.model.FileTreeNode;
import java.util.Set;

/**
 * 终端 IPC 文件树仓库接口，定义 IpcFileTreeRepository 的公开契约。
 */
public interface IpcFileTreeStore {

    /**
     * 构造终端 IPC 根目录的文件树。
     */
    FileTreeNode buildTree(String rootPath, Set<String> expandedPaths) throws Exception;

    /**
     * 列出终端 IPC 目录的子项。
     */
    FileTreeNode listDirectory(String directoryPath) throws Exception;

    /**
     * 读取终端 IPC 路径下的文件字节。
     */
    byte[] readFileBytes(String path, long maxBytes) throws Exception;

    /**
     * 在终端 IPC 父目录下创建空文件。
     */
    void createFile(String parentPath, String name) throws Exception;

    /**
     * 在终端 IPC 父目录下创建子目录。
     */
    void createDirectory(String parentPath, String name) throws Exception;

    /**
     * 重命名终端 IPC 路径。
     */
    String rename(String path, String newName) throws Exception;

    /**
     * 递归删除终端 IPC 路径。
     */
    void delete(String path) throws Exception;

    /**
     * 复制到目标目录。
     */
    String copyInto(String sourcePath, String targetDirectoryPath) throws Exception;
}
