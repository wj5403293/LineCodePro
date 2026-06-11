# LineCode Pro

> An AI coding workspace that fits in your pocket. Chat with any major LLM, let it read and edit your project files, run shell commands, and reach remote hosts over SSH — all on Android.

[English](README.md) · [中文](README_CN.md)

License: **GPL-3.0-or-later** · Android **7.0+** (API 24) · Latest: **1.0.3**

---

## What it does

LineCode Pro turns your phone or tablet into a real coding assistant. Point it at a project folder, pick a model, and it can:

- Hold a streaming conversation with **OpenAI-compatible APIs, Codex Responses, Anthropic Messages, or a local GGUF model** — all from the same chat.
- Read, edit, glob, delete and create files in any folder you grant it access to.
- Run shell commands locally via Termux, or work against a remote machine over SSH.
- Search and fetch the web, understand and generate images.
- Distil long conversations into memories, then bring them back into context on the next session.

Everything runs on-device. Your project files never leave your phone unless you wire it up to a remote model.

## Highlights

- **One chat, many backends.** Switch between OpenAI-compatible providers, Anthropic, Codex, and a local GGUF runtime without changing the UI.
- **A real tool loop.** File read / write / edit / delete, glob, directory listing, shell execution, HTTP server, web fetch & search, image understanding, image generation, sub-agent delegation — all callable by the model and reviewable by you.
- **Bring your own extensions.** Custom Agent and MCP-HTTP tools register automatically and show up alongside the built-ins.
- **Works on any folder.** Pick a directory via the system picker; LineCode remembers it across launches.
- **Remote-friendly.** SSH browsing and editing through `jsch`, plus optional Termux shell integration.
- **Private by default.** Secrets are stripped from exports, cleartext HTTP is rejected outside loopback, and the in-app browser ships with JavaScript off.
- **Import / export.** Save your conversations, projects, settings and extensions to a single `.linecode` archive and load them on another device.

## Screenshots

_Screenshots coming soon._

## Install

**Download a release APK.** Grab the latest `LineCode Pro <version>.APK` from the Releases page and sideload it.

**Build from source.** See [Building](#building) below.

> Heads-up: LineCode requests `MANAGE_EXTERNAL_STORAGE` so the AI can actually work on real project trees. If you'd rather not grant it, you can still pick a folder through the system document picker — most features will work, but globs over very large trees will be slower.

## Getting started

1. Open the app and accept the storage permission prompt.
2. Tap the drawer → **Models** → **Add model**, pick a protocol (OpenAI-compatible / Anthropic / Codex Responses / Local GGUF) and paste your API base URL, key, and model name.
3. Pick a workspace folder via **Projects → Open external folder**.
4. Start chatting. The model can request tool calls — you can review and approve them per-call, or enable auto-confirm for this session.

Optional:

- **Termux integration.** Install Termux from F-Droid, grant `RUN_COMMAND`, and LineCode can run shell tools through it.
- **SSH workspace.** Add an SSH host under **Settings → SSH**, then browse the remote tree from the project drawer.
- **Custom agents / MCP tools.** Configure under **Extensions**. They become available to the model immediately.

## Building

You need JDK 11+, the Android SDK with platform 36, and a checkout of this repository.

```bash
# Debug build for sideload
./gradlew :app:assembleDebugUserCert
# Output: app/build/outputs/apk/debugUserCert/export/LineCode-user-cert-debug.apk

# Run the unit test suite
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

**Release builds** require a `signing.properties` file at the repo root with your keystore details — the build will refuse to use the debug certificate for release artifacts. Once signed, `./gradlew :app:assembleRelease` produces `app/release/LineCode Pro <version>.APK`.

For internals (architecture, controllers, tool system, context manager), see [`CLAUDE.md`](CLAUDE.md).

## Supported model providers

LineCode talks to anything that speaks one of these protocols:

| Protocol | Tested with |
| --- | --- |
| OpenAI Compatible | OpenAI, DeepSeek, Qwen, Moonshot, Zhipu, Groq, Ollama, llama.cpp server, … |
| Anthropic Messages | Claude 4.x and 3.x |
| Codex Responses | OpenAI Responses API |
| Local GGUF | On-device llama.cpp inference |

Adding a new provider is usually just a base URL and key.

## Privacy & security

- API keys, SSH credentials and MCP request headers are **redacted** from `.linecode` exports.
- All outbound URLs flow through a strict allow-list; cleartext HTTP is only permitted to `localhost`, `127.0.0.1`, `10.0.2.2`.
- The built-in WebView keeps JavaScript off until you toggle it on, and disables `file://` / `content://` access.
- Release builds strip line-number tables, native debug symbols, and mapping files.

The full threat model is documented in [`docs/android-compliance-audit.md`](docs/android-compliance-audit.md).

## Contributing

Bug reports, ideas and patches are welcome. A few notes if you plan to send code:

- This is a Java-only codebase. The Kotlin stdlib is intentionally excluded from the runtime classpath.
- Views are built in Java, not XML.
- Add new chat or tool behaviour by extending the matching controller under `cn.lineai.mvp`, not by reaching into views.

Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` before sending a PR.

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

Full text in [`LICENSE`](LICENSE). Short notice in [`COPYING`](COPYING). Online copy at <https://www.gnu.org/licenses/gpl-3.0.txt>.

Third-party libraries shipped with the APK keep their own licenses: commonmark (BSD-2), JSch (BSD-style), org.json (JSON License).
