# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Development (starts Tauri dev server with hot reload)
npm run dev          # also runs predev → downloads toolchains for Windows

# Production build (Windows NSIS installer)
npm run build        # also runs prebuild → downloads toolchains

# Rust backend only
cd src-tauri && cargo build
cd src-tauri && cargo test
cd src-tauri && cargo test <test_name>   # run a single test
```

## Architecture

ZenCode is a Tauri 2 desktop app — a zero-config code playground for beginners. The UI is in Chinese (zh-CN).

**Frontend** (`src/`): Vanilla HTML/CSS/JS single-page app. No framework, no bundler. The editor is a plain `<textarea>` with a custom autocomplete popup. Web projects render in a sandboxed iframe via Blob URLs.

**Backend** (`src-tauri/src/`): Rust. All frontend↔backend communication is via Tauri IPC (`invoke()`). Global state is a `Mutex<AppState>` containing three subsystems:

- **Trie** (`trie/`): Prefix-tree autocomplete with pre-loaded keywords for 7 languages (C, C++, Java, Python, HTML, CSS, JS).
- **Executor** (`executor/mod.rs`): Writes code to a temp dir, spawns compiler/interpreter via `std::process::Command`, captures stdout/stderr. Supports C, C++, Java, Python. Also contains `bundle_web_project()` which inlines CSS/JS into HTML for iframe preview.
- **VFS** (`vfs/mod.rs`): In-memory flat file system (HashMap) for web projects. No disk I/O — all files live in memory.
- **Toolchain** (`toolchain/mod.rs`): Resolves compiler paths. Portable toolchains (bundled in `toolchains/`) take priority; falls back to system PATH. On macOS/Linux, C/C++ falls back to `cc`/`c++` (clang) if gcc/g++ not found.
- **Error Translator** (`error_translator/rules.rs`): Regex-based parser that converts raw compiler errors (GCC, javac, Python tracebacks) into friendly Chinese messages with line numbers.

**IPC flow**: `main.js` calls `invoke("run_code", {code, language})` → `lib.rs` dispatches to executor → result JSON `{stdout, stderr, exitCode, errors}` → frontend renders in output panel.

## Key Conventions

- The app bundles portable toolchains for Windows only (`scripts/download-toolchains.js`). macOS/Linux use system compilers.
- Tauri resources config (`tauri.conf.json`) bundles `toolchains/**/*` — these get extracted to `<app_data>/ZenCode/toolchains/` on first run.
- Web project console output is captured by injecting a `postMessage` bridge script into the bundled HTML; the parent window listens and displays it in the output panel.
- All user-facing strings are in Chinese.
