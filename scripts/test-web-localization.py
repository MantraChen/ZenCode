#!/usr/bin/env python3
"""Check localized wizard keys and Java resource fallback against the shipped host JARs."""
from pathlib import Path
import subprocess, zipfile, tempfile
root = Path(__file__).resolve().parents[1]
plugins = root / 'products/cn.zencode.product/target/products/cn.zencode.product/win32/win32/x86_64/plugins'
java = r"""
import java.io.*; import java.net.*; import java.util.*; import java.util.regex.*;
public class Check {
 public static void main(String[] args) throws Exception {
  URL[] urls={new File(args[0]).toURI().toURL(),new File(args[1]).toURI().toURL(),new File(args[2]).toURI().toURL()};
  try(URLClassLoader loader=new URLClassLoader(urls,null)) {
   Properties local=new Properties(), base=new Properties();
   try(InputStream in=loader.getResourceAsStream(args[3]+"_zh_CN.properties")) {local.load(in);}
   try(InputStream in=new java.util.jar.JarFile(args[1]).getInputStream(new java.util.jar.JarEntry(args[3]+".properties"))) {base.load(in);}
   ResourceBundle bundle=ResourceBundle.getBundle(args[3].replace('/','.'),Locale.SIMPLIFIED_CHINESE,loader);
   for(String key:local.stringPropertyNames()) {
    if(!base.containsKey(key))throw new AssertionError("Unknown key "+key);
    if(!local.getProperty(key).equals(bundle.getString(key)))throw new AssertionError("Locale fallback "+key);
    if(!tokens(base.getProperty(key)).equals(tokens(local.getProperty(key))))throw new AssertionError("Placeholder mismatch "+key);
   }
   System.out.println("PASS "+args[3]+": "+local.size()+" keys");
  }
 }
 static Set<String> tokens(String text) {
  Set<String> result=new TreeSet<String>(); Matcher m=Pattern.compile("[{][0-9]+[}]").matcher(text);
  while(m.find())result.add(m.group()); return result;
 }
}
"""
with tempfile.TemporaryDirectory(prefix='zencode-l10n-') as td:
    td = Path(td); (td/'Check.java').write_text(java)
    subprocess.run(['javac','--release','8','-d',str(td),str(td/'Check.java')],check=True)
    count=0
    for fragment in sorted(plugins.glob('cn.zencode.nls.web*.jar')):
        with zipfile.ZipFile(fragment) as z:
            manifest=z.read('META-INF/MANIFEST.MF').decode()
            host=next(l.split(': ',1)[1].split(';')[0] for l in manifest.splitlines() if l.startswith('Fragment-Host:'))
            hostjar=next(plugins.glob(host+'_[0-9]*.jar'))
            babel=next(plugins.glob(host+'.nl_zh_*.jar'))
            for resource in z.namelist():
                if resource.endswith('_zh_CN.properties'):
                    subprocess.run(['java','-cp',str(td),'Check',str(fragment),str(hostjar),str(babel),resource[:-17]],check=True)
                    count+=1
    assert count>=12, 'Missing language fragment resources'
    print('PASS',count,'resource bundles; desktop layout still requires Windows validation.')
