package cn.lineai.mvp;

import cn.lineai.model.ModelConfig;
import java.util.List;

public final class ModelManagementController {
    interface Host {
        void refreshModelsScreen();

        void returnToModelsScreen();

        void render();
    }

    interface ModelStore {
        List<ModelConfig> getModels();

        ModelConfig getModel(String id);

        String getSelectedModelId();

        void setSelectedModelId(String id);

        ModelConfig save(ModelConfig model);

        void deleteModels(List<String> ids);
    }

    private static final class RepositoryModelStore implements ModelStore {
        private final cn.lineai.model.ModelStore modelStore;

        RepositoryModelStore(cn.lineai.model.ModelStore modelStore) {
            this.modelStore = modelStore;
        }

        @Override
        public List<ModelConfig> getModels() {
            return modelStore.getModels();
        }

        @Override
        public ModelConfig getModel(String id) {
            return modelStore.getModel(id);
        }

        @Override
        public String getSelectedModelId() {
            return modelStore.getSelectedModelId();
        }

        @Override
        public void setSelectedModelId(String id) {
            modelStore.setSelectedModelId(id);
        }

        @Override
        public ModelConfig save(ModelConfig model) {
            return modelStore.save(model);
        }

        @Override
        public void deleteModels(List<String> ids) {
            modelStore.deleteModels(ids);
        }
    }

    private final ModelStore modelStore;
    private final Host host;

    public ModelManagementController(cn.lineai.model.ModelStore modelStore, Host host) {
        this(new RepositoryModelStore(modelStore), host);
    }

    ModelManagementController(ModelStore modelStore, Host host) {
        this.modelStore = modelStore;
        this.host = host;
    }

    public List<ModelConfig> getModels() {
        return modelStore.getModels();
    }

    public ModelConfig getModel(String id) {
        return modelStore.getModel(id);
    }

    public String getSelectedModelId() {
        return modelStore.getSelectedModelId();
    }

    public void selectModel(String id) {
        modelStore.setSelectedModelId(id);
        host.refreshModelsScreen();
        host.render();
    }

    public void saveModel(ModelConfig model) {
        ModelConfig saved = modelStore.save(model);
        modelStore.setSelectedModelId(saved.getId());
        host.returnToModelsScreen();
        host.render();
    }

    public void deleteModels(List<String> ids) {
        modelStore.deleteModels(ids);
        host.refreshModelsScreen();
        host.render();
    }
}
