use super::FriendlyError;
use regex::Regex;

/// 将编译器/解释器的 stderr 输出翻译成友好的中文提示
/// 支持: GCC/G++ (C/C++), javac (Java), Python 解释器
pub fn translate_error(stderr: &str, language: &str) -> Vec<FriendlyError> {
    let mut errors = Vec::new();

    for line in stderr.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }

        let friendly = match language {
            "c" | "cpp" => translate_gcc(trimmed),
            "java" => translate_javac(trimmed),
            "python" => translate_python(trimmed),
            _ => None,
        };

        if let Some(err) = friendly {
            errors.push(err);
        }
    }

    // 如果没有匹配到任何规则，把整段 stderr 作为一条通用错误返回
    if errors.is_empty() && !stderr.trim().is_empty() {
        // 尝试多行 Python 错误解析
        if language == "python" {
            if let Some(err) = translate_python_multiline(stderr) {
                errors.push(err);
                return errors;
            }
        }
        errors.push(FriendlyError {
            raw: stderr.to_string(),
            message: format!("程序运行时遇到了问题，以下是详细信息：\n{}", stderr.trim()),
            line: None,
            column: None,
            level: "error".to_string(),
        });
    }

    errors
}

/// GCC / G++ 错误翻译
fn translate_gcc(line: &str) -> Option<FriendlyError> {
    // 格式: file.c:10:5: error: expected ';' before 'return'
    let re = Regex::new(
        r"(?P<file>[^:]+):(?P<line>\d+):(?P<col>\d+):\s*(?P<level>error|warning):\s*(?P<msg>.*)"
    ).ok()?;

    let caps = re.captures(line)?;
    let line_num: u32 = caps["line"].parse().ok()?;
    let col_num: u32 = caps["col"].parse().ok()?;
    let level = caps["level"].to_string();
    let msg = &caps["msg"];

    let friendly_msg = translate_gcc_message(msg, line_num);

    Some(FriendlyError {
        raw: line.to_string(),
        message: friendly_msg,
        line: Some(line_num),
        column: Some(col_num),
        level,
    })
}

fn translate_gcc_message(msg: &str, line: u32) -> String {
    // 缺少分号
    if msg.contains("expected ';'") || msg.contains("expected ';'") {
        return format!(
            "💡 语法小贴士：在第 {} 行，你似乎忘记写分号 ';' 啦。C/C++ 中每条语句结尾都需要一个分号。",
            line
        );
    }

    // 未声明的变量
    if msg.contains("undeclared") || msg.contains("was not declared in this scope") {
        let var_name = extract_quoted(msg).unwrap_or("某个变量".to_string());
        return format!(
            "💡 第 {} 行：编译器不认识 '{}'。你是不是忘记声明它了？检查一下变量名有没有拼错，或者是不是忘记写 int/char 等类型声明了。",
            line, var_name
        );
    }

    // 类型不匹配
    if msg.contains("incompatible type") || msg.contains("cannot convert") {
        return format!(
            "💡 第 {} 行：数据类型不匹配。比如你可能把一个小数赋给了整数变量，或者把文字赋给了数字变量。检查一下等号两边的类型是否一致。",
            line
        );
    }

    // 缺少括号
    if msg.contains("expected ')'") || msg.contains("expected '}'") || msg.contains("expected '{'") {
        let bracket = if msg.contains(')') { ")" } else if msg.contains('}') { "}" } else { "{" };
        return format!(
            "💡 第 {} 行：似乎少了一个 '{}' 括号。检查一下括号是不是成对出现的。",
            line, bracket
        );
    }

    // 函数未定义
    if msg.contains("undefined reference to") {
        let func_name = extract_quoted(msg).unwrap_or("某个函数".to_string());
        return format!(
            "💡 第 {} 行：找不到函数 '{}'。你是不是忘记定义这个函数了，或者函数名拼错了？",
            line, func_name
        );
    }

    // 头文件找不到
    if msg.contains("No such file or directory") && msg.contains(".h") {
        return format!(
            "💡 第 {} 行：找不到你引用的头文件。检查 #include 后面的文件名是否拼写正确。",
            line
        );
    }

    // 通用翻译
    format!("💡 第 {} 行有个问题：{}", line, msg)
}

/// javac 错误翻译
fn translate_javac(line: &str) -> Option<FriendlyError> {
    // 格式: Main.java:5: error: ';' expected
    let re = Regex::new(
        r"(?P<file>[^:]+\.java):(?P<line>\d+):\s*(?P<level>error|warning):\s*(?P<msg>.*)"
    ).ok()?;

    let caps = re.captures(line)?;
    let line_num: u32 = caps["line"].parse().ok()?;
    let level = caps["level"].to_string();
    let msg = &caps["msg"];

    let friendly_msg = translate_javac_message(msg, line_num);

    Some(FriendlyError {
        raw: line.to_string(),
        message: friendly_msg,
        line: Some(line_num),
        column: None,
        level,
    })
}

fn translate_javac_message(msg: &str, line: u32) -> String {
    if msg.contains("';' expected") {
        return format!(
            "💡 第 {} 行：你忘记写分号 ';' 了。Java 中每条语句结尾都需要分号哦。",
            line
        );
    }

    if msg.contains("cannot find symbol") {
        return format!(
            "💡 第 {} 行：Java 编译器找不到你用的某个名字。检查一下变量名或方法名是不是拼错了，或者忘记声明了。",
            line
        );
    }

    if msg.contains("class, interface, or enum expected") {
        return format!(
            "💡 第 {} 行：Java 要求所有代码都写在 class 里面。检查一下你的大括号 {{}} 是不是对齐的。",
            line
        );
    }

    if msg.contains("incompatible types") {
        return format!(
            "💡 第 {} 行：类型不匹配。例如你不能把 String 直接赋给 int。检查一下数据类型。",
            line
        );
    }

    if msg.contains("reached end of file while parsing") {
        return format!(
            "💡 第 {} 行附近：代码似乎少了一个右大括号 '}}' 。数一数你的 {{ 和 }} 是不是一样多。",
            line
        );
    }

    format!("💡 第 {} 行有个问题：{}", line, msg)
}

/// Python 单行错误翻译
fn translate_python(line: &str) -> Option<FriendlyError> {
    // 格式: File "test.py", line 5
    // 或: SyntaxError: invalid syntax
    // 或: NameError: name 'x' is not defined

    // 尝试匹配 Python 错误类型行
    let re = Regex::new(
        r"(?P<errtype>\w+Error):\s*(?P<msg>.*)"
    ).ok()?;

    if let Some(caps) = re.captures(line) {
        let err_type = &caps["errtype"];
        let msg = &caps["msg"];

        let friendly_msg = translate_python_error(err_type, msg);
        return Some(FriendlyError {
            raw: line.to_string(),
            message: friendly_msg,
            line: None,
            column: None,
            level: "error".to_string(),
        });
    }

    None
}

/// Python 多行错误解析（用于 Traceback 格式）
fn translate_python_multiline(stderr: &str) -> Option<FriendlyError> {
    // 提取行号: File "xxx.py", line N
    let line_re = Regex::new(r#"File ".*?", line (\d+)"#).ok()?;
    let line_num: u32 = line_re
        .captures_iter(stderr)
        .last()? // 取最后一个匹配（最内层调用）
        .get(1)?
        .as_str()
        .parse()
        .ok()?;

    // 提取错误类型
    let err_re = Regex::new(r"(?P<errtype>\w+Error):\s*(?P<msg>.*)").ok()?;
    let caps = err_re.captures(stderr)?;
    let err_type = &caps["errtype"];
    let msg = &caps["msg"];

    let friendly_msg = format!(
        "在第 {} 行：{}",
        line_num,
        translate_python_error(err_type, msg)
    );

    Some(FriendlyError {
        raw: stderr.to_string(),
        message: friendly_msg,
        line: Some(line_num),
        column: None,
        level: "error".to_string(),
    })
}

fn translate_python_error(err_type: &str, msg: &str) -> String {
    match err_type {
        "SyntaxError" => {
            if msg.contains("invalid syntax") {
                "💡 语法错误：Python 看不懂这行代码。常见原因：少写了冒号 ':'（比如 if/for/def 后面）、括号没配对、或者用了中文标点。".to_string()
            } else if msg.contains("EOL while scanning string") {
                "💡 语法错误：字符串没有正确关闭。检查一下引号是不是成对出现的（单引号 '' 或双引号 \"\"）。".to_string()
            } else if msg.contains("unexpected EOF") {
                "💡 语法错误：代码似乎还没写完。检查一下括号、引号是不是都配对了。".to_string()
            } else {
                format!("💡 语法错误：{}", msg)
            }
        }
        "IndentationError" => {
            if msg.contains("unexpected indent") {
                "💡 缩进错误：这行代码的缩进（前面的空格）多了。Python 用缩进来表示代码块，确保同一层级的代码对齐。建议统一用 4 个空格缩进。".to_string()
            } else if msg.contains("expected an indented block") {
                "💡 缩进错误：这里需要缩进。在 if/for/def/class 等语句的冒号 ':' 后面，下一行代码需要缩进（加 4 个空格）。".to_string()
            } else {
                "💡 缩进错误：代码的缩进不对。Python 对缩进非常严格，同一层级的代码必须对齐。建议统一用 4 个空格。".to_string()
            }
        }
        "NameError" => {
            let var_name = extract_quoted(msg).unwrap_or("某个变量".to_string());
            format!(
                "💡 名称错误：Python 不认识 '{}'。你是不是拼错了名字，或者忘记先定义/赋值了？",
                var_name
            )
        }
        "TypeError" => {
            if msg.contains("unsupported operand") {
                "💡 类型错误：你尝试对不兼容的类型做运算。比如不能直接把数字和文字加在一起。试试用 str() 或 int() 转换类型。".to_string()
            } else if msg.contains("argument") {
                format!("💡 类型错误：函数调用参数有问题。{}", msg)
            } else {
                format!("💡 类型错误：{}", msg)
            }
        }
        "ValueError" => {
            format!("💡 值错误：数据的值不对。例如你可能试图把无法转换的内容转成数字。详情：{}", msg)
        }
        "IndexError" => {
            "💡 索引越界：你访问的位置超出了列表的范围。比如列表只有 3 个元素（索引 0-2），但你访问了索引 3。".to_string()
        }
        "KeyError" => {
            let key = extract_quoted(msg).unwrap_or("某个键".to_string());
            format!("💡 键错误：字典里找不到键 '{}'。检查一下键名是否正确，或者用 .get() 方法来安全访问。", key)
        }
        "AttributeError" => {
            format!("💡 属性错误：对象没有你要访问的属性或方法。{}", msg)
        }
        "ZeroDivisionError" => {
            "💡 除零错误：不能除以零！检查一下你的除法运算，除数是不是可能为 0。".to_string()
        }
        "FileNotFoundError" => {
            "💡 文件未找到：你要打开的文件不存在。检查一下文件路径和文件名是否正确。".to_string()
        }
        "ImportError" | "ModuleNotFoundError" => {
            format!("💡 导入错误：找不到你要导入的模块。检查模块名是否拼写正确。{}", msg)
        }
        _ => {
            format!("💡 {}：{}", err_type, msg)
        }
    }
}

/// 提取引号中的内容（单引号或双引号）
fn extract_quoted(s: &str) -> Option<String> {
    let re = Regex::new(r#"['"]([^'"]+)['"]"#).ok()?;
    re.captures(s).map(|c| c[1].to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_gcc_semicolon() {
        let errors = translate_error("main.c:10:5: error: expected ';' before 'return'", "c");
        assert_eq!(errors.len(), 1);
        assert!(errors[0].message.contains("分号"));
        assert_eq!(errors[0].line, Some(10));
    }

    #[test]
    fn test_python_indentation() {
        let stderr = r#"  File "test.py", line 3
    print("hello")
    ^
IndentationError: unexpected indent"#;
        let errors = translate_error(stderr, "python");
        assert!(!errors.is_empty());
        assert!(errors.last().unwrap().message.contains("缩进"));
    }

    #[test]
    fn test_python_name_error() {
        let stderr = r#"Traceback (most recent call last):
  File "test.py", line 1, in <module>
    print(x)
NameError: name 'x' is not defined"#;
        let errors = translate_error(stderr, "python");
        assert!(!errors.is_empty());
        let msg = &errors.last().unwrap().message;
        assert!(msg.contains("x") || msg.contains("不认识"));
    }

    #[test]
    fn test_javac_semicolon() {
        let errors = translate_error("Main.java:5: error: ';' expected", "java");
        assert_eq!(errors.len(), 1);
        assert!(errors[0].message.contains("分号"));
    }
}
