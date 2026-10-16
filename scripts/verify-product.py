#!/usr/bin/env python3
"""Inspect a built Windows product without claiming to run it on Windows."""
from pathlib import Path
import io
import struct
import sys
import zipfile
import xml.etree.ElementTree as ET

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
        # Language fragments must ship in the offline archive, not just appear in the product definition.
        for host in ('org.eclipse.ui.workbench', 'org.eclipse.ui.ide', 'org.eclipse.jdt.ui',
                     'org.eclipse.wst.html.ui', 'org.eclipse.wst.css.ui', 'org.eclipse.jst.jsp.ui',
                     'org.eclipse.wst.server.ui'):
            fragment = next((n for n in names if '/' + host + '.nl_zh_' in '/' + n and n.endswith('.jar')), None)
            assert fragment, 'Missing Chinese language fragment: ' + host
            with zipfile.ZipFile(io.BytesIO(product.read(fragment))) as language:
                assert any(n.endswith('_zh.properties') for n in language.namelist()), 'Empty language fragment: ' + host
        ide_name = next(n for n in names if '/cn.zencode.ide_' in '/' + n and n.endswith('.jar'))
        with zipfile.ZipFile(io.BytesIO(product.read(ide_name))) as ide:
            descriptor = ET.fromstring(ide.read('plugin.xml'))
            commands = {c.get('id') for c in descriptor.findall("./extension[@point='org.eclipse.ui.commands']/command")}
            for action in ('open', 'help', 'dark', 'light', 'webProject', 'tomcatCheck', 'webCheck', 'database', 'webTemplate', 'newWebSample', 'configureServer', 'runServer', 'debugServer'):
                assert 'cn.zencode.ide.' + action in commands, 'Missing menu action: ' + action
            assert descriptor.find("./extension[@point='org.eclipse.ui.menus']/menuContribution/menu[@id='cn.zencode.ide.menu']") is not None
            for asset in ('css/terminal.css', 'css/light.css', 'cn/zencode/ide/ThemeController.class',
                          'cn/zencode/ide/ThemeStartup.class', 'cn/zencode/ide/WorkbenchCommand.class'):
                assert asset in ide.namelist(), 'Missing theme/menu asset: ' + asset
        wizard_found = False
        for member in names:
            if '/plugins/' not in '/' + member or not member.endswith('.jar'):
                continue
            with zipfile.ZipFile(io.BytesIO(product.read(member))) as bundle:
                if 'plugin.xml' in bundle.namelist():
                    try:
                        xml = ET.fromstring(bundle.read('plugin.xml'))
                        wizard_found |= any(w.get('id') == 'org.eclipse.jst.servlet.ui.project.facet.WebProjectWizard'
                            for w in xml.findall("./extension[@point='org.eclipse.ui.newWizards']/wizard"))
                    except ET.ParseError:
                        pass
        assert wizard_found, 'Missing actual WTP Dynamic Web Project wizard registration'
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
        ini_lines = product.read(ini).decode('utf-8').splitlines()
        assert ini_lines[ini_lines.index('-nl') + 1] == 'zh_CN', 'Chinese locale not selected'
        config = next(n for n in names if n.endswith('configuration/config.ini'))
        assert 'eclipse.product=cn.zencode.ide.product' in product.read(config).decode('utf-8'), 'Wrong product entry point'
        print(f'PASS {archive.name}: launcher/configuration/bundles; {compiled} ZenCode classes target Java 8 or earlier.')
        print(f'PASS {dependency_classes} top-level plugin classes target Java 8 or earlier (nested JARs not covered).')
        print('PASS offline Chinese language resources, Chinese locale, theme assets and global menu commands.')
        print('This is archive validation only. Windows launch, UI and memory checks are still required.')
