use super::Trie;

/// 各语言内置关键字 + 常用标准库函数名
/// 系统启动时一次性加载到 Trie 中

pub fn load_c_keywords(trie: &mut Trie) {
    trie.insert_many(&[
        // C 关键字
        "auto", "break", "case", "char", "const", "continue", "default", "do",
        "double", "else", "enum", "extern", "float", "for", "goto", "if",
        "int", "long", "register", "return", "short", "signed", "sizeof",
        "static", "struct", "switch", "typedef", "union", "unsigned", "void",
        "volatile", "while",
        // 常用标准库
        "printf", "scanf", "malloc", "free", "calloc", "realloc",
        "strlen", "strcpy", "strcmp", "strcat", "memcpy", "memset",
        "fopen", "fclose", "fread", "fwrite", "fprintf", "fscanf",
        "include", "define", "ifdef", "ifndef", "endif",
        "NULL", "stdin", "stdout", "stderr",
    ]);
}

pub fn load_cpp_keywords(trie: &mut Trie) {
    // 先加载 C 的关键字（C++ 是超集）
    load_c_keywords(trie);
    trie.insert_many(&[
        // C++ 额外关键字
        "bool", "catch", "class", "const_cast", "delete", "dynamic_cast",
        "explicit", "export", "false", "friend", "inline", "mutable",
        "namespace", "new", "operator", "private", "protected", "public",
        "reinterpret_cast", "static_cast", "template", "this", "throw",
        "true", "try", "typeid", "typename", "using", "virtual", "wchar_t",
        "override", "final", "nullptr", "constexpr", "decltype", "noexcept",
        "static_assert", "thread_local", "alignas", "alignof",
        // 常用 STL
        "vector", "string", "map", "set", "unordered_map", "unordered_set",
        "list", "deque", "queue", "stack", "pair", "tuple",
        "cout", "cin", "endl", "cerr", "getline",
        "push_back", "pop_back", "begin", "end", "size", "empty",
        "sort", "find", "count", "erase", "insert",
        "iostream", "algorithm", "cstring", "cstdio", "cstdlib", "cmath",
    ]);
}

pub fn load_java_keywords(trie: &mut Trie) {
    trie.insert_many(&[
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while",
        "true", "false", "null",
        // 常用类
        "String", "System", "Integer", "Double", "Boolean", "Character",
        "ArrayList", "HashMap", "HashSet", "LinkedList", "Collections",
        "Arrays", "Math", "Scanner", "StringBuilder", "Object",
        "println", "print", "printf", "length", "equals", "toString",
        "compareTo", "indexOf", "substring", "charAt", "toCharArray",
        "parseInt", "valueOf", "format",
    ]);
}

pub fn load_python_keywords(trie: &mut Trie) {
    trie.insert_many(&[
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else",
        "except", "finally", "for", "from", "global", "if", "import",
        "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield",
        // 常用内置函数
        "print", "input", "len", "range", "type", "int", "float", "str",
        "list", "dict", "set", "tuple", "bool", "abs", "max", "min",
        "sum", "sorted", "reversed", "enumerate", "zip", "map", "filter",
        "open", "read", "write", "close", "append", "extend", "insert",
        "remove", "pop", "split", "join", "strip", "replace", "format",
        "upper", "lower", "find", "count", "index", "keys", "values",
        "items", "update", "copy", "clear",
        "__init__", "__str__", "__repr__", "__len__", "__name__", "__main__",
    ]);
}

pub fn load_html_keywords(trie: &mut Trie) {
    trie.insert_many(&[
        "html", "head", "body", "title", "meta", "link", "script", "style",
        "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "tr",
        "td", "th", "thead", "tbody", "form", "input", "button", "select",
        "option", "textarea", "label", "h1", "h2", "h3", "h4", "h5", "h6",
        "header", "footer", "nav", "main", "section", "article", "aside",
        "br", "hr", "pre", "code", "blockquote", "strong", "em",
        "class", "id", "src", "href", "alt", "type", "value", "name",
        "placeholder", "action", "method", "charset", "content", "rel",
        "DOCTYPE",
    ]);
}

pub fn load_css_keywords(trie: &mut Trie) {
    trie.insert_many(&[
        "color", "background", "background-color", "background-image",
        "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
        "padding", "padding-top", "padding-bottom", "padding-left", "padding-right",
        "border", "border-radius", "border-color", "border-width", "border-style",
        "font-size", "font-family", "font-weight", "font-style",
        "text-align", "text-decoration", "text-transform",
        "display", "position", "top", "bottom", "left", "right",
        "width", "height", "max-width", "max-height", "min-width", "min-height",
        "flex", "flex-direction", "justify-content", "align-items", "align-self",
        "grid", "grid-template-columns", "grid-template-rows", "gap",
        "overflow", "opacity", "visibility", "z-index",
        "transition", "transform", "animation",
        "cursor", "box-shadow", "box-sizing",
        "none", "block", "inline", "inline-block", "flex", "grid",
        "relative", "absolute", "fixed", "sticky",
        "center", "bold", "italic", "underline", "pointer",
    ]);
}

pub fn load_javascript_keywords(trie: &mut Trie) {
    trie.insert_many(&[
        "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "false",
        "finally", "for", "function", "if", "import", "in", "instanceof",
        "let", "new", "null", "return", "super", "switch", "this",
        "throw", "true", "try", "typeof", "undefined", "var", "void",
        "while", "with", "yield", "async", "await", "of", "from",
        // 常用 API
        "console", "log", "warn", "error",
        "document", "getElementById", "querySelector", "querySelectorAll",
        "addEventListener", "createElement", "appendChild", "innerHTML",
        "window", "alert", "prompt", "confirm", "setTimeout", "setInterval",
        "clearTimeout", "clearInterval",
        "JSON", "parse", "stringify",
        "Array", "Object", "String", "Number", "Boolean", "Map", "Set",
        "Promise", "then", "catch", "finally", "resolve", "reject",
        "fetch", "Response", "Request",
        "Math", "random", "floor", "ceil", "round", "abs", "max", "min",
        "push", "pop", "shift", "unshift", "splice", "slice", "concat",
        "map", "filter", "reduce", "forEach", "find", "some", "every",
        "includes", "indexOf", "join", "split", "replace", "trim",
        "length", "toString", "valueOf",
    ]);
}
