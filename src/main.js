const { invoke } = window.__TAURI__.core;

// ============ DOM 引用 ============
const editor = document.getElementById("editor");
const langSelect = document.getElementById("language-select");
const runBtn = document.getElementById("run-btn");
const outputContent = document.getElementById("output-content");
const clearOutputBtn = document.getElementById("clear-output");
const autocompletePopup = document.getElementById("autocomplete-popup");
const webPreview = document.getElementById("web-preview");

// ============ 状态 ============
let selectedIndex = -1; // 补全列表选中索引
let completionItems = []; // 当前补全项列表
let debounceTimer = null;

// ============ 默认代码模板 ============
const templates = {
  c: '#include <stdio.h>\n\nint main() {\n    printf("Hello, World!\\n");\n    return 0;\n}',
  cpp: '#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, World!" << endl;\n    return 0;\n}',
  java: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, World!");\n    }\n}',
  python: 'print("Hello, World!")',
  html: '<!DOCTYPE html>\n<html>\n<head>\n    <title>My Page</title>\n</head>\n<body>\n    <h1>Hello, World!</h1>\n</body>\n</html>',
  css: 'body {\n    background-color: #f0f0f0;\n    font-family: sans-serif;\n    text-align: center;\n    padding: 50px;\n}\n\nh1 {\n    color: #333;\n}',
  javascript: 'console.log("Hello, World!");',
};

// ============ 初始化 ============
window.addEventListener("DOMContentLoaded", () => {
  editor.value = templates[langSelect.value] || "";
  editor.focus();
});

// ============ 语言切换 ============
langSelect.addEventListener("change", () => {
  const lang = langSelect.value;
  editor.value = templates[lang] || "";
  hideAutocomplete();
  outputContent.innerHTML = "";

  // Web 前端语言切换预览显示
  const isWeb = ["html", "css", "javascript"].includes(lang);
  if (!isWeb) {
    webPreview.classList.add("hidden");
  }
});

// ============ 运行代码 ============
runBtn.addEventListener("click", runCode);

document.addEventListener("keydown", (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
    e.preventDefault();
    runCode();
  }
});

async function runCode() {
  const code = editor.value;
  const language = langSelect.value;

  if (!code.trim()) {
    outputContent.innerHTML = '<span class="output-error">请先写一些代码再运行。</span>';
    return;
  }

  // Web 前端语言：直接在 iframe 中渲染
  if (language === "html" || language === "css" || language === "javascript") {
    runWebCode(code, language);
    return;
  }

  runBtn.disabled = true;
  runBtn.textContent = "运行中...";
  outputContent.innerHTML = '<span class="output-stdout">正在编译运行...</span>';

  try {
    const result = await invoke("run_code", { code, language });

    outputContent.innerHTML = "";

    // 显示友好错误提示
    if (result.errors && result.errors.length > 0) {
      for (const err of result.errors) {
        const span = document.createElement("span");
        span.className = "output-friendly";
        span.textContent = err.message;
        outputContent.appendChild(span);
      }
    }

    // 显示标准输出
    if (result.stdout) {
      const span = document.createElement("span");
      span.className = result.exitCode === 0 ? "output-stdout" : "output-error";
      span.textContent = result.stdout;
      outputContent.appendChild(span);
    }

    // 运行成功标记
    if (result.exitCode === 0 && !result.errors.length) {
      const span = document.createElement("span");
      span.className = "output-success";
      span.textContent = "\n--- 程序运行完毕 ---";
      outputContent.appendChild(span);
    }
  } catch (err) {
    outputContent.innerHTML = `<span class="output-error">调用失败: ${err}</span>`;
  } finally {
    runBtn.disabled = false;
    runBtn.textContent = "\u25B6 运行";
  }
}

function runWebCode(code, language) {
  let htmlContent = "";

  if (language === "html") {
    htmlContent = code;
  } else if (language === "css") {
    htmlContent = `<!DOCTYPE html><html><head><style>${code}</style></head><body><h1>CSS 预览</h1><p>这是一段示例文字。</p><button>按钮</button></body></html>`;
  } else if (language === "javascript") {
    htmlContent = `<!DOCTYPE html><html><head></head><body><pre id="out"></pre><script>
      const _log = console.log;
      console.log = function(...args) {
        document.getElementById('out').textContent += args.join(' ') + '\\n';
        _log.apply(console, args);
      };
      try { ${code} } catch(e) { console.log('错误: ' + e.message); }
    <\/script></body></html>`;
  }

  webPreview.classList.remove("hidden");
  webPreview.srcdoc = htmlContent;

  outputContent.innerHTML = '<span class="output-success">已在右侧预览面板中渲染。</span>';
}

// ============ 清空输出 ============
clearOutputBtn.addEventListener("click", () => {
  outputContent.innerHTML = "";
  webPreview.classList.add("hidden");
  webPreview.srcdoc = "";
});

// ============ 自动补全 ============
editor.addEventListener("input", () => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(handleAutocomplete, 50);
});

editor.addEventListener("keydown", (e) => {
  if (autocompletePopup.classList.contains("hidden")) return;

  if (e.key === "ArrowDown") {
    e.preventDefault();
    selectedIndex = Math.min(selectedIndex + 1, completionItems.length - 1);
    updateSelection();
  } else if (e.key === "ArrowUp") {
    e.preventDefault();
    selectedIndex = Math.max(selectedIndex - 1, 0);
    updateSelection();
  } else if (e.key === "Enter" || e.key === "Tab") {
    if (selectedIndex >= 0 && selectedIndex < completionItems.length) {
      e.preventDefault();
      applyCompletion(completionItems[selectedIndex]);
    }
  } else if (e.key === "Escape") {
    hideAutocomplete();
  }
});

editor.addEventListener("blur", () => {
  setTimeout(hideAutocomplete, 150);
});

// Tab 键缩进支持
editor.addEventListener("keydown", (e) => {
  if (e.key === "Tab" && autocompletePopup.classList.contains("hidden")) {
    e.preventDefault();
    const start = editor.selectionStart;
    const end = editor.selectionEnd;
    editor.value = editor.value.substring(0, start) + "    " + editor.value.substring(end);
    editor.selectionStart = editor.selectionEnd = start + 4;
  }
});

async function handleAutocomplete() {
  const cursorPos = editor.selectionStart;
  const textBefore = editor.value.substring(0, cursorPos);

  // 提取光标前的当前单词（前缀）
  const match = textBefore.match(/[a-zA-Z_][\w-]*$/);
  if (!match || match[0].length < 1) {
    hideAutocomplete();
    return;
  }

  const prefix = match[0];
  const language = langSelect.value;

  try {
    const results = await invoke("autocomplete", { prefix, language });

    if (results.length === 0 || (results.length === 1 && results[0] === prefix)) {
      hideAutocomplete();
      return;
    }

    showAutocomplete(results, prefix);
  } catch {
    hideAutocomplete();
  }
}

function showAutocomplete(items, prefix) {
  completionItems = items;
  selectedIndex = 0;

  autocompletePopup.innerHTML = "";
  for (let i = 0; i < items.length && i < 10; i++) {
    const div = document.createElement("div");
    div.className = "autocomplete-item" + (i === 0 ? " selected" : "");
    div.textContent = items[i];
    div.addEventListener("mousedown", (e) => {
      e.preventDefault();
      applyCompletion(items[i]);
    });
    autocompletePopup.appendChild(div);
  }

  // 定位弹出框（粗略计算）
  const rect = editor.getBoundingClientRect();
  const lineHeight = 24;
  const lines = editor.value.substring(0, editor.selectionStart).split("\n");
  const currentLine = lines.length;
  const currentCol = lines[lines.length - 1].length;

  const charWidth = 9;
  const top = rect.top + currentLine * lineHeight - editor.scrollTop + 4;
  const left = rect.left + (currentCol - prefix.length) * charWidth + 16;

  autocompletePopup.style.top = Math.min(top, window.innerHeight - 220) + "px";
  autocompletePopup.style.left = Math.min(left, window.innerWidth - 200) + "px";
  autocompletePopup.classList.remove("hidden");
}

function hideAutocomplete() {
  autocompletePopup.classList.add("hidden");
  completionItems = [];
  selectedIndex = -1;
}

function updateSelection() {
  const items = autocompletePopup.querySelectorAll(".autocomplete-item");
  items.forEach((item, i) => {
    item.classList.toggle("selected", i === selectedIndex);
  });
  // 滚动到可见区域
  if (items[selectedIndex]) {
    items[selectedIndex].scrollIntoView({ block: "nearest" });
  }
}

function applyCompletion(word) {
  const cursorPos = editor.selectionStart;
  const textBefore = editor.value.substring(0, cursorPos);
  const textAfter = editor.value.substring(cursorPos);

  const match = textBefore.match(/[a-zA-Z_][\w-]*$/);
  if (!match) return;

  const prefixStart = cursorPos - match[0].length;
  editor.value = editor.value.substring(0, prefixStart) + word + textAfter;
  editor.selectionStart = editor.selectionEnd = prefixStart + word.length;
  editor.focus();
  hideAutocomplete();
}
