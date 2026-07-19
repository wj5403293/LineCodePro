package cn.lineai.data.repository;

/**
 * 学习上下文仓库接口，定义 LearningContextRepository 的数据存取契约。
 * 业务编排方法（buildLearningContext、getOverview）已移至 LearningContextService。
 */
public interface LearningContextStore {

    /**
     * 手动保存一条记忆。
     */
    void saveMemory(String id, String scope, String projectId, String content);

    /**
     * 自动抽取并保存记忆，置信度与现有条目合并。
     */
    void saveExtractedMemory(String scope, String projectId, String content, double confidence);

    /**
     * 删除一条记忆。
     */
    void deleteMemory(String id);

    /**
     * 将指定会话索引到对话索引中。
     */
    void indexConversation(String projectId, ConversationRecord conversation);
}
