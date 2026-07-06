![LineCode Pro](https://socialify.git.ci/LangLang03/LineCodePro/image?description=1&font=KoHo&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2FLangLang03%2FLineCodePro%2Frefs%2Fheads%2Fmain%2F.idea%2Ficon.svg&name=1&pulls=1&stargazers=1&pattern=Circuit%20Board&theme=Auto)

# LineCode Pro

> **An AI coding workspace that fits in your pocket.**
> Chat with any major LLM, let it read and edit your project files, run shell commands, and reach remote hosts over SSH or a pluggable IPC provider — all on Android.

[English](README.md) · [中文](README_CN.md)

[![License: GPL-3.0-or-later](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84.svg)](app/build.gradle.kts)
[![Latest: 1.1.7](https://img.shields.io/badge/version-1.1.7-success.svg)](app/build.gradle.kts)
[![Java only](https://img.shields.io/badge/code-Java%2011-orange.svg)](#project-layout)

---

## Table of contents

1. [What is LineCode Pro?](#what-is-linecode-pro)
2. [What it can do](#what-it-can-do)
3. [Highlights](#highlights)
4. [Project layout](#project-layout)
5. [Install](#install)
6. [Getting started](#getting-started)
7. [Execution modes](#execution-modes)
8. [Model providers](#model-providers)
9. [Tool system](#tool-system)
10. [Extending LineCode](#extending-linecode)
11. [Building from source](#building-from-source)
12. [Privacy & security](#privacy--security)
13. [Contributing](#contributing)
14. [License](#license)

---

## What is LineCode Pro?

**LineCode Pro** is a self-hosted, on-device AI coding assistant for Android 7.0+. It is built around a single Activity that hosts a streaming chat with a real tool-call loop. You point it at a project folder (local, SSH remote, or a pluggable IPC provider), wire up a model, and the model can read, edit, glob, create, and delete files — as well as run shell commands, fetch and search the web, understand and generate images, and dispatch sub-agents.

LineCode is not a thin chat client. It is a full coding workspace: the system prompt, the tool registry, the context manager, the diff store, the file tree, the project picker, the SSH / IPC plumbing, the import/export archive, the extensions framework, and the security policy all live in the app. Nothing leaves your phone unless you wire it up to a remote model.

The application id is `cn.lineai` and the project is shipped as a single-module Gradle project (`:app`) plus a reusable library (`:ipc`) and a sample provider app (`:terminal-provider`).

---

## What it can do

### Conversations

- Streaming chat with **multiple model protocols** in the same UI: OpenAI-compatible HTTP APIs, Anthropic Messages, OpenAI Codex Responses, and a local GGUF runtime.
- Reasoning blocks (`<think>…</think>`) are extracted and rendered separately from the final answer.
- Tool-call text inside a stream is parsed and dispatched by `ToolCallTextParser`; everything the model asks to do is shown to you before it runs.
- System prompts are assembled from `app/src/main/assets/prompts/*.txt` — tone variants (chat / coding), context-compaction, memory-extraction, skill-extraction, work-directory, learning-context, and model-identity templates. You can override the tone, the work directory, the identity block, and the prompt template from settings.
- Long conversations are summarised in the background by `ContextCompactionService` using the active model itself; durable knowledge is extracted by `MemoryExtractionService` and reinjected next session by `LearningContextRepository`.

### Tool execution

The model has access to a registry of tools (`ToolRegistry`) with session-scoped confirmation:

| Category    | Built-in tools |
| ----------- | -------------- |
| Filesystem  | `file_read`, `file_write`, `file_edit`, `file_delete`, `glob`, `list_directory` |
| Shell       | `shell_execute` (Termux or via IPC) |
| Web         | `web_search`, `web_fetch`, `http_server` |
| Media       | `image_understanding`, `image_generation` |
| Sub-agents  | `agent`, `agent_pipeline` (delegate work to another LLM loop) |
| Productivity| `todo_update` |

Every file-touching tool routes paths through `FileToolPathPolicy` so the model can only act inside the workspace you opened. Shell calls go through Termux or an IPC provider — never the app process itself.

### Workspace & files

- **Local** — pick a folder with the system Storage Access Framework picker; the app remembers it across launches. With `MANAGE_EXTERNAL_STORAGE` the model can glob over real project trees.
- **SSH** — browse, read, write and execute on a remote host via `jsch`; credentials are stored in the encrypted-on-disk settings repository.
- **IPC provider (pluggable)** — bind to any third-party Android app that exposes the `cn.lineai.action.IPC_TERMINAL_PROVIDER` action and the `ITerminalProviderService` AIDL interface. The bundled `terminal-provider` module is a reference implementation that runs commands and file ops in its own process.
- **Custom agents & MCP** — register your own `agentx_` / `mcpx_` tools in the **Extensions** screen; they show up next to the built-ins and the model can call them immediately.

### Diff & review

- Every successful file write or edit produces a `DiffRecord` in `DiffRepository`; the chat history shows the change inline and the **Storage → Diff history** screen replays it.
- Shell runs are streamed to a custom `ToolCallShellView`; agent runs render an `ToolCallAgentView` / `ToolCallAgentPipelineView` card with live progress.

### Import / export

- A single `.linecode` archive contains the database (conversations, messages, memories, settings, extensions) plus the workspace files. **Secrets — model API keys, SSH credentials, web-search keys, sensitive MCP headers — are stripped** by `ArchiveSecretRedactor` before serialisation.
- The import flow requires explicit confirmation because it overwrites local data.

### Storage

- All persistence goes through SQLite (`LineCodeDatabase` singleton, schema in `LineCodeSchema`) and one repository per concern. Controllers never touch the database directly.

---

## Highlights

- **One chat, many backends.** OpenAI-compatible, Anthropic Messages, Codex Responses, and on-device GGUF — pick any of them per session.
- **A real tool loop.** The model can read, edit, glob, create, delete, run shell commands, fetch and search the web, look at images, generate images, and recurse into sub-agents — all gated by a permission prompt you can auto-confirm per session.
- **Works on any folder.** Local (SAF + optional `MANAGE_EXTERNAL_STORAGE`), remote (SSH via jsch), or any third-party IPC provider.
- **Bring your own extensions.** Custom agents (`agentx_*`) and MCP-HTTP tools (`mcpx_*`) register automatically.
- **Pluggable IPC provider.** Run shell and file ops in a separate process for security and isolation. Ship a provider as a normal Android app — see [`ipc/README.md`](ipc/README.md) for the protocol.
- **Memory and context.** Long conversations are summarised; durable knowledge is re-injected next session.
- **Private by default.** URL allow-list, strict `network_security_config.xml`, secrets redacted from exports, in-app browser keeps JavaScript off.
- **Java-only, on purpose.** No Kotlin runtime, no XML layouts — the app is built entirely in Java 11 for transparency and reviewability.

---

## Project layout

```
LineCode/
├── app/                       # The :app Gradle module (cn.lineai)
│   ├── build.gradle.kts
│   ├── lint.xml
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/prompts/        # System prompt templates (.txt)
│       │   ├── aidl/                  # IPC AIDL stubs (compiled to :app)
│       │   ├── java/cn/lineai/
│       │   │   ├── MainActivity.java
│       │   │   ├── ai/                # Protocols, messages, tool-call parser, system prompt
│       │   │   ├── context/           # ContextManager, compaction, memory
│       │   │   ├── data/              # SQLite schema, repositories, importer/exporter
│       │   │   ├── model/             # Plain data types (records, settings, attachments)
│       │   │   ├── mvp/               # MainCoordinator + per-concern controllers
│       │   │   ├── security/          # UrlPolicy
│       │   │   ├── service/           # KeepAliveService
│       │   │   ├── ssh/               # SshService, jsch pool, TermuxHelper
│       │   │   ├── state/             # TodoStateStore
│       │   │   ├── tool/              # ToolRegistry + builtin/*
│       │   │   ├── ui/                # Views built in Java, markdown rendering
│       │   │   └── workspace/         # WorkspacePaths, SafPathResolver, storage perms
│       │   └── res/                   # Drawables, strings, themes, layout XML (none)
│       ├── test/                      # JUnit 4 unit tests
│       └── debugUserCert/             # Sideload build flavor
├── ipc/                       # Reusable Android library (cn.lineai.ipc)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml        # <permission> cn.lineai.permission.IPC_TERMINAL_PROVIDER
│       ├── aidl/                      # IBaseIpcService + terminal interfaces
│       └── java/cn/lineai/ipc/        # BaseIpcProvider, IpcProviderManager, registry, scanner
├── terminal-provider/         # Sample provider app (cn.lineai.terminalprovider)
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml        # <service> with android:permission enforced
│       └── java/cn/lineai/terminalprovider/TerminalProviderService.java
├── docs/                      # Compliance audit, threat model
├── gradle/libs.versions.toml  # Version catalog
├── settings.gradle.kts
├── build.gradle.kts
├── CLAUDE.md                  # Architecture bible for AI/code reviewers
├── README.md / README_CN.md   # ← you are here
├── LICENSE / COPYING
└── 问题.txt                   # Local scratch issue list (gitignored)
```

### Architecture in 30 seconds

* `MainActivity` is the only Activity. It instantiates `MainCoordinator` (the presenter) and `MainChatView` (the view, built entirely in Java — no XML inflation; `lint.xml` silences `ViewConstructor` accordingly).
* `MainCoordinator` implements `MainUiController` and delegates behaviour to per-concern controllers in `cn.lineai.mvp.*` (chat, generation, tool run, model management, settings, screens, permission mode, archive, project sheet, SSH file tree, IPC file tree, file operations). UI state flows through `ChatUiStateAssembler` → `ChatUiState` → `MainContract.View.render(...)`.
* `cn.lineai.ai.protocol.ModelProtocol` is the streaming/completion interface. `ModelProtocolFactory` dispatches on `ModelProtocolType` (`OPENAI_COMPATIBLE`, `CODEX_RESPONSES`, `ANTHROPIC_MESSAGES`, `LOCAL_GGUF`).
* `BaseTool` (name, description, category, JSON schema, `execute(JSONObject, ToolContext)`) is the tool contract. `ToolRegistry` registers built-ins and reloads user extensions. `ToolExecutionCoordinator` + `ToolExecutor` run them; `PermissionModeController` + `ToolReviewListener` gate per-call confirmation.

For the full architecture bible (controllers, tool system, context manager, SQLite schema, security model), see [`CLAUDE.md`](CLAUDE.md).

---

## Install

**Option A — Download a prebuilt APK.** Grab the latest `LineCode Pro <version>.APK` from the Releases page and sideload it. The `release` build is signed with a private keystore (see `signing.properties`); the `debug` build is signed with a debug certificate for ad-hoc testing.

**Option B — Build from source.** See [Building from source](#building-from-source) below.

> **Heads-up about storage permission.** LineCode requests `MANAGE_EXTERNAL_STORAGE` so the AI can glob and edit real project trees on shared storage. If you'd rather not grant that, you can still pick a folder through the system document picker — most features will work, but globs over very large trees will be slower.

---

## Getting started

1. **Grant storage.** On first launch LineCode will ask for the storage permission it needs. Approve it, then the document picker becomes available.
2. **Add a model.** Drawer → **Models** → **Add model**. Pick a protocol:
   - **OpenAI Compatible** — base URL, API key, model name. Works with OpenAI, DeepSeek, Qwen, Moonshot, Zhipu, Groq, Ollama, llama.cpp server, …
   - **Anthropic Messages** — base URL, API key, model name (Claude 4.x and 3.x).
   - **Codex Responses** — base URL, API key, model name.
   - **Local GGUF** — on-device llama.cpp inference.
3. **Pick a workspace.** Drawer → **Projects → Open external folder** (local). The path is remembered across launches; you can also add an SSH host in **Settings → SSH** or bind an IPC provider in **Settings → MCP execution mode → Terminal Provider**.
4. **Start chatting.** Type a question or task. The model will ask for tool calls — review them, or tap **Auto-confirm this session** to let it run unattended.
5. **Optional integrations.**
   - **Termux.** Install Termux from F-Droid, grant `RUN_COMMAND`, and the `shell_execute` tool will route through it.
   - **SSH.** Add a host under **Settings → SSH**; remote files appear in the project drawer.
   - **IPC provider.** Install `terminal-provider` (or any third-party provider) and enable it under **Settings → MCP execution mode → Terminal Provider**. LineCode auto-rebinds on every cold start.
   - **Extensions.** Configure custom agents / MCP tools under **Extensions**.

### Try it with a model you already have

If you have Ollama or a llama.cpp server on the same Wi-Fi, point LineCode at it: `OpenAI Compatible` → base URL `http://10.0.2.2:11434/v1` (emulator) or `http://<laptop-ip>:11434/v1` (real device), API key `ollama`, model `llama3.1:8b`. Works without an account, fully on-prem.

---

## Execution modes

LineCode has three execution modes for shell and file tools. You can switch between them per session in **Settings → MCP execution mode**.

| Mode | How shell and file tools run | When to use it |
| ---- | ---------------------------- | -------------- |
| **Local** | Through the Android SAF picker, in the app's own context. | Picking arbitrary folders on the device, no Termux required. |
| **SSH** | Through `jsch` against an SSH host you configured. | Working on a remote dev box. |
| **Terminal Provider (IPC)** | Through a third-party Android app bound over AIDL (the bundled `terminal-provider` is the reference implementation). | Isolated, sandboxed shell in a separate process. Pluggable — you can ship your own provider. |

Each mode exposes a consistent `IpcFileTreeStore` / `SshFileTreeStore` / `FileTreeStore` interface, so the file tree, the attachment picker, and the model all see the same UI regardless of where bytes actually live.

---

## Model providers

LineCode talks to anything that speaks one of these protocols:

| Protocol | Implementation | Tested with |
| -------- | -------------- | ----------- |
| OpenAI Compatible | `OpenAiCompatibleProtocol` (default) | OpenAI, DeepSeek, Qwen, Moonshot, Zhipu, Groq, Ollama, llama.cpp server, LM Studio, … |
| Anthropic Messages | `AnthropicMessagesProtocol` | Claude 4.x and 3.x |
| Codex Responses | `CodexResponsesProtocol` | OpenAI Responses API |
| Local GGUF | `LocalGgufProtocol` | On-device llama.cpp |

Adding a new provider is usually just a base URL and key. The capabilities inspector (`OpenAiCompatibleCapabilities`) probes tool support, so the model is told which tools it can use.

---

## Tool system

`BaseTool` is the contract. Every tool exposes:

* `name`, `description`, `category`
* a JSON schema for its arguments
* an `execute(JSONObject args, ToolContext ctx)` that returns a `ToolResult`

Built-in tools live in `app/src/main/java/cn/lineai/tool/builtin/`:

```
FileReadTool      FileWriteTool      FileEditTool      FileDeleteTool
GlobTool          ListDirectoryTool  HttpServerTool    ShellExecuteTool
ImageUnderstandingTool  ImageGenerationTool  WebSearchTool  WebFetchTool
AgentTool  AgentPipelineTool  TodoUpdateTool
```

Execution is driven by `ToolExecutionCoordinator` + `ToolExecutor`. Every call goes through `PermissionModeController` + `ToolReviewListener`; the user can confirm per-call or auto-confirm for the session. The list of auto-confirmed tools is tracked on the coordinator (`MainCoordinator.sessionAutoConfirmedTools`).

The chat renders tool calls in custom cards under `app/src/main/java/cn/lineai/ui/component/toolcall/` (`ToolCallReadView`, `ToolCallWriteView`, `ToolCallShellView`, `ToolCallAgentView`, `ToolCallAgentPipelineView`, `ToolCallGenericView`, …). New tools get a custom card by registering with `ToolCallUtils`.

---

## Extending LineCode

Two extension points are first-class:

1. **Custom agents** (`agentx_*`) — implement `CustomAgentExtensionTool`; the model can call them like any other tool.
2. **Custom MCP-HTTP tools** (`mcpx_*`) — implement `CustomMcpHttpTool`; the model can call them over HTTP, with per-tool headers stored in the settings repository.

Both are persisted via `ExtensionRepository` and hot-reloaded by `ToolRegistry.reloadExtensions()`. Configure them under **Extensions** in the app.

3. **Custom IPC provider** — implement the `IBaseIpcService` / `ITerminalProviderService` AIDL in any Android app, ship it as a normal APK, and LineCode will auto-detect, bind, and route shell + file ops through it. See [`ipc/README.md`](ipc/README.md) for the full protocol and a working example.

---

## Building from source

You need:

* **JDK 11** or newer
* **Android SDK** with platform 36 installed
* A checkout of this repository

All Gradle commands go through the wrapper. The settings file forces `FAIL_ON_PROJECT_REPOS` and is wired to a Tencent Cloud Maven mirror — keep that in `settings.gradle.kts` when adding new repositories.

### Quick build

```bash
# Debug build signed with the debug certificate
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Debug build signed with the debug cert but renamed for sideload
./gradlew :app:assembleDebugUserCert
# → app/build/outputs/apk/debugUserCert/export/LineCode-user-cert-debug.apk

# Run the unit test suite (JUnit 4 only, no Robolectric)
./gradlew :app:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "cn.lineai.tool.ToolRegistryTest"
# Append .methodName to run a single method

# Static analysis (custom rules in app/lint.xml)
./gradlew :app:lintDebug
```

### Release build

A release APK needs a `signing.properties` file at the repo root with:

```properties
storeFile=/abs/path/to/your.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

The `validateReleaseSigning` task refuses to use the debug certificate for release artifacts. Once configured:

```bash
./gradlew :app:assembleRelease
# → app/release/LineCode Pro <versionName>.APK
```

The release pipeline is intentionally aggressive:

* 8192-entry R8 obfuscation dictionary generated by `generateReleaseObfuscationDictionary` (`app/build/generated/r8/obfuscation-dictionary.txt`)
* `exportReleaseApk` renames the artifact to `LineCode Pro <versionName>.APK` under `app/release/`
* `purgeReleaseSymbolFiles` deletes `outputs/mapping/release` and `outputs/native-debug-symbols/release`

Bumping the version requires editing `releaseVersionName` and `defaultConfig.versionCode` in `app/build.gradle.kts`; the release APK filename is derived from `releaseVersionName`.

### Building the IPC library & sample provider standalone

```bash
./gradlew :ipc:assembleDebug
./gradlew :terminal-provider:assembleDebug
```

The `terminal-provider` module produces a regular debug APK (`terminal-provider/build/outputs/apk/debug/terminal-provider-debug.apk`) that you can install alongside the main app to see IPC in action.

### Required gates before tagging a release

Per [`docs/android-compliance-audit.md`](docs/android-compliance-audit.md), the following must all pass:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

---

## Privacy & security

* **Secrets are redacted from exports.** API keys, SSH passwords / private keys / passphrases, web-search API keys, and sensitive MCP request headers are stripped by `ArchiveSecretRedactor` whenever you export a `.linecode` archive. Extend it whenever you add a new secret-bearing field.
* **Outbound URLs go through `UrlPolicy`.** Every fetch — in-app browser, external link opener, model HTTP, model catalog, web fetch/search, custom MCP HTTP — is checked against an allow-list. HTTP is rejected outside `localhost`, `127.0.0.1`, `10.0.2.2`. This is mirrored in `res/xml/network_security_config.xml` (`cleartextTrafficPermitted="false"` by default).
* **In-app WebView** disables `file://` and `content://` access, blocks mixed content, and keeps JavaScript off until the user toggles it.
* **IPC permission model.** A provider Service must declare `android:permission="cn.lineai.permission.IPC_TERMINAL_PROVIDER"` on its `<service>` tag; the app declares both the permission and the matching `<uses-permission>`. Callers without the permission are rejected at the Android framework layer — no extra check needed in the provider.
* **Release hardening.** R8 with the 8192-entry obfuscation dictionary, plus removal of line-number tables, native debug symbols, and mapping files. See `validateReleaseSigning` for the debug-cert refusal.

The full threat model and accepted risks are documented in [`docs/android-compliance-audit.md`](docs/android-compliance-audit.md).

---

## Contributing

Bug reports, ideas and patches are welcome. A few notes if you plan to send code:

* **Java only.** `app/src/main/java` is pure Java. The Kotlin stdlib is intentionally excluded from the runtime classpath in `app/build.gradle.kts`; pulling in a Kotlin transitive will silently fail at runtime. The same is true for `ipc/` and `terminal-provider/`.
* **Views in Java, not XML.** `lint.xml` silences `ViewConstructor` and `IconDuplicates` deliberately.
* **Extend the controllers.** When adding chat or tool behaviour, extend the matching controller in `cn.lineai.mvp.*` and thread state through `ChatUiStateAssembler` → `ChatUiState` → `MainContract.View.render(...)`. Do not reach into views from new code.
* **Tests mirror the package layout.** `app/src/test/java/cn/lineai/...` mirrors production. JUnit 4 + `org.json` only (no Robolectric, no Mockito). When testing repository or controller logic, prefer the in-memory fakes that already exist in sibling tests.
* **`问题.txt` is a scratch list.** It's gitignored; do not edit it as part of normal changes.
* **Run the gates before sending a PR:**

  ```bash
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
  ```

---

## License

LineCode Pro is free software, licensed under the **GNU General Public License v3.0 or later**.

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

Full text in [`LICENSE`](LICENSE). Online copy at <https://www.gnu.org/licenses/gpl-3.0.txt>.

Third-party libraries shipped with the APK keep their own licenses: commonmark (BSD-2), JSch (BSD-style), org.json (JSON License).
