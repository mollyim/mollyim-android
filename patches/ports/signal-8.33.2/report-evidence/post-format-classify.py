import os,pathlib,subprocess,csv,re,ast,xml.dom.minidom as D,xml.parsers.expat,collections,hashlib,shutil
P=pathlib.Path.home()/'agent-scratch/mollyim-android/pr-stack'; W=P/'post-format-module-gates'; T='f25a1de0fc87f0f862cc270f823d240857878bc5'; S='6e9dddc01f1b26e608ac683807c51e139c141ed2'
def git(*a,env=None,input=None):
 p=subprocess.run(['git',*a],env=env,input=input,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True); return p.stdout
source=(P/'translation-xml-proof.py').read_text(); mod=ast.parse(source); exec(compile(ast.Module(body=[n for n in mod.body if isinstance(n,ast.FunctionDef) and n.name in ('dom','text','norm','elements','transform')],type_ignores=[]),'<translation-proof-functions>','exec'))
rows=list(csv.DictReader((W/'cohort.tsv').open(),delimiter='\t')); assert len(rows)==524
patches={}
for chunk in (W/'cohort.patch').read_bytes().split(b'# hunk ')[1:]:
 h,p=chunk.split(b'\n',1); patches[h.split()[0].decode()]=p.rstrip(b'\n')+b'\n'
assert len(patches)==len(rows)
obj=W/'objects'; (obj/'info').mkdir(parents=True); (obj/'pack').mkdir(); common=pathlib.Path(os.fsdecode(git('rev-parse','--git-common-dir').strip())).resolve()/'objects'; env=dict(os.environ,GIT_INDEX_FILE=str(W/'index'),GIT_OBJECT_DIRECTORY=str(obj),GIT_ALTERNATE_OBJECT_DIRECTORIES=str(common))
assert git('rev-parse',T+':build-logic/plugins/src/main/java/mollyify.gradle.kts').decode().strip()=='266df84b06b09453f47b7c0dd59561a8001d88af'
cache={}; result=[]; marker=re.compile(rb'(?m)^\s*(?:<{7}|={7}|>{7})(?:\s|$)')
for r in rows:
 p=r['path']; hid=r['hunk_id']; patch=patches[hid]; assert hashlib.sha256(patch).hexdigest()==r['reverse_patch_sha256']
 if p not in cache:
  current=git('show',T+':'+p); assert not marker.search(current); cur=elements(dom(current)); base=p.split('/src/main/res/')[0]+'/src/main/res/values/strings.xml'; b=dom(git('show',T+':'+base)); marks={e.getAttribute('name') for tag in ('string','plurals') for e in b.getElementsByTagName(tag) if e.getAttribute('mollyify')=='true'}; predicted,_=transform(git('show',S+':'+p),marks); cache[p]=(current,cur,predicted)
 current,cur,predicted=cache[p]; outcome='file_compile_failure'; detail=''; equal='unmeasured'
 if r['revert_exit']=='0':
  git('read-tree',T,env=env); git('apply','--cached','-R','--whitespace=nowarn','-',env=env,input=patch); assert git('ls-files','--stage','--',p,env=env).split()[1].decode()==r['reverted_blob']; after=git('show',':'+p,env=env); match=marker.search(after)
  if match: outcome='restored_anchored_conflict_marker'; detail='line '+str(after[:match.start()].count(b'\n')+1)+'; '+repr(match.group().decode())
  else:
   try: inv=elements(dom(after))
   except xml.parsers.expat.ExpatError as e: outcome='invalid_xml_other'; detail=str(e)
   else:
    equal=str(predicted==cur)
    if predicted==cur and predicted!=inv: outcome='modeled_transform_value_contract_changed'
    elif cur==inv: outcome='resource_identity'
    else: outcome='unmeasured_change'; detail='model current match='+equal
 r.update(contract_outcome=outcome,contract_detail=detail,model_baseline_equal=equal); result.append(r)
with (W/'results.tsv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=result[0],delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(result)
print('files',len(cache),'hunks',len(result),'compile',dict(collections.Counter(r['revert_exit'] for r in result)),'outcomes',dict(collections.Counter(r['contract_outcome'] for r in result)),flush=True)
shutil.rmtree(obj); (W/'index').unlink()
