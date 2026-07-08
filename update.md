# 更新日志

## v1.1.9

### 消息引用与多选分享

- **`QuoteController` 纯逻辑控制器** - 新增 `cn.lineai.mvp.QuoteController`，持有引用文本与预览接口 `QuotePreview`（`showQuote` / `hideQuote`），提供 `setQuote` / `clearQuote` / `hasQuote` / `composeWithQuote`；`composeWithQuote` 自动把引用拼成 `"> ..."` 块再插入用户输入并清空，发送时一次完成。`setQuote` 文本超过 80 字符自动截断为预览。
- **`ComposerView` 引用预览条** - `ComposerView` 实现 `QuotePreview`，在输入框上方构建圆角描边的引用预览条：左侧 3dp 宽 `LineTheme.ACCENT` 竖线、中间预览文本 2 行 `END` 省略、右侧 28dp `IconButtonView.CLOSE` 关闭按钮；通过 `QuoteDismissListener` 在关闭时通知 `QuoteController.clearQuote`。
- **引用文本解析与渲染** - `UserMessageView` 在 `bind` 时解析 `"> quoted\n> lines\n\nactual"` 格式，把引用部分抽到 `quoteTextView` 单独渲染（最多 3 行省略），主消息文本只保留后半段；空引用时自动隐藏引用条，避免在历史消息上误显示引用块。
- **多选分享模式** - `ChatMessageListView` 新增 `multiSelectMode` / `selectedMessageIds` / `MultiSelectListener`；`buildMultiSelectBar` 在底部构建高 8dp 浮起的操作条（选中计数 + 导出按钮 + 关闭按钮）；`MessageActionBarView` 新增 `MultiSelectButton`（`IconButtonView.CHECK_SQUARE`）入口；`enterMultiSelectMode` / `exitMultiSelectMode` 切换模式并清空选择。
- **多选消息转 `ChatMessage` 列表** - `ChatMessageListView.getSelectedMessages` 通过 `adapter.getVisibleMessages` 过滤选中 id 输出完整消息列表，供 `ShareController.showFormatPicker` 复用导出流程。
- **多选发送队列** - `ComposerView` 在流式生成期间允许输入；发送按钮三态：流式 + 有内容 → 橙色 `IconButtonView.ARROW_UP` 追加排队、流式 + 无内容 + 有队列 → 橙色停止、流式 + 无内容 + 无队列 → 红色停止；`render` 监听 `wasStreamingBefore && !streaming` 自动 `post(sendPending)`，把队列消息在 AI 停下的瞬间继续发送。
- **`MessageActionBarView` 拆分接口** - `Listener` 重构为 `ActionListener`（`onCopy` / `onQuote` / `onShare`）+ `SelectListener`（`onSelect` / `onMultiSelect`）+ `RecallListener`（`onRecall`）三组接口；新增 `IconButtonView.SHARE` / `QUOTE` / `TEXT_CURSOR` / `CHECK_SQUARE` 4 个图标常量与对应 `ic_lucide_share_2` / `ic_lucide_quote` / `ic_lucide_text_cursor` / `ic_lucide_check_square` 矢量图。
- **`MessageActionListener` 接口扩展** - 新增 `onQuoteMessage` / `onShareMessage` / `onSelectText` / `onMultiSelectToggle` 四个回调；`AssistantMessageView` / `UserMessageView` 同步绑定到 `setActionListener` + `setSelectListener` + `setRecallListener`，`streaming` 构造参数时 `setActionsVisible(false)` 隐藏引用/分享/选中/多选按钮。

### 多格式聊天记录导出

- **`ExportFormat` / `ExportResult` 抽象** - 新增 `cn.lineai.share.ExportFormat` 接口（`displayName` / `execute`）与 `cn.lineai.share.ExportResult` 不可变结果类，支持 `ACTION_SHARE_FILE`（带 `File` + `mimeType` + `Uri`）/ `ACTION_CLIPBOARD`（带 `content`）/ `ACTION_SHARE_TEXT`（带 `content`）三类动作。
- **`ExportFormatResolver` 解析器** - 默认注册 5 种格式：`ClipboardFormat` / `PlainTextFormat` / `MarkdownFormat` / `PdfFormat` / `ChatImageFormat`；`getDisplayNames` / `get` / `size` 供 `ShareController.showFormatPicker` 弹窗消费，`register` 允许扩展。
- **`PlainTextFormat` / `MarkdownFormat`** - 纯文本格式用 `【我】` / `【AI】` 标签分段、末尾追加 `FOOTER_PLAIN = "—— 来自 LineCode Pro"`；Markdown 格式用 `##` 二级标题 + `---` 分隔 + `FOOTER_MD = "*—— 来自 LineCode Pro*"`。`ChatMessages.toPlainText` / `toMarkdown` 静态方法支持单元测试直接构造。
- **`ChatImageFormat` + `ChatBitmapRenderer`** - 用 `ChatLayout.measure` 阶段按 28sp 字体 + `IMG_WIDTH=720` 测每行宽度算出总高，再用 `ChatCanvas.draw` 在 `0xFF1E1E2E` 背景上按"我"/"AI"角色绘制深浅气泡（`0xFF3B3B5C` / `0xFF2A2A3E` 圆角 24）+ LTGRAY 角色名；最终 `Bitmap.compress(PNG, 100)` 写入 `cache/share/chat_screenshot.png`。
- **`PdfFormat` + `PdfRenderer`** - 自实现 A4（595×842pt）PDF 渲染：标题 + 正文双 `TextPaint`，每行按 `measureText` 自适应宽度自动换行，遇 `\n` 强制断行，遇页底 `finishPage` + `startNewPage`；最终由 `PdfExporter.save` 写入 `cache/share/`，再通过 `ShareFileProvider.uriFor` 转 `content://` URI。
- **`ChatMessages.wrapText` 通用换行** - 用 `Paint.measureText` 在指定 `maxWidth` 内逐字符测量，超过则回退到上一换行点；保留原始 `\n`，跳过段落间尾随空白，避免切到字符中间。
- **`ShareController` 调度中心** - 构造注入 `ExportFormatResolver`；`showFormatPicker` 弹 `AlertDialog` 列出 `displayNames`，选择后调用 `format.execute` 并按 `ExportResult.action` 派发到 `ShareHelper.shareFile` / `ShareHelper.copy` / `ShareHelper.shareText`；`ClipboardFormat` 在文本 > 5000 字时 `shouldWarn` 提示"内容较长，仅部分可能被复制"。

### 分享体验优化

- **合并转发多选 + 格式选择** - `MainChatView.showMultiSelectDialog` 弹 `setMultiChoiceItems` 多选列表（每行 `[角色] 前 50 字`），预选中长按触发的 `position`；`showMergeFormatDialog` 提供 5 种导出：对话截图 / PDF / Markdown / 纯文本分享 / 复制到剪贴板，分别走 `shareAsChatImage` / `shareAsPdfWithName` / `shareAsFile` / `Intent.ACTION_SEND` / `ClipboardManager.setPrimaryClip`。
- **AI 取名文件名** - `askFileNameAndShare` 弹自定义圆角卡片布局（深色卡片 + 提示文字 + 输入框 + "AI 取名"胶囊按钮），AI 取名按钮调用 `generateSmartFileName`：从首段有效内容（去 `^[#>*\-\s]+` 头，长度 ≥ 4 截到 20 字）抽取文件名，缺省回退到 `chat_<时间戳末 4 位>`；最后用 `name.replaceAll("[/\\\\:*?\"<>|]", "_")` 清洗非法字符再拼上后缀。
- **PDF / 对话截图生成器（`MainChatView` 内部版）** - 独立的 `shareAsPdfInternal` 用 `PdfDocument` + 双 `Paint` 手动绘制 A4 页面，分页阈值 `pageHeight - margin`；`shareAsChatImage` 用 `wrapText` + `Paint.measureText` 二次测量得到真实换行结果，再画左右气泡（用户右侧 `0xFF3B5998`、AI 左侧 `0xFF2D2D44`）+ 深色背景 + 居中页脚 `—— 来自 LineCode Pro`。
- **`ShareFileProvider`（`cn.lineai.log`）** - 全新自定义 `ContentProvider`（authority `<applicationId>.fileprovider`）替代 `ShareFileProvider`（`cn.lineai.share`），提供 `getType`（按扩展名返回 `application/octet-stream` / `application/pdf` / `image/png` / `text/plain`）、`openFile`（只读 + 规范化路径防穿越 + 文件存在校验）、`query`（返回 `OpenableColumns.DISPLAY_NAME` + `SIZE` 的 `MatrixCursor`，QQ/微信读取文件前会先 `query`）、`insert` / `update` / `delete` 全部禁用。`uriFor` 用 `new Uri.Builder().scheme("content").authority(...).appendPath(name)` 拼装 URI。
- **悬浮窗"再发"按钮** - `launchShareIntent` 启动分享 chooser 后延迟 6 秒 `postDelayed(showFloatingShareButton)`，使用 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 弹出 3 键悬浮条（"再发"紫色 + "返回"灰色 + "关闭"）；无悬浮窗权限时弹 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 引导页；`onWindowFocusChanged(hasWindowFocus=true)` 时自动清理悬浮窗与 `pendingShareFile`。
- **`MainChatView` 长按多选入口** - `messageListView.setOnItemLongClickListener` 触发 `MultiSelectListener.onMultiSelectTriggered(position)`，弹 `showMultiSelectDialog(position)` 时预选该位置。
- **长按文字选中** - `MainChatView.triggerTextSelection` 用 `EditText.setKeyListener(null) + setTextIsSelectable(true) + selectAll()` 构造只读可复制的对话框，规避 `setTextIsSelectable` 在聊天项内拦截兄弟按钮触摸事件的问题（同步在 `UserMessageView` / `MarkdownTextBlockView` 注释中明示）。
- **复制逻辑统一** - `MainChatView.copyMessage` 改用 `ShareHelper.copy`，与导出格式共享同一份 `ClipboardManager.setPrimaryClip("chat", text)` 实现。

### 安全设置与 HTTP 限制旁路

- **独立 `SecuritySettingsScreenView` 页面** - 新增 `cn.lineai.ui.component.SecuritySettingsScreenView extends ScreenScaffoldView`，拆出"HTTPS 与 HTTP 限制"+"浏览器 JavaScript"两个分区；`ScreenFactories.SecuritySettingsScreenFactory` 注册 `screenId = "security"`，由 `SettingsScreenView` 改为点击跳转到该新页面。
- **`UrlPolicy` 放宽模式** - 新增 `volatile relaxedHttpEnabled` 静态开关 + `setRelaxedHttpEnabled` / `isRelaxedHttpEnabled` 访问器；`normalizeHttpOrHttpsUrl` / `normalizeBrowserUrl` / `requireHttpOrLocalCleartextUrl` / `isHttpOrLocalCleartext` 四处在原 `isAllowedCleartextHttpHost` 校验之前先判断 `relaxedHttpEnabled`，开启时任意 `http://` 与 `https://` 一律放行。
- **`network_security_config.xml` 调整** - `base-config` 由 `cleartextTrafficPermitted="false"` 改为 `"true"`（让任意 http 请求至少能进到应用层），通过注释明确"cleartext 控制交由 `cn.lineai.security.UrlPolicy` 而非此处"，避免误以为系统层关闭。
- **`OutputSettings` 持久化字段** - 新增 `allowAnyHttp` 字段（`isAllowAnyHttp` / `setAllowAnyHttp`），由 `OutputSettingsRepository` 读写；`SettingsManagementController` 透传 `onAllowAnyHttpChanged` 回调到 `OutputSettingsController`，最终调用 `UrlPolicy.setRelaxedHttpEnabled`。
- **`minSdk` 升级到 26** - `app/build.gradle.kts.defaultConfig.minSdk` 从 24 提升到 26，与 `OutputSettings` 持久化、安全设置页、`TYPE_APPLICATION_OVERLAY` 等新 API 对齐。

### 数据库备份与恢复

- **`LineCodeDatabaseBackup` 自动备份** - 新增 `cn.lineai.data.db.LineCodeDatabaseBackup`，单线程 `ExecutorService` 串行化备份任务，目录 `context.getDir("backups", MODE_PRIVATE)/linecode/`，文件 `backup-<timestamp>.json` + `latest.json`（固定文件名），最多保留 `MAX_BACKUPS = 3` 份（`pruneOldBackups` 按时间戳删旧）。`saveAsync` 失败吞异常，绝不抛到主流程。
- **`LineCodeDatabaseArchive.exportFullSnapshot`** - 与现有 `exportSnapshot`（脱敏空 messages 正文）并列新增"完整导出"方法，遍历 `TABLES` 时对 `TABLE_MESSAGE_TEXT_CHUNKS` 表调用专用 `exportMessageTextChunksTable`，其他表复用 `exportTable`，输出 JSON 携带 `"full": true` 标记；`exportSnapshot` 与 `exportFullSnapshot` 共享 `LineCodeArchiveCodec.ENTRY_DATABASE` 文件名。
- **`LineCodeDatabaseErrorHandler` 自定义损坏处理** - 实现 `DatabaseErrorHandler` 替换 `DefaultDatabaseErrorHandler`：`onCorruption` 走"复制为 `.corrupted-<timestamp>` 存档 → 写入 `.needs-restore` 标记 → 删除损坏库文件"三步，全部 `try/catch Throwable` 吞异常，避免向上抛出二次崩溃。
- **`needs-restore` 自动恢复** - `LineCodeDatabase.onOpen` 新增 `maybeRestoreFromBackup`：`LineCodeSchema.DATABASE_NAME + ".needs-restore"` 标志存在时调用 `LineCodeDatabaseBackup.restoreLatest(this)` 把 `latest.json` 数据导入当前空库（`LineCodeDatabaseArchive` 反序列化），恢复成功/失败均删除标志，保留空库兜底。`integrityOk` 用 `PRAGMA integrity_check(1)` 启动期自检；`backupAsync` 公开方法供 `ConversationRepository` 在事务 `setTransactionSuccessful` 之后异步触发一次。
- **`ConversationRepository` 事务后自动备份** - `appendMessages` 在 `db.endTransaction()` 后调用 `database.backupAsync()`，保证每次成功落库后 6 个提交窗口内都有可用备份，应对系统杀死进程导致的库损坏。

### 工具调用系统重构

- **`ToolDisplayCategory` 枚举** - 新增 `cn.lineai.tool.ToolDisplayCategory`（`READ` / `WRITE` / `DELETE` / `SHELL` / `AGENT` / `AGENT_PIPELINE` / `TODO` / `IMAGE_GENERATION` / `PHONE_CONTROL` / `HTTP` / `GENERIC`），把工具显示路由从 UI 字符串判断改为枚举。
- **`ToolDisplayResolver` 解析器** - 新增 `cn.lineai.tool.ToolDisplayResolver` 取代 `ToolCallUtils` 的字符串散落判断，方法：`getDisplayCategory(name)`（优先用 `ToolRegistry.getCachedDisplayCategory`，缺省走 `fallbackDisplayCategory` 覆盖 `file_read` / `glob` / `list_dir` / `web_search` / `web_fetch` / `image_understanding` → `READ`、`file_write` / `file_edit` → `WRITE`、`file_delete` → `DELETE`、`http_server` → `HTTP`、`shell_execute` → `SHELL`、`agent` / `agentx_` → `AGENT`、`agent_pipeline` → `AGENT_PIPELINE`、`todo_update` → `TODO`、`image_generation` → `IMAGE_GENERATION`、`phone_*` → `PHONE_CONTROL`、`mcpx_` → `GENERIC`）、`getDisplayLabel` / `getActionName` 直接从 `BaseTool` 拿。静态 `setDefault` / `getDefault` 提供全局默认实例。
- **`ToolCallCardView` 统一接口** - 新增 `cn.lineai.ui.component.toolcall.ToolCallCardView` 接口（`setToolReviewListener` / `setProjectPath` / `bind(ToolCall, ToolResult)`），让 `ToolCallBlockView` 不再依赖具体子类型（之前对 `ToolCallWriteView` / `ToolCallDeleteView` / `ToolCallReadView` / `ToolCallShellView` / `ToolCallTodoView` / `ToolCallAgentView` / `ToolCallAgentPipelineView` 一连串 `instanceof` 判断）。
- **`ToolCallViewFactory` + `ToolCallViewFactoryRegistry`** - 新增 `cn.lineai.ui.component.toolcall.ToolCallViewFactory`（`createView(Context) -> ToolCallCardView`），内置 `ReadToolCallViewFactory` / `WriteToolCallViewFactory` / `DeleteToolCallViewFactory` / `ShellToolCallViewFactory` / `TodoToolCallViewFactory` / `AgentToolCallViewFactory` / `AgentPipelineToolCallViewFactory` / `HttpToolCallViewFactory` / `ImageGenerationToolCallViewFactory` / `PhoneControlToolCallViewFactory` / `GenericToolCallViewFactory` 11 个实现。`ToolCallViewFactoryRegistry` 按 `ToolDisplayCategory` 注册工厂，`createView(Context, category)` 返回对应 `ToolCallCardView`。
- **`ToolCallBlockView` 简化渲染** - 把 100+ 行 `if/else instanceof` 链替换为 `REGISTRY.createView(ctx, ToolCallUtils.getDisplayCategory(name))` + `childView.setToolReviewListener` + `childView.setProjectPath` + `addView(childView)` + `childView.bind` 5 行；`setToolReviewListener` / `setProjectPath` 也改用统一的 `ToolCallCardView` 接口。
- **`NestedToolCallParser` 嵌套工具调用解析** - 新增 `cn.lineai.tool.NestedToolCallParser.parse(JSONArray) -> List<NestedCall>`，把 Agent / Pipeline progress 数组中 `{"id", "name", "arguments", "result": {...}}` 解析为 `NestedCall(call, result)`，`ToolCallAgentView` / `ToolCallAgentPipelineView` 不再内嵌 JSON 解析逻辑。

### UI / 数据层解耦

- **UI 模型类独立包** - 新增 `cn.lineai.model` 下 6 个 UI 层不可变数据类：`ConversationUiModel` / `DiffUiModel` / `ExtensionItemUiModel` / `ExtensionKindUiModel` / `KeepAliveSettings` / `StorageStatsUiModel`，把 `Conversation` / `DiffRecord` / `ExtensionItem` / `ExtensionKind` / `KeepAliveSettings`（数据层原定义）等实体映射为 UI 专用模型，View 层只接收 UI 模型。
- **`ExtensionActionCallback` 回调接口** - 新增 `cn.lineai.mvp.ExtensionActionCallback`（`onSave` / `onDelete` / `onTest` 等），把扩展详情页的操作从 View 直接调用 Repository 改为回调到 Coordinator，View 不再依赖 Repository。
- **`DiffLoader` 接口** - 新增 `cn.lineai.ui.component.toolcall.DiffLoader`（`loadDiff(diffId) -> DiffRecord`），由 Controller/Assembler 实现，注入到 `ToolCallWriteView`，View 层不再直接持有 `DiffRepository`。
- **`CardViewHelper` 圆角卡片工具** - 新增 `cn.lineai.ui.component.CardViewHelper`，统一封装"圆角背景 + padding + 标题 + 描述"组合，被多个 Screen 复用，删除 `ExtensionDetailScreenView` 等页面内重复的卡片段。
- **`StringUtils` 字符串工具** - 新增 `cn.lineai.util.StringUtils`，提供空值 / 截断 / 路径安全 / 字符计数等纯函数，被多个 Repository 复用。
- **`SkillFrontmatterParser` + `SkillFileManager` + `SkillPromptBuilder`** - 把 `SkillRepository` 中"读 frontmatter + 文件管理 + prompt 拼装"三段混在一起的代码拆为 3 个独立类：`SkillFrontmatterParser` 解析 YAML frontmatter（`SkillPromptBuilder` 装配系统提示词、`SkillFileManager` 负责文件 IO + 缓存）。`SkillRepository` 内部行数从 665 减少到 ~50，只剩数据库 CRUD。
- **`ModelRepository` SQLite 迁移** - 新增 `cn.lineai.data.repository.ModelRepository implements ModelStore`：`ensureModelConfigColumns` 自检并补齐 `model_configs` 表列；`migrateLegacyPreferencesIfNeeded` 检测 `linecode_models` SharedPreferences 中的 `sqlite_migrated` 标记，未迁移时把 `KEY_MODELS` JSON 数组和 `KEY_SELECTED_ID` 一次性导入 SQLite；之后 `getModels` / `getModel` / `save` / `deleteModels` / `setSelectedModelId` / `getSelectedModel` / `clearAll` 全部走 SQLite（`ORDER BY selected DESC, updated_at DESC`）。
- **`ToolSettingsScreenView` 默认模式硬编码修复** - 之前默认执行模式硬编码为 `null`，改为从 `ToolSettingsRepository.getDefaultExecutionMode()` 读取，避免初次安装后界面显示空白。
- **`ComposerView` 等视图成员变量可修改** - `ComposerView` / `DrawerView` / `KeepAliveSettingsScreenView` 等视图的成员变量从 `final` 改为非 `final`，便于子类扩展与运行时切换。

### Agent 能力增强

- **`ModelProtocol` 默认方法** - `ModelProtocol` 接口新增 `supportsNativeTools()` / `supportsContextCompaction()` / `supportsImageGeneration()` / `supportsImageUnderstanding()` 4 个 `default` 方法返回 `true`；`AnthropicMessagesProtocol` / `CodexResponsesProtocol` / `LocalGgufProtocol` / `OpenAiCompatibleProtocol` 按需覆写其中部分为 `false`，调用方代码从 `if (protocol instanceof ...)` 改为 `if (!protocol.supportsXxx())`。
- **`ReasoningRequestStrategy` 推理策略** - 新增 `cn.lineai.ai.protocol.ReasoningRequestStrategy` 接口（`matches(baseUrl, modelId)` + `apply(JSONObject body, ReasoningRequestContext context)`）与 `ReasoningStrategyRegistry`（按顺序注册、按 `matches` 找到第一个命中的策略）。`OpenAiCompatibleProtocol` 把原来硬编码的 `if (url.contains("deepseek"))` 等分支改为遍历 registry；新增 `DefaultReasoningStrategy`（`matches` 始终返回 `true` 兜底）/ `DeepseekReasoningStrategy` / `DashscopeReasoningStrategy` / `MoonshotReasoningStrategy` / `MinimaxReasoningStrategy` 5 个实现。
- **多厂商推理支持** - 实现了 `DeepseekReasoningStrategy`（`baseUrl` 命中 `deepseek` 时在请求体加 `reasoning_effort` 字段）、`DashscopeReasoningStrategy`（`baseUrl` 命中 `dashscope.aliyuncs` 时加 `enable_thinking` + `thinking_budget`）、`MoonshotReasoningStrategy`（`baseUrl` 命中 `moonshot` 时加 `reasoning_content` 字段）等。`ReasoningRequestContext` 携带 `enabled` / `effort` / `preserveReasoning` / `baseUrl` / `modelId` / `thinkingBudget` 全部上下文，避免在每个策略里重新拼装。
- **Web 搜索多提供商** - 新增 `cn.lineai.tool.builtin.WebSearchProvider` 接口（`providerId` + `buildRequest` + `normalizeResults`）+ `WebSearchProviderRegistry`（注册 + 按 `providerId` 取出）；实现 `BingSearchProvider` / `BraveSearchProvider` / `SerpApiSearchProvider` / `TavilySearchProvider` / `DefaultSearchProvider` / `QueryCountSearchProvider` 6 个。`WebSearchService` 原本 160+ 行 `if (provider.equals(...))` 重构为注册表查找 + 通用 `HttpRequest` 调度；`SearchRequest` 与 `SearchResultItem` 数据类明确请求/响应契约。
- **扩展管理策略模式** - 新增 `cn.lineai.mvp.ExtensionKindDescriptor` 抽象接口 + `ExtensionKindRegistry` 注册表 + `AgentKindDescriptor` / `McpKindDescriptor` / `LinecodeKindDescriptor` / `SkillsKindDescriptor` 4 个实现。`ExtensionManagementController` / `ExtensionDetailScreenView` 把 `if (kind == AGENT) ... else if (kind == MCP) ...` 链条改为遍历注册表 + `descriptor.canHandle(item)` 分发，新增扩展类型只需注册 descriptor，不再动业务代码。
- **Agent 角色提示词模板** - `assets/prompts/` 新增 5 个 Agent 模板：`agent-role-coding-local.txt` / `agent-role-coding-remote.txt`（编程型 Agent 区分本地/远端 shell 模式）、`agent-role-explore-local.txt` / `agent-role-explore-remote.txt`（探索型 Agent 同样区分）、`agent-system-prompt-template.txt`（系统提示词主模板，含 `{{ROLE_PROMPT}}` / `{{TASK_DESCRIPTION}}` / `{{WORKSPACE_CONTEXT}}` / `{{SCOPE_CONTEXT}}` / `{{EXTENSIONS_CONTEXT}}` / `{{TOOLS_CONTEXT}}` 占位符）。`PromptTemplateRepository` 新增 `getTemplateText(id)` 读取方法。
- **流式渲染独立控制器** - 新增 `cn.lineai.mvp.StreamingRenderController` 把流式渲染的缓冲 / 调度 / 状态机从 `GenerationFlowController` 拆出来，便于针对 streaming 路径做单元测试与扩展。

### 工具系统增强

- **`BaseTool.NAME` 常量** - 25 个内置工具（`FileReadTool` / `FileWriteTool` / `FileEditTool` / `FileDeleteTool` / `GlobTool` / `ListDirectoryTool` / `HttpServerTool` / `ImageGenerationTool` / `ImageUnderstandingTool` / `ImageResponseParser` / `PhoneClickTool` / `PhoneClickViewTool` / `PhoneLongPressTool` / `PhoneSwipeTool` / `PhoneGlobalActionTool` / `PhoneScreenshotTool` / `PhoneViewHierarchyTool` / `ShellExecuteTool` / `WebFetchTool` / `WebSearchTool` / `TodoUpdateTool` / `AgentTool` / `AgentPipelineTool` / `CustomAgentExtensionTool` / `CustomMcpHttpTool`）全部添加 `public static final String NAME` 常量；`ToolCategory.isReadType` / `isWriteType` / `isDeleteType` / `isHttpType` / `isShellType` / `isAgentType` / `isAgentPipelineType` / `isTodoType` / `isImageGenerationType` / `isImageUnderstandingType` / `isPhoneControlTool` 全部从硬编码字符串改为引用常量。所有工具 `getName()` 返回 `NAME`，避免工具重命名时漏改一处。
- **`BaseTool` 扩展方法** - `requiresConfirmation` 标记 `@Deprecated`，新增 `needsConfirmation()`（`default false`）和 `isAllowedInReadonlyMode()`（`default false`）分别表达"是否需要用户确认"和"在只读模式下是否允许执行"；新增 `promptSupplement(String executionMode, boolean isSsh)`（`default null`）允许工具按执行模式与 SSH 标识补充提示文本，例如 `ImageUnderstandingTool` 在 SSH 模式下提示"通过 SFTP 读取 SSH 工作区图片"。
- **`ToolResult` 快捷工厂** - 新增 `static success(output)` / `error(error)` / `withReview(output, toolCallId, toolName, diffId, reviewState, reviewMessage)` 三个工厂方法，4 个旧构造器标记 `@Deprecated`，调用方从 `new ToolResult("", "", msg, true)` 简化为 `ToolResult.error(msg)`。
- **`ToolContext` 依赖注入扩展** - 新增 `ToolSettingsStore` / `ModelStore` / `SshFileTreeStore` / `ModelProtocolFactory` / `ModelClient` / `PromptTemplateRepository` 6 个可选依赖字段 + 4 个新构造器重载；`ToolExecutor` 在执行工具前调用 `needsInjection` 检查并 `injectDependencies` 把 Executor 持有的依赖注入到 `ToolContext`，工具不再需要在自己的 `execute` 里 `new ModelRepository(context)`。
- **`ToolExecutor` 8 参数构造器** - 新增 `ToolExecutor(registry, settingsRepository, diffRepository, modelRepository, sshFileTreeRepository, modelProtocolFactory, modelClient, promptTemplateRepository)` 全量依赖构造器；`MainDependencies` 改为调用 8 参数版，把整套依赖一次注入。
- **`ToolRegistry.getCachedDisplayCategory`** - 新增 `getCachedDisplayCategory(name)` 内部缓存 `Map<String, ToolDisplayCategory>`，避免每次 `getDisplayCategory` 都重新计算；启动期一次性 `preload` 全部内置工具。
- **图片生成工具依赖注入修复** - `ImageGenerationTool` 之前在自己构造时 `new` 一堆 repository，`dc6d82e` 改为从 `ToolContext` 取 `toolSettingsStore` / `modelRepository` / `modelProtocolFactory` / `modelClient` / `sshFileTreeRepository`，与 `ImageUnderstandingTool` 对齐；`ImageUnderstandingTool` 同步从 `Context` 构造改为 `ToolContext` 注入，并新增 `promptSupplement` 在 SSH 模式返回 "SFTP 读取图片" 提示。
- **`WebSearchTool` 环境提示** - `WebSearchTool.getDescription` 末尾追加"环境补充：搜索结果中给出的 URL 必须在 `web_fetch` 之前先用 `UrlPolicy` 校验"提示，提醒模型筛选可用 URL。
- **部分工具 readonly / confirmation 标识** - `FileReadTool` / `ListDirectoryTool` 标记 `isAllowedInReadonlyMode = true`（只读模式下仍允许执行）；`FileWriteTool` / `FileEditTool` / `FileDeleteTool` / `ShellExecuteTool` / `WebSearchTool` 等写操作工具标记 `needsConfirmation` 走原 `requiresConfirmation` 路径；`ImageGenerationTool` / `ImageUnderstandingTool` 标记 `needsConfirmation` 由用户在工具设置里决定。
- **`ImageGenerationTool` 协议工厂修复** - 之前 `ImageGenerationTool` 缺少 `modelProtocolFactory` 注入导致 `create(model.getProtocolType())` NPE，`dc6d82e` 注入后由 `ImageResponseParser` 统一解析图片 URL/Base64。
- **`HostBase` 抽象宿主** - 新增 `cn.lineai.mvp.HostBase implements CoordinatorHost`，把 `MainCoordinator` 公共方法（`basename` / `parentPath` / `projectPath` / `projectLabel` / `isSshExecutionMode` / `isTerminalProviderExecutionMode` / `showNotice` / `isViewAttached` / `viewHideOverlays` / `viewShowScreen` / `viewShowChatScreen` / `refreshVisibleScreen` / `returnToScreen`）抽到 `HostBase`，各 Controller 改为 `extends HostBase` 减少样板代码。
- **`ErrorLogController` / `PhoneControlController`** - 新增两个轻量 Controller，把 `ErrorLogRepository` / `PhoneControlRepository` 暴露给 `MainCoordinator`；`ErrorLogController` 提供 `list()` / `clear()`；`PhoneControlController` 提供 `isAccessibilityEnabled` / `isDisclaimerAccepted` / `isPermissionEnabled` / `setPermissionEnabled` / `setDisclaimerAccepted`。
- **`ToolCallTextParser` 健壮性** - 内部实现对工具调用文本解析失败时返回空数组而非抛异常；`ToolPromptRenderer` 路径从 `data/repository` 移到 `ai/prompt`，与 `SystemPromptProvider` 同包。

### 视图组件优化

- **`ChatMessageListView` 缓存复用重构** - 移除 `canReturnCachedView` 与 `bindCachedView` 两个内部方法，引入通用 `obtain(Class<T> type, String key, T created)` 工具：先查 `rowCache.get(key)`，类型匹配且 `getParent() == null` 则复用，否则用 `created` 替换并 `putCache`；`putCache` 顺手清理旧 view 的 `tag`。模型切换通知的缓存命中后不再调用 `bind`，直接复用 `convertView`。
- **`UserMessageView` / `AssistantMessageView` 复用判断** - `getView` 中先判断 `convertView` 是否同类型实例（`UserMessageView` / `AssistantMessageView`），同类型直接 `setXxxListener` + `bind` 复用；不同类型才走 `obtain` 路径，避免每条消息都 `new` 视图实例触发 `setToolReviewListener` / `setProjectPath` 副作用。
- **`MainChatView` 长按多选入口** - `listView.setOnItemLongClickListener` 把 `position` 透传给 `MultiSelectListener`，触发 `showMultiSelectDialog`。
- **`MarkdownTextBlockView` 触摸事件修复** - 删除原 `setTextIsSelectable(true)`，通过注释明确"会偷走兄弟按钮的触摸事件，改由 `TextSelectionDialog` 弹可选择 EditText"。
- **`ComposerView` 多选发送队列渲染** - `render(ChatUiState state)` 中检测 `wasStreamingBefore && !streaming && !pendingQueue.isEmpty()` 时 `post(sendPending)` 自动续发；`input.setEnabled(true)` 允许在流式期间继续输入；`attachButton` 仍按 `streaming` 灰化。
- **`UserMessageView` 引用块** - 新增 `quoteBlockView`（圆角深色 + 左侧 3dp `LineTheme.ACCENT` 竖线 + 最多 3 行省略的斜体引用文本）作为 `contentText` 之上的子视图，宽度限制与主气泡一致；空引用时 `setVisibility(GONE)` 隐藏。
- **`ChatMessageListView` 多选模式滚动条控制** - `updateScrollToBottomVisibility` 在 `multiSelectMode` 开启时强制隐藏"滚动到底部"按钮，避免遮挡底部操作条；`programmaticScroll` 标志在 `smoothScrollBy` 期间抑制"用户主动滚动"判断，避免流式跟随后误关闭 `followTailEnabled`。
- **`IconButtonView` 新增 4 个图标** - `SHARE` (80) / `QUOTE` (81) / `TEXT_CURSOR` (82) / `CHECK_SQUARE` (83)，对应 `ic_lucide_share_2` / `ic_lucide_quote` / `ic_lucide_text_cursor` / `ic_lucide_check_square` 4 个矢量图。
- **`MainChatView` 合并转发/AI 取名 UI** - 自定义圆角卡片布局：深色 `0xFF2A2A3E` 圆角 18 卡片 + "后缀自动添加: .pdf" 灰色提示 + `EditText` 圆角描边输入框（自动 selectAll + `inputType=text`）+ 紫色 `0xFF5B4FCF` 圆角 14 "AI 取名" 胶囊按钮。

### 许可证与构建优化

- **许可证资源打包方式重构** - 移除 `app/src/main/resources/META-INF/LICENSE`（674 行）与 `app/build.gradle.kts` 中 `META-INF/resources/META-INF/LICENSE` 的 `assets` 注入配置；新增 `app/build.gradle.kts` 自定义 `generateLicenseCopy` 任务，把仓库根 `LICENSE` 文件自动复制到 `app/build/generated/assets/license/LICENSE`，由 `processReleaseResources` / `processDebugResources` 作为额外 `inputs.file` 包含；统一许可证加载来源到根目录文件。
- **移除冗余 COPYING 文件** - 删除仓库根 `COPYING` 副本；同步更新中英文 `README.md` / `README_CN.md` 中对 `COPYING` 的引用，统一指向根目录 `LICENSE` 作为唯一许可证来源。

### 国际化与多语言

- **新增 11 条字符串** - `values/strings.xml` 与 `values-zh/strings.xml` 同步添加：`message_action_quote_desc`（引用消息）/ `message_action_share_desc`（分享消息）/ `message_action_select_desc`（选中文字）/ `message_action_multi_select_desc`（多选）/ `dialog_export_format_title`（导出格式）/ `dialog_select_text_title`（长按选中文字）/ `quote_preview_label`（引用）/ `export_button_label`（导出）/ `toast_clipboard_large`（内容较长，仅部分可能被复制）。
- **安全设置页文案** - `settings_row_security_allow_any_http_title` / `settings_row_security_allow_any_http_desc` / `screen_security_title` / `screen_security_section_http` / `screen_security_section_browser` 5 条新字符串。

### 兼容性

- **数据库迁移** - `LineCodeSchema` 表结构未变，仅在 `LineCodeDatabase.onOpen` 启动期增加 `integrity_check(1)` 自检与 `maybeRestoreFromBackup` 恢复，旧数据库直接打开不会丢失数据。
- **旧 `ToolSettingsRepository` API 兼容** - 旧调用方通过 `@Deprecated` 构造器仍可工作，新代码改用 `MainDependencies` 注入的 `ToolSettingsStore` 接口。
- **`ModelConfig` 旧构造器兼容** - 3 个旧 `@Deprecated` 构造器（`ModelConfig(id, name, protocolType, ..., modelId)` 等）保留可用；新代码使用 `ModelConfig.builder(...).toolCallLimit(...).compressionModelEnabled(...).build()` 链式构造。

### 版本

- 版本号升级到 `1.1.9`
- `versionCode` 升级到 `22`
- `minSdk` 升级到 `26`

### 测试

- 新增 `QuoteControllerTest`（94 行）覆盖 `setQuoteStoresText` / `composeWithQuotePrefixesQuote` / `composeWithQuoteClearsAfterSend` / `composeWithoutQuoteReturnsInputUnchanged` / `clearQuoteRemovesQuote` / `setQuoteNotifiesPreview` / `clearQuoteHidesPreview` / `longQuoteIsTruncatedInPreview` 等用例
- 新增 `ChatMessagesTest`（41 行）覆盖 `toPlainText` / `toMarkdown` / `wrapText` 三种格式的转换与换行
- 新增 `ExportFormatResolverTest`（49 行）覆盖默认注册顺序 / `register` 扩展 / `get` / `size` / `getDisplayNames` 行为
- 新增 `ExportResultTest`（32 行）覆盖 `forFile` / `forClipboard` / `forShareText` 三种 action 的字段填充
- 扩展 `AgentExecutionControllerTest`（+52 行）覆盖 `dc6d82e` 引入的 `isAllowedInReadonlyMode` / `needsConfirmation` / `promptSupplement(executionMode, isSsh)` 三组新方法
- `SlashCommandCatalogTest` 之前用例保持通过
- 已验证 `./gradlew :app:testDebugUnitTest` / `./gradlew :app:lintDebug` / `./gradlew :app:assembleDebug` / `./gradlew :app:assembleDebugUserCert`

---

## v1.1.8

### 聊天输入框斜杠命令

- **斜杠命令系统** - 聊天 composer 中输入 `/` 触发命令弹窗，按键入内容实时过滤候选项；支持主命令（`/chat`、`/plan`、`/agent`、`/control`、`/model`）、模型 id 补全与思考等级选择，点击候选项自动回填为完整命令文本
- **`SlashCommandCatalog` 解析与过滤** - 新增 `cn.lineai.ui.util.SlashCommandCatalog` 纯逻辑工具类，提供 `MAIN_COMMANDS` / `REASONING_LEVELS` 常量、`filterMain` / `filterModelIds` / `filterReasoningLevels` 前缀过滤、`parse` 整段文本解析为 `Kind.MODE` 或 `Kind.MODEL`；不依赖 Android 视图，便于 JUnit 覆盖
- **`SlashCommandPopup` 组件** - 独立实现 composer 上方展示的命令弹窗：圆角描边、行高紧凑化（标题 22dp）、`/` 前缀加 ACCENT 色 + BOLD 加粗、当前态圆点指示；`show` 在 `title+rows` 数量未变时跳过重建，`showAtAnchor` 自动对齐到 anchor 容器并向上 8dp 弹出
- **`/` 标签着色** - `SlashCommandPopup.formatLabel` 把以 `/` 开头的 token 第一个空白前的部分加 `ForegroundColorSpan(ACCENT)` + `StyleSpan(BOLD)`，弹窗中 `/chat`、`/model` 等命令一眼可辨
- **点击回调主线程投递** - 行 `OnClickListener` 改为 `new Handler(Looper.getMainLooper()).post(onClick)`，避免 popup `dismiss()` 与点击动作同帧触发的竞态
- **发送时拦截斜杠命令** - `ComposerView.handleSendClick` 在 send 时调用 `SlashCommandCatalog.parse`；命中 `MODE` 时走 `Listener.onModeChanged`，命中 `MODEL` 时同时调用 `onModelQuickSwitch` 与可选的 `onAiReasoningEffortChanged`；未命中时按原逻辑走 `onSend`
- **多语言文案** - `values/strings.xml` 与 `values-zh/strings.xml` 新增 `slash_command_main_title` / `slash_command_model_title` / `slash_command_reasoning_title` 以及 4 种模式、5 档思考等级的副标题，弹窗描述根据语言自动切换
- **流式生成时禁用** - `setStreaming` 在生成期间关闭并清理 `slashPopup`，避免与现有 model / mode 弹窗互相干扰

### Agent 工具审核

- **子 Agent 工具审核流程** - `AgentExecutionController.executeAgentToolCallWithReview` 在 `awaitReview` 之前调用新增的 `host.requestAgentToolReview(displayToolCallId, call, pending)`，主流程感知到 `pending` 审核态后立即持久化并 `render`；`finally` 块保证在执行完成、被拒绝或被中断时调用 `host.clearAgentToolReview` 清理
- **拒绝结果落库** - 拒绝分支不再直接返回新建的 `ToolResult`，而是同步调用 `progress.putToolResult` + `host.addOrReplaceToolResult(progress.snapshotResult())`，让 UI 在拒绝时立刻看到 `rejected` 状态卡片
- **`AgentProgressSession.snapshotResult` 透传状态** - 由 `new ToolResult(..., error)` 调整为 `new ToolResult(..., error, "", status, "")`，把 `pending` / `running` / `done` / `error` 等内部状态写进外层 `ToolResult.reviewState`
- **`GenerationFlowController` 跟踪待审核请求** - 新增 `pendingAgentToolRequests` 映射与 `requestAgentToolReview` / `clearAgentToolReview` / `isPendingAgentToolReview` / `acceptAgentToolReview` 入口；`cancelActiveGeneration` 同步清空；`MainCoordinatorDelegates.handleToolReview` 在收到审核回调时优先尝试 agent 路径
- **UI 审核态判断扩展** - `ToolCallAgentView` 读取 `result.getReviewState()`，与 progress status 任一为 `pending` 时都视为待审核；`pending` 卡片稳定显示，避免审核期间闪烁

### 工具调用与会话自动确认

- **会话级自动确认** - `AgentExecutionController.executeAgentToolCall` 在工具需要确认时先调用 `toolReviewAwaiter.isAutoConfirmed(call)`；命中则直接走 `toolExecutor.executeConfirmed`，跳过 `awaitReview` 阻塞与 UI 审核面板
- **`ToolReviewAwaiter` 接口扩展** - 新增 `boolean isAutoConfirmed(ToolCall call)` 默认返回 `false` 的方法，由主流程在初始化 `setToolReviewAwaiter` 时实现并与 `MainCoordinator.sessionAutoConfirmedTools` 联动
- **工具调用 UI 视图复用** - `ToolCallBlockView` 对 `ToolCallAgentView` / `ToolCallAgentPipelineView` / `ToolCallAgentView`（自定义 Agent）三类子视图在 `bind` 前先 `getChildAt(0)` 复用已有实例，避免每次刷新都重新 `new` 并触发 `setToolReviewListener` / `setProjectPath` 副作用
- **Todo 行高自适应** - `ToolCallTodoView` 将固定 `rowHeight` 改为 `minRowHeight`，通过 `setMinimumHeight` 实现最小 28dp + WRAP_CONTENT，长任务描述能自然撑高而短条目仍维持原视觉

### Agent 循环预算与远端模式

- **总时长预算替代最大轮次** - 移除 `AGENT_MAX_TURNS = 8` 硬轮次上限，新增 `AGENT_TOTAL_BUDGET_MS = 30 分钟`；`runAgentLoop` 把 `for (turn = 0; turn < 8)` 改为 `while (true)`，由取消 / 工具次数限制 / 时间预算三种条件共同决定退出，避免深度任务被 8 轮卡死
- **工具调用次数共享预算** - `GenerationFlowController.toolContext` 透传 `usedToolCallCount` 到 `AgentRunner`，`runAgentTool` / `runAgentPipelineTool` 在调用入口构造 `int[] toolCallBudget`；Agent / Pipeline / Pipeline 内部子 Agent 每次工具调用都 `toolCallBudget[0]++`，跨调用共享模型配置的 `toolCallLimit`
- **超过预算安全退出** - `runAgentLoop` 在每轮入口与执行工具前检查 `toolCallBudget[0] >= toolCallLimit`，命中返回 `AGENT_TOOL_LIMIT_MESSAGE`（"Agent 已达到主流程总工具调用次数上限。"）并把 progress 同步到 `error` 状态；Pipeline 收到含该消息的子结果立即中断后续层并写入错误摘要
- **时间预算检查** - 同样在每轮入口检查 `System.currentTimeMillis() - startedAt > AGENT_TOTAL_BUDGET_MS`，命中返回带"最后输出"的超时消息；Pipeline 整体 `pipelineBudgetMs = agents.size() * AGENT_TOTAL_BUDGET_MS` 在每个 level / agent 进入前双重判断
- **取消与初始检查** - `runAgentTool` / `runAgentPipelineTool` 入口增加 `cancellationToken.isCancelled()` 提前返回；Pipeline 在外层循环与每个 agent 进入前各检查一次取消与时间预算
- **Pipeline 进度汇总落库** - `PipelineProgressSession` 新增 `setStatus` / `setFinalSummary` / `getFinalSummary` / `payload` 公共方法与 `ProgressPublisher` 接口；`runAgentPipelineTool` 在最终/异常分支统一调用 `pipelineProgress.setFinalSummary` + `setStatus` + `publish(true)` + `addOrReplaceToolResult`，让 UI 在流水线结束时能直接拿到 summary
- **远端 Shell 模式提示词** - `agentRolePrompt` / `agentWorkspacePrompt` / `agentScopePrompt` 新增 `boolean remoteMode` 重载；SSH 或终端提供者模式下返回"远程 Shell"专用提示词：明确 `file_read / file_write / file_edit / glob / list_dir` 不一定可用、必须通过 `shell_execute` 完成探查与写入、禁止调用写操作类工具；探索 Agent 进一步禁止任何有副作用的命令

### 模型 HTTP 连接

- **读取超时延长到 10 分钟** - `AbstractHttpModelProtocol.openConnection` 把 `setReadTimeout(120000)` 调整为 `setReadTimeout(600000)`，匹配 Agent 30 分钟总预算下的长流式响应与 reasoning 内容；避免大输出 / 慢模型在 2 分钟后被强行断开
- **Connection: close 头** - 显式设置 `Connection: close` 请求头，避免长 keep-alive 在 NAT / 代理环境下被中间设备单边切断导致首包延迟或半包

### Agent 流水线视图

- **参数解析失败专用卡片** - `ToolCallAgentPipelineView` 在 `bind` 时调用 `parseInputError` 检查模型原始参数；JSON 解析失败或输入为空时直接渲染 `bindParseError` 视图（标题、红色 `CIRCLE_X` 状态、错误描述），不再误显示空 agents 列表
- **最终 summary 单独展示** - `progress.optString("summary")` 在 agents 为空时通过分割线 + `MarkdownView` 单独渲染，便于查看流水线终止原因

### 斜杠命令视觉

- **标题紧凑化** - `titleParams` 高度由 32dp 改为 22dp，margin 从底部 `XS` 调整为顶部 1dp，弹窗整体更紧凑
- **行间距** - 容器 vertical padding 由 0 改为 `LineTheme.SM`，描述行顶部 margin 1dp，行间层次更清晰

### 版本

- 版本号升级到 `1.1.8`
- `versionCode` 升级到 `21`

### 测试

- 新增 `SlashCommandCatalogTest`（134 行）覆盖 `filterMain` 空查询返回全部 / 大小写不敏感前缀匹配 / 未知前缀返回空、`parse` 模式命令解析、`/model` 必填 id 与可选思考等级、无效思考等级保留 id、未知命令与 `/model` 缺 id 返回 null
- 扩展 `AgentExecutionControllerTest`：
  - `subAgentToolReviewNotifiesMainFlowBeforeAwaiting` 验证子 Agent 工具审核在 await 之前就触发主流程的 `requestAgentToolReview` 并在结束时 `clearAgentToolReview`
  - `subAgentToolReviewClearsMainFlowAfterReject` 验证拒绝路径返回 `rejected` 状态、错误内容包含"用户拒绝执行"并清理主流程审核
  - `snapshotResultPropagatesStatusToOuterReviewState` 验证 progress 状态变化能正确透传到外层 `ToolResult.reviewState`
  - `shellExecuteAutoConfirmedSkipsReviewAndExecutesConfirmed` 验证会话级自动确认时不再走 `awaitReview` 且不触发主流程审核
  - `agentRolePromptSwitchesToShellInRemoteMode` 验证 `agentRolePrompt(type, false)` 提及 file 工具但不出现"远程 Shell"，`agentRolePrompt(type, true)` 出现"远程 Shell"、"shell_execute" 与"不一定可用"
  - `agentRolePromptExploreInRemoteModeRecommendsShell` 验证探索型 Agent 在远端模式下提示词同时出现"远程 Shell"与 `shell_execute`
- 已有 `shellExecuteWaitsForConfirmationBeforeRunningInsideAgent` 改名为 `shellExecuteWaitsForReviewAndResumesOnAccept` 并补充 `FakeHost` 默认实现 `requestAgentToolReview` / `clearAgentToolReview` / `isSshExecutionMode` / `isTerminalProviderExecutionMode` 桩方法
- 已验证 `./gradlew :app:testDebugUnitTest`

---

## v1.1.7

### 数据存储与导出

- **消息文本分块存储** - 新增 `message_text_chunks` 侧表，把 `messages.content` / `reasoning_content` / `raw_json` 等大文本按 64KB 切片存放，规避 SQLite `CursorWindow` 单行 2MB 限制；模型输出或大文件 diff 写入不再因为单行过大而触发 `SQLiteBlobTooBigException`
- **数据库版本升级到 4** - `LineCodeSchema.VERSION` 升到 4，并新增 `AddMessageTextChunksTable` 迁移，在 `onUpgrade` 时按顺序建表与索引；v3 数据库升级时数据不会丢失
- **Repository 适配分块读写** - `ConversationRepository`、`LearningContextRepository`、`StorageStatsRepository` 改为通过 `MessageTextChunkStore` 读写大文本；`ConversationIndexer` 写入索引时只保留 4000 字符的摘要，`raw_json` 不再落到 `conversation_index`，避免索引行过大
- **学习上下文兼容** - `LearningContextRepository` 在读取历史消息和索引时通过 `substr(..., 320)` 截取前缀，并通过 `message_text_chunks` 兜底读取分块文本；`storage_stats` 中的聊天与 diff 缓存统计改为基于 `MessageTextChunkStore.totalLength` 聚合
- **.linecode 归档适配分块** - `LineCodeDatabaseArchive` 导出时把 `messages` 表的大文本列置空并在 `message_text_chunks` 中按 64KB 切片存放；旧版归档中可能仍包含历史分块文本的兼容逻辑已补齐，新增 `nullCell` / `integerCell` / `stringCell` 工具方法

### 提示词与 Tool Call

- **Tool Call 配对修复** - `ModelPromptController.completeToolCallPairsForRequest` 会在构造模型请求前把缺失的 `tool` 消息补齐，避免 assistant `tool_calls` 没有对应结果导致 OpenAI / Anthropic / Responses 协议拒绝请求；同时把游离的 `tool` 结果移动到对应 assistant 之后，过期或孤儿的 `tool` 消息会被丢弃
- **中断补齐默认文案** - `ModelPromptController.Host` 新增 `interruptedGenerationMessage` 默认方法，中文返回 `上次生成已中断。`、英文返回 `Previous generation was interrupted.`；`MainCoordinator` 通过 `R.string.message_generation_interrupted` 覆盖默认文案，保证多语言一致
- **补齐逻辑覆盖 stop / kill 场景** - 用户主动停止生成、应用被强杀、进程重启后，模型请求都会带上补齐后的 `tool` 配对，对外模型不会因缺结果而报错或要求重复工具调用

### UI 与导航

- **屏幕切换无动画刷新** - `MainChatView.showScreen` 新增 `animate` 重载；`invalidateScreen` 重建当前页时不再播放转场动画，避免数据刷新时的闪烁；`ScreenView` 新增 `evictScreen` 默认方法，调用链路更清晰
- **前进 / 返回方向控制** - `ScreenNavigationController.Host.showScreen(id, forward, animate)` 默认实现转发到 `showScreen(id, forward)`，新接口允许在 `backFrom` 路径上保留方向感知的反向动画
- **抽屉方向修复** - `DrawerView` 从顶部面板调整为左侧侧边栏样式：`Gravity.START` + `DrawerWidth 360dp` + `translationX` 动画；与 v1.1.6 的描述一致，避免小屏上文件树与会话列表被压缩
- **`refreshVisibleScreen` 不再误触发动画** - `MainCoordinator` 中两处 `view.invalidateScreen` 调用改为 `view.evictScreen` + `showScreen(id, true, false)`，重建当前页时直接落到终态，行为符合直觉

### 版本

- 版本号升级到 `1.1.7`
- `versionCode` 升级到 `20`

### 测试

- 新增 `ModelPromptControllerTest` 覆盖：缺失 `tool` 结果补齐、已有 `tool` 结果重排到 assistant 之后、孤儿 `tool` 结果丢弃、空输入不抛异常等场景
- 新增 `AddMessageTextChunksTableTest` 校验迁移目标版本为 4
- 扩展 `LineCodeSchemaTest` 覆盖 `message_text_chunks` 表与索引的建表 SQL 声明
- 扩展 `ScreenNavigationControllerTest` 覆盖 `refreshVisibleScreen` 无动画重建与 `backFrom` 反向动画
- 已验证 `./gradlew :app:testDebugUnitTest`

---

## v1.1.6

### Agent 与工具审批

- **子 Agent 工具确认机制** - Agent 内部调用需要确认的工具时不再直接失败，而是进入与普通工具一致的待确认流程；Shell、删除等高风险工具会显示等待确认状态，用户确认后继续执行，拒绝后写入拒绝结果
- **Agent 进度待确认状态** - Agent 进度 JSON 新增 `pending` 状态展示，卡片中显示“需要确认”，并保留嵌套工具调用、确认状态、执行结果与错误信息，避免等待确认时被误判为运行失败
- **Agent Pipeline 待确认展示** - Agent 流水线卡片支持统计和展示子 Agent 的待确认状态；每个 Agent 行可展开显示其内部工具调用，审批按钮与普通工具调用保持一致
- **会话级自动确认延续到 Agent** - 子 Agent 内部工具确认支持“本次会话自动执行”，确认策略与普通 Shell 工具一致，减少重复审批

### Bug 修复

- **SSH 连接池锁异常** - 修复 SSH 测试连接和执行命令在输出正确后仍抛出 `IllegalMonitorStateException` 的问题；新建连接会正确持有锁，释放、丢弃和重建连接时只由持锁线程解锁
- **中断后重开进度圈残留** - 修复用户停止生成、直接关闭 APP 或进程被杀后，重新打开会话仍显示进度圈的问题；加载历史会话时会把残留的 `streaming`、`running`、`pending`、缺失 tool result、Agent / Agent Pipeline 运行态统一收敛为“上次生成已中断。”并落库
- **Agent / Pipeline 停止状态清理** - 主动停止生成时复用同一套进度清理逻辑，Agent、子 Agent 工具调用、Agent Pipeline 运行态与待确认态都会立即变为错误完成态，避免 UI 继续等待不存在的后台任务

### UI 与导航

- **屏幕切换动画** - 设置页、模型页、扩展页等二级页面新增前进/返回方向感知动画，返回时使用反向过渡，导航栈状态更清晰
- **抽屉与弹层动画优化** - 优化抽屉、底部操作表、目录选择器、附件选择器的入场和退场动画，关闭时会取消旧动画并复位状态，减少快速切换时的闪烁和残留
- **抽屉布局调整** - 会话/文件抽屉改为更适合移动端的顶部面板样式，并限制高度，文件树与会话列表在小屏上更稳定

### 版本

- 版本号升级到 `1.1.6`
- `versionCode` 升级到 `19`

### 测试

- 新增 `AgentExecutionControllerTest` 覆盖子 Agent Shell 工具确认、拒绝、自动确认与权限边界
- 新增 `SshConnectionPoolTest` 覆盖新建连接释放、丢弃重借、陈旧连接移除等锁行为，防止回归到 `IllegalMonitorStateException`
- 扩展 `ScreenNavigationControllerTest` 覆盖前进/返回方向传递
- 新增 `ConversationResumeSanitizerTest` 覆盖重启恢复时清理 streaming、pending 工具、缺失 tool result、Agent pending、Pipeline running 等残留状态
- 已验证 `./gradlew :app:testDebugUnitTest`

---

## v1.1.5

### Tool Call 与提示词

- **扩展工具顺序稳定** - 自定义 MCP / 扩展工具在工具提示词中按工具名排序输出，避免同一批工具因 Map/加载顺序变化导致提示词不稳定
- **工具调用次数提示去重** - 移除 `ModelPromptController.buildToolPrompt` 中重复拼接的工具调用次数限制说明，改由工具提示构建逻辑统一处理；次数耗尽时仅提示“当前没有可用工具”，减少重复和冲突文案
- **系统提示模板顺序调整** - `{{TOOLS_CONTEXT}}` 从 Agent 协作规则前移到工作目录上下文之后，让工具清单更贴近当前工作区信息，降低提示词前段噪音

### Bug 修复

- **Agent 进度回调栈溢出** - 修复 Agent / Agent Pipeline 工具进度回调在主线程同步派发时递归调用自身的问题；Release 混淆栈中的 `hi.j()` / `t.run()` 死循环已定位为 `GenerationFlowController` 匿名 Host 回调未限定外部类方法，现改为明确调用外层控制器，避免 `StackOverflowError`
- **保活通知权限** - 补齐 Android 13+ `POST_NOTIFICATIONS` 权限声明与运行时申请；用户开启前台服务通知或假音乐播放时会主动申请通知权限，权限返回后自动重新应用保活设置
- **前台保活通知实现** - 完善 `KeepAliveService` 前台通知，补齐默认状态文案、通知点击回到 LineCode、service 分类、ticker 与可见性设置；无保活开关时会停止服务，避免空启动
- **假音乐播放保活** - 补上静音 `AudioTrack` 循环播放实现，用户开启“假音乐播放”后后台任务期间会实际启动静音音频；任务结束或关闭开关时会停止并释放音频资源

### 测试

- 新增 `ToolSettingsRepositoryTest` 覆盖扩展工具按名称稳定排序输出
- 新增 `GenerationFlowControllerTest` 覆盖 Agent 工具结果回调只派发一次，防止回归到递归栈溢出
- 已验证 `testDebugUnitTest` 定向测试、`compileDebugJavaWithJavac` 与 `assembleDebug`

---

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
