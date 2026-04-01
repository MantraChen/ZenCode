mod trie;
mod error_translator;
mod executor;
mod vfs;

use std::sync::Mutex;
use trie::Trie;
use trie::keywords;
use error_translator::FriendlyError;
use vfs::{VirtualFS, VfsFileInfo};

/// 全局应用状态
struct AppState {
    tries: std::collections::HashMap<String, Trie>,
    vfs: VirtualFS,
}

impl AppState {
    fn new() -> Self {
        let mut tries = std::collections::HashMap::new();

        let mut c_trie = Trie::new();
        keywords::load_c_keywords(&mut c_trie);
        tries.insert("c".to_string(), c_trie);

        let mut cpp_trie = Trie::new();
        keywords::load_cpp_keywords(&mut cpp_trie);
        tries.insert("cpp".to_string(), cpp_trie);

        let mut java_trie = Trie::new();
        keywords::load_java_keywords(&mut java_trie);
        tries.insert("java".to_string(), java_trie);

        let mut python_trie = Trie::new();
        keywords::load_python_keywords(&mut python_trie);
        tries.insert("python".to_string(), python_trie);

        let mut html_trie = Trie::new();
        keywords::load_html_keywords(&mut html_trie);
        tries.insert("html".to_string(), html_trie);

        let mut css_trie = Trie::new();
        keywords::load_css_keywords(&mut css_trie);
        tries.insert("css".to_string(), css_trie);

        let mut js_trie = Trie::new();
        keywords::load_javascript_keywords(&mut js_trie);
        tries.insert("javascript".to_string(), js_trie);

        AppState {
            tries,
            vfs: VirtualFS::new(),
        }
    }
}

// ============ Trie 补全 IPC ============

/// 前缀补全查询 —— 支持 scope 参数用于 HTML 混合文件上下文嗅探
#[tauri::command]
fn autocomplete(
    prefix: &str,
    language: &str,
    state: tauri::State<'_, Mutex<AppState>>,
) -> Vec<String> {
    let state = state.lock().unwrap();
    match state.tries.get(language) {
        Some(trie) => trie.search_prefix(prefix),
        None => vec![],
    }
}

// ============ 代码执行 IPC ============

#[tauri::command]
fn run_code(code: &str, language: &str) -> serde_json::Value {
    let temp_dir = std::env::temp_dir().join("zencode_workspace");
    std::fs::create_dir_all(&temp_dir).unwrap_or_default();

    let result = executor::execute_code(code, language, &temp_dir);

    let friendly_errors: Vec<FriendlyError> = if !result.stderr.is_empty() {
        error_translator::translate_error(&result.stderr, language)
    } else {
        vec![]
    };

    serde_json::json!({
        "stdout": result.stdout,
        "stderr": result.stderr,
        "exitCode": result.exit_code,
        "errors": friendly_errors,
    })
}

// ============ VFS 文件系统 IPC ============

#[tauri::command]
fn vfs_list_files(state: tauri::State<'_, Mutex<AppState>>) -> Vec<VfsFileInfo> {
    let state = state.lock().unwrap();
    state.vfs.list_files()
}

#[tauri::command]
fn vfs_read_file(name: &str, state: tauri::State<'_, Mutex<AppState>>) -> Option<String> {
    let state = state.lock().unwrap();
    state.vfs.read_file(name).map(|s| s.to_string())
}

#[tauri::command]
fn vfs_write_file(name: &str, content: &str, state: tauri::State<'_, Mutex<AppState>>) {
    let mut state = state.lock().unwrap();
    state.vfs.write_file(name, content);
}

#[tauri::command]
fn vfs_delete_file(name: &str, state: tauri::State<'_, Mutex<AppState>>) -> bool {
    let mut state = state.lock().unwrap();
    state.vfs.delete_file(name)
}

#[tauri::command]
fn vfs_rename_file(
    old_name: &str,
    new_name: &str,
    state: tauri::State<'_, Mutex<AppState>>,
) -> bool {
    let mut state = state.lock().unwrap();
    state.vfs.rename_file(old_name, new_name)
}

#[tauri::command]
fn vfs_set_active(name: &str, state: tauri::State<'_, Mutex<AppState>>) {
    let mut state = state.lock().unwrap();
    state.vfs.set_active(name);
}

#[tauri::command]
fn vfs_create_web_project(state: tauri::State<'_, Mutex<AppState>>) -> Vec<VfsFileInfo> {
    let mut state = state.lock().unwrap();
    state.vfs.create_web_project();
    state.vfs.list_files()
}

// ============ Web 打包器 IPC ============

/// 将虚拟文件系统中的 Web 项目打包成单个 HTML 字符串
/// 前端先调用 vfs_write_file 保存所有文件，再调用此命令获取打包结果
#[tauri::command]
fn bundle_web(state: tauri::State<'_, Mutex<AppState>>) -> Result<String, String> {
    let state = state.lock().unwrap();
    executor::bundle_web_project(state.vfs.all_files())
}

// ============ 启动 ============

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(Mutex::new(AppState::new()))
        .invoke_handler(tauri::generate_handler![
            autocomplete,
            run_code,
            vfs_list_files,
            vfs_read_file,
            vfs_write_file,
            vfs_delete_file,
            vfs_rename_file,
            vfs_set_active,
            vfs_create_web_project,
            bundle_web,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
