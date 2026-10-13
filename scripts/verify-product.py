#!/usr/bin/env python3
"""Inspect a built Windows product without claiming to run it on Windows."""
from pathlib import Path
import io
import struct
import sys
import zipfile

base = Path(__file__).resolve().parents[1]
archives = sorted((base / 'products/cn.zencode.product/target/products').glob('*.zip'))
if len(sys.argv) > 1:
    archives = [Path(sys.argv[1])]
if not archives:
    raise SystemExit('No Windows product ZIP; run mvn -f releng/pom.xml verify first.')
for archive in archives:
    with zipfile.ZipFile(archive) as product:
        names = product.namelist()
        for suffix in ('ZenCode.exe', 'ZenCode.ini', 'configuration/config.ini'):
            assert any(n.endswith(suffix) for n in names), 'Missing ' + suffix
        for bundle in ('cn.zencode.assist.core_', 'cn.zencode.ide_', 'org.eclipse.jdt.core_', 'org.eclipse.jst.server.tomcat.core_'):
            assert any('/' + bundle in '/' + n for n in names), 'Missing ' + bundle
        compiled = 0
        dependency_classes = 0
        for name in names:
            if not name.endswith('.jar') or '/plugins/' not in '/' + name:
                continue
            with zipfile.ZipFile(io.BytesIO(product.read(name))) as plugin:
                for member in plugin.namelist():
                    if member.endswith('.class') and not member.startswith('META-INF/versions/') and not member.endswith('module-info.class'):
                        header = plugin.read(member)[:8]
                        magic, minor, major = struct.unpack('>IHH', header)
                        assert magic == 0xCAFEBABE and major <= 52, (name, member, major)
                        dependency_classes += 1
                        if '/cn.zencode.' in '/' + name:
                            compiled += 1
        assert compiled > 0, 'Missing compiled ZenCode classes'
        ini = next(n for n in names if n.endswith('ZenCode.ini'))
        assert '-Xmx768m' in product.read(ini).decode('utf-8'), 'Missing bounded heap setting'
        config = next(n for n in names if n.endswith('configuration/config.ini'))
        assert 'eclipse.product=cn.zencode.ide.product' in product.read(config).decode('utf-8'), 'Wrong product entry point'
        print(f'PASS {archive.name}: launcher/configuration/bundles; {compiled} ZenCode classes target Java 8 or earlier.')
        print(f'PASS {dependency_classes} top-level plugin classes target Java 8 or earlier (nested JARs not covered).')
        print('This is archive validation only. Windows launch, UI and memory checks are still required.')
