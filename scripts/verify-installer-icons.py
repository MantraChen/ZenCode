from pathlib import Path
import struct

def icons(exe):
 b=exe.read_bytes();pe=struct.unpack_from('<I',b,60)[0];n=struct.unpack_from('<H',b,pe+6)[0];osize=struct.unpack_from('<H',b,pe+20)[0];opt=pe+24
 dd=opt+(96 if struct.unpack_from('<H',b,opt)[0]==0x10b else 112)
 rva=struct.unpack_from('<I',b,dd+16)[0]
 sections=[]
 for i in range(n):
  s=opt+osize+40*i;vsize,va,rs,offset=struct.unpack_from('<IIII',b,s+8);sections.append((va,max(vsize,rs),offset))
 def raw(r):
  for va,size,offset in sections:
   if va<=r<va+size:return offset+r-va
  raise ValueError(r)
 root=raw(rva)
 def entries(at):
  named,count=struct.unpack_from('<HH',b,root+at+12)
  return [struct.unpack_from('<II',b,root+at+16+8*i) for i in range(named+count)]
 found=[]
 def descend(at):
  for name,offset in entries(at):
   if offset&0x80000000:descend(offset&0x7fffffff)
   else:
    data,size=struct.unpack_from('<II',b,root+offset);found.append(b[raw(data):raw(data)+size])
 for typ,offset in entries(0):
  if typ==3:descend(offset&0x7fffffff)
 return found
ROOT = Path(__file__).resolve().parents[1]
ico=(ROOT / 'bundles/cn.zencode.ide/icons/FelixMantra.ico').read_bytes();count=struct.unpack_from('<H',ico,4)[0];expected=[]
for i in range(count):
 size,offset=struct.unpack_from('<II',ico,6+16*i+8);expected.append(ico[offset:offset+size])
import argparse
parser=argparse.ArgumentParser(description='Verify install and uninstall icons after extracting the NSIS archive with 7zz.')
parser.add_argument('installer',type=Path)
parser.add_argument('extracted',type=Path)
args=parser.parse_args()
exe=args.installer
for f in [exe,args.extracted/'Uninstall.exe']:
 actual=icons(f);matched=sum(x in actual for x in expected)
 assert matched==len(expected),(f,matched,len(expected))
 print('PASS FelixMantra icon entries',matched,f.name)
