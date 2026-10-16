#!/usr/bin/env python3
"""Build a per-user offline Windows installer from the verified product and pinned JDK."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JDK_SHA256 = '0a08945f8caf28a4eba82d9828e093fb95d944b84b4aef07dd43155158efe3da'
parser = argparse.ArgumentParser()
parser.add_argument('--jdk-zip', type=Path, required=True)
args = parser.parse_args()
assert hashlib.sha256(args.jdk_zip.read_bytes()).hexdigest() == JDK_SHA256, 'Unexpected JDK archive'
product = ROOT / 'products/cn.zencode.product/target/products/cn.zencode.product-win32.win32.x86_64.zip'
release = '0.9.0-zh1-20261016'
base = ROOT / 'dist/windows-installer'
payload = base / 'payload'
if payload.exists():
    shutil.rmtree(payload)
payload.mkdir(parents=True)

def extract(archive, destination, strip=False):
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        for info in z.infolist():
            parts = Path(info.filename).parts
            if strip:
                parts = parts[1:]
            if not parts:
                continue
            target = destination.joinpath(*parts)
            assert target.resolve().is_relative_to(destination.resolve()), info.filename
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(z.read(info))

extract(product, payload)
shutil.copytree(ROOT / 'samples/acceptance-0.9.0', payload / '测试用例')
extract(args.jdk_zip, payload / 'jdk8', strip=True)
ini = payload / 'ZenCode.ini'
text = ini.read_text()
assert '\n-vm\n' not in text
ini.write_text(text.replace('-vmargs', '-vm\njdk8/bin/javaw.exe\n-vmargs'))
assert (payload / 'jdk8/bin/javac.exe').exists()
(payload / '使用说明.txt').write_text('''ZenCode 测试版

从桌面或开始菜单快捷方式启动。已内置 JDK 8，无需设置环境变量。
默认工作区：文档目录下的 ZenCodeWorkspace。
如果项目缺少 JRE 系统库，在首选项的 Java / 已安装的 JRE 中添加安装目录下的 jdk8。
中文字体异常时，在颜色和字体中将 Java 编辑器文本字体恢复默认。
Windows 菜单栏主题完整切换需要重启。
卸载保留工作区；请勿将项目放在程序安装目录。
安装目录必须为空；不同测试版本可以并存。升级前请关闭旧版本。
各组件及 JDK 的许可证文件随程序保留在对应目录中。
''', encoding='utf-8-sig')

# Enumerate only shipped files. Never recursively remove the install tree on uninstall.
def quote(value):
    return value.replace('$', '$$').replace('"', '$\\"')
files = sorted(p for p in payload.rglob('*') if p.is_file())
dirs = sorted((p for p in payload.rglob('*') if p.is_dir()), key=lambda p: len(p.parts), reverse=True)
manifest = base / 'uninstall-files.nsh'
manifest.write_text('\n'.join(
    ['Delete "$INSTDIR\\' + quote(str(p.relative_to(payload)).replace('/', '\\')) + '"' for p in files] +
    ['RMDir "$INSTDIR\\' + quote(str(p.relative_to(payload)).replace('/', '\\')) + '"' for p in dirs]
), encoding='utf-8')
output = base / ('ZenCode-' + release + '-Windows-x64-Setup.exe')
subprocess.run(['makensis', '-V2', '-DRELEASE=' + release, '-DPAYLOAD=' + str(payload),
                '-DOUTPUT=' + str(output), '-DDELETE_MANIFEST=' + str(manifest),
                '-DICON=' + str(ROOT / 'bundles/cn.zencode.ide/icons/FelixMantra.ico'),
                str(ROOT / 'packaging/windows/installer.nsi')], check=True)
digest = hashlib.sha256(output.read_bytes()).hexdigest()
output.with_suffix('.exe.sha256').write_text(digest + '  ' + output.name + '\n')
print(f'Built {output}\n{output.stat().st_size:,} bytes\nSHA-256 {digest}')
