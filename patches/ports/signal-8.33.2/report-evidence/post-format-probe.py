import sys, types, os, subprocess, pathlib, hashlib, re, csv, shutil
sys.modules['release']=types.ModuleType('release')
pr=types.ModuleType('port_report')
exec(subprocess.run(['git','show','fbee68f36ccb20cb3c76cb80359ecd7d423efcad:tools/patches/port_report.py'],check=True,stdout=subprocess.PIPE).stdout,pr.__dict__)
ROOT=pathlib.Path.home()/'agent-scratch/mollyim-android/pr-stack'
WORK=ROOT/'post-format-module-gates'
WORK.mkdir(exist_ok=True)
M='759ef1c309d93aa7e4ac7217b144c04545562f11'; T='f25a1de0fc87f0f862cc270f823d240857878bc5'; T3='3a20fd39d1f0e444bcc4711a2909635d8762b67e'
A='/var/home/x/.gradle/caches/9.6.0/transforms/db7e385e81351d1a8da4d35a4e20239c/transformed/aapt2-9.4.0-15978811-linux/aapt2'
def run(*a,**kw): return subprocess.run(a,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,**kw).stdout
def git(*a,**kw): return run('git',*a,**kw)
stats=pr.numstat(M,T); modes=pr.modes(M,T); selected=[]
for path in sorted(stats):
 if not re.fullmatch(rb'(?!app/).+/src/main/res/values-[^/]+/strings\.xml',path): continue
 oldmode,newmode,oldblob,newblob=modes[path]
 if oldmode != '100644' or newmode != '100644' or stats[path] is None: continue
 p=os.fsdecode(path); patch=git('diff','--no-ext-diff','--no-renames','--unified=3',M,T,'--',p)
 parsed=pr.parse_hunks(patch); contents=[body.split(b'\n',1)[1] for _,_,body in parsed]; occurrences={}
 header=patch[:patch.index(b'@@ ')]; header=b''.join(l for l in header.splitlines(keepends=True) if not l.startswith(b'index '))
 for _,_,body in parsed:
  content=body.split(b'\n',1)[1]; occurrences[content]=occurrences.get(content,0)+1
  hid=hashlib.sha256(b'\0'.join((path,b'text',content,str(contents.count(content)).encode(),str(occurrences[content]).encode()))).hexdigest()[:20]
  selected.append((p,hid,newblob.decode(),header+body))
assert selected
checked={}
for p,hid,blob,patch in selected:
 if p in checked:
  assert hid in checked[p]
  continue
 assert git('rev-parse',T3+':'+p).strip().decode()==blob
 # Equal entire file blobs imply equal report IDs; explicitly recompute from PR3 diff.
 d=git('diff','--no-ext-diff','--no-renames','--unified=3',M,T3,'--',p); hunks=pr.parse_hunks(d); cs=[b.split(b'\n',1)[1] for _,_,b in hunks]; occ={}; ids=[]
 for c in cs:
  occ[c]=occ.get(c,0)+1; ids.append(hashlib.sha256(b'\0'.join((os.fsencode(p),b'text',c,str(cs.count(c)).encode(),str(occ[c]).encode()))).hexdigest()[:20])
 checked[p]=set(ids)
 assert hid in ids
print('selected',len(selected),'hunks',len(set(x[0] for x in selected)),'files',flush=True)
print('first',selected[0][0],selected[0][1],'last',selected[-1][0],selected[-1][1],flush=True)
print('aapt2',subprocess.run([A,'version'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True).stdout.decode().strip(),hashlib.sha256(pathlib.Path(A).read_bytes()).hexdigest(),flush=True)
if len(sys.argv)==1: sys.exit()
objects=WORK/'objects'; objects.mkdir(exist_ok=True); (objects/'info').mkdir(exist_ok=True); (objects/'pack').mkdir(exist_ok=True)
common=pathlib.Path(os.fsdecode(git('rev-parse','--git-common-dir').strip())).resolve()/'objects'
env=dict(os.environ,GIT_INDEX_FILE=str(WORK/'index'),GIT_OBJECT_DIRECTORY=str(objects),GIT_ALTERNATE_OBJECT_DIRECTORIES=str(common))
res=WORK/'input'; out=WORK/'output'; out.mkdir(exist_ok=True)
def materialize(p,data):
 shutil.rmtree(res,ignore_errors=True); f=res/pathlib.Path('res')/pathlib.Path(p).parent.name/'strings.xml'; f.parent.mkdir(parents=True); f.write_bytes(data); return f
def compile_file(f):
 for q in out.iterdir(): q.unlink()
 r=subprocess.run([A,'compile','-o',str(out),str(f)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 return r.returncode,(r.stdout+r.stderr).decode('utf-8','replace').replace(str(WORK),'<WORK>').strip()
base={}
for p,_,blob,_ in selected:
 if p not in base:
  f=materialize(p,git('cat-file','blob',blob)); code,diag=compile_file(f); base[p]=(code,diag)
  if code: raise RuntimeError('baseline failed '+p+': '+diag)
print('baseline passed',len(base),flush=True)
fields=['hunk_id','path','target_blob','reverted_blob','reverse_patch_sha256','source3_source4_file_and_hunk_match','compiler_sha256','compiler_version','command','baseline_exit','revert_exit','diagnostic','triage','molly_provenance_lead']
sha=hashlib.sha256(pathlib.Path(A).read_bytes()).hexdigest(); version=subprocess.run([A,'version'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True).stdout.decode().strip()
with (WORK/'cohort.tsv').open('w',newline='') as fo, (WORK/'cohort.patch').open('wb') as po:
 w=csv.DictWriter(fo,fieldnames=fields,delimiter='\t',lineterminator='\n'); w.writeheader()
 for i,(p,hid,blob,patch) in enumerate(selected,1):
  git('read-tree',T,env=env)
  subprocess.run(['git','apply','--cached','-R','--whitespace=nowarn','-'],input=patch,env=env,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  changed=git('diff','--cached','--name-only','-z','--no-renames',T,env=env).split(b'\0'); assert changed==[os.fsencode(p),b''],changed
  entry=git('ls-files','--stage','-z','--',p,env=env).split(b'\t',1)[0].split(); assert entry[0]==b'100644'; reverted=entry[1].decode()
  f=materialize(p,git('cat-file','blob',reverted,env=env)); code,diag=compile_file(f)
  po.write(b'# hunk '+hid.encode()+b' sha256 '+hashlib.sha256(patch).hexdigest().encode()+b'\n'+patch+b'\n')
  w.writerow(dict(hunk_id=hid,path=p,target_blob=blob,reverted_blob=reverted,reverse_patch_sha256=hashlib.sha256(patch).hexdigest(),source3_source4_file_and_hunk_match='yes',compiler_sha256=sha,compiler_version=version,command='aapt2 compile -o <OUTPUT> <INPUT>/'+str(pathlib.Path('res')/pathlib.Path(p).parent.name/'strings.xml'),baseline_exit=0,revert_exit=code,diagnostic=diag,triage='pass_requires_link_or_semantic_review' if code==0 else 'file_compile_failure',molly_provenance_lead='0e202dda90785771ff8c87526eaaf30c655f54f3 / mollyify transform; not per-hunk history proof'))
  fo.flush(); print(i,hid,code,flush=True)
shutil.rmtree(objects); shutil.rmtree(res); shutil.rmtree(out); (WORK/'index').unlink()
