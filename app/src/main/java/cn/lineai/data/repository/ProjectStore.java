package cn.lineai.data.repository;

import java.util.List;

/**
 * 项目仓储接口，定义 ProjectRepository 的公开契约。
 */
public interface ProjectStore {
    List<ProjectRecord> getProjects();

    List<ProjectRecord> getProjects(String executionMode);

    ProjectRecord getSelectedProject();

    ProjectRecord getSelectedProject(String executionMode);

    ProjectRecord getSelectedProjectLegacy();

    void save(ProjectRecord project);

    void setSelected(String id);

    void setSelected(String id, String executionMode);

    boolean deleteProject(String id);

    boolean deleteProject(String id, String executionMode);

    ProjectRecord selectDefaultProject(String executionMode);

    ProjectRecord createManagedProject(String name);

    ProjectRecord saveExternalProject(String path, String label);

    ProjectRecord saveSshProject(String path, String label);

    ProjectRecord ensureSelectedProjectPath();

    ProjectRecord ensureSelectedProjectPath(String executionMode);

    String getLinecodeRootPath();

    String getDefaultHomePath();
}
