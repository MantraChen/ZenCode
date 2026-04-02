use tauri::Manager;

mod trie;
mod error_translator;
mod executor;
mod vfs;
mod toolchain;
mod workspace;

use std::sync::Mutex;
use trie::Trie;
use trie::keywords;
use error_translator::FriendlyError;
use vfs::{VirtualFS, VfsFileInfo};
use toolchain::{ToolchainManager, ToolchainStatus};
use workspace::FsEntry;

/// 全局应用状态
struct AppState {
    tries: std::collections::HashMap<String, Trie>,
    vfs: VirtualFS,
    toolchain: ToolchainManager,
    /// 当前打开的真实文件系统工作区路径（非 Web 语言使用）
    workspace_path: Option<std::path::PathBuf>,
}

impl AppState {
    fn new(resource_dir: Option<std::path::PathBuf>) -> Self {
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

        // 初始化工具链管理器
        // Windows: 使用 C:\ZenCode 避免中文用户名路径导致 MinGW ld.exe 崩溃
        // 其他平台: 使用标准 app_data 目录
        let app_data_dir = get_safe_data_dir();
        std::fs::create_dir_all(&app_data_dir).unwrap_or_default();

        let toolchain = ToolchainManager::init(
            resource_dir.as_deref(),
            &app_data_dir,
        );

        AppState {
            tries,
            vfs: VirtualFS::new(),
            toolchain,
            workspace_path: None,
        }
    }
}

/// 获取安全的数据目录（避免中文路径）
fn get_safe_data_dir() -> std::path::PathBuf {
    #[cfg(windows)]
    {
        // Windows: 如果用户目录含非 ASCII 字符，使用 C:\ZenCode
        let default = dirs::data_dir()
            .unwrap_or_else(|| std::path::PathBuf::from("C:\\ZenCode"))
            .join("ZenCode");
        if default.to_string_lossy().is_ascii() {
            default
        } else {
            std::path::PathBuf::from("C:\\ZenCode")
        }
    }
    #[cfg(not(windows))]
    {
        dirs::data_dir()
            .unwrap_or_else(|| std::path::PathBuf::from("."))
            .join("ZenCode")
    }
}

/// 获取安全的工作区目录（编译临时文件）
fn get_safe_workspace_dir() -> std::path::PathBuf {
    #[cfg(windows)]
    {
        let tmp = std::env::temp_dir().join("zencode_workspace");
        if tmp.to_string_lossy().is_ascii() {
            tmp
        } else {
            std::path::PathBuf::from("C:\\ZenCode\\workspace")
        }
    }
    #[cfg(not(windows))]
    {
        std::env::temp_dir().join("zencode_workspace")
    }
}

// ============ Trie 补全 IPC ============

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
fn run_code(code: &str, language: &str, state: tauri::State<'_, Mutex<AppState>>) -> serde_json::Value {
    let temp_dir = get_safe_workspace_dir();
    std::fs::create_dir_all(&temp_dir).unwrap_or_default();

    let state = state.lock().unwrap();
    let result = executor::execute_code(code, language, &temp_dir, &state.toolchain);

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

/// 根据文件路径自动识别语言并编译运行
#[tauri::command]
fn run_file(file_path: &str, stdin_input: Option<String>, state: tauri::State<'_, Mutex<AppState>>) -> serde_json::Value {
    let path = std::path::Path::new(file_path);

    let language = match path.extension().and_then(|e| e.to_str()) {
        Some("c") => "c",
        Some("cpp" | "cc" | "cxx") => "cpp",
        Some("java") => "java",
        Some("py") => "python",
        _ => {
            return serde_json::json!({
                "stdout": "",
                "stderr": format!("不支持的文件类型: {}", file_path),
                "exitCode": 1,
                "errors": [],
            });
        }
    };

    // 使用文件所在目录作为工作区
    let work_dir = path.parent().unwrap_or(std::path::Path::new("."));

    let state = state.lock().unwrap();
    let result = executor::execute_project_from_dir(work_dir, language, path, stdin_input.as_deref(), &state.toolchain);

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

// ============ 工作区 IPC（真实文件系统） ============

#[tauri::command]
fn ws_open_folder(state: tauri::State<'_, Mutex<AppState>>) -> Option<String> {
    let folder = workspace::pick_folder()?;
    let path_str = folder.to_string_lossy().to_string();
    let mut state = state.lock().unwrap();
    state.workspace_path = Some(folder);
    Some(path_str)
}

#[tauri::command]
fn ws_set_folder(path: &str, state: tauri::State<'_, Mutex<AppState>>) {
    let mut state = state.lock().unwrap();
    state.workspace_path = Some(std::path::PathBuf::from(path));
}

#[tauri::command]
fn ws_get_folder(state: tauri::State<'_, Mutex<AppState>>) -> Option<String> {
    let state = state.lock().unwrap();
    state.workspace_path.as_ref().map(|p| p.to_string_lossy().to_string())
}

#[tauri::command]
fn ws_list_tree(state: tauri::State<'_, Mutex<AppState>>) -> Vec<FsEntry> {
    let state = state.lock().unwrap();
    match &state.workspace_path {
        Some(p) => workspace::list_tree(p),
        None => vec![],
    }
}

#[tauri::command]
fn ws_read_file(path: &str) -> Result<String, String> {
    workspace::read_file(std::path::Path::new(path))
}

#[tauri::command]
fn ws_write_file(path: &str, content: &str) -> Result<(), String> {
    workspace::write_file(std::path::Path::new(path), content)
}

#[tauri::command]
fn ws_create_file(name: &str, state: tauri::State<'_, Mutex<AppState>>) -> Result<String, String> {
    let state = state.lock().unwrap();
    let dir = state.workspace_path.as_ref().ok_or("未打开项目文件夹")?;
    workspace::create_file(dir, name)
}

#[tauri::command]
fn ws_create_dir(name: &str, state: tauri::State<'_, Mutex<AppState>>) -> Result<String, String> {
    let state = state.lock().unwrap();
    let dir = state.workspace_path.as_ref().ok_or("未打开项目文件夹")?;
    workspace::create_dir(dir, name)
}

#[tauri::command]
fn ws_delete(path: &str) -> Result<(), String> {
    workspace::delete_entry(std::path::Path::new(path))
}

#[tauri::command]
fn ws_rename(path: &str, new_name: &str) -> Result<String, String> {
    workspace::rename_entry(std::path::Path::new(path), new_name)
}

#[tauri::command]
fn ws_move(src: &str, dest_dir: &str) -> Result<String, String> {
    workspace::move_entry(std::path::Path::new(src), std::path::Path::new(dest_dir))
}

// ============ VFS 文件系统 IPC（Web 项目专用） ============

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

#[tauri::command]
fn vfs_create_project(language: &str, state: tauri::State<'_, Mutex<AppState>>) -> Vec<VfsFileInfo> {
    let mut state = state.lock().unwrap();
    state.vfs.create_project(language);
    state.vfs.list_files()
}

// ============ Web 打包器 IPC ============

#[tauri::command]
fn bundle_web(entry: Option<String>, state: tauri::State<'_, Mutex<AppState>>) -> Result<String, String> {
    let state = state.lock().unwrap();
    executor::bundle_web_project(state.vfs.all_files(), entry.as_deref())
}

// ============ 工具链状态 IPC ============

#[tauri::command]
fn toolchain_status(state: tauri::State<'_, Mutex<AppState>>) -> Vec<ToolchainStatus> {
    let state = state.lock().unwrap();
    state.toolchain.status()
}

// ============ 启动 ============

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            // 获取 Tauri resource 目录（打包后才有值）
            let resource_dir = app.path().resource_dir().ok();
            let state = AppState::new(resource_dir);
            app.manage(Mutex::new(state));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            autocomplete,
            run_code,
            run_file,
            // 工作区（真实文件系统）
            ws_open_folder,
            ws_set_folder,
            ws_get_folder,
            ws_list_tree,
            ws_read_file,
            ws_write_file,
            ws_create_file,
            ws_create_dir,
            ws_delete,
            ws_rename,
            ws_move,
            // VFS（Web 项目）
            vfs_list_files,
            vfs_read_file,
            vfs_write_file,
            vfs_delete_file,
            vfs_rename_file,
            vfs_set_active,
            vfs_create_web_project,
            vfs_create_project,
            bundle_web,
            toolchain_status,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
