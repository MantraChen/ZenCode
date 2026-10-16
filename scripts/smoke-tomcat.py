#!/usr/bin/env python3
"""Offline Tomcat smoke check. Requires external Tomcat dirs and a local JDK 8.
Example: python3 scripts/smoke-tomcat.py --java-home /jdk8 /tomcat7 /tomcat8 /tomcat85 /tomcat9
Starts only child JVMs owned by this script; never touches an installed server's base.
"""
import argparse
import json
import platform
import hashlib
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.request
import zipfile
import re


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def check(home, java_home):
    home = home.resolve()
    with zipfile.ZipFile(home / 'lib/catalina.jar') as jar:
        info = jar.read('org/apache/catalina/util/ServerInfo.properties').decode('ISO-8859-1')
    version = re.search(r'^server.number=(.+)$', info, re.M).group(1).strip()
    if not re.fullmatch(r'(7\.0|8\.0|8\.5|9\.0)\.\d+(\.\d+)?', version):
        raise ValueError('Unsupported Tomcat: ' + version)
    suffix = '.exe' if os.name == 'nt' else ''
    java = java_home / 'bin' / ('java' + suffix)
    javac = java_home / 'bin' / ('javac' + suffix)
    with tempfile.TemporaryDirectory(prefix='zencode-tomcat-') as temp:
        base = Path(temp)
        for name in ['logs', 'temp', 'work', 'webapps', 'conf']:
            (base / name).mkdir()
        shutil.copytree(home / 'conf', base / 'conf', dirs_exist_ok=True)
        http_port = port()
        (base / 'conf/server.xml').write_text('''<Server port="-1"><Service name="Catalina"><Connector address="127.0.0.1" port="%d" protocol="HTTP/1.1"/><Engine name="Catalina" defaultHost="localhost"><Host name="localhost" appBase="webapps" autoDeploy="false" unpackWARs="true"/></Engine></Service></Server>''' % http_port, encoding='utf-8')
        app = base / 'webapps/smoke'
        exported = base / 'exported-template'
        helper = base / 'helper'
        helper.mkdir()
        source_file = Path(__file__).resolve().parents[1] / 'bundles/cn.zencode.assist.core/src/cn/zencode/assist/core/runtime/OfflineWebTemplate.java'
        exporter = helper / 'ExportTemplate.java'
        exporter.write_text('import java.nio.file.*; public class ExportTemplate { public static void main(String[] a)throws Exception { cn.zencode.assist.core.runtime.OfflineWebTemplate.create(Paths.get(a[0])); }}', encoding='utf-8')
        subprocess.run([str(javac), '-encoding', 'UTF-8', '-d', str(helper), str(source_file), str(exporter)], check=True)
        subprocess.run([str(java), '-cp', str(helper), 'ExportTemplate', str(exported)], check=True)
        shutil.copytree(exported / 'WebContent', app)
        classes = app / 'WEB-INF/classes'
        classes.mkdir(parents=True)
        sources = [str(path) for path in (exported / 'src').rglob('*.java')]
        subprocess.run([str(javac), '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', str(home / 'lib/servlet-api.jar'), '-d', str(classes)] + sources, check=True)
        command = [str(java), '-Dcatalina.home=' + str(home), '-Dcatalina.base=' + str(base), '-Djava.io.tmpdir=' + str(base / 'temp'), '-classpath', os.pathsep.join(str(home / 'bin' / jar) for jar in ['bootstrap.jar', 'tomcat-juli.jar']), 'org.apache.catalina.startup.Bootstrap', 'start']
        with (base / 'console.log').open('wb') as output:
            process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 60
                while True:
                    try:
                        with urllib.request.urlopen('http://127.0.0.1:%d/smoke/index.jsp' % http_port, timeout=2) as response:
                            assert 'READY / JSP' in response.read().decode('utf-8')
                        break
                    except Exception:
                        if process.poll() is not None or time.monotonic() >= deadline:
                            raise RuntimeError((base / 'console.log').read_text(errors='replace'))
                        time.sleep(.25)
                with urllib.request.urlopen('http://127.0.0.1:%d/smoke/hello' % http_port, timeout=5) as response:
                    assert response.read().decode('utf-8').strip() == 'READY / Servlet'
                print('PASS Tomcat ' + version + ': exported template JSP compilation + annotation Servlet + JDBC compile (Java 8)')
                return {'tomcat_version': version, 'catalina_jar_sha256': hashlib.sha256((home / 'lib/catalina.jar').read_bytes()).hexdigest(), 'jsp': 'passed', 'servlet': 'passed', 'jdbc_source_compile': 'passed', 'jdbc_connection': 'not_tested'}
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--report-json', type=Path, help='Write machine-readable host/version/results evidence')
    parser.add_argument('tomcat', nargs='+', type=Path)
    args = parser.parse_args()
    java_home = args.java_home.resolve()
    java_version = subprocess.check_output([str(java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')), '-version'], stderr=subprocess.STDOUT).decode(errors='replace')
    if not re.search(r'version \"1\.8\.', java_version):
        raise SystemExit('This verification requires JDK 8; actual: ' + java_version)
    report = {'host': platform.platform(), 'architecture': platform.machine(), 'java_version': java_version, 'windows_ui_tested': False, 'results': []}
    for installation in args.tomcat:
        report['results'].append(check(installation, java_home))
    if args.report_json:
        args.report_json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
