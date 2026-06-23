# 更新日志

## v1.1.4

### 架构与维护

- **MVP 控制器继续拆分** - 将聊天交互、生成流程、模型提示、会话持久化、上下文压缩、目录选择、扩展草稿、扩展管理、IPC Provider、文件树交互、项目工作区、模型交互、覆盖层动作、工具审批、工具消息与存储维护等职责拆到独立控制器，`MainCoordinator` 进一步收敛为协调入口，降低后续功能改动的冲突面
- **屏幕缓存与刷新机制** - `MainChatView` 新增屏幕缓存，切换设置/模型/扩展等页面时复用已构建视图；同时新增 `invalidateScreen` 刷新入口，支持在数据变化后精准重建当前页面
- **模型列表错误日志** - 查询模型列表与压缩模型列表失败时会写入错误日志中心，并对 API key 等敏感字段脱敏，方便定位第三方模型服务返回异常

### Tool Call 与上下文

- **停止生成时补齐 tool 消息** - 用户停止生成或工具执行被中断时，会为所有未完成的 `tool_call` 追加错误态 `tool` 结果，并替换仍处于 `running`、`pending` 或空内容 `accepted` 状态的工具结果，避免后续请求因缺少 tool 消息而触发模型协议顺序错误
- **上下文窗口保持工具调用分组** - `ContextManager` 选择上下文窗口时会把 assistant 的 `tool_calls` 与对应 `tool` 结果作为一组保留，避免只保留 tool 结果或只保留 assistant tool_call 导致 OpenAI/Responses 等协议拒绝请求

### SSH 与 Shell 修复

- **SSH 成功命令不再误报错误** - 修复 SSH 命令已成功结束但收尾读取阶段被中断时误标为失败、界面显示 `错误:null` 的问题；命令通道关闭后会继续读取退出码和剩余输出，再恢复线程中断状态
- **工具执行错误文案更准确** - `ToolExecutor` 区分参数解析失败与工具执行失败；异常无 message 时显示异常类型或“未知错误”，不再把 `null` 拼进用户可见错误
- **Shell 退出码展示修复** - IPC/终端提供者 shell 返回非 0 退出码且已有输出时，会同时保留命令输出和退出码，便于判断失败原因
- **SSH 设置页错误显示修复** - SSH 连接测试失败时会显示可读异常描述，避免无 message 异常显示空白或 `null`
- **主题页面文案修复** - 修复 Release 资源裁剪后主题页面显示 `color_background`、`starter_high_contrast` 等带下划线 ID 的问题；主题模式、创作起点、颜色项和“当前编辑”标签改为直接引用多语言资源，中文环境下正常显示中文名称

### 测试

- 新增 `ToolMessageControllerTest` 覆盖停止生成时为所有未完成 tool call 补齐结果、保留已完成结果、替换 running 结果
- 新增 `ToolExecutorTest` 覆盖无 message 异常不再渲染 `null`
- 扩展 `ContextManagerTest` 覆盖 assistant tool_calls 与 tool 结果在上下文窗口中的成组保留

---

## v1.1.3

### 新功能

- **控制手机 (实验)** - 新增手机控制能力，可在 `设置 -> 高级功能 -> 控制手机` 中开启并配置权限；支持截图、坐标点击、滑动、长按、View 层级读取、按文本/资源 ID 点击 View，以及返回、主页、最近任务、通知栏、快捷设置、锁屏、退出当前应用等系统动作
- **截图与图片理解联动** - 手机截图改为保存到应用缓存目录并仅向 AI 返回图片路径，AI 可直接将路径传给现有图片理解工具；截图缓存会在应用启动时清理，避免长期占用存储
- **错误日志中心** - 新增 `设置 -> 数据 -> 错误日志` 页面，遇到解析、HTTP、流式、SSE、IO、未捕获异常等错误时自动保存完整日志到应用私有目录；列表可查看日志，点击后可通过其他应用打开完整堆栈
- **HTTP/解析诊断日志** - HTTP 与响应解析相关错误会记录完整请求、完整响应、流式已接收内容和异常堆栈，并自动脱敏 `Authorization`、`x-api-key`、JSON 密钥字段与大段图片/base64 内容
- **关于页 GitHub 入口** - 关于页面新增 GitHub 仓库入口，点击可跳转到 `github.com/LangLang03/LineCodePro`

### 优化与体验

- **手机控制工具卡片** - 控制手机工具调用不再按 MCP 通用 JSON 卡片展示，改为与文件读取、目录列表、搜索等工具一致的简洁展示；标题直接显示 `截图`、`点击`、`滑动`、`长按`、`系统动作` 等具体操作名称
- **手机控制设置页** - 优化控制手机设置页面布局，权限申请、权限管理标题与开关行对齐；移除权限管理底部窄卡片背景，视觉上更接近普通设置列表
- **手机控制 i18n** - 手机控制工具卡片、权限项、错误提示、执行结果等用户可见文案全部资源化，补齐英文与中文翻译，避免硬编码中文
- **图片理解模型提示调整** - 控制手机页面不再因未配置图片理解模型而禁用功能；未配置时由图片理解工具在实际调用时给出提示
- **LLM 通信保活** - LLM 生成、流式响应和手动压缩期间临时启用前台保活服务与 WakeLock，降低应用进入后台后与 LLM 通信断开的概率；生成结束后会释放临时保活，不影响用户手动保活状态

### Agent 与远端工具

- **SSH 子 Agent 修复** - 修复 `ssh` 模式下 Agent、Agent 编排、探索 Agent、Sub Coding 工具列表为空的问题，子 Agent 可正常使用远端 shell、搜索、任务清单、图片理解等工具
- **SSH 只读策略调整** - SSH/终端提供者模式下不再通过工具过滤强制子 Agent 只读，而是通过提示词约束探索类 Agent 只执行只读命令；Agent 与 Agent Pipeline 在子 Agent 内继续黑名单递归调用，避免无限嵌套
- **终端提供者工具隔离** - SSH Shell 与终端提供者使用独立工具开关存储，关闭终端提供者的图片理解、Shell 等工具不会影响 SSH 模式，反之亦然
- **终端提供者命名优化** - 终端提供者里的 Shell 工具组改为 `IPC Shell`，描述改为通过终端提供者 IPC 执行，避免与 SSH Shell 混淆

### Bug 修复

- **无障碍状态检测** - 修复系统已开启无障碍但应用重进后工具调用误报“无障碍没打开”的问题；当授权存在但服务连接尚未恢复时，会短暂等待连接并给出更准确提示
- **截图兼容性崩溃** - 修复部分 Android 版本 `AccessibilityService.ScreenshotResult` 无 `getBitmap()` 方法导致的反射崩溃问题
- **手机控制系统动作** - 新增独立系统动作工具与权限开关，补齐返回、主页、最近任务、通知栏、快捷设置、锁屏、电源菜单、退出当前应用等能力，避免 AI 无法退出 APP 或返回上一级
- **错误日志打开与清理** - 错误日志通过只读私有 `ContentProvider` 授权单个日志文件给外部应用打开，右上角支持一键清空全部日志

### 安全与合规

- **GPL 许可证打包** - 将 GPL 许可证作为 APK `META-INF/LICENSE` 随包分发，不放入 `strings.xml`，不放入 Android `res/raw`；Gradle packaging 保留该文件，避免被默认排除
- **日志脱敏** - 错误日志保存前自动脱敏密钥、鉴权头与超大图片内容，降低诊断日志泄露敏感信息的风险

### 测试与验证

- 新增 `ToolSettingsRepositoryTest` 覆盖 SSH/终端提供者工具开关隔离、远端工具过滤与 IPC Shell 展示
- 新增 `ErrorLogRedactorTest` 覆盖错误日志密钥、鉴权头和大段内容脱敏
- 已验证 `compileDebugJavaWithJavac`、`lintDebug`、`assembleDebug`，并检查 APK 内包含 `META-INF/LICENSE`

---

## v1.1.1

### 优化与重构

- **IPC 架构模块化** - 新增 `ipc` 独立 Gradle 模块（`ipc/`），将原 `app` 内的 IPC 服务端/客户端抽象（`IpcProviderManager`、`IpcProviderRegistry`、`IpcProviderScanner`、`IpcProviderConnectionState`、`IpcProviderStateListener`、`IpcProviderFactory`、`BaseIpcProvider`、`AbstractIpcProviderService`、`IpcServerExecutors`、`IpcServerPermission` 等）抽离到独立模块；`terminal-provider` 模块新增 `TerminalIpcProvider` / `TerminalProviderServiceFactory` 接入新抽象；`cn.lineai.ipc.terminal` 模块继续作为终端提供者实现，整体 IPC 拓扑从「app 内混合」改为「core 抽象 + provider 实现」分层
- **Provider 管理器重构** - `IpcProviderManager` 改为支持工厂注册扩展（`IpcProviderRegistry`），新增全局观察者模式与连接状态机（`IpcProviderConnectionState`），供 UI 订阅 provider 连接生命周期
- **MainCoordinator 内部类外提** - 8 个内联内部类（`AgentRunResult`、`PipelineAgent`、`AgentProgressMirror`、`AgentProgressSession`、`PipelineProgressSession`、`PipelineAgentState`、`ToolExecutionBatch`、`PendingToolExecution`）抽取为 `cn.lineai.mvp.agent` 包下的独立顶层类；`MainCoordinator` 由 561 行 → 180 行；统一以 getter 替换直接字段访问，简化 `Collections.singletonList` 调用
- **AgentExecutionController 抽取** - 将 `MainCoordinator` 内联 Agent 与 Pipeline Agent 执行相关代码（590 行）抽离到独立 `AgentExecutionController`；`MainCoordinator` 由 639 行 → 49 行，职责简化为协调调度
- **实验性功能与插件页清理** - 移除 `ExperimentalSettingsScreenView`、`PluginPageScreenView` 页面与 `ScreenFactories` 中的注册；清理 `SettingsScreenView`、`ScreenNavigationController`、`MainChatView`、`SimpleScreenContent` 中的实验性入口与冗余资源；`values/strings.xml` 与 `values-zh/strings.xml` 同步删除对应文案
- **教程页重构** - 新增 `app/src/main/assets/tutorials/simple.md`（78 行）与 `pro.md`（217 行）两套内置教程源；`TutorialScreenView` 支持从 assets 读取 Markdown 渲染，新增简单模式与专业模式分类
- **Markdown 渲染** - `MarkdownView` 扩展 36 行，支持教程页与错误状态文本渲染
- **AssistantMessageView 错误态** - 新增错误状态纯文本渲染支持
- **附件选择器** - `AttachmentPickerCoordinator` 调整 73 行以适配新 IPC 抽象

### 文档

- **README 社交预览** - `README.md` 与 `README_CN.md` 顶部新增动态社交预览横幅
- **ipc 模块文档** - 新增 `ipc/README.md`（741 行）与 `ipc/README_CN.md`（739 行），介绍 IPC 抽象、Provider 注册、连接状态机、权限模型与扩展方式

---

## v1.1.0

### 新功能

- **IPC 终端提供者** - 新增 `cn.lineai.ipc.terminal` 模块，通过 AIDL 跨进程调用终端提供者服务执行 SHELL/SFTP/文件读写操作；支持 `executeShell`、`readFile`/`writeFile`、`readFileChunk`/`writeFileChunk`（大文件分块，1MB 上限）、`listDirDetailed`、`statFile`、`getFileSize` 等接口
- **AIDL 混淆保护** - `proguard-rules.pro` 新增 IPC 接口 keep 规则，保护 AIDL 生成的 `Stub` / `Proxy` / `asInterface` 入口类名，避免 release 包跨进程 Binder 调用失败

### 优化与重构

- **Repository 接口抽象** - 为 `ConversationRepository` / `ProjectRepository` / `ModelRepository` / `DiffRepository` / `ExtensionRepository` / `IpcProviderRepository` / `LearningContextRepository` / `ToolSettingsRepository` / `FileTreeRepository` / `IpcFileTreeRepository` / `SshFileTreeRepository` 抽取 `*Store` 接口；`MainDependencies` 字段类型改为接口
- **BaseRepository 公共基类** - 抽取 `database` 字段与 `value/intValue/longValue/safe` Cursor 辅助方法，消除各 Repository 重复代码
- **ExtensionRepository 拆分** - 1232 行巨型门面拆为 `AgentExtensionRepository`、`McpExtensionRepository`、`SkillRepository` 三个子 Repository；`ExtensionRepository` 降为 103 行纯门面
- **LearningContextRepository 拆分** - 793 行拆为 `MemoryRanker`（BM25 排序）、`TextTokenizer`（CJK 双字分词）、`ConversationIndexer`（对话索引）三个独立类
- **MainCoordinator 拆分** - 5046 行 → 4981 行，提取 `AttachmentPickerCoordinator` 接管附件选择器的 8 个状态字段与 7 个方法
- **MainChatView 拆分** - 1589 行 → 789 行（-50%），提取 `ScreenFactory` 注册表（582 行 `buildScreen` → 7 行委托）、`DialogManager`、`PermissionUiHelper`、`MainChatViewLayoutBuilder`、`SimpleScreenContent`、`FileActionRow`、`BackNavigation`、`DialogDimensions`、`SafPickerDelegate` 共 9 个独立类
- **巨型 Controller 拆分** - `SettingsController` 拆为 7 个细粒度接口（AiBehaviorSettingsController / InputSettingsController / OutputSettingsController / ThemeSettingsController / McpSettingsController / ArchiveController / StorageController）；`MainContract.View` 拆为 5 个细粒度接口（ChatRenderView / OverlayView / PickerView / ScreenView / PermissionView）
- **过长方法拆分** - `CodexResponsesProtocol.stream()`（122→28）、`AnthropicMessagesProtocol.stream()`（87→30）、`ToolCallWriteView.bind()`（130→20）、`MarkdownRenderer.renderBlock()`（70→9）
- **重复代码消除** - `RemoteFileTreeController` 公共基类；`BaseTool.ok/error` 上移；`FileIo.readUtf8` 提取；`ToolArgs.requireNonEmpty` 提取；`BaseToolCallView` 抽象基类；`thinkingBudget` 上移到 `AbstractHttpModelProtocol`
- **OCP 改造** - `ModelProtocolFactory`、`Migrations`、`ArchiveSecretRedactor` 改为注册表实现，新增工具通过 `BuiltInToolProvider` 注册到 `BuiltInToolProviders.defaults()` 即可
- **SshService 拆分** - 提取 `SshConnectionPool`（连接复用缓存）与 `TermuxHelper`（Termux 集成独立类）；`termux_setup.sh` 移至 assets 资源
- **ContextCompactionService 依赖注入** - 构造函数注入 `ModelClient` / `OpenAiResponsesCompactionProtocol` / `CodexResponsesProtocol` / `PromptTemplateRepository`；`MemoryExtractionService` 从 `data.repository` 包移至 `context` 包修复反向依赖
- **字符串外部化** - 所有硬编码中文字符串替换为 `strings.xml` 资源引用，英文 / 中文分别放在 `values/` 和 `values-zh/`
- **兼容性代码清理** - 移除 `getSelectedProjectLegacy`、`SshService.TERMUX_*` 委托字段、`SshService.TermuxSetupResult` 委托类、`MainActivity.Legacy branch` 注释等兼容代码

### Bug 修复

- **HttpServerTool 健壮性** - 请求头读取改为循环至结束标记（8192 字节上限）；`new Thread` 替换为 10 线程固定线程池；`Socket.setSoTimeout(30s)`；异常记录 `Log.w` 不再静默
- **AIDL 大文件** - `ITerminalProviderService` 新增 `readFileChunk` / `writeFileChunk` / `getFileSize` 三个分块方法，避免 1MB+ 事务缓冲区溢出
- **SshService TOFU** - `TrustOnFirstUseUserInfo.promptYesNo` 添加警告日志，降低中间人攻击风险
- **数据库迁移** - `LineCodeSchema` 集中 SQL 常量；`AddToolCallObservabilityColumns.apply` 通过 `PRAGMA table_info` 检查避免重复列；`onCreate` 与 `onUpgrade` 职责厘清
- **IPC 权限** - 新增 `IpcProviderScanner` / `BaseIpcProvider` 校验 target service signature fingerprint

### 安全

- **UrlPolicy SSRF 加固** - 增加内网 IP 段黑名单（192.168/10/172.16-31）、端口白名单（80/443/8080/8443）、URL 用户信息（user:pass@host）校验
- **KeepAliveService** - 移除静音音频 hack；WakeLock 与任务生命周期绑定
- **requestLegacyExternalStorage** - 移除冗余声明
- **AIDL 调用方权限** - 在 `ITerminalProviderService.aidl` 注释中声明调用方所需权限

### 测试

- 新增 `UrlPolicyTest` 覆盖 SSRF 新增场景
- 新增 `LineCodeSchemaTest`、`AddToolCallObservabilityColumnsTest`、`MigrationsTest` 覆盖迁移框架
- 新增多个 Repository / Controller / Service 单测

---

## v1.0.9

### 新功能

- **数据库迁移框架** - 新增版本化迁移系统：抽象类 `DatabaseMigration`、`Migrations.all()` 迁移注册表、`schema_migrations` 迁移记录表；`onUpgrade` 路径由"DROP 全部表 + onCreate 重建"改为"按版本顺序应用迁移"，升级不再破坏用户数据
- **tool_call 可观测字段** - `tool_calls` 表新增 `duration_ms INTEGER NOT NULL DEFAULT 0` 与 `error_message TEXT` 两列，支持记录工具耗时与失败原因
- **TODO 任务清单** - 新增 `todo_update` 工具与卡片视图，模型在每轮开始可把计划拆分为三态任务项（未开始 / 进行中 / 已完成），工具卡片会跟随消息流滚动，并在每次列表变化时追加新卡片
- **TODO 状态注入** - 当前任务清单每轮自动注入到系统提示词的 `{{TODO_STATE}}` 占位符，模型能感知待办进度
- **工具与执行页开关** - 任务清单、Agent、Agent Pipeline 在本地模式与 SSH 模式下均显示开关；Agent 与 Agent Pipeline 在 SSH 模式下可用，子 Agent 通过 `shell_execute` 在 SSH 环境内完成文件操作

### Bug 修复

- **数据库迁移崩溃** - 修复 `LineCodeDatabase.applyMigrations` 在 `onUpgrade` 路径上未先创建 `schema_migrations` 表就尝试 `INSERT` 导致的 `SQLiteException: no such table: schema_migrations` 崩溃；v1→v2 升级路径现在安全
- **.linecode 归档导入校验** - `LineCodeDatabaseArchive` 拒绝导入由更高版本生成的快照，避免数据丢失；旧版本快照导入时打印警告日志
- **归档 schemaVersion 硬编码** - `LineCodeArchiveCodec` 写出的 `schemaVersion` 从硬编码 `2` 改为读取 `LineCodeSchema.VERSION`，未来升级不再失同步

### 测试

- 新增 `LineCodeSchemaTest`、`AddToolCallObservabilityColumnsTest`、`MigrationsTest` 单测覆盖迁移框架与 schema 变更

---

## v1.0.8

### Bug 修复

- **添加自定义 MCP** - 修复 `SettingsSectionView.addRow` 中 view parent 状态泄漏导致的 `IllegalStateException`，连续添加 3 个及以上自定义请求头不再闪退；`McpExtensionEditScreenView.save()` 在未查询 tools 或 URL 变更时拒绝保存，避免保存无效 MCP

---

## v1.0.7

### Bug 修复

- **自定义 Agent UI** - 修复用户自定义 Agent 扩展（agentx_*）使用 MCP 通用组件渲染的问题，现在正确使用内置 Agent 专用组件，支持类型标签、嵌套工具调用展示、thinking 块、流式进度等完整 Agent UI 能力，并修复Agent无法使用部分TOOL和MCP的问题

---

## v1.0.6

### CI/CD 修复

- 修复 GitHub Actions Release 变量传递问题，正确显示版本名称

---

## v1.0.5

### UI 优化

- **模型选择器** - 模型快速切换按钮宽度改为自适应，根据模型名称长度自动调整

### Bug 修复

- **通知权限** - 修复 Android 13+ 保活服务通知权限检查问题
- **CI/CD** - 修复 GitHub Actions 签名配置路径问题

---

## v1.0.4

### 新功能

- **保活服务** - 新增后台保活功能，支持长时间运行任务
- **存储管理** - 添加存储统计与管理页面
- **代理流水线** - 实现 Agent Pipeline 多步骤执行能力
- **模型快速切换** - 聊天界面新增模型下拉框，支持快速切换模型
- **模型身份提示** - 支持模型身份信息注入到系统提示词

### CI/CD

- **自动发布** - 新增 GitHub Actions Release APK 自动构建与发布流程
- **版本触发** - 版本号变更时自动触发构建，发布到 GitHub Releases
- **更新日志** - 新增 `update.md` 文件，自动读取版本更新内容

### 优化与重构

- 重构模型管理 API 与工具检查逻辑
- 更新聊天消息处理流程

---

## v1.0.3

### 新功能

- **图片生成工具** - 新增 ImageGenerationTool，支持 AI 图片生成
- **图片理解工具** - 新增 ImageUnderstandingTool，支持图片内容分析
- **数据导入导出** - 支持 `.linecode` 格式的完整数据备份与恢复
- **会话级工具确认** - 实现会话级自动确认工具执行，减少重复确认
- **输入设置** - 新增输入设置页面，支持回车键行为自定义

### 安全加固

- 完成安卓合规审计相关的安全加固与优化
- URL 策略强制 HTTPS，限制明文流量
- WebView 安全配置强化

### 文档

- 新增多语言文档
- 添加许可证文件

---

## v1.0.2

### 新功能

- **提示词模板管理** - 新增提示词模板管理功能
- **消息附件支持** - 实现消息附件支持与消息操作功能
- **输入设置页面** - 新增输入设置页面和回车键行为控制

### 优化

- 多项功能优化与 bug 修复

---

## v1.0.1

### 新功能

- **聊天模式** - 添加聊天模式功能
- **扩展系统** - 新增扩展系统与技能管理能力
- **SSH Shell 工具** - 新增 SSH Shell 工具与上下文压缩功能
- **项目管理** - 完善项目管理与 UI 体验

---

## v1.0.0

### 核心功能

- **AI 聊天** - 基于 LLM 的智能对话系统
- **工具系统** - 文件读写、Shell 执行、Web 搜索等内置工具
- **工作区管理** - SAF 文件树选择与项目管理
- **MVP 架构** - 单 Activity MVP 架构设计
- **多模型协议** - 支持 OpenAI Compatible、Anthropic、Codex Responses 等协议
