import os, pathlib, subprocess, xml.etree.ElementTree as ET, shutil, hashlib
root=pathlib.Path(__file__).resolve().parent; work=pathlib.Path(os.environ['EVIDENCE_WORK']); res=work/'res'; out=work/'out'
aapt=pathlib.Path(os.environ['AAPT2'])
jar=pathlib.Path(os.environ['ANDROID_JAR'])
T='2b0ba46b23fd27974b8cefbd36e0d91791b525bd'; OLD='0e202dda90785771ff8c87526eaaf30c655f54f3'
import csv
rows=list(csv.DictReader((root/'locale-remaining-cohort.tsv').open(),delimiter='\t'))
quals=[r['path'].split('/values-')[1].split('/')[0] for r in rows if r['revert_exit']=='0' and r['hunk_id'] not in {'9e219bacc82afa4c3b39','2ba53a3a1f452b453e4a','89596a0601c89ef6401b','2fcbd48f7e04b0b3dfb1','6cf83bac075165093e80'}]
assert len(quals)==len(set(quals))
def run(*args):
 p=subprocess.run([str(x) for x in args],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if p.returncode: raise RuntimeError(f'command {args} exit {p.returncode}: {(p.stdout+p.stderr).decode()}')
 return (p.stdout+p.stderr).decode()
def entry(ref,path):
 b=subprocess.run(['git','show',ref+':'+path],stdout=subprocess.PIPE,check=True).stdout
 matches=[e for e in ET.fromstring(b) if e.attrib.get('name')=='please_wait']; assert len(matches)==1
 matches[0].tail=None; return matches[0]
def mk(q,e):
 d=res/('values'+('-'+q if q else '')); d.mkdir(parents=True,exist_ok=True)
 f=d/'strings.xml'; f.write_bytes(b'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'+ET.tostring(e,encoding='utf-8')+b'\n</resources>\n'); return f
work.mkdir()
try:
 out.mkdir(); base=entry(T,'app/src/main/res/values/strings2.xml'); oldbase=entry(OLD,'app/src/main/res/values/strings.xml'); assert ET.tostring(base)==ET.tostring(oldbase)
 files=[mk('',base)]; expected={}
 for q in quals:
  path=f'app/src/main/res/values-{q}/strings.xml'; target=entry(T,path); old=entry(OLD,path); assert ET.tostring(target)==ET.tostring(old); expected[q]=target.text; files.append(mk(q,target))
 manifest=work/'AndroidManifest.xml'; manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example.localeprobe"><application/></manifest>')
 for f in files: run(aapt,'compile','-o',out,f)
 flats=sorted(out.glob('*.flat')); assert len(flats)==len(quals)+1
 print('AAPT2',run(aapt,'version').strip(),'sha256',hashlib.sha256(aapt.read_bytes()).hexdigest())
 print('ANDROID_JAR',jar,'sha256',hashlib.sha256(jar.read_bytes()).hexdigest())
 baseline_lines=None
 for case in ['baseline']+quals:
  chosen=[f for f in flats if case=='baseline' or f.name!=f'values-{case}_strings.arsc.flat']
  assert len(chosen)==(len(quals)+1 if case=='baseline' else len(quals)),(case,chosen)
  apk=work/(case+'.apk'); run(aapt,'link','--manifest',manifest,'-I',jar,'-o',apk,*chosen)
  dump=run(aapt,'dump','resources',apk)
  print('\nCASE',case,'\n'+dump)
  assert base.text in dump
  lines=set(dump.splitlines())
  if case=='baseline':
   baseline_lines=lines
   for q,value in expected.items(): assert value in dump,(q,dump)
  else:
   removed=baseline_lines-lines; added=lines-baseline_lines
   assert len(removed)==1 and not added,(case,removed,added)
   assert expected[case] in next(iter(removed)),(case,removed,expected[case])
   print('PRESERVATION_ASSERTION',case,'FAIL: exact old Molly locale configuration missing; link succeeded')
 print('ASSERTIONS: exact old Molly/default values preserved in baseline; each counterfactual loses only the selected locale value; default remains')
finally:
 for item in list(work.iterdir()):
  if item.name=='probe.py': continue
  if item.is_dir(): shutil.rmtree(item)
  else: item.unlink()
