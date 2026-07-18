package cn.lineai.data.repository;

import cn.lineai.model.FileTreeNode;
import java.util.Set;

/**
 * 本地文件树仓库接口，定义 FileTreeRepository 的公开契约。
 */
public interface FileTreeStore {

    /**
     * 构造根目录的本地文件树，缺失根时返回占位节点。
     */
    FileTreeNode buildTree(String rootPath, Set<String> expandedPaths);

    /**
     * 构造根目录的本地文件树，对缺失或非目录情况抛出异常。
     */
    FileTreeNode buildReadableTree(String rootPath, Set<String> expandedPaths);

    /**
     * 判断本地路径是否为目录。
     */
    boolean isDirectory(String path);

    /**
     * 判断本地路径是否存在。
     */
    boolean exists(String path);

    /**
     * 在父目录下创建空文件。
     */
    void createFile(String parentPath, String name) throws Exception;

    /**
     * 在父目录下创建子目录。
     */
    void createDirectory(String parentPath, String name) throws Exception;

    /**
     * 重命名本地路径，返回新绝对路径。
     */
    String rename(String path, String newName) throws Exception;

    /**
     * 递归删除本地路径。
     */
    void delete(String path) throws Exception;

    /**
     * 将源路径复制到目标目录，返回新绝对路径。
     */
    String copyInto(String sourcePath, String targetDirectoryPath) throws Exception;
}
