import subprocess,json,base64,os,urllib.request,concurrent.futures
from pathlib import Path
REPO='aristheg201/Mod-dev'; BRANCH='feature/world-comes-alive'; ROOT=str(Path(__file__).resolve().parents[2])
def git(*args,raw=False):
 b=subprocess.check_output(['git',*args],cwd=ROOT)
 return b if raw else b.decode().strip()
assert git('branch','--show-current')==BRANCH
TOKEN=os.environ['GH_TOKEN']
def api(path,body=None,method=None):
 data=None if body is None else json.dumps(body).encode()
 req=urllib.request.Request('https://api.github.com/repos/'+REPO+'/'+path,data=data,method=method or ('POST' if body is not None else 'GET'),headers={'Authorization':'Bearer '+TOKEN,'Accept':'application/vnd.github+json','Content-Type':'application/json','X-GitHub-Api-Version':'2022-11-28'})
 try:
  with urllib.request.urlopen(req,timeout=120) as r:return json.load(r)
 except urllib.error.HTTPError as e:
  raise RuntimeError(f'GitHub API {path}: {e.code}: '+e.read().decode()[:500]) from None
BASE=git('rev-parse','refs/remotes/origin/'+BRANCH)
assert api('git/ref/heads/'+BRANCH)['object']['sha']==BASE, 'Remote changed; fetch and reconcile on this branch before publishing'
commits=git('rev-list','--reverse',BASE+'..HEAD').splitlines()
assert commits, 'No unpublished commits'
for sha in commits:
 assert '[skip ci]' in git('show','-s','--format=%B',sha), 'Intermediate commits must skip CI'
mapping={git('rev-parse',BASE):git('rev-parse',BASE)}
for index,sha in enumerate(commits,1):
 parent=git('rev-parse',sha+'^');remote_parent=mapping[parent]
 base_tree=api('git/commits/'+remote_parent)['tree']['sha']
 changes=git('diff-tree','--no-commit-id','--name-status','--no-renames','-r',parent,sha).splitlines()
 elements=[];binary=[]
 for line in changes:
  status,path=line.split('\t',1)
  if status=='D':elements.append({'path':path,'mode':'100644','type':'blob','sha':None});continue
  info=git('ls-tree',sha,'--',path).split(None,3); mode,kind,blob=info[:3]
  assert kind=='blob',info
  content=git('cat-file','blob',blob,raw=True)
  entry={'path':path,'mode':mode,'type':'blob'}
  try:
   assert b'\x00' not in content
   entry['content']=content.decode('utf-8')
  except (UnicodeDecodeError,AssertionError):binary.append((entry,blob,content))
  elements.append(entry)
 def publish_blob(item):
  entry,expected,content=item
  result=api('git/blobs',{'content':base64.b64encode(content).decode(),'encoding':'base64'})
  assert result['sha']==expected,(result['sha'],expected)
  entry['sha']=result['sha']
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(publish_blob,binary))
 tree=api('git/trees',{'base_tree':base_tree,'tree':elements})['sha']
 expected_tree=git('rev-parse',sha+'^{tree}')
 assert tree==expected_tree,(tree,expected_tree)
 fields=git('show','-s','--format=%an%x00%ae%x00%aI%x00%cn%x00%ce%x00%cI%x00%B',sha).split('\x00',6)
 an,ae,ad,cn,ce,cd,message=fields
 result=api('git/commits',{'tree':tree,'parents':[remote_parent],'message':message,'author':{'name':an,'email':ae,'date':ad},'committer':{'name':cn,'email':ce,'date':cd}})
 remote_sha=result['sha'];mapping[sha]=remote_sha
 print(json.dumps({'milestone':index,'files':len(elements),'local':sha,'remote':remote_sha,'treeMatches':True}),flush=True)
head=mapping[commits[-1]]
assert '[skip ci]' in api('git/commits/'+head)['message']
# Publish only the authorized ref once all validated objects are available. No intermediate branches.
try:existing=api('git/ref/heads/'+BRANCH)
except RuntimeError as e:
 if '404' not in str(e):raise
 existing=None
if existing is None:result=api('git/refs',{'ref':'refs/heads/'+BRANCH,'sha':head})
else:
 assert existing['object']['sha'] in mapping.values(),'Remote branch advanced; refusing overwrite'
 result=api('git/refs/heads/'+BRANCH,{'sha':head,'force':False},'PATCH')
verified=api('git/ref/heads/'+BRANCH)['object']['sha'];assert verified==head
Path(ROOT+'/build/checkpoints/github-published.json').write_text(json.dumps({'branch':BRANCH,'sha':head,'local':commits[-1],'mapping':mapping},indent=2))
print('PUBLISHED '+BRANCH+' '+head,flush=True)

# Align the same local branch to API-created commit metadata, preserving the worktree.
subprocess.run(['git','-c','credential.helper=','-c','credential.https://github.com.helper=','fetch','--no-tags','origin',BRANCH+':refs/remotes/origin/'+BRANCH],cwd=ROOT,check=True)
assert git('rev-parse','HEAD^{tree}')==git('rev-parse','origin/'+BRANCH+'^{tree}')
subprocess.run(['git','reset','--soft','origin/'+BRANCH],cwd=ROOT,check=True)
