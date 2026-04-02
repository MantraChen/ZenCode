use std::collections::HashMap;
use std::process::Command;
use std::path::Path;
use std::fs;
use regex::Regex;

#[cfg(windows)]
use std::os::windows::process::CommandExt;

use crate::toolchain::ToolchainManager;

/// Windows: 隐藏控制台窗口，防止 GUI 应用弹黑框
#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;

/// 代码执行结果
#[derive(Debug, serde::Serialize)]
pub struct ExecResult {
    pub stdout: String,
    pub stderr: String,
    pub exit_code: i32,
}

/// 零配置执行引擎
pub fn execute_code(
    code: &str,
    language: &str,
    work_dir: &Path,
    toolchain: &ToolchainManager,
) -> ExecResult {
    match language {
        "c" => execute_c(code, work_dir, toolchain),
        "cpp" => execute_cpp(code, work_dir, toolchain),
        "java" => execute_java(code, work_dir, toolchain),
        "python" => execute_python(code, work_dir, toolchain),
        _ => ExecResult {
            stdout: String::new(),
            stderr: format!("不支持的语言: {}", language),
            exit_code: 1,
        },
    }
}

fn execute_c(code: &str, work_dir: &Path, tc: &ToolchainManager) -> ExecResult {
    let src_path = work_dir.join("main.c");
    let out_path = work_dir.join(if cfg!(windows) { "main.exe" } else { "main" });

    fs::write(&src_path, code).unwrap_or_default();

    let gcc = tc.gcc();
    let compile = run_command(&gcc, &[
        src_path.to_str().unwrap(),
        "-o", out_path.to_str().unwrap(),
        "-Wall",
    ]);

    if compile.exit_code != 0 {
        return compile;
    }

    run_command(out_path.to_str().unwrap(), &[])
}

fn execute_cpp(code: &str, work_dir: &Path, tc: &ToolchainManager) -> ExecResult {
    let src_path = work_dir.join("main.cpp");
    let out_path = work_dir.join(if cfg!(windows) { "main.exe" } else { "main" });

    fs::write(&src_path, code).unwrap_or_default();

    let gpp = tc.gpp();
    let compile = run_command(&gpp, &[
        src_path.to_str().unwrap(),
        "-o", out_path.to_str().unwrap(),
        "-Wall", "-std=c++17",
    ]);

    if compile.exit_code != 0 {
        return compile;
    }

    run_command(out_path.to_str().unwrap(), &[])
}

fn execute_java(code: &str, work_dir: &Path, tc: &ToolchainManager) -> ExecResult {
    let class_name = extract_java_class_name(code).unwrap_or("Main".to_string());
    let src_path = work_dir.join(format!("{}.java", class_name));

    fs::write(&src_path, code).unwrap_or_default();

    let javac = tc.javac();
    let src = src_path.to_str().unwrap();
    // -encoding UTF-8: 以 UTF-8 读取源码
    // -J-Dfile.encoding=UTF-8: 让 javac 自身以 UTF-8 输出错误信息（防止 Windows GBK 乱码）
    let compile = run_command(&javac, &[
        "-encoding", "UTF-8",
        "-J-Dfile.encoding=UTF-8",
        src,
    ]);

    if compile.exit_code != 0 {
        return compile;
    }

    let java = tc.java();
    let work = work_dir.to_str().unwrap();
    // -Dfile.encoding=UTF-8: 让 java 进程以 UTF-8 输出
    run_command(&java, &[
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-cp", work,
        &class_name,
    ])
}

fn execute_python(code: &str, work_dir: &Path, tc: &ToolchainManager) -> ExecResult {
    let src_path = work_dir.join("main.py");
    fs::write(&src_path, code).unwrap_or_default();

    let python = tc.python();
    // -u: 禁用 stdout/stderr 缓冲，确保输出能被正确捕获
    let mut cmd = Command::new(&python);
    cmd.args(&["-u", src_path.to_str().unwrap()])
        .env("PYTHONUNBUFFERED", "1")
        .env("PYTHONIOENCODING", "utf-8")
        .current_dir(work_dir);
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);
    match cmd.output()
    {
        Ok(output) => ExecResult {
            stdout: String::from_utf8_lossy(&output.stdout).to_string(),
            stderr: String::from_utf8_lossy(&output.stderr).to_string(),
            exit_code: output.status.code().unwrap_or(1),
        },
        Err(e) => ExecResult {
            stdout: String::new(),
            stderr: format!(
                "无法启动 {}：{}。\n\n请确认已安装对应的编译器/解释器，或将便携工具链放入 toolchains/ 目录。",
                python, e
            ),
            exit_code: 1,
        },
    }
}

fn run_command(program: &str, args: &[&str]) -> ExecResult {
    let mut cmd = Command::new(program);
    cmd.args(args);
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);

    // 如果 program 是绝对路径，将其所在目录加入 PATH
    // 这样 gcc 才能找到同目录下的 as.exe, ld.exe 等工具
    let prog_path = Path::new(program);
    if prog_path.is_absolute() {
        if let Some(bin_dir) = prog_path.parent() {
            let current_path = std::env::var("PATH").unwrap_or_default();
            let new_path = format!("{}{}{}", bin_dir.display(), if cfg!(windows) { ";" } else { ":" }, current_path);
            cmd.env("PATH", new_path);
        }
    }

    match cmd.output() {
        Ok(output) => ExecResult {
            stdout: String::from_utf8_lossy(&output.stdout).to_string(),
            stderr: String::from_utf8_lossy(&output.stderr).to_string(),
            exit_code: output.status.code().unwrap_or(1),
        },
        Err(e) => ExecResult {
            stdout: String::new(),
            stderr: format!(
                "无法启动 {}：{}。\n\n请确认已安装对应的编译器/解释器，或将便携工具链放入 toolchains/ 目录。",
                program, e
            ),
            exit_code: 1,
        },
    }
}

fn extract_java_class_name(code: &str) -> Option<String> {
    let re = regex::Regex::new(r"public\s+class\s+(\w+)").ok()?;
    re.captures(code).map(|c| c[1].to_string())
}

/// 内存级 Web 打包器 (Zero-Server Bundler)
pub fn bundle_web_project(files: &HashMap<String, String>) -> Result<String, String> {
    let html = files
        .get("index.html")
        .ok_or_else(|| "虚拟项目中缺少 index.html 文件".to_string())?;

    let mut result = html.clone();

    let css_re = Regex::new(
        r#"<link\s+[^>]*rel\s*=\s*["']stylesheet["'][^>]*href\s*=\s*["']([^"']+\.css)["'][^>]*/?\s*>"#
    ).unwrap();
    let css_re2 = Regex::new(
        r#"<link\s+[^>]*href\s*=\s*["']([^"']+\.css)["'][^>]*rel\s*=\s*["']stylesheet["'][^>]*/?\s*>"#
    ).unwrap();

    for re in &[&css_re, &css_re2] {
        let mut new_result = result.clone();
        for caps in re.captures_iter(&result) {
            let full_match = &caps[0];
            let css_filename = &caps[1];
            if let Some(css_content) = files.get(css_filename) {
                let inline = format!("<style>\n{}\n</style>", css_content);
                new_result = new_result.replace(full_match, &inline);
            }
        }
        result = new_result;
    }

    let js_re = Regex::new(
        r#"<script\s+[^>]*src\s*=\s*["']([^"']+\.js)["'][^>]*>\s*</script>"#
    ).unwrap();

    let mut new_result = result.clone();
    for caps in js_re.captures_iter(&result) {
        let full_match = &caps[0];
        let js_filename = &caps[1];
        if let Some(js_content) = files.get(js_filename) {
            let inline = format!("<script>\n{}\n</script>", js_content);
            new_result = new_result.replace(full_match, &inline);
        }
    }
    result = new_result;

    // 注入 console 拦截脚本，将 console.log/warn/error 输出通过 postMessage 传回父窗口
    let console_capture = r#"<script>
(function() {
  var _origConsole = { log: console.log, warn: console.warn, error: console.error, info: console.info };
  function send(level, args) {
    var parts = [];
    for (var i = 0; i < args.length; i++) {
      try { parts.push(typeof args[i] === 'object' ? JSON.stringify(args[i], null, 2) : String(args[i])); }
      catch(e) { parts.push(String(args[i])); }
    }
    window.parent.postMessage({ __zencode_console: true, level: level, text: parts.join(' ') }, '*');
  }
  console.log   = function() { send('log',   arguments); _origConsole.log.apply(console, arguments); };
  console.warn  = function() { send('warn',  arguments); _origConsole.warn.apply(console, arguments); };
  console.error = function() { send('error', arguments); _origConsole.error.apply(console, arguments); };
  console.info  = function() { send('log',   arguments); _origConsole.info.apply(console, arguments); };
  window.addEventListener('error', function(e) {
    send('error', [e.message + ' (line ' + e.lineno + ')']);
  });
})();
</script>"#;

    // 在 <head> 之后或 <html> 之后注入
    if let Some(pos) = result.find("<head>") {
        result.insert_str(pos + 6, console_capture);
    } else if let Some(pos) = result.find("<html") {
        if let Some(end) = result[pos..].find('>') {
            result.insert_str(pos + end + 1, console_capture);
        }
    } else {
        result.insert_str(0, console_capture);
    }

    Ok(result)
}