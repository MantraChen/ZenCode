#!/usr/bin/env python3
"""Use the materialized product's exact dependencies to test real JDT bindings headlessly."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
plugins = root / 'products/cn.zencode.product/target/products/cn.zencode.product/win32/win32/x86_64/plugins'
if not plugins.is_dir():
    raise SystemExit('Build the desktop product first.')
# Outside OSGi, prioritize the JDT core's compiler over WTP's separately isolated ECJ.
jars = sorted(plugins.glob('*.jar'), key=lambda p: (not p.name.startswith('org.eclipse.jdt.core_'), p.name))
output = root / 'integration-tests/target'
output.mkdir(exist_ok=True)
classpath = os.pathsep.join(map(str, jars))
source = root / 'integration-tests/QuickFixSmoke.java'
def run_args(executable, arguments):
    # JDK argument files also avoid Windows' command-line length limit.
    args_file = output / (executable + '.args')
    quoted = ['"' + str(arg).replace('\\', '/').replace('"', '\\"') + '"' for arg in arguments]
    args_file.write_text('\n'.join(quoted), encoding='utf-8')
    result = subprocess.run([executable, '@' + str(args_file)])
    if result.returncode:
        raise SystemExit(result.returncode)

run_args('javac', ['--release', '8', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(output), str(source)])
run_args('java', ['-cp', str(output) + os.pathsep + classpath, 'QuickFixSmoke'])
