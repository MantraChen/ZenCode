const { invoke } = window.__TAURI__.core;

// ============ DOM 引用 ============
const editor = document.getElementById("editor");
const langSelect = document.getElementById("language-select");
const runBtn = document.getElementById("run-btn");
const outputContent = document.getElementById("output-content");
const clearOutputBtn = document.getElementById("clear-output");
const toggleStdinBtn = document.getElementById("toggle-stdin");
const stdinArea = document.getElementById("stdin-area");
const stdinInput = document.getElementById("stdin-input");
const autocompletePopup = document.getElementById("autocomplete-popup");
const highlightLayer = document.getElementById("highlight-layer");
const webPreview = document.getElementById("web-preview");
const sidebar = document.getElementById("sidebar");
const fileTree = document.getElementById("file-tree");
const tabBar = document.getElementById("tab-bar");
const newFileBtn = document.getElementById("new-file-btn");
const newFolderBtn = document.getElementById("new-folder-btn");

// ============ 状态 ============
let selectedIndex = -1;
let completionItems = [];
let debounceTimer = null;

// 模式状态
let isWebMode = false;
let openTabs = [];       // 当前打开的 tab: { name, path }（web 模式 path 为 null）
let activeTab = null;    // 当前活动 tab 的 path（web 模式为文件名）
let activeTabName = null; // 当前活动 tab 的显示名

// ============ 初始化 ============
window.addEventListener("DOMContentLoaded", async () => {
  await switchMode(langSelect.value);
});

// ============ 模式切换 ============
langSelect.addEventListener("change", async () => {
  hideAutocomplete();
  outputContent.innerHTML = "";
  webPreview.classList.add("hidden");
  document.getElementById("preview-resizer").classList.add("hidden");
  document.querySelector(".editor-area").style.flex = "";
  document.querySelector(".editor-area").style.width = "";
  await switchMode(langSelect.value);
});

async function switchMode(mode) {
  isWebMode = (mode === "web");
  showSidebar();

  if (isWebMode) {
    await enterWebMode();
  } else {
    await enterProjectMode();
  }
}

async function enterProjectMode() {
  // 尝试加载已打开的工作区
  const existing = await invoke("ws_get_folder");
  if (existing) {
    await refreshRealFileTree();
  } else {
    openTabs = [];
    activeTab = null;
    activeTabName = null;
    tabBar.innerHTML = "";
    editor.value = "";
    showOpenFolderHint();
  }
}

function showOpenFolderHint() {
  fileTree.innerHTML = '<div class="file-item" id="open-folder-hint" style="color:var(--text-secondary);padding:12px;font-size:13px;cursor:pointer;">点击此处打开项目文件夹...</div>';
  document.getElementById("open-folder-hint")?.addEventListener("click", openWorkspaceFolder);
}

function showSidebar() {
  sidebar.classList.remove("hidden");
  document.getElementById("sidebar-resizer").classList.remove("hidden");
  tabBar.classList.remove("hidden");
}

// ============ Web 项目模式（VFS 内存） ============
async function enterWebMode() {
  isWebMode = true;
  showSidebar();

  const files = await invoke("vfs_create_web_project");
  renderVfsFileTree(files);

  openTabs = [{ name: "index.html", path: null }];
  activeTab = "index.html";
  activeTabName = "index.html";
  await loadVfsFile("index.html");
  renderTabs();
}

// ============ 打开真实文件夹 ============
async function openWorkspaceFolder() {
  const folder = await invoke("ws_open_folder");
  if (!folder) return; // 用户取消了

  openTabs = [];
  activeTab = null;
  activeTabName = null;
  editor.value = "";
  tabBar.innerHTML = "";
  await refreshRealFileTree();
}

async function refreshRealFileTree() {
  const tree = await invoke("ws_list_tree");
  renderRealFileTree(tree);
}

// ============ 文件树渲染 ============
function getFileIcon(name) {
  const ext = name.split(".").pop().toLowerCase();
  const icons = { html: "&#9776;", css: "&#9733;", js: "&#9883;", c: "C", cpp: "C+", h: "H", java: "J", py: "Py" };
  return icons[ext] || "&#9634;";
}

// --- VFS 文件树（Web 项目） ---
function renderVfsFileTree(files) {
  fileTree.innerHTML = "";
  for (const file of files) {
    const div = document.createElement("div");
    div.className = "file-item" + (file.name === activeTab ? " active" : "");
    div.innerHTML = `
      <span class="file-icon">${getFileIcon(file.name)}</span>
      <span class="file-name">${file.name}</span>
      <button class="file-delete" title="删除">&#10005;</button>
    `;
    div.querySelector(".file-name").addEventListener("click", () => openVfsFile(file.name));
    div.querySelector(".file-delete").addEventListener("click", (e) => {
      e.stopPropagation();
      deleteVfsFile(file.name);
    });
    fileTree.appendChild(div);
  }
}

// --- 自定义拖拽系统（mousedown/mousemove/mouseup，不依赖 HTML5 DnD） ---
let dragState = null;   // { entry, srcDiv, ghost }
let folderDivMap = [];  // [{ entry, div }] — 每次渲染树时重建

function initDrag(e, entry, div) {
  // 需要按住一小段距离才真正开始拖拽，防止和 click 冲突
  const startX = e.clientX, startY = e.clientY;
  const threshold = 5;

  function onMove(e2) {
    const dx = e2.clientX - startX, dy = e2.clientY - startY;
    if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) return;
    // 真正开始拖拽
    document.removeEventListener("mousemove", onMove);
    document.removeEventListener("mouseup", onCancel);
    startDrag(e2, entry, div);
  }
  function onCancel() {
    document.removeEventListener("mousemove", onMove);
    document.removeEventListener("mouseup", onCancel);
  }
  document.addEventListener("mousemove", onMove);
  document.addEventListener("mouseup", onCancel);
}

function startDrag(e, entry, srcDiv) {
  // 禁止拖拽过程中选中文字
  document.body.style.userSelect = "none";
  document.body.style.webkitUserSelect = "none";

  // 创建跟随鼠标的幽灵元素
  const ghost = document.createElement("div");
  ghost.className = "drag-ghost";
  ghost.textContent = entry.name;
  ghost.style.left = e.clientX + "px";
  ghost.style.top = e.clientY + "px";
  document.body.appendChild(ghost);

  srcDiv.classList.add("dragging");
  dragState = { entry, srcDiv, ghost };

  let currentDropTarget = null;

  function onMove(e2) {
    ghost.style.left = e2.clientX + 12 + "px";
    ghost.style.top = e2.clientY + 4 + "px";

    // 寻找鼠标下方的文件夹 drop 目标
    const target = findDropTarget(e2.clientX, e2.clientY);
    if (target !== currentDropTarget) {
      if (currentDropTarget) currentDropTarget.div.classList.remove("drop-target");
      currentDropTarget = target;
      if (currentDropTarget) currentDropTarget.div.classList.add("drop-target");
    }
  }

  function onUp(e2) {
    document.removeEventListener("mousemove", onMove);
    document.removeEventListener("mouseup", onUp);
    document.body.style.userSelect = "";
    document.body.style.webkitUserSelect = "";
    ghost.remove();
    srcDiv.classList.remove("dragging");
    if (currentDropTarget) {
      currentDropTarget.div.classList.remove("drop-target");
      doMove(entry.path, currentDropTarget.entry.path);
    }
    dragState = null;
  }

  document.addEventListener("mousemove", onMove);
  document.addEventListener("mouseup", onUp);
}

function findDropTarget(x, y) {
  for (const item of folderDivMap) {
    // 不能拖到自身或自身子目录
    if (!dragState || item.entry.path === dragState.entry.path) continue;
    if (item.entry.path.startsWith(dragState.entry.path + "/") ||
        item.entry.path.startsWith(dragState.entry.path + "\\")) continue;
    const rect = item.div.getBoundingClientRect();
    if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
      return item;
    }
  }
  return null;
}

async function doMove(srcPath, destDirPath) {
  if (!srcPath || srcPath === destDirPath) return;
  try {
    const newPath = await invoke("ws_move", { src: srcPath, destDir: destDirPath });
    const tab = openTabs.find(t => t.path === srcPath);
    if (tab) {
      tab.path = newPath;
      if (activeTab === srcPath) activeTab = newPath;
      renderTabs();
    }
    await refreshFileTree();
  } catch (err) {
    outputContent.innerHTML = `<span class="output-error">${err}</span>`;
  }
}

// --- 真实文件树（非 Web 语言） ---
function renderRealFileTree(entries, container, depth) {
  if (!container) {
    fileTree.innerHTML = "";
    folderDivMap = [];
    // 打开文件夹按钮
    const openBtn = document.createElement("div");
    openBtn.className = "file-item";
    openBtn.style.color = "var(--accent)";
    openBtn.style.fontSize = "12px";
    openBtn.innerHTML = '<span class="file-icon">&#128193;</span><span class="file-name">打开文件夹...</span>';
    openBtn.addEventListener("click", openWorkspaceFolder);
    fileTree.appendChild(openBtn);
    container = fileTree;
    depth = 0;
  }

  for (const entry of entries) {
    const div = document.createElement("div");
    div.className = "file-item" + (entry.path === activeTab ? " active" : "");
    div.style.paddingLeft = (12 + depth * 16) + "px";

    if (entry.is_dir) {
      div.innerHTML = `<span class="file-icon">&#9656;</span><span class="file-name">${entry.name}</span>`;
      const childContainer = document.createElement("div");
      childContainer.className = "file-tree-children";
      let expanded = true;

      div.addEventListener("click", () => {
        expanded = !expanded;
        childContainer.style.display = expanded ? "" : "none";
        div.querySelector(".file-icon").innerHTML = expanded ? "&#9662;" : "&#9656;";
      });
      div.addEventListener("contextmenu", (e) => showContextMenu(e, entry));
      div.addEventListener("mousedown", (e) => {
        if (e.button === 0) initDrag(e, entry, div);
      });

      // 注册为 drop 目标
      folderDivMap.push({ entry, div });

      container.appendChild(div);
      container.appendChild(childContainer);
      renderRealFileTree(entry.children, childContainer, depth + 1);
    } else {
      div.innerHTML = `
        <span class="file-icon">${getFileIcon(entry.name)}</span>
        <span class="file-name">${entry.name}</span>
      `;
      div.addEventListener("click", () => openRealFile(entry.path, entry.name));
      div.addEventListener("contextmenu", (e) => showContextMenu(e, entry));
      div.addEventListener("mousedown", (e) => {
        if (e.button === 0) initDrag(e, entry, div);
      });
      container.appendChild(div);
    }
  }
}

async function refreshFileTree() {
  if (isWebMode) {
    const files = await invoke("vfs_list_files");
    renderVfsFileTree(files);
  } else {
    await refreshRealFileTree();
  }
}

// ============ VFS 文件操作（Web 模式） ============
async function openVfsFile(name) {
  await saveCurrentFile();
  const content = await invoke("vfs_read_file", { name });
  editor.value = content || "";
  activeTab = name;
  activeTabName = name;
  await invoke("vfs_set_active", { name });

  if (!openTabs.find(t => t.name === name)) {
    openTabs.push({ name, path: null });
  }
  renderTabs();
  await refreshFileTree();
}

async function loadVfsFile(name) {
  const content = await invoke("vfs_read_file", { name });
  editor.value = content || "";
  activeTab = name;
  activeTabName = name;
  await invoke("vfs_set_active", { name });
  scheduleHighlight();
}

function deleteVfsFile(name) {
  showConfirmDialog(`确定要删除 ${name} 吗？`, async () => {
    await invoke("vfs_delete_file", { name });
    openTabs = openTabs.filter(t => t.name !== name);
    if (activeTab === name) {
      activeTab = openTabs.length > 0 ? (openTabs[0].path || openTabs[0].name) : null;
      activeTabName = openTabs.length > 0 ? openTabs[0].name : null;
      if (activeTab && isWebMode) await loadVfsFile(activeTab);
      else editor.value = "";
    }
    renderTabs();
    await refreshFileTree();
  });
}

// ============ 真实文件操作（非 Web 模式） ============
async function openRealFile(path, name) {
  await saveCurrentFile();
  try {
    const content = await invoke("ws_read_file", { path });
    editor.value = content;
    activeTab = path;
    activeTabName = name;
    scheduleHighlight();

    if (!openTabs.find(t => t.path === path)) {
      openTabs.push({ name, path });
    }
    renderTabs();
    await refreshFileTree();
  } catch (err) {
    outputContent.innerHTML = `<span class="output-error">无法读取文件: ${err}</span>`;
  }
}

// ============ 通用保存 ============
async function saveCurrentFile() {
  if (!activeTab) return;
  if (isWebMode) {
    await invoke("vfs_write_file", { name: activeTab, content: editor.value });
  } else {
    try {
      await invoke("ws_write_file", { path: activeTab, content: editor.value });
    } catch (err) {
      // 静默失败（可能还没打开文件）
    }
  }
}

// 新建文件
newFileBtn.addEventListener("click", () => {
  if (isWebMode) {
    showNewFileDialog();
  } else {
    showNewRealItemDialog("file");
  }
});

// 新建文件夹
newFolderBtn.addEventListener("click", () => {
  if (isWebMode) {
    showNewFileDialog(); // Web 模式只有文件
  } else {
    showNewRealItemDialog("folder");
  }
});

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
    await openVfsFile(name);
    await refreshFileTree();
  };

  overlay.querySelector("#dialog-confirm").addEventListener("click", confirm);
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") confirm();
    if (e.key === "Escape") close();
  });
}

function showNewRealItemDialog(type) {
  const isFolder = type === "folder";
  const overlay = document.createElement("div");
  overlay.className = "dialog-overlay";
  overlay.innerHTML = `
    <div class="dialog">
      <h3>新建${isFolder ? "文件夹" : "文件"}</h3>
      <input type="text" id="new-file-input" placeholder="${isFolder ? "文件夹名" : "文件名 (如 utils.c)"}" autofocus />
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
  overlay.addEventListener("click", (e) => { if (e.target === overlay) close(); });

  const doCreate = async () => {
    const name = input.value.trim();
    if (!name) return;
    close();
    try {
      if (isFolder) {
        await invoke("ws_create_dir", { name });
      } else {
        const path = await invoke("ws_create_file", { name });
        await openRealFile(path, name);
      }
      await refreshFileTree();
    } catch (err) {
      outputContent.innerHTML = `<span class="output-error">${err}</span>`;
    }
  };

  overlay.querySelector("#dialog-confirm").addEventListener("click", doCreate);
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") doCreate();
    if (e.key === "Escape") close();
  });
}

// ============ Tab 栏 ============
function renderTabs() {
  tabBar.innerHTML = "";
  for (const t of openTabs) {
    const tab = document.createElement("div");
    const tabId = t.path || t.name;
    tab.className = "tab-item" + (tabId === activeTab ? " active" : "");

    const label = document.createElement("span");
    label.textContent = t.name;
    label.addEventListener("click", () => {
      if (isWebMode) openVfsFile(t.name);
      else if (t.path) openRealFile(t.path, t.name);
    });

    const closeBtn = document.createElement("button");
    closeBtn.className = "tab-close";
    closeBtn.innerHTML = "&#10005;";
    closeBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      closeTab(tabId);
    });

    tab.appendChild(label);
    tab.appendChild(closeBtn);
    tabBar.appendChild(tab);
  }
}

async function closeTab(id) {
  await saveCurrentFile();
  openTabs = openTabs.filter(t => (t.path || t.name) !== id);
  if (activeTab === id) {
    const last = openTabs[openTabs.length - 1];
    if (last) {
      if (isWebMode) await openVfsFile(last.name);
      else if (last.path) await openRealFile(last.path, last.name);
    } else {
      activeTab = null;
      activeTabName = null;
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

  // 先保存当前编辑的文件
  await saveCurrentFile();

  if (!activeTab) {
    outputContent.innerHTML = '<span class="output-error">请先打开一个文件再运行。</span>';
    return;
  }

  // 自动检测代码是否需要 stdin 输入
  const code = editor.value;
  const needsInput = /\bScanner\b|\bBufferedReader\b|\bscanf\b|\bgets\b|\bfgets\b|\bgetline\b|\bcinput\(\b|\binput\s*\(|\bcin\b/.test(code);
  if (needsInput && !stdinInput.value && stdinArea.classList.contains("hidden")) {
    stdinArea.classList.remove("hidden");
    toggleStdinBtn.classList.add("active");
    stdinInput.focus();
    outputContent.innerHTML = '<span class="output-friendly">检测到程序需要输入数据，请在上方 stdin 区域填入输入内容后再次运行。</span>';
    return;
  }

  runBtn.disabled = true;
  runBtn.textContent = "运行中...";
  outputContent.innerHTML = '<span class="output-stdout">正在编译运行...</span>';

  try {
    // stdin: 如果用户填了内容就传给后端，自动追加换行确保最后一行能被读到
    let stdinText = stdinInput.value;
    if (stdinText && !stdinText.endsWith("\n")) stdinText += "\n";
    const result = await invoke("run_file", { filePath: activeTab, stdinInput: stdinText || null });
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
    // 调用 Rust 内存打包器，以当前活动的 HTML 文件为入口
    const entry = activeTab && activeTab.endsWith(".html") ? activeTab : null;
    const bundledHtml = await invoke("bundle_web", { entry });

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
    document.getElementById("preview-resizer").classList.remove("hidden");

    outputContent.innerHTML = '<span class="output-success">Web 项目已在右侧预览面板中渲染。</span>\n';
  } catch (err) {
    outputContent.innerHTML = `<span class="output-error">打包失败: ${err}</span>`;
  }
}

// ============ Web 项目 Console 输出捕获 ============
window.addEventListener("message", (e) => {
  if (e.data && e.data.__zencode_console) {
    const span = document.createElement("span");
    const level = e.data.level;
    if (level === "error") {
      span.className = "output-error";
    } else if (level === "warn") {
      span.className = "output-friendly";
    } else {
      span.className = "output-stdout";
    }
    span.textContent = (level === "log" ? "" : "[" + level + "] ") + e.data.text + "\n";
    outputContent.appendChild(span);
  }
});

// ============ Stdin 切换 ============
toggleStdinBtn.addEventListener("click", () => {
  stdinArea.classList.toggle("hidden");
  toggleStdinBtn.classList.toggle("active");
});

// ============ 清空输出 ============
clearOutputBtn.addEventListener("click", () => {
  outputContent.innerHTML = "";
  webPreview.classList.add("hidden");
  document.getElementById("preview-resizer").classList.add("hidden");
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
    scheduleHighlight();
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

  // 根据当前文件扩展名确定补全语言
  let language = detectLanguageFromTab();

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
/// 根据当前打开的文件扩展名检测语言（用于补全和运行）
function detectLanguageFromTab() {
  const name = activeTabName || activeTab || "";
  const ext = name.split(".").pop().toLowerCase();
  const map = { c: "c", h: "c", cpp: "cpp", cc: "cpp", cxx: "cpp", hpp: "cpp", java: "java", py: "python",
    js: "javascript", css: "css", html: "html", htm: "html" };
  const lang = map[ext];
  if (lang) {
    // HTML 文件使用 Scope Sniffer
    if ((ext === "html" || ext === "htm") && isWebMode) {
      return detectScope(editor.value, editor.selectionStart);
    }
    return lang;
  }
  return "python"; // 默认
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

  // 定位：用隐藏镜像元素精确测量光标位置
  const rect = editor.getBoundingClientRect();
  const style = getComputedStyle(editor);
  const padTop = parseFloat(style.paddingTop);
  const padLeft = parseFloat(style.paddingLeft);

  let mirror = document.getElementById("__editor-mirror");
  if (!mirror) {
    mirror = document.createElement("div");
    mirror.id = "__editor-mirror";
    mirror.style.cssText =
      "position:absolute;top:-9999px;left:-9999px;visibility:hidden;white-space:pre;pointer-events:none;padding:0;";
    document.body.appendChild(mirror);
  }
  mirror.style.font = style.font;
  mirror.style.lineHeight = style.lineHeight;
  mirror.style.letterSpacing = style.letterSpacing;
  mirror.style.tabSize = style.tabSize;

  const textBeforeCursor = editor.value.substring(0, editor.selectionStart);
  const lines = textBeforeCursor.split("\n");
  const currentLineText = lines[lines.length - 1];

  // 测量当前行文本宽度（不含 padding）
  mirror.textContent = currentLineText;
  const textWidth = mirror.offsetWidth;

  // 测量行高
  mirror.textContent = "X";
  const lineHeight = mirror.offsetHeight;

  // 弹窗出现在光标正下方
  const top = rect.top + padTop + (lines.length - 1) * lineHeight - editor.scrollTop - 25;
  const left = rect.left + padLeft + textWidth - editor.scrollLeft;

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
  scheduleHighlight();
}

// ============ 右键上下文菜单 ============
let contextMenu = null;

function showContextMenu(e, entry) {
  e.preventDefault();
  e.stopPropagation();
  hideContextMenu();

  const workspacePath = null; // 延迟获取

  contextMenu = document.createElement("div");
  contextMenu.className = "context-menu";

  const items = [];

  if (!entry.is_dir) {
    items.push({ label: "打开", action: () => openRealFile(entry.path, entry.name) });
  }
  items.push({ label: "重命名", action: () => showRenameDialog(entry) });
  items.push({ label: "删除", action: () => deleteEntry(entry) });
  items.push({ type: "separator" });
  items.push({ label: "复制路径", action: () => copyToClipboard(entry.path) });
  items.push({
    label: "复制相对路径",
    action: async () => {
      const wsPath = await invoke("ws_get_folder");
      if (wsPath && entry.path.startsWith(wsPath)) {
        let rel = entry.path.slice(wsPath.length);
        if (rel.startsWith("/") || rel.startsWith("\\")) rel = rel.slice(1);
        copyToClipboard(rel);
      } else {
        copyToClipboard(entry.path);
      }
    }
  });

  for (const item of items) {
    if (item.type === "separator") {
      const sep = document.createElement("div");
      sep.className = "context-menu-separator";
      contextMenu.appendChild(sep);
    } else {
      const div = document.createElement("div");
      div.className = "context-menu-item";
      div.textContent = item.label;
      div.addEventListener("click", () => {
        hideContextMenu();
        item.action();
      });
      contextMenu.appendChild(div);
    }
  }

  document.body.appendChild(contextMenu);

  // 定位，避免超出窗口
  const menuRect = contextMenu.getBoundingClientRect();
  let top = e.clientY;
  let left = e.clientX;
  if (top + menuRect.height > window.innerHeight) top = window.innerHeight - menuRect.height - 4;
  if (left + menuRect.width > window.innerWidth) left = window.innerWidth - menuRect.width - 4;
  contextMenu.style.top = top + "px";
  contextMenu.style.left = left + "px";
}

function hideContextMenu() {
  if (contextMenu) {
    contextMenu.remove();
    contextMenu = null;
  }
}

document.addEventListener("click", hideContextMenu);
document.addEventListener("contextmenu", (e) => {
  // 只在非文件树区域关闭菜单
  if (!e.target.closest(".context-menu")) hideContextMenu();
});

function deleteEntry(entry) {
  const type = entry.is_dir ? "文件夹" : "文件";
  showConfirmDialog(`确定要删除${type} "${entry.name}" 吗？`, async () => {
    try {
      // 先把 activeTab 清掉，防止 openRealFile → saveCurrentFile 把刚删的文件重新写回去
      const wasActive = (activeTab === entry.path);
      if (wasActive) {
        activeTab = null;
        activeTabName = null;
      }
      // 也要从 tabs 中移除被删项（以及子路径）
      openTabs = openTabs.filter(t => {
        if (!t.path) return true;
        return t.path !== entry.path && !t.path.startsWith(entry.path + "/") && !t.path.startsWith(entry.path + "\\");
      });

      await invoke("ws_delete", { path: entry.path });

      // 切到剩余的最后一个 tab
      if (wasActive) {
        const last = openTabs[openTabs.length - 1];
        if (last && last.path) {
          await openRealFile(last.path, last.name);
        } else {
          editor.value = "";
          renderTabs();
        }
      }
      await refreshFileTree();
    } catch (err) {
      outputContent.innerHTML = `<span class="output-error">删除失败: ${err}</span>`;
    }
  });
}

function showConfirmDialog(message, onConfirm) {
  const overlay = document.createElement("div");
  overlay.className = "dialog-overlay";
  overlay.innerHTML = `
    <div class="dialog">
      <h3>${message}</h3>
      <div class="dialog-buttons">
        <button id="dialog-cancel">取消</button>
        <button id="dialog-confirm" class="primary">确定</button>
      </div>
    </div>
  `;
  document.body.appendChild(overlay);
  const close = () => overlay.remove();
  overlay.querySelector("#dialog-cancel").addEventListener("click", close);
  overlay.addEventListener("click", (e) => { if (e.target === overlay) close(); });
  overlay.querySelector("#dialog-confirm").addEventListener("click", () => {
    close();
    onConfirm();
  });
}

function showRenameDialog(entry) {
  const overlay = document.createElement("div");
  overlay.className = "dialog-overlay";
  overlay.innerHTML = `
    <div class="dialog">
      <h3>重命名</h3>
      <input type="text" id="rename-input" value="${entry.name}" autofocus />
      <div class="dialog-buttons">
        <button id="dialog-cancel">取消</button>
        <button id="dialog-confirm" class="primary">确定</button>
      </div>
    </div>
  `;
  document.body.appendChild(overlay);
  const input = overlay.querySelector("#rename-input");
  // 选中文件名（不含扩展名）
  const dotIdx = entry.name.lastIndexOf(".");
  input.setSelectionRange(0, dotIdx > 0 ? dotIdx : entry.name.length);
  input.focus();

  const close = () => overlay.remove();
  overlay.querySelector("#dialog-cancel").addEventListener("click", close);
  overlay.addEventListener("click", (e) => { if (e.target === overlay) close(); });

  const doRename = async () => {
    const newName = input.value.trim();
    if (!newName || newName === entry.name) { close(); return; }
    close();
    try {
      const newPath = await invoke("ws_rename", { path: entry.path, newName });
      // 更新已打开的 tab
      const tab = openTabs.find(t => t.path === entry.path);
      if (tab) {
        tab.path = newPath;
        tab.name = newName;
        if (activeTab === entry.path) {
          activeTab = newPath;
          activeTabName = newName;
        }
        renderTabs();
      }
      await refreshFileTree();
    } catch (err) {
      outputContent.innerHTML = `<span class="output-error">${err}</span>`;
    }
  };

  overlay.querySelector("#dialog-confirm").addEventListener("click", doRename);
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") doRename();
    if (e.key === "Escape") close();
  });
}

function copyToClipboard(text) {
  navigator.clipboard.writeText(text).catch(() => {
    // fallback
    const ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    ta.remove();
  });
}

// ============ 语法高亮 ============
const HIGHLIGHT_RULES = {
  c: {
    keywords: /\b(auto|break|case|char|const|continue|default|do|double|else|enum|extern|float|for|goto|if|inline|int|long|register|restrict|return|short|signed|sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while|_Bool|_Complex|_Imaginary|NULL|true|false)\b/g,
    types: /\b(int|char|float|double|void|long|short|unsigned|signed|size_t|bool|FILE|uint8_t|uint16_t|uint32_t|uint64_t|int8_t|int16_t|int32_t|int64_t)\b/g,
    builtins: /\b(printf|scanf|malloc|free|strlen|strcmp|strcpy|memcpy|memset|fopen|fclose|fprintf|fscanf|exit|abort|sizeof)\b/g,
  },
  cpp: {
    keywords: /\b(alignas|alignof|and|and_eq|asm|auto|bitand|bitor|bool|break|case|catch|char|char8_t|char16_t|char32_t|class|compl|concept|const|consteval|constexpr|constinit|const_cast|continue|co_await|co_return|co_yield|decltype|default|delete|do|double|dynamic_cast|else|enum|explicit|export|extern|false|float|for|friend|goto|if|inline|int|long|mutable|namespace|new|noexcept|not|not_eq|nullptr|operator|or|or_eq|private|protected|public|register|reinterpret_cast|requires|return|short|signed|sizeof|static|static_assert|static_cast|struct|switch|template|this|thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|wchar_t|while|xor|xor_eq|override|final)\b/g,
    types: /\b(string|vector|map|set|list|deque|array|pair|tuple|shared_ptr|unique_ptr|optional|variant|any|size_t|uint8_t|uint16_t|uint32_t|uint64_t|int8_t|int16_t|int32_t|int64_t|cout|cin|endl|cerr|ostream|istream|ifstream|ofstream|stringstream)\b/g,
    builtins: /\b(std|cout|cin|endl|cerr|printf|scanf|malloc|free|sizeof|new|delete)\b/g,
  },
  java: {
    keywords: /\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|var|yield|record|sealed|permits|non-sealed)\b/g,
    types: /\b(String|Integer|Boolean|Double|Float|Long|Short|Byte|Character|Object|List|ArrayList|Map|HashMap|Set|HashSet|LinkedList|Queue|Stack|Scanner|System|Math|Arrays|Collections|Optional|Stream|StringBuilder|Exception|RuntimeException|Thread|Runnable)\b/g,
    builtins: /\b(System\.out\.println|System\.out\.print|System\.err|Math\.\w+)\b/g,
  },
  python: {
    keywords: /\b(False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\b/g,
    types: /\b(int|float|str|bool|list|dict|tuple|set|frozenset|bytes|bytearray|complex|type|object|NoneType)\b/g,
    builtins: /\b(print|input|len|range|enumerate|zip|map|filter|sorted|reversed|min|max|sum|abs|round|open|isinstance|issubclass|hasattr|getattr|setattr|delattr|super|property|classmethod|staticmethod|format|repr|id|hash|type|dir|vars|globals|locals|iter|next|all|any)\b/g,
  },
  html: null,     // HTML 用专用高亮
  css: null,      // CSS 用专用高亮
  javascript: {
    keywords: /\b(await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|from|function|if|import|in|instanceof|let|new|of|return|static|super|switch|this|throw|try|typeof|var|void|while|with|yield|async|null|undefined|true|false|NaN|Infinity)\b/g,
    types: /\b(Array|Boolean|Date|Error|Function|JSON|Map|Math|Number|Object|Promise|Proxy|RegExp|Set|String|Symbol|WeakMap|WeakSet|BigInt|Int8Array|Uint8Array|Float32Array|Float64Array|ArrayBuffer|DataView|Element|HTMLElement|Node|NodeList|Event|Response|Request|URL|Headers|FormData)\b/g,
    builtins: /\b(console|document|window|navigator|localStorage|sessionStorage|fetch|setTimeout|setInterval|clearTimeout|clearInterval|requestAnimationFrame|alert|confirm|prompt|parseInt|parseFloat|isNaN|isFinite|encodeURI|decodeURI|encodeURIComponent|decodeURIComponent|require|module|exports|process)\b/g,
  },
};

function escapeHtml(text) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function highlightCode(code, lang) {
  if (!lang || !code) return escapeHtml(code || "") + "\n";

  // HTML: 专用高亮
  if (lang === "html") return highlightHtml(code);
  // CSS: 专用高亮
  if (lang === "css") return highlightCss(code);

  const rules = HIGHLIGHT_RULES[lang];
  if (!rules) return escapeHtml(code) + "\n";

  // 先提取字符串和注释，用占位符替换
  const tokens = [];
  let safe = code;

  // 提取多行注释 /* ... */
  safe = safe.replace(/\/\*[\s\S]*?\*\//g, (m) => {
    const i = tokens.length;
    tokens.push(`<span class="hl-comment">${escapeHtml(m)}</span>`);
    return `\x00T${i}\x00`;
  });

  // 提取单行注释 // ... 和 # ... (python)
  const commentRe = (lang === "python") ? /#[^\n]*/g : /\/\/[^\n]*/g;
  safe = safe.replace(commentRe, (m) => {
    const i = tokens.length;
    tokens.push(`<span class="hl-comment">${escapeHtml(m)}</span>`);
    return `\x00T${i}\x00`;
  });

  // 提取字符串 "..." '...' `...`
  safe = safe.replace(/("""[\s\S]*?"""|'''[\s\S]*?'''|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\]|\\.)*`)/g, (m) => {
    const i = tokens.length;
    tokens.push(`<span class="hl-string">${escapeHtml(m)}</span>`);
    return `\x00T${i}\x00`;
  });

  // 转义 HTML
  safe = escapeHtml(safe);

  // 数字
  safe = safe.replace(/\b(\d+\.?\d*(?:[eE][+-]?\d+)?[fFlLdD]?|0[xX][0-9a-fA-F]+[lL]?|0[bB][01]+[lL]?)\b/g,
    '<span class="hl-number">$1</span>');

  // 函数调用: word(
  safe = safe.replace(/\b([a-zA-Z_]\w*)(\s*\()/g, (m, name, paren) => {
    // 如果已经是关键字就不标注为函数
    if (rules.keywords && rules.keywords.test(name)) {
      rules.keywords.lastIndex = 0;
      return m;
    }
    return `<span class="hl-function">${name}</span>${paren}`;
  });

  // 关键字
  if (rules.keywords) {
    rules.keywords.lastIndex = 0;
    safe = safe.replace(rules.keywords, '<span class="hl-keyword">$&</span>');
  }

  // 类型
  if (rules.types) {
    rules.types.lastIndex = 0;
    safe = safe.replace(rules.types, '<span class="hl-type">$&</span>');
  }

  // 内置
  if (rules.builtins) {
    rules.builtins.lastIndex = 0;
    safe = safe.replace(rules.builtins, '<span class="hl-builtin">$&</span>');
  }

  // 操作符
  safe = safe.replace(/([+\-*/%=!&|^~<>?:]+)/g, '<span class="hl-operator">$1</span>');

  // 恢复字符串/注释占位符
  safe = safe.replace(/\x00T(\d+)\x00/g, (_, idx) => tokens[+idx]);

  return safe + "\n"; // 末尾换行保证 pre 高度匹配
}

function highlightHtml(code) {
  const tokens = [];

  // 提取 <style>...</style> 内容
  let safe = code.replace(/<style([\s\S]*?)>([\s\S]*?)<\/style>/gi, (m, attrs, css) => {
    const i = tokens.length;
    const inner = highlightCss(css);
    tokens.push(`<span class="hl-tag">&lt;style${escapeHtml(attrs)}&gt;</span>${inner}<span class="hl-tag">&lt;/style&gt;</span>`);
    return `\x00T${i}\x00`;
  });

  // 提取 <script>...</script> 内容
  safe = safe.replace(/<script([\s\S]*?)>([\s\S]*?)<\/script>/gi, (m, attrs, js) => {
    const i = tokens.length;
    const inner = highlightCode(js, "javascript");
    tokens.push(`<span class="hl-tag">&lt;script${escapeHtml(attrs)}&gt;</span>${inner}<span class="hl-tag">&lt;/script&gt;</span>`);
    return `\x00T${i}\x00`;
  });

  // HTML 注释
  safe = safe.replace(/<!--[\s\S]*?-->/g, (m) => {
    const i = tokens.length;
    tokens.push(`<span class="hl-comment">${escapeHtml(m)}</span>`);
    return `\x00T${i}\x00`;
  });

  // HTML 标签
  safe = safe.replace(/<\/?[a-zA-Z][a-zA-Z0-9-]*(?:\s[^>]*)?\/?>/g, (tag) => {
    const i = tokens.length;
    // 高亮标签名和属性
    let h = escapeHtml(tag);
    // 标签名
    h = h.replace(/^(&lt;\/?)([\w-]+)/, '$1<span class="hl-tag">$2</span>');
    // 属性值
    h = h.replace(/(=)(&quot;[^&]*?&quot;|&#x27;[^&]*?&#x27;)/g, '$1<span class="hl-value">$2</span>');
    // 属性名
    h = h.replace(/\s([\w-]+)(=)/g, ' <span class="hl-attr">$1</span>$2');
    tokens.push(h);
    return `\x00T${i}\x00`;
  });

  safe = escapeHtml(safe);
  safe = safe.replace(/\x00T(\d+)\x00/g, (_, idx) => tokens[+idx]);
  return safe + "\n";
}

function highlightCss(code) {
  const tokens = [];

  // CSS 注释
  let safe = code.replace(/\/\*[\s\S]*?\*\//g, (m) => {
    const i = tokens.length;
    tokens.push(`<span class="hl-comment">${escapeHtml(m)}</span>`);
    return `\x00T${i}\x00`;
  });

  // 字符串
  safe = safe.replace(/(["'])(?:[^"'\\]|\\.)*?\1/g, (m) => {
    const i = tokens.length;
    tokens.push(`<span class="hl-string">${escapeHtml(m)}</span>`);
    return `\x00T${i}\x00`;
  });

  safe = escapeHtml(safe);

  // 选择器 (行首的非空白内容直到 {)
  safe = safe.replace(/(^|\n)([\s]*)([\w.#\-\[\]=~^*:,>\s+]+?)(\s*\{)/g,
    '$1$2<span class="hl-tag">$3</span>$4');

  // 属性名: 值
  safe = safe.replace(/([\w-]+)(\s*:)([^;{}]+)/g, (m, prop, colon, val) => {
    return `<span class="hl-property">${prop}</span>${colon}<span class="hl-value">${val}</span>`;
  });

  // 数字+单位
  safe = safe.replace(/\b(\d+\.?\d*)(px|em|rem|%|vh|vw|deg|s|ms)?\b/g,
    '<span class="hl-number">$1$2</span>');

  safe = safe.replace(/\x00T(\d+)\x00/g, (_, idx) => tokens[+idx]);
  return safe + "\n";
}

let highlightTimer = null;

function updateHighlight() {
  const lang = detectLanguageFromTab();
  highlightLayer.innerHTML = highlightCode(editor.value, lang);
}

function scheduleHighlight() {
  clearTimeout(highlightTimer);
  highlightTimer = setTimeout(updateHighlight, 30);
}

// 同步滚动
editor.addEventListener("scroll", () => {
  highlightLayer.scrollTop = editor.scrollTop;
  highlightLayer.scrollLeft = editor.scrollLeft;
});

// 输入时更新高亮
editor.addEventListener("input", scheduleHighlight);

// ============ 可拖拽面板 ============
(function initResizers() {
  const outputResizer = document.getElementById("output-resizer");
  const sidebarResizer = document.getElementById("sidebar-resizer");
  const previewResizer = document.getElementById("preview-resizer");
  const editorPane = document.querySelector(".editor-pane");
  const outputPane = document.querySelector(".output-pane");
  const workspace = document.querySelector(".workspace");
  const mainContainer = document.querySelector(".main-container");
  const editorArea = document.querySelector(".editor-area");

  function setupResizer(resizer, onDrag, direction) {
    if (!resizer) return;
    let startPos, startSizeA, startSizeB;

    resizer.addEventListener("mousedown", (e) => {
      e.preventDefault();
      resizer.classList.add("active");
      const initial = onDrag.init(e);
      startPos = direction === "row" ? e.clientY : e.clientX;
      startSizeA = initial.sizeA;
      startSizeB = initial.sizeB;

      // 拖拽时禁止 iframe 捕获鼠标事件
      webPreview.style.pointerEvents = "none";

      function onMouseMove(e) {
        const current = direction === "row" ? e.clientY : e.clientX;
        const delta = current - startPos;
        onDrag.move(delta, startSizeA, startSizeB);
      }

      function onMouseUp() {
        resizer.classList.remove("active");
        webPreview.style.pointerEvents = "";
        document.removeEventListener("mousemove", onMouseMove);
        document.removeEventListener("mouseup", onMouseUp);
      }

      document.addEventListener("mousemove", onMouseMove);
      document.addEventListener("mouseup", onMouseUp);
    });
  }

  // 输出面板上下拖拽
  setupResizer(outputResizer, {
    init: () => ({
      sizeA: editorPane.offsetHeight,
      sizeB: outputPane.offsetHeight,
    }),
    move: (delta, startA, startB) => {
      const newEditor = Math.max(100, startA + delta);
      const newOutput = Math.max(60, startB - delta);
      editorPane.style.flex = "none";
      editorPane.style.height = newEditor + "px";
      outputPane.style.height = newOutput + "px";
    },
  }, "row");

  // 侧边栏左右拖拽
  setupResizer(sidebarResizer, {
    init: () => ({
      sizeA: sidebar.offsetWidth,
      sizeB: 0,
    }),
    move: (delta, startA) => {
      const newWidth = Math.max(120, Math.min(500, startA + delta));
      sidebar.style.width = newWidth + "px";
      sidebar.style.minWidth = newWidth + "px";
    },
  }, "col");

  // Web 预览左右拖拽
  setupResizer(previewResizer, {
    init: () => ({
      sizeA: editorArea.offsetWidth,
      sizeB: webPreview.offsetWidth,
    }),
    move: (delta, startA, startB) => {
      const total = startA + startB;
      const newEditor = Math.max(200, startA + delta);
      const newPreview = Math.max(200, total - newEditor);
      editorArea.style.flex = "none";
      editorArea.style.width = newEditor + "px";
      webPreview.style.width = newPreview + "px";
    },
  }, "col");
})();
