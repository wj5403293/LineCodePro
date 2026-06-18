# 更新日志

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