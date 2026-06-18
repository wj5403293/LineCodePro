package cn.lineai.data.repository;

import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderType;
import java.util.List;

/**
 * IPC 仓库接口，定义 IpcProviderRepository 的公开契约。
 */
public interface IpcProviderStore {

    /**
     * 列出所有已注册的 IPC 终端/工具提供者。
     */
    List<IpcProviderConfig> getProviders();

    /**
     * 根据 provider_type 字符串过滤返回 IPC 终端/工具提供者。
     */
    List<IpcProviderConfig> getProvidersByType(String providerType);

    /**
     * 根据枚举类型过滤返回 IPC 终端/工具提供者。
     */
    List<IpcProviderConfig> getProvidersByType(IpcProviderType type);

    /**
     * 保存（插入或替换）IPC 终端/工具提供者配置。
     */
    IpcProviderConfig saveProvider(IpcProviderConfig input);

    /**
     * 设置提供者启用状态。
     */
    void setProviderEnabled(String id, boolean enabled);

    /**
     * 删除 IPC 终端/工具提供者。
     */
    void deleteProvider(String id);
}
