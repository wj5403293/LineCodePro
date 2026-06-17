package cn.lineai.model;

import java.util.List;

/**
 * 模型仓储接口，定义 ModelRepository 的公开契约。
 */
public interface ModelStore {
    List<ModelConfig> getModels();

    ModelConfig save(ModelConfig model);

    ModelConfig getModel(String id);

    void deleteModels(List<String> ids);

    void setSelectedModelId(String id);

    String getSelectedModelId();

    ModelConfig getSelectedModel();

    void clearAll();
}
