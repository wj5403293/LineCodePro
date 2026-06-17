// ITerminalProviderService.aidl
//
// 接口版本：v1
//
// 调用方权限：调用方必须持有 cn.lineai.permission.IPC_TERMINAL_PROVIDER
// （protectionLevel="normal"，在 app 模块 AndroidManifest.xml 中声明）。
// 提供方 Service 须在 <service> 标签上通过 android:permission 属性强制校验，
// 参见 terminal-provider 模块 AndroidManifest.xml 中的 TerminalProviderService 声明。
package cn.lineai.ipc.terminal;

import cn.lineai.ipc.terminal.ITerminalProviderCallback;

interface ITerminalProviderService {
    // 通用 IPC 基类方法
    String getProviderType();
    String getProviderInfo();
    boolean isAvailable();

    // SHELL 执行
    int executeShell(String command, String cwd, long timeoutMs, ITerminalProviderCallback callback);

    // 文件操作（类似 SFTP）
    byte[] readFile(String path);
    boolean writeFile(String path, in byte[] data);
    boolean deleteFile(String path);
    String[] listDir(String path);
    boolean fileExists(String path);
    long fileSize(String path);

    // 列出目录下的文件与子目录，返回 JSON 数组字符串。
    // 格式：[{"name":"文件名","dir":是否为目录,"size":字节数}, ...]
    // 空目录或无效路径返回 "[]"。
    String listDirDetailed(String path);
}
