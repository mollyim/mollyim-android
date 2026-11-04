import subprocess,re,collections,xml.dom.minidom as D,hashlib,difflib
from pathlib import Path
P=Path('/var/home/x/agent-scratch/mollyim-android/pr-stack')
S='6e9dddc01f1b26e608ac683807c51e139c141ed2'; A='2533afec2fe2d7f9fc682f699fc21d7624521889'; B='70f2eba69967c4a3ae82dd9204999feb1bd70f9b'; O='c47a74ba0961903eeaeda6d22347a990056c3e83'; M='0e202dda90785771ff8c87526eaaf30c655f54f3'
def tree(ref):
 out=subprocess.check_output(['git','ls-tree','-rz',ref]); return {p.decode():h.decode() for x in out.split(b'\0') if x for meta,p in [x.split(b'\t',1)] for h in [meta.split()[2]]}
trees={r:tree(r) for r in (S,A,B,O,M)}
ids={h for t in trees.values() for p,h in t.items() if re.search(r'/src/main/res/values(?:-[^/]+)?/strings\.xml$',p)}; proc=subprocess.Popen(['git','cat-file','--batch'],stdin=subprocess.PIPE,stdout=subprocess.PIPE); blobs={}
# Requests and reads interleaved to avoid pipe deadlock.
for h in ids:
 proc.stdin.write((h+'\n').encode());proc.stdin.flush(); header=proc.stdout.readline().split(); n=int(header[2]);blobs[h]=proc.stdout.read(n);proc.stdout.read(1)
proc.stdin.close();proc.wait()
def data(r,p): return blobs[trees[r][p]] if p in trees[r] else None
def dom(b):return D.parseString(b)
def text(n):return ''.join(text(c) if c.nodeType==c.ELEMENT_NODE else c.data if c.nodeType in (c.TEXT_NODE,c.CDATA_SECTION_NODE) else '' for c in n.childNodes)
def norm(n, mixed=False):
 if n.nodeType==n.ELEMENT_NODE:
  mixed = mixed or n.tagName in ('string','item')
  ch=[]
  for c in n.childNodes:
   if c.nodeType==c.ELEMENT_NODE:ch.append(norm(c,mixed))
   elif c.nodeType in (c.TEXT_NODE,c.CDATA_SECTION_NODE) and (mixed or c.data.strip() or not any(x.nodeType==x.ELEMENT_NODE for x in n.childNodes)):
    if ch and ch[-1][0]=='text':ch[-1]=('text',ch[-1][1]+c.data)
    else:ch.append(('text',c.data))
  return (n.tagName,tuple(sorted(n.attributes.items())),tuple(ch))
 return None
def elements(d):return [norm(x) for x in d.documentElement.childNodes if x.nodeType==x.ELEMENT_NODE]
def marker(r,base):
 d=dom(data(r,base));return {x.getAttribute('name') for typ in ('string','plurals') for x in d.getElementsByTagName(typ) if x.getAttribute('mollyify')=='true'}
def transform(raw,marks):
 d=dom(raw); changed=False
 for tag in ('string','plurals'):
  for e in list(d.getElementsByTagName(tag)):
   if e.getAttribute('name') not in marks:continue
   for v in ([e] if tag=='string' else list(e.getElementsByTagName('item'))):
    old=text(v);new=old.replace('signal.org','molly.im').replace('Signal','Molly'); changed |= old!=new
    for c in list(v.childNodes):v.removeChild(c)
    if new:v.appendChild(d.createTextNode(new))
 return elements(d) if changed else elements(dom(raw)),changed
rx=re.compile(r'(^|/)src/main/res/values-[^/]+/strings\.xml$')
paths=sorted(p for p in trees[A] if rx.search(p));rows=[];counts=collections.Counter(); missing=[];entity=[];unmatched=[]
markers={}
for p in paths:
 base=p.split('/src/main/res/')[0]+'/src/main/res/values/strings.xml'; raw=data(S,p); tar=data(A,p); other=data(B,p)
 if raw is None or tar is None or other is None or data(A,base) is None:raise Exception(p)
 marks=markers.setdefault(base,marker(A,base)) if base not in markers else markers[base]
 predicted,changed=transform(raw,marks); actual=elements(dom(tar)); filtered=[x for x in actual if not(x[0]=='string' and ('name','please_wait') in x[1])]; added=len(actual)-len(filtered)
 equal=predicted==actual; residual=predicted==filtered and added==1
 counts.update({'total':1,'identical':raw==tar,'raw_parsed':elements(dom(raw))==actual,'changed':changed,'predicted':equal,'please_only':residual,'other':not(equal or residual),'pr3pr4_same':tar==other})
 if not(equal or residual):unmatched.append(p)
 spdx=b'SPDX-License-Identifier' in raw and b'SPDX-License-Identifier' not in tar
 if spdx:missing.append(p)
 ent=[]
 for e in (b'&#160;',b'&#8212;'):
  if raw.count(e)!=tar.count(e):ent.append(e.decode()+':'+str(raw.count(e))+'>'+str(tar.count(e)))
 if ent:entity.append((p,','.join(ent)))
 rows.append('\t'.join((p,str(raw==tar),str(elements(dom(raw))==actual),str(changed),'equal' if equal else 'please_wait_only' if residual else 'UNMATCHED',str(spdx),','.join(ent))))
control=sorted(p for p in trees[M] if rx.search(p) and p.startswith('app/') and p in trees[O]);cc=collections.Counter(); crows=[]
for p in control:
 base='app/src/main/res/values/strings.xml';pred,ch=transform(data(O,p),marker(M,base));actual=elements(dom(data(M,p))); filtered=[x for x in actual if not(x[0]=='string' and ('name','please_wait') in x[1])];ok=pred==actual;res=pred==filtered and len(actual)-len(filtered)==1
 cc.update({'total':1,'equal':ok,'please_only':res,'other':not(ok or res),'changed':ch});crows.append('\t'.join((p,'equal' if ok else 'please_wait_only' if res else 'UNMATCHED')))
(P/'TRANSLATION-XML-COVERAGE.tsv').write_text('path\traw_bytes_equal\traw_elements_equal\ttransform_changed\ttransform_result\tmissing_spdx\tentity_count_changes\n'+'\n'.join(rows)+'\n')
(P/'TRANSLATION-XML-CONTROL.tsv').write_text('path\tresult\n'+'\n'.join(crows)+'\n')
(P/'TRANSLATION-XML-LEXICAL.tsv').write_text('path\tmissing_spdx\tentity_count_changes\n'+'\n'.join('\t'.join((p,str(p in missing),next((e for q,e in entity if q==p),''))) for p in sorted(set(missing)|{x[0] for x in entity}))+'\n')
print('CURRENT',counts,'CONTROL',cc,'SPDX',len(missing),'ENTITY',len(entity),'UNMATCHED',unmatched)
