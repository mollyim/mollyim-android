import os,pathlib,subprocess,shutil,xml.etree.ElementTree as ET, hashlib
P=pathlib.Path(__file__).resolve().parent; W=pathlib.Path(os.environ['EVIDENCE_WORK']); repo=pathlib.Path.cwd()
T='2b0ba46b23fd27974b8cefbd36e0d91791b525bd'; S='6e9dddc01f1b26e608ac683807c51e139c141ed2'; A=pathlib.Path(os.environ['AAPT2']); J=pathlib.Path(os.environ['ANDROID_JAR'])
def call(args,**kw): return subprocess.run([str(x) for x in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,**kw)
def git(*a): return call(['git',*a],cwd=repo,check=True).stdout
def entry(ref,path,key):
 e=next(e for e in ET.fromstring(git('show',ref+':'+path)) if e.attrib.get('name')==key); e.tail=None; return e
W.mkdir()
try:
 path='app/src/main/res/values-ky/strings.xml'; original=git('show',T+':'+path); f=W/path; f.parent.mkdir(parents=True); fragments=(P/'locale-remaining-cohort.patch').read_bytes()
 for hid in ['2fcbd48f7e04b0b3dfb1','6cf83bac075165093e80']:
  f.write_bytes(original); patch=fragments.split(b'# hunk '+hid.encode()+b' ',1)[1].split(b'\n',1)[1].split(b'\n# hunk ',1)[0]; base=call(['rg','-n','^>>>>>>> SIGNAL$',f]); assert base.returncode==1
  a=call(['git','apply','-R','--whitespace=nowarn','-'],input=patch,cwd=W); assert a.returncode==0,a.stdout
  rev=call(['rg','-n','^>>>>>>> SIGNAL$',f]); assert rev.returncode==0 and rev.stdout.count(b'>>>>>>> SIGNAL')==1
  print('MARKER',hid,'baseline rg exit',base.returncode,'inverse rg exit',rev.returncode,'inverse output',rev.stdout.decode().strip())
 print('AAPT sha256',hashlib.sha256(A.read_bytes()).hexdigest(),'android.jar sha256',hashlib.sha256(J.read_bytes()).hexdigest())
 for hid,key in [('9e219bacc82afa4c3b39','DeprecatedNotificationJob_update_signal'),('2ba53a3a1f452b453e4a','GroupLinkBottomSheet_share_via_signal'),('89596a0601c89ef6401b','UpdateSignalExpiredDialog__title')]:
  ko='app/src/main/res/values-ko/strings.xml'; base=entry(T,'app/src/main/res/values/strings.xml',key); assert base.attrib.pop('mollyify')=='true'; cur=entry(T,ko,key); sig=entry(S,ko,key); assert sig.text.replace('signal.org','molly.im').replace('Signal','Molly')==cur.text
  d=W/'res'; out=W/'out'; d.mkdir();out.mkdir(); manifest=W/'AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example.brandprobe"><application/></manifest>')
  def xml(q,e):
   sub=d/('values'+('-'+q if q else ''));sub.mkdir(exist_ok=True); f=sub/'strings.xml';f.write_bytes(b'<resources>'+ET.tostring(e)+b'</resources>'); r=call([A,'compile','-o',out,f]); assert r.returncode==0,r.stdout; return sorted(out.glob('*.flat'))
  xml('',base); xml('ko',cur)
  for case,e in [('baseline',cur),('inverse',sig)]:
   xml('ko',e); apk=W/(case+'.apk'); r=call([A,'link','--manifest',manifest,'-I',J,'-o',apk,*sorted(out.glob('*.flat'))]); assert r.returncode==0,r.stdout; dump=call([A,'dump','resources',apk],check=True).stdout.decode(); assert '(ko) "'+e.text+'"' in dump and ('(ko) "'+cur.text+'"' in dump)==(case=='baseline')
   print('BRANDING',hid,key,case,'compile=0 link=0','assert-current-Molly=', 'PASS' if case=='baseline' else 'FAIL', 'value=',e.text)
  shutil.rmtree(d);shutil.rmtree(out)
finally: shutil.rmtree(W,ignore_errors=True)
