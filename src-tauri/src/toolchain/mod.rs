use std::path::{Path, PathBuf};
use std::fs;

/// 便携工具链管理器
///
/// 工作原理：
///   打包模式：Tauri 将 `toolchains/` 目录作为 resource 打包进 exe，
///            运行时释放到 `<app_data>/ZenCode/toolchains/` 下，
///            executor 使用释放后的绝对路径调用编译器。
///   开发模式：如果便携工具链目录不存在，回退到系统 PATH 中的命令。
///
/// 目录结构约定 (toolchains/):
///   toolchains/
///   ├── mingw64/              ← w64devkit 解压后
///   │   └── w64devkit/bin/    ← gcc.exe, g++.exe 在这层
///   │       或 bin/           ← 也兼容直接放 bin/ 的情况
///   ├── python/               ← Python Embeddable (扁平结构)
///   │   └── python.exe        ← 直接在 python/ 根目录
///   └── jre/                  ← jlink 精简 JRE + javac
///       └── bin/
///           ├── java.exe (或 java)
///           └── javac.exe (或 javac)

pub struct ToolchainManager {
    /// 便携工具链根目录
    base_dir: PathBuf,
    /// 是否已就绪（便携工具链目录存在且包含内容）
    portable_available: bool,
}

impl ToolchainManager {
    /// 初始化工具链管理器
    /// `resource_dir`: Tauri resource 目录（打包后的资源路径）
    /// `app_data_dir`: 应用数据目录（工具链释放的目标位置）
    pub fn init(resource_dir: Option<&Path>, app_data_dir: &Path) -> Self {
        let toolchain_dest = app_data_dir.join("toolchains");

        // 尝试从 Tauri resource 目录释放工具链到 app_data
        if let Some(res_dir) = resource_dir {
            let toolchain_src = res_dir.join("toolchains");
            if toolchain_src.exists() && !toolchain_dest.exists() {
                let _ = copy_dir_recursive(&toolchain_src, &toolchain_dest);
                // 给释放出的二进制文件加执行权限 (Unix)
                #[cfg(unix)]
                set_executable_permissions(&toolchain_dest);
            }
        }

        let portable_available = toolchain_dest.exists()
            && fs::read_dir(&toolchain_dest)
                .map(|mut d| d.next().is_some())
                .unwrap_or(false);

        ToolchainManager {
            base_dir: toolchain_dest,
            portable_available,
        }
    }

    /// 获取 C 编译器路径
    /// 优先便携 GCC，回退到系统 gcc，macOS/Linux 再回退到 cc (clang)
    pub fn gcc(&self) -> String {
        if let Some(path) = self.find_portable("mingw64", "gcc") {
            return path.to_string_lossy().to_string();
        }
        if which_exists("gcc") {
            return "gcc".to_string();
        }
        #[cfg(not(windows))]
        if which_exists("cc") {
            return "cc".to_string();
        }
        "gcc".to_string()
    }

    /// 获取 C++ 编译器路径
    /// 优先便携 G++，回退到系统 g++，macOS/Linux 再回退到 c++ (clang++)
    pub fn gpp(&self) -> String {
        if let Some(path) = self.find_portable("mingw64", "g++") {
            return path.to_string_lossy().to_string();
        }
        if which_exists("g++") {
            return "g++".to_string();
        }
        #[cfg(not(windows))]
        if which_exists("c++") {
            return "c++".to_string();
        }
        "g++".to_string()
    }

    /// 获取 Python 解释器路径
    pub fn python(&self) -> String {
        // 便携版: 搜索 python 和 python3
        if let Some(path) = self.find_portable("python", "python") {
            return path.to_string_lossy().to_string();
        }
        if let Some(path) = self.find_portable("python", "python3") {
            return path.to_string_lossy().to_string();
        }
        // 回退到系统命令
        if which_exists("python3") {
            "python3".to_string()
        } else {
            "python".to_string()
        }
    }

    /// 获取 javac 编译器路径
    pub fn javac(&self) -> String {
        self.resolve("jre", "javac")
    }

    /// 获取 java 运行时路径
    pub fn java(&self) -> String {
        self.resolve("jre", "java")
    }

    /// 工具链是否使用便携版
    pub fn is_portable(&self) -> bool {
        self.portable_available
    }

    /// 获取工具链状态信息（供前端显示）
    pub fn status(&self) -> Vec<ToolchainStatus> {
        vec![
            ToolchainStatus {
                name: "C/C++ (GCC)".to_string(),
                path: self.gcc(),
                available: path_or_which_exists(&self.gcc()),
                portable: self.is_portable_tool("mingw64", "gcc"),
            },
            ToolchainStatus {
                name: "Python".to_string(),
                path: self.python(),
                available: path_or_which_exists(&self.python()),
                portable: self.is_portable_tool("python", "python"),
            },
            ToolchainStatus {
                name: "Java (JDK)".to_string(),
                path: self.javac(),
                available: path_or_which_exists(&self.javac()),
                portable: self.is_portable_tool("jre", "javac"),
            },
        ]
    }

    // ---- 内部方法 ----

    /// 获取可执行文件名（Windows 加 .exe 后缀）
    fn exe_name(cmd: &str) -> String {
        if cfg!(windows) {
            format!("{}.exe", cmd)
        } else {
            cmd.to_string()
        }
    }

    /// 在 subdir 下搜索可执行文件，兼容多种解压目录结构
    /// 搜索顺序:
    ///   base_dir/<subdir>/bin/<cmd>          ← 标准布局
    ///   base_dir/<subdir>/<cmd>              ← 扁平布局 (Python Embeddable)
    ///   base_dir/<subdir>/<any>/bin/<cmd>    ← 嵌套布局 (w64devkit 解压后多一层目录)
    fn find_portable(&self, subdir: &str, cmd: &str) -> Option<PathBuf> {
        if !self.portable_available {
            return None;
        }
        let exe = Self::exe_name(cmd);
        let subdir_path = self.base_dir.join(subdir);

        // 1) 标准: subdir/bin/cmd
        let standard = subdir_path.join("bin").join(&exe);
        if standard.exists() {
            return Some(standard);
        }

        // 2) 扁平: subdir/cmd (Python Embeddable)
        let flat = subdir_path.join(&exe);
        if flat.exists() {
            return Some(flat);
        }

        // 3) 嵌套: subdir/*/bin/cmd (w64devkit → mingw64/w64devkit/bin/gcc.exe)
        if let Ok(entries) = fs::read_dir(&subdir_path) {
            for entry in entries.flatten() {
                if entry.path().is_dir() {
                    let nested = entry.path().join("bin").join(&exe);
                    if nested.exists() {
                        return Some(nested);
                    }
                }
            }
        }

        None
    }

    /// 解析命令路径：便携版优先，回退到系统命令
    fn resolve(&self, subdir: &str, cmd: &str) -> String {
        if let Some(path) = self.find_portable(subdir, cmd) {
            return path.to_string_lossy().to_string();
        }
        cmd.to_string()
    }

    fn is_portable_tool(&self, subdir: &str, cmd: &str) -> bool {
        self.find_portable(subdir, cmd).is_some()
    }
}

#[derive(Debug, serde::Serialize)]
pub struct ToolchainStatus {
    pub name: String,
    pub path: String,
    pub available: bool,
    pub portable: bool,
}

/// 递归复制目录
fn copy_dir_recursive(src: &Path, dst: &Path) -> std::io::Result<()> {
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let src_path = entry.path();
        let dst_path = dst.join(entry.file_name());
        if src_path.is_dir() {
            copy_dir_recursive(&src_path, &dst_path)?;
        } else {
            fs::copy(&src_path, &dst_path)?;
        }
    }
    Ok(())
}

/// Unix 下递归设置可执行权限
#[cfg(unix)]
fn set_executable_permissions(dir: &Path) {
    use std::os::unix::fs::PermissionsExt;
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                set_executable_permissions(&path);
            } else if path.join("").to_string_lossy().contains("/bin/") {
                if let Ok(metadata) = fs::metadata(&path) {
                    let mut perms = metadata.permissions();
                    perms.set_mode(0o755);
                    let _ = fs::set_permissions(&path, perms);
                }
            }
        }
    }
}

fn which_exists(cmd: &str) -> bool {
    std::process::Command::new(if cfg!(windows) { "where" } else { "which" })
        .arg(cmd)
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
}

fn path_or_which_exists(cmd: &str) -> bool {
    // 绝对路径 → 直接检查文件存在
    let p = Path::new(cmd);
    if p.is_absolute() {
        return p.exists();
    }
    // 相对命令名 → which/where 查找
    which_exists(cmd)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::env;

    #[test]
    fn test_fallback_to_system() {
        let tmp = env::temp_dir().join("zencode_test_toolchain");
        let mgr = ToolchainManager::init(None, &tmp);
        // 没有便携工具链，应该回退到系统命令
        assert!(!mgr.is_portable());
        // C/C++ 编译器回退到 gcc/cc 或 g++/c++ 取决于系统安装情况
        let gcc = mgr.gcc();
        assert!(gcc == "gcc" || gcc == "cc", "gcc fallback should be gcc or cc, got: {}", gcc);
        let gpp = mgr.gpp();
        assert!(gpp == "g++" || gpp == "c++", "g++ fallback should be g++ or c++, got: {}", gpp);
        assert_eq!(mgr.javac(), "javac");
        assert_eq!(mgr.java(), "java");
        // 清理
        let _ = fs::remove_dir_all(&tmp);
    }

    #[test]
    fn test_status_report() {
        let tmp = env::temp_dir().join("zencode_test_status");
        let mgr = ToolchainManager::init(None, &tmp);
        let status = mgr.status();
        assert_eq!(status.len(), 3);
        assert_eq!(status[0].name, "C/C++ (GCC)");
        let _ = fs::remove_dir_all(&tmp);
    }
}
