package cn.lineai.mvp;

/**
 * 扩展操作回调接口，供 ExtensionKindDescriptor 的 performAdd/performEdit 回调使用。
 * 将描述符与具体 View 解耦，避免 mvp 层依赖 ui.component 层。
 */
public interface ExtensionActionCallback {
    void onAddAgent();

    void onEditAgent(String id);

    void onAddMcp();

    void onEditMcp(String id);

    void onAddSkill();
}
