use std::collections::HashMap;
use std::process::Command;
use std::path::Path;
use std::fs;
use regex::Regex;

/// 代码执行结果
#[derive(Debug, serde::Serialize)]
pub struct ExecResult {
    pub stdout: String,
    pub stderr: String,
    pub exit_code: i32,
}

/// 零配置执行引擎
/// 自动检测工具链位置，编译并运行用户代码
pub fn execute_code(code: &str, language: &str, work_dir: &Path) -> ExecResult {
    match language {
        "c" => execute_c(code, work_dir),
        "cpp" => execute_cpp(code, work_dir),
        "java" => execute_java(code, work_dir),
        "python" => execute_python(code, work_dir),
        _ => ExecResult {
            stdout: String::new(),
            stderr: format!("不支持的语言: {}", language),
            exit_code: 1,
        },
    }
}

fn execute_c(code: &str, work_dir: &Path) -> ExecResult {
    let src_path = work_dir.join("main.c");
    let out_path = work_dir.join(if cfg!(windows) { "main.exe" } else { "main" });

    fs::write(&src_path, code).unwrap_or_default();

    // 编译
    let compile = run_command("gcc", &[
        src_path.to_str().unwrap(),
        "-o", out_path.to_str().unwrap(),
        "-Wall",
    ]);

    if compile.exit_code != 0 {
        return compile;
    }

    // 运行
    run_command(out_path.to_str().unwrap(), &[])
}

fn execute_cpp(code: &str, work_dir: &Path) -> ExecResult {
    let src_path = work_dir.join("main.cpp");
    let out_path = work_dir.join(if cfg!(windows) { "main.exe" } else { "main" });

    fs::write(&src_path, code).unwrap_or_default();

    let compile = run_command("g++", &[
        src_path.to_str().unwrap(),
        "-o", out_path.to_str().unwrap(),
        "-Wall", "-std=c++17",
    ]);

    if compile.exit_code != 0 {
        return compile;
    }

    run_command(out_path.to_str().unwrap(), &[])
}

fn execute_java(code: &str, work_dir: &Path) -> ExecResult {
    // 提取 public class 名
    let class_name = extract_java_class_name(code).unwrap_or("Main".to_string());
    let src_path = work_dir.join(format!("{}.java", class_name));

    fs::write(&src_path, code).unwrap_or_default();

    // 编译
    let compile = run_command("javac", &[
        src_path.to_str().unwrap(),
    ]);

    if compile.exit_code != 0 {
        return compile;
    }

    // 运行
    run_command("java", &[
        "-cp", work_dir.to_str().unwrap(),
        &class_name,
    ])
}

fn execute_python(code: &str, work_dir: &Path) -> ExecResult {
    let src_path = work_dir.join("main.py");
    fs::write(&src_path, code).unwrap_or_default();

    // 尝试 python3，回退到 python
    let python_cmd = if which_exists("python3") { "python3" } else { "python" };

    run_command(python_cmd, &[src_path.to_str().unwrap()])
}

fn run_command(program: &str, args: &[&str]) -> ExecResult {
    match Command::new(program).args(args).output() {
        Ok(output) => ExecResult {
            stdout: String::from_utf8_lossy(&output.stdout).to_string(),
            stderr: String::from_utf8_lossy(&output.stderr).to_string(),
            exit_code: output.status.code().unwrap_or(1),
        },
        Err(e) => ExecResult {
            stdout: String::new(),
            stderr: format!("无法启动 {}：{}。请确认已安装对应的编译器/解释器。", program, e),
            exit_code: 1,
        },
    }
}

fn which_exists(cmd: &str) -> bool {
    Command::new("which")
        .arg(cmd)
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
}

fn extract_java_class_name(code: &str) -> Option<String> {
    let re = regex::Regex::new(r"public\s+class\s+(\w+)").ok()?;
    re.captures(code).map(|c| c[1].to_string())
}

/// 内存级 Web 打包器 (Zero-Server Bundler)
/// 读取虚拟文件系统中的 index.html，将外链的 CSS/JS 内联替换，
/// 输出一个自包含的 HTML 字符串，可直接注入 iframe 渲染
pub fn bundle_web_project(files: &HashMap<String, String>) -> Result<String, String> {
    let html = files
        .get("index.html")
        .ok_or_else(|| "虚拟项目中缺少 index.html 文件".to_string())?;

    let mut result = html.clone();

    // 替换 <link rel="stylesheet" href="xxx.css"> → <style>内容</style>
    let css_re = Regex::new(
        r#"<link\s+[^>]*rel\s*=\s*["']stylesheet["'][^>]*href\s*=\s*["']([^"']+\.css)["'][^>]*/?\s*>"#
    ).unwrap();
    // 也匹配 href 在 rel 前面的情况
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

    // 替换 <script src="xxx.js"></script> → <script>内容</script>
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

    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bundle_web_project() {
        let mut files = HashMap::new();
        files.insert(
            "index.html".to_string(),
            r#"<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <h1>Test</h1>
    <script src="main.js"></script>
</body>
</html>"#.to_string(),
        );
        files.insert("style.css".to_string(), "h1 { color: red; }".to_string());
        files.insert("main.js".to_string(), "alert('hi');".to_string());

        let bundled = bundle_web_project(&files).unwrap();
        assert!(bundled.contains("<style>"));
        assert!(bundled.contains("h1 { color: red; }"));
        assert!(bundled.contains("alert('hi');"));
        assert!(!bundled.contains(r#"href="style.css""#));
        assert!(!bundled.contains(r#"src="main.js""#));
    }
}
