# LineCode Pro

> 一个能塞进口袋的 AI 编程工作台。接入主流大模型，让它直接读写你的项目文件、跑 Shell、用 SSH 连远程主机 —— 全部在 Android 上完成。

[English](README.md) · [中文](README_CN.md)

许可证：**GPL-3.0-or-later** · Android **7.0+**（API 24） · 当前版本：**1.0.3**

---

## 它能做什么

LineCode Pro 把手机或平板变成一个真正能上手干活的 AI 编程助手。选一个项目目录、配好模型，它就可以：

- 用**同一个聊天界面**与 OpenAI 兼容 API、Codex Responses、Anthropic Messages，或本地 GGUF 推理后端对话。
- 在你授权的目录里读取、编辑、glob、删除和新建文件。
- 通过 Termux 在本地执行 Shell，或者经由 SSH 在远程机器上工作。
- 抓取与搜索网页、理解图片、生成图片。
- 把长对话沉淀成长期记忆，下次会话再喂回上下文。

整个流程都在端侧运行，项目文件不会被传到任何地方 —— 除非你自己把它接到远程模型。

## 功能亮点

- **一个聊天界面，多家模型。** OpenAI 兼容、Anthropic、Codex、本地 GGUF 之间任意切换，UI 不变。
- **真正能干活的工具循环。** 文件读 / 写 / 编辑 / 删除、glob、目录列表、Shell 执行、HTTP 服务、网页抓取与搜索、图像理解、图像生成、子任务分派 —— 模型可调，你可以逐条审批。
- **支持自定义扩展。** 自定义 Agent 和 MCP-HTTP 工具会自动注册，和内置工具放在一起。
- **可在任意目录工作。** 通过系统选择器挑一个目录，LineCode 会在重启后记住授权。
- **远程友好。** 用 `jsch` 浏览和编辑 SSH 远程文件，可选 Termux 本地 Shell 集成。
- **默认隐私优先。** 导出文件自动去敏，明文 HTTP 仅放行 loopback，内置浏览器默认禁用 JavaScript。
- **可导入导出。** 会话、项目、设置和扩展可以打包成单个 `.linecode` 文件，换设备即恢复。

## 截图

_截图待补。_

## 安装

**下载预编译 APK。** 在 Releases 页面下载最新的 `LineCode Pro <版本号>.APK`，安装即可。

**从源码构建。** 见下方的 [构建](#构建)。

> 提示：LineCode 默认申请 `MANAGE_EXTERNAL_STORAGE`，这样 AI 才能对真实项目目录下手。如果你不愿授权，也可以只通过系统文档选择器选目录，大部分功能仍然能用，但对超大目录的 glob 会变慢。

## 上手指南

1. 打开应用，按提示授予存储权限。
2. 抽屉 → **模型** → **新增模型**，挑一个协议（OpenAI 兼容 / Anthropic / Codex Responses / 本地 GGUF），填好 API base URL、密钥和模型名。
3. 通过 **项目 → 打开外部目录** 选一个工作区。
4. 开始聊天。模型会发起工具调用，你可以逐条审批，或在本次会话内开启自动确认。

可选：

- **Termux 集成。** 从 F-Droid 安装 Termux，授权 `RUN_COMMAND`，LineCode 就能通过它跑 Shell。
- **SSH 工作区。** 在 **设置 → SSH** 添加远程主机，然后在项目抽屉里浏览远程目录。
- **自定义 Agent / MCP 工具。** 在 **扩展** 里配置，配好立刻可用。

## 构建

需要 JDK 11+、安装了平台 36 的 Android SDK，以及本仓库的源码。

```bash
# Debug 构建（用 debug 证书签名，便于旁加载）
./gradlew :app:assembleDebugUserCert
# 输出：app/build/outputs/apk/debugUserCert/export/LineCode-user-cert-debug.apk

# 单元测试
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

**Release 构建** 需要在仓库根目录放一份 `signing.properties` 写明 keystore 信息 —— 构建任务会拒绝用 debug 证书签 release。签名配置好后，`./gradlew :app:assembleRelease` 会输出 `app/release/LineCode Pro <版本号>.APK`。

架构内幕（控制器、工具系统、上下文管理等）见 [`CLAUDE.md`](CLAUDE.md)。

## 支持的模型协议

只要符合下列协议之一，LineCode 都能接：

| 协议 | 已验证可用的模型/服务 |
| --- | --- |
| OpenAI 兼容 | OpenAI、DeepSeek、通义千问、Moonshot、智谱、Groq、Ollama、llama.cpp server …… |
| Anthropic Messages | Claude 4.x 与 3.x |
| Codex Responses | OpenAI Responses API |
| 本地 GGUF | 设备本地 llama.cpp 推理 |

加新模型通常只需要填一个 base URL 和密钥。

## 隐私与安全

- API 密钥、SSH 凭据和 MCP 请求头都会从 `.linecode` 导出中**自动去敏**。
- 所有出网请求都会过白名单；明文 HTTP 仅放行 `localhost`、`127.0.0.1`、`10.0.2.2`。
- 内置 WebView 默认关闭 JavaScript，并禁用 `file://` / `content://` 访问。
- Release 构建会剥离 LineNumberTable、native debug symbols 和 mapping 文件。

完整威胁模型见 [`docs/android-compliance-audit.md`](docs/android-compliance-audit.md)。

## 参与贡献

欢迎提 Issue、想法和 PR。如果要写代码，请注意：

- 这是一个纯 Java 项目，Kotlin stdlib 被刻意排除在运行时 classpath 之外。
- 视图全部由 Java 代码构建，不写 XML 布局。
- 新增聊天或工具行为，请扩展 `cn.lineai.mvp` 下对应的控制器，不要绕过它去改 View。

提交 PR 前，请确认以下命令全部通过：
`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`。

## 许可证

LineCode Pro 是自由软件，按 **GNU 通用公共许可证 v3.0 或更高版本** 发布。

```
Copyright (C) 2026 langlang03 <jiyu03@qq.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

许可证全文见 [`LICENSE`](LICENSE)，简要版权声明见 [`COPYING`](COPYING)，在线副本：<https://www.gnu.org/licenses/gpl-3.0.txt>。

随 APK 一起分发的第三方库各自遵循自己的许可证：commonmark（BSD-2）、JSch（BSD 风格）、org.json（JSON License）。
