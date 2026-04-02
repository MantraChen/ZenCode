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
///   ├── mingw64/          ← MinGW-w64 精简版
///   │   └── bin/
///   │       ├── gcc.exe
///   │       └── g++.exe
///   ├── python/           ← Python Embeddable
///   │   └── python.exe (或 python3)
///   └── jre/              ← jlink 精简 JRE + javac
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
        let primary = self.resolve("mingw64", "gcc");
        if primary != "gcc" {
            return primary; // 便携版找到了
        }
        // 系统 gcc 存在就用
        if which_exists("gcc") {
            return "gcc".to_string();
        }
        // macOS/Linux 回退到 cc (通常是 clang)
        #[cfg(not(windows))]
        if which_exists("cc") {
            return "cc".to_string();
        }
        "gcc".to_string() // 最终回退，让错误信息显示 gcc
    }

    /// 获取 C++ 编译器路径
    /// 优先便携 G++，回退到系统 g++，macOS/Linux 再回退到 c++ (clang++)
    pub fn gpp(&self) -> String {
        let primary = self.resolve("mingw64", "g++");
        if primary != "g++" {
            return primary;
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
        // 便携版
        if self.portable_available {
            let portable = self.bin_path("python", "python");
            if portable.exists() {
                return portable.to_string_lossy().to_string();
            }
            let portable3 = self.bin_path("python", "python3");
            if portable3.exists() {
                return portable3.to_string_lossy().to_string();
            }
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

    /// 拼接 bin 路径: base_dir/<subdir>/bin/<cmd>[.exe]
    fn bin_path(&self, subdir: &str, cmd: &str) -> PathBuf {
        let bin_dir = self.base_dir.join(subdir).join("bin");
        if cfg!(windows) {
            bin_dir.join(format!("{}.exe", cmd))
        } else {
            bin_dir.join(cmd)
        }
    }

    /// 解析命令路径：便携版优先，回退到系统命令
    fn resolve(&self, subdir: &str, cmd: &str) -> String {
        if self.portable_available {
            let portable = self.bin_path(subdir, cmd);
            if portable.exists() {
                return portable.to_string_lossy().to_string();
            }
        }
        cmd.to_string()
    }

    fn is_portable_tool(&self, subdir: &str, cmd: &str) -> bool {
        if !self.portable_available {
            return false;
        }
        self.bin_path(subdir, cmd).exists()
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
