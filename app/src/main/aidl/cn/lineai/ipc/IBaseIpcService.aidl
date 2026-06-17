// IBaseIpcService.aidl
//
// 接口版本：v1
//
// 所有 IPC 提供者服务的基类接口，定义通用的提供者元信息查询方法。
// 子接口（如 ITerminalProviderService）在此基础上扩展特定能力。
package cn.lineai.ipc;

interface IBaseIpcService {
    String getProviderType();
    String getProviderInfo();
    boolean isAvailable();
}
