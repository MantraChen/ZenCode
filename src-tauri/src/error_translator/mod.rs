use serde::Serialize;

mod rules;
pub use rules::translate_error;

/// 翻译后的友好错误信息
#[derive(Debug, Serialize, Clone)]
pub struct FriendlyError {
    /// 原始错误文本
    pub raw: String,
    /// 翻译后的友好提示
    pub message: String,
    /// 错误所在行号（如果能解析到）
    pub line: Option<u32>,
    /// 错误所在列号
    pub column: Option<u32>,
    /// 错误级别: error / warning
    pub level: String,
}
