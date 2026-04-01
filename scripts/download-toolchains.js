// 文件路径: scripts/download-toolchains.js
import fs from 'fs';
import path from 'path';
import axios from 'axios';
import unzipper from 'unzipper';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 🚨 核心拦截：如果当前是 Mac 环境，直接跳过 Windows 工具链下载
if (process.platform === 'darwin') {
    console.log("🍎 检测到 macOS 开发环境！跳过 Windows 便携编译器的下载。");
    console.log("👉 本地开发测试将自动调用 Mac 系统自带的 python3 和 gcc。");
    process.exit(0);
}

const TARGET_DIR = path.join(__dirname, '../src-tauri/toolchains');

const TOOLCHAINS = {
    python: {
        url: 'https://www.python.org/ftp/python/3.11.8/python-3.11.8-embed-amd64.zip',
        dest: path.join(TARGET_DIR, 'python')
    },
    gcc: {
        // 修复 404: 更换为极其稳定的 w64devkit (轻量级纯净版 MinGW)
        url: 'https://github.com/skeeto/w64devkit/releases/download/v1.22.0/w64devkit-1.22.0.zip',
        dest: path.join(TARGET_DIR, 'mingw64')
    }
};

async function downloadAndExtract(name, config) {
    if (fs.existsSync(config.dest)) {
        console.log(`✅ [${name}] 工具链已存在: ${config.dest}`);
        return;
    }
    console.log(`⏳ 开始下载 [${name}] 工具链... 这可能需要一两分钟。`);
    
    fs.mkdirSync(config.dest, { recursive: true });
    
    const response = await axios({
        method: 'GET',
        url: config.url,
        responseType: 'stream'
    });

    return new Promise((resolve, reject) => {
        response.data.pipe(unzipper.Extract({ path: config.dest }))
            .on('close', () => resolve(console.log(`🎉 [${name}] 解压完成!`)))
            .on('error', reject);
    });
}

async function main() {
    console.log("🚀 初始化 Windows 打包所需的工具链...");
    try {
        await downloadAndExtract('python', TOOLCHAINS.python);
        await downloadAndExtract('gcc', TOOLCHAINS.gcc);
        console.log("✨ 所有底层编译工具准备就绪！");
    } catch (error) {
        console.error("❌ 下载失败:", error.message);
        process.exit(1);
    }
}

main();