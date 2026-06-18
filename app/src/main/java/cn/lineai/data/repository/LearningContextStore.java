package cn.lineai.data.repository;

import cn.lineai.model.MemoryOverviewState;

/**
 * 学习上下文仓库接口，定义 LearningContextRepository 的公开契约。
 */
public interface LearningContextStore {

    /**
     * 构造当前项目的学习上下文文本，用于注入 system prompt。
     */
    String buildLearningContext(String projectId, String userInput, String excludeConversationId);

    /**
     * 返回当前项目的记忆总览（用户/项目/环境/工作记忆/历史）。
     */
    MemoryOverviewState getOverview(String projectId);

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
