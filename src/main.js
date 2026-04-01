const { invoke } = window.__TAURI__.core;

// ============ DOM 引用 ============
const editor = document.getElementById("editor");
const langSelect = document.getElementById("language-select");
const runBtn = document.getElementById("run-btn");
const outputContent = document.getElementById("output-content");
const clearOutputBtn = document.getElementById("clear-output");
const autocompletePopup = document.getElementById("autocomplete-popup");
const webPreview = document.getElementById("web-preview");
const sidebar = document.getElementById("sidebar");
const fileTree = document.getElementById("file-tree");
const tabBar = document.getElementById("tab-bar");
const newFileBtn = document.getElementById("new-file-btn");

// ============ 状态 ============
let selectedIndex = -1;
let completionItems = [];
let debounceTimer = null;

// Web 项目模式状态
let isWebMode = false;
let openTabs = [];       // 当前打开的 tab 文件名列表
let activeTab = null;    // 当前活动 tab

// ============ 默认代码模板 ============
const templates = {
  c: '#include <stdio.h>\n\nint main() {\n    printf("Hello, World!\\n");\n    return 0;\n}',
  cpp: '#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, World!" << endl;\n    return 0;\n}',
  java: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, World!");\n    }\n}',
  python: 'print("Hello, World!")',
};

// ============ 初始化 ============
window.addEventListener("DOMContentLoaded", () => {
  editor.value = templates[langSelect.value] || "";
  editor.focus();
});

// ============ 语言切换 ============
langSelect.addEventListener("change", async () => {
  const lang = langSelect.value;
  hideAutocomplete();
  outputContent.innerHTML = "";
  webPreview.classList.add("hidden");

  if (lang === "web") {
    await enterWebMode();
  } else {
    exitWebMode();
    editor.value = templates[lang] || "";
  }
});

// ============ Web 项目模式 ============
async function enterWebMode() {
  isWebMode = true;
  sidebar.classList.remove("hidden");
  tabBar.classList.remove("hidden");

  // 在后端创建 Web 项目模板
  const files = await invoke("vfs_create_web_project");
  renderFileTree(files);

  // 打开 index.html
  openTabs = ["index.html"];
  activeTab = "index.html";
  await loadFile("index.html");
  renderTabs();
}

function exitWebMode() {
  isWebMode = false;
  sidebar.classList.add("hidden");
  tabBar.classList.add("hidden");
  webPreview.classList.add("hidden");
  openTabs = [];
  activeTab = null;
  fileTree.innerHTML = "";
  tabBar.innerHTML = "";
}

// ============ 文件树渲染 ============
function getFileIcon(ext) {
  const icons = {
    html: "&#9776;",
    css: "&#9733;",
    js: "&#9883;",
    c: "C",
    cpp: "C+",
    java: "J",
    py: "Py",
  };
  return icons[ext] || "&#9634;";
}

function renderFileTree(files) {
  fileTree.innerHTML = "";
  for (const file of files) {
    const div = document.createElement("div");
    div.className = "file-item" + (file.name === activeTab ? " active" : "");
    div.innerHTML = `
      <span class="file-icon">${getFileIcon(file.extension)}</span>
      <span class="file-name">${file.name}</span>
      <button class="file-delete" title="删除">&#10005;</button>
    `;

    div.querySelector(".file-name").addEventListener("click", () => openFile(file.name));
    div.querySelector(".file-delete").addEventListener("click", (e) => {
      e.stopPropagation();
      deleteFile(file.name);
    });

    fileTree.appendChild(div);
  }
}

async function refreshFileTree() {
  const files = await invoke("vfs_list_files");
  renderFileTree(files);
}

// ============ 文件操作 ============
async function openFile(name) {
  // 先保存当前文件
  await saveCurrentFile();

  // 加载目标文件
  await loadFile(name);

  // 更新 tab
  if (!openTabs.includes(name)) {
    openTabs.push(name);
  }
  activeTab = name;
  renderTabs();
  await refreshFileTree();
}

async function loadFile(name) {
  const content = await invoke("vfs_read_file", { name });
  editor.value = content || "";
  activeTab = name;
  await invoke("vfs_set_active", { name });
}

async function saveCurrentFile() {
  if (activeTab && isWebMode) {
    await invoke("vfs_write_file", {
      name: activeTab,
      content: editor.value,
    });
  }
}

async function deleteFile(name) {
  if (!confirm(`确定要删除 ${name} 吗？`)) return;

  await invoke("vfs_delete_file", { name });

  // 从 tabs 中移除
  openTabs = openTabs.filter((t) => t !== name);
  if (activeTab === name) {
    activeTab = openTabs[0] || null;
    if (activeTab) {
      await loadFile(activeTab);
    } else {
      editor.value = "";
    }
  }

  renderTabs();
  await refreshFileTree();
}

// 新建文件
newFileBtn.addEventListener("click", () => showNewFileDialog());

function showNewFileDialog() {
  const overlay = document.createElement("div");
  overlay.className = "dialog-overlay";
  overlay.innerHTML = `
    <div class="dialog">
      <h3>新建文件</h3>
      <input type="text" id="new-file-input" placeholder="文件名 (如 app.js)" autofocus />
      <div class="dialog-buttons">
        <button id="dialog-cancel">取消</button>
        <button id="dialog-confirm" class="primary">创建</button>
      </div>
    </div>
  `;
  document.body.appendChild(overlay);

  const input = overlay.querySelector("#new-file-input");
  input.focus();

  const close = () => overlay.remove();

  overlay.querySelector("#dialog-cancel").addEventListener("click", close);
  overlay.addEventListener("click", (e) => {
    if (e.target === overlay) close();
  });

  const confirm = async () => {
    const name = input.value.trim();
    if (!name) return;
    await invoke("vfs_write_file", { name, content: "" });
    close();
    await openFile(name);
    await refreshFileTree();
  };

  overlay.querySelector("#dialog-confirm").addEventListener("click", confirm);
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") confirm();
    if (e.key === "Escape") close();
  });
}

// ============ Tab 栏 ============
function renderTabs() {
  tabBar.innerHTML = "";
  for (const name of openTabs) {
    const tab = document.createElement("div");
    tab.className = "tab-item" + (name === activeTab ? " active" : "");

    const label = document.createElement("span");
    label.textContent = name;
    label.addEventListener("click", () => openFile(name));

    const closeBtn = document.createElement("button");
    closeBtn.className = "tab-close";
    closeBtn.innerHTML = "&#10005;";
    closeBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      closeTab(name);
    });

    tab.appendChild(label);
    tab.appendChild(closeBtn);
    tabBar.appendChild(tab);
  }
}

async function closeTab(name) {
  await saveCurrentFile();
  openTabs = openTabs.filter((t) => t !== name);
  if (activeTab === name) {
    activeTab = openTabs[openTabs.length - 1] || null;
    if (activeTab) {
      await loadFile(activeTab);
    } else {
      editor.value = "";
    }
  }
  renderTabs();
}

// ============ 运行代码 ============
runBtn.addEventListener("click", runCode);

document.addEventListener("keydown", (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
    e.preventDefault();
    runCode();
  }
});

async function runCode() {
  if (isWebMode) {
    await runWebProject();
    return;
  }

  const code = editor.value;
  const language = langSelect.value;

  if (!code.trim()) {
    outputContent.innerHTML = '<span class="output-error">请先写一些代码再运行。</span>';
    return;
  }

  runBtn.disabled = true;
  runBtn.textContent = "运行中...";
  outputContent.innerHTML = '<span class="output-stdout">正在编译运行...</span>';

  try {
    const result = await invoke("run_code", { code, language });
    outputContent.innerHTML = "";

    if (result.errors && result.errors.length > 0) {
      for (const err of result.errors) {
        const span = document.createElement("span");
        span.className = "output-friendly";
        span.textContent = err.message;
        outputContent.appendChild(span);
      }
    }

    if (result.stdout) {
      const span = document.createElement("span");
      span.className = result.exitCode === 0 ? "output-stdout" : "output-error";
      span.textContent = result.stdout;
      outputContent.appendChild(span);
    }

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

// ============ Web 项目运行（内存打包器） ============
async function runWebProject() {
  // 先保存当前文件
  await saveCurrentFile();

  try {
    // 调用 Rust 内存打包器
    const bundledHtml = await invoke("bundle_web");

    // 使用 Blob URL 安全渲染
    const blob = new Blob([bundledHtml], { type: "text/html" });
    const url = URL.createObjectURL(blob);

    // 释放旧的 Blob URL
    if (webPreview.dataset.blobUrl) {
      URL.revokeObjectURL(webPreview.dataset.blobUrl);
    }
    webPreview.dataset.blobUrl = url;
    webPreview.src = url;
    webPreview.classList.remove("hidden");

    outputContent.innerHTML = '<span class="output-success">Web 项目已在右侧预览面板中渲染。</span>';
  } catch (err) {
    outputContent.innerHTML = `<span class="output-error">打包失败: ${err}</span>`;
  }
}

// ============ 清空输出 ============
clearOutputBtn.addEventListener("click", () => {
  outputContent.innerHTML = "";
  webPreview.classList.add("hidden");
  if (webPreview.dataset.blobUrl) {
    URL.revokeObjectURL(webPreview.dataset.blobUrl);
    webPreview.dataset.blobUrl = "";
  }
  webPreview.src = "about:blank";
});

// ================================================================
//  上下文嗅探器 (Scope Sniffer)
//  在 HTML 混合文件中，O(N) 判断光标处于 HTML / CSS / JS 作用域
// ================================================================

/**
 * detectScope(text, cursorPos)
 *
 * 算法：从文本开头到 cursorPos 做一次线性扫描。
 * 维护一个状态机：
 *   - 遇到 <style  → 找到 > 后进入 "css" 模式
 *   - 遇到 </style> → 回到 "html" 模式
 *   - 遇到 <script → 找到 > 后进入 "javascript" 模式
 *   - 遇到 </script> → 回到 "html" 模式
 *   - 其他情况保持当前模式
 *
 * 时间复杂度 O(N)，N = cursorPos
 * 无正则、无回溯，纯状态机驱动
 */
function detectScope(text, cursorPos) {
  let scope = "html";
  const len = Math.min(cursorPos, text.length);
  let i = 0;

  while (i < len) {
    if (text[i] === "<") {
      // 检测 <style 开标签
      if (matchTagAt(text, i, "<style", len)) {
        // 找到 > 表示标签结束
        const closeAngle = text.indexOf(">", i + 6);
        if (closeAngle !== -1 && closeAngle < len) {
          scope = "css";
          i = closeAngle + 1;
          continue;
        }
      }
      // 检测 </style> 闭标签
      else if (matchTagAt(text, i, "</style>", len)) {
        scope = "html";
        i += 8;
        continue;
      }
      // 检测 <script 开标签
      else if (matchTagAt(text, i, "<script", len)) {
        const closeAngle = text.indexOf(">", i + 7);
        if (closeAngle !== -1 && closeAngle < len) {
          scope = "javascript";
          i = closeAngle + 1;
          continue;
        }
      }
      // 检测 </script> 闭标签
      else if (matchTagAt(text, i, "</script>", len)) {
        scope = "html";
        i += 9;
        continue;
      }
    }
    i++;
  }

  return scope;
}

/** 大小写不敏感地检测 text[pos..] 是否以 tag 开头 */
function matchTagAt(text, pos, tag, maxLen) {
  if (pos + tag.length > maxLen) return false;
  for (let j = 0; j < tag.length; j++) {
    if (text[pos + j].toLowerCase() !== tag[j].toLowerCase()) return false;
  }
  return true;
}

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

// Tab 键缩进
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

  const match = textBefore.match(/[a-zA-Z_][\w-]*$/);
  if (!match || match[0].length < 1) {
    hideAutocomplete();
    return;
  }

  const prefix = match[0];

  // 确定补全语言/作用域
  let language;
  if (isWebMode) {
    // Web 项目模式：根据文件扩展名 + Scope Sniffer 判定
    language = getLanguageForAutocomplete();
  } else {
    language = langSelect.value;
  }

  try {
    const results = await invoke("autocomplete", { prefix, language });

    if (results.length === 0 || (results.length === 1 && results[0] === prefix)) {
      hideAutocomplete();
      return;
    }

    showAutocomplete(results, prefix, language);
  } catch {
    hideAutocomplete();
  }
}

/**
 * 确定当前应该使用哪个 Trie 进行补全
 * Web 项目模式下：
 *   - .css 文件 → css
 *   - .js 文件 → javascript
 *   - .html 文件 → 使用 Scope Sniffer 判断
 */
function getLanguageForAutocomplete() {
  if (!activeTab) return "html";

  const ext = activeTab.split(".").pop().toLowerCase();
  if (ext === "css") return "css";
  if (ext === "js") return "javascript";
  if (ext === "html" || ext === "htm") {
    // HTML 文件使用 Scope Sniffer
    return detectScope(editor.value, editor.selectionStart);
  }
  return "html";
}

function showAutocomplete(items, prefix, scope) {
  completionItems = items;
  selectedIndex = 0;

  autocompletePopup.innerHTML = "";
  for (let i = 0; i < items.length && i < 10; i++) {
    const div = document.createElement("div");
    div.className = "autocomplete-item" + (i === 0 ? " selected" : "");

    const text = document.createElement("span");
    text.textContent = items[i];
    div.appendChild(text);

    // 在 HTML 文件的补全项旁显示 scope 标记
    if (isWebMode && activeTab && activeTab.endsWith(".html")) {
      const badge = document.createElement("span");
      badge.className = "scope-badge";
      badge.textContent = scope === "javascript" ? "JS" : scope.toUpperCase();
      div.appendChild(badge);
    }

    div.addEventListener("mousedown", (e) => {
      e.preventDefault();
      applyCompletion(items[i]);
    });
    autocompletePopup.appendChild(div);
  }

  // 定位
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
