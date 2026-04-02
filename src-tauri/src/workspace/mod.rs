use std::path::{Path, PathBuf};
use std::fs;
use serde::Serialize;

/// 真实文件系统工作区（非 Web 语言使用）
/// 操作用户选择的真实目录，文件持久化到磁盘

#[derive(Debug, Serialize, Clone)]
pub struct FsEntry {
    pub name: String,
    pub path: String,
    pub is_dir: bool,
    pub children: Vec<FsEntry>,
}

/// 打开文件夹选择对话框
pub fn pick_folder() -> Option<PathBuf> {
    rfd::FileDialog::new()
        .set_title("选择项目文件夹")
        .pick_folder()
}

/// 递归读取目录树
pub fn list_tree(root: &Path) -> Vec<FsEntry> {
    let mut entries = Vec::new();
    if let Ok(dir) = fs::read_dir(root) {
        let mut items: Vec<_> = dir.flatten().collect();
        items.sort_by_key(|e| e.file_name());

        for entry in items {
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().to_string();

            // 跳过隐藏文件和编译产物
            if name.starts_with('.') || name == "__pycache__" || name.ends_with(".class") || name.ends_with(".exe") || name.ends_with(".o") {
                continue;
            }

            if path.is_dir() {
                let children = list_tree(&path);
                entries.push(FsEntry {
                    name,
                    path: path.to_string_lossy().to_string(),
                    is_dir: true,
                    children,
                });
            } else {
                entries.push(FsEntry {
                    name,
                    path: path.to_string_lossy().to_string(),
                    is_dir: false,
                    children: vec![],
                });
            }
        }
    }
    entries
}

/// 读取文件
pub fn read_file(path: &Path) -> Result<String, String> {
    fs::read_to_string(path).map_err(|e| format!("无法读取文件: {}", e))
}

/// 写入文件（自动创建父目录）
pub fn write_file(path: &Path, content: &str) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("无法创建目录: {}", e))?;
    }
    fs::write(path, content).map_err(|e| format!("无法写入文件: {}", e))
}

/// 创建文件
pub fn create_file(dir: &Path, name: &str) -> Result<String, String> {
    let path = dir.join(name);
    if path.exists() {
        return Err(format!("文件 {} 已存在", name));
    }
    write_file(&path, "")?;
    Ok(path.to_string_lossy().to_string())
}

/// 创建目录
pub fn create_dir(parent: &Path, name: &str) -> Result<String, String> {
    let path = parent.join(name);
    if path.exists() {
        return Err(format!("目录 {} 已存在", name));
    }
    fs::create_dir_all(&path).map_err(|e| format!("无法创建目录: {}", e))?;
    Ok(path.to_string_lossy().to_string())
}

/// 删除文件或目录
pub fn delete_entry(path: &Path) -> Result<(), String> {
    if path.is_dir() {
        fs::remove_dir_all(path).map_err(|e| format!("无法删除目录: {}", e))
    } else {
        fs::remove_file(path).map_err(|e| format!("无法删除文件: {}", e))
    }
}

/// 重命名
pub fn rename_entry(old: &Path, new_name: &str) -> Result<String, String> {
    let new_path = old.parent()
        .ok_or("无效路径")?
        .join(new_name);
    fs::rename(old, &new_path).map_err(|e| format!("重命名失败: {}", e))?;
    Ok(new_path.to_string_lossy().to_string())
}
