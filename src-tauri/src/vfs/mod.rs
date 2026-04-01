use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// 虚拟文件系统 —— 内存级扁平文件树
/// 不暴露宿主机真实路径，所有文件存储在内存 HashMap 中
#[derive(Debug, Default)]
pub struct VirtualFS {
    /// 文件名 -> 文件内容
    files: HashMap<String, String>,
    /// 当前打开的文件名
    active_file: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct VfsFileInfo {
    pub name: String,
    pub extension: String,
}

impl VirtualFS {
    pub fn new() -> Self {
        VirtualFS {
            files: HashMap::new(),
            active_file: None,
        }
    }

    /// 创建/覆盖文件
    pub fn write_file(&mut self, name: &str, content: &str) {
        self.files.insert(name.to_string(), content.to_string());
    }

    /// 读取文件内容
    pub fn read_file(&self, name: &str) -> Option<&str> {
        self.files.get(name).map(|s| s.as_str())
    }

    /// 删除文件
    pub fn delete_file(&mut self, name: &str) -> bool {
        if self.active_file.as_deref() == Some(name) {
            self.active_file = None;
        }
        self.files.remove(name).is_some()
    }

    /// 重命名文件
    pub fn rename_file(&mut self, old_name: &str, new_name: &str) -> bool {
        if let Some(content) = self.files.remove(old_name) {
            if self.active_file.as_deref() == Some(old_name) {
                self.active_file = Some(new_name.to_string());
            }
            self.files.insert(new_name.to_string(), content);
            true
        } else {
            false
        }
    }

    /// 获取文件列表（按名称排序）
    pub fn list_files(&self) -> Vec<VfsFileInfo> {
        let mut files: Vec<VfsFileInfo> = self
            .files
            .keys()
            .map(|name| {
                let ext = name
                    .rsplit('.')
                    .next()
                    .unwrap_or("")
                    .to_string();
                VfsFileInfo {
                    name: name.clone(),
                    extension: ext,
                }
            })
            .collect();
        files.sort_by(|a, b| a.name.cmp(&b.name));
        files
    }

    /// 设置当前活动文件
    pub fn set_active(&mut self, name: &str) {
        if self.files.contains_key(name) {
            self.active_file = Some(name.to_string());
        }
    }

    /// 获取当前活动文件名
    pub fn active(&self) -> Option<&str> {
        self.active_file.as_deref()
    }

    /// 检查文件是否存在
    pub fn exists(&self, name: &str) -> bool {
        self.files.contains_key(name)
    }

    /// 获取所有文件（用于 Web 打包器）
    pub fn all_files(&self) -> &HashMap<String, String> {
        &self.files
    }

    /// 一键生成 Web 项目模板
    pub fn create_web_project(&mut self) {
        self.write_file(
            "index.html",
            r#"<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>My Web Project</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <h1>Hello, World!</h1>
    <p id="demo"></p>
    <script src="main.js"></script>
</body>
</html>"#,
        );

        self.write_file(
            "style.css",
            r#"* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: sans-serif;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    background: #f5f5f5;
    color: #333;
}

h1 {
    color: #2196F3;
    margin-bottom: 16px;
}"#,
        );

        self.write_file(
            "main.js",
            r#"document.getElementById("demo").textContent = "JavaScript is working!";"#,
        );

        self.active_file = Some("index.html".to_string());
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_web_project_creation() {
        let mut vfs = VirtualFS::new();
        vfs.create_web_project();
        assert_eq!(vfs.list_files().len(), 3);
        assert!(vfs.exists("index.html"));
        assert!(vfs.exists("style.css"));
        assert!(vfs.exists("main.js"));
        assert_eq!(vfs.active(), Some("index.html"));
    }

    #[test]
    fn test_crud() {
        let mut vfs = VirtualFS::new();
        vfs.write_file("test.txt", "hello");
        assert_eq!(vfs.read_file("test.txt"), Some("hello"));
        vfs.rename_file("test.txt", "renamed.txt");
        assert!(!vfs.exists("test.txt"));
        assert!(vfs.exists("renamed.txt"));
        vfs.delete_file("renamed.txt");
        assert!(!vfs.exists("renamed.txt"));
    }
}
