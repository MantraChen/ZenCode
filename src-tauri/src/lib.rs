mod trie;
mod error_translator;
mod executor;

use std::sync::Mutex;
use trie::Trie;
use trie::keywords;
use error_translator::FriendlyError;

/// 全局应用状态：为每种语言维护独立的 Trie 树
struct AppState {
    tries: std::collections::HashMap<String, Trie>,
}

impl AppState {
    fn new() -> Self {
        let mut tries = std::collections::HashMap::new();

        // 为每种语言创建独立的 Trie 并加载关键字
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

        AppState { tries }
    }
}

/// IPC 命令：前缀补全查询
/// 前端输入变化时调用，1ms 内返回匹配列表
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

/// IPC 命令：执行代码
#[tauri::command]
fn run_code(code: &str, language: &str) -> serde_json::Value {
    let temp_dir = std::env::temp_dir().join("zencode_workspace");
    std::fs::create_dir_all(&temp_dir).unwrap_or_default();

    let result = executor::execute_code(code, language, &temp_dir);

    // 如果有 stderr，翻译成友好消息
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

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(Mutex::new(AppState::new()))
        .invoke_handler(tauri::generate_handler![autocomplete, run_code])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
