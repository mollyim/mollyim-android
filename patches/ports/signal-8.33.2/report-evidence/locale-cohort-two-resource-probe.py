import os, pathlib, subprocess, xml.etree.ElementTree as ET, shutil, hashlib
root=pathlib.Path.home()/'agent-scratch/mollyim-android/pr-stack'; work=root/'locale-cohort-two'/'resource-work'; res=work/'res'; out=work/'out'
aapt=pathlib.Path('/var/home/x/.gradle/caches/9.6.0/transforms/db7e385e81351d1a8da4d35a4e20239c/transformed/aapt2-9.4.0-15978811-linux/aapt2')
jar=pathlib.Path.home()/'Android/Sdk/platforms/android-36.1/android.jar'
T='2b0ba46b23fd27974b8cefbd36e0d91791b525bd'; OLD='0e202dda90785771ff8c87526eaaf30c655f54f3'
quals=['bg','bn','bs','ca']
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
try:
 work.mkdir(exist_ok=True); out.mkdir(); base=entry(T,'app/src/main/res/values/strings2.xml'); oldbase=entry(OLD,'app/src/main/res/values/strings.xml'); assert ET.tostring(base)==ET.tostring(oldbase)
 files=[mk('',base)]; expected={}
 for q in quals:
  path=f'app/src/main/res/values-{q}/strings.xml'; target=entry(T,path); old=entry(OLD,path); assert ET.tostring(target)==ET.tostring(old); expected[q]=target.text; files.append(mk(q,target))
 manifest=work/'AndroidManifest.xml'; manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example.localeprobe"><application/></manifest>')
 for f in files: run(aapt,'compile','-o',out,f)
 flats=sorted(out.glob('*.flat')); assert len(flats)==5
 print('AAPT2',run(aapt,'version').strip(),'sha256',hashlib.sha256(aapt.read_bytes()).hexdigest())
 print('ANDROID_JAR',jar,'sha256',hashlib.sha256(jar.read_bytes()).hexdigest())
 for case in ['baseline']+quals:
  chosen=[f for f in flats if case=='baseline' or f.name!=f'values-{case}_strings.arsc.flat']
  assert len(chosen)==(5 if case=='baseline' else 4),(case,chosen)
  apk=work/(case+'.apk'); run(aapt,'link','--manifest',manifest,'-I',jar,'-o',apk,*chosen)
  dump=run(aapt,'dump','resources',apk)
  print('\nCASE',case,'\n'+dump)
  assert base.text in dump
  for q,value in expected.items():
   if q==case: assert value not in dump,(q,dump)
   else: assert value in dump,(q,dump)
 print('ASSERTIONS: exact old Molly/default values preserved in baseline; each counterfactual loses only the selected locale value; default remains')
finally:
 for item in list(work.iterdir()):
  if item.name=='probe.py': continue
  if item.is_dir(): shutil.rmtree(item)
  else: item.unlink()
