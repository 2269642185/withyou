"""Opt-in real CloudBase anniversary/photo regression with newly generated disposable QA accounts only.
Run after deploying SQL 004. Credentials stay under ignored build/cloud-private until explicit QA cleanup.
"""
from pathlib import Path
import sys,json,uuid,secrets,urllib.request,urllib.error,hashlib,io
sys.path.insert(0,str(Path(__file__).parent))
import cloudbase_sql as cli
BASE=f'https://{cli.ENV}.api.tcloudbasegateway.com'
QA=Path('build/cloud-private/v16-accounts.json')
REPORT=Path('delivery/v1.6.0/shared-cloud-tests.json')
results=[]
def request(i,path,body=None,method='POST',binary=False,auth=True):
    headers={'Content-Type':'image/jpeg' if binary else 'application/json','x-device-id':'withyou-v16-qa'}
    if auth:headers['Authorization']='Bearer '+accounts[i]['session']['access_token']
    data=body if binary else json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
    with urllib.request.urlopen(req,timeout=35) as r:
        raw=r.read();return raw if binary or not raw else json.loads(raw)
def rpc(i,name,body={}):return request(i,'/v1/rdb/rest/rpc/'+name,body)
def ok(name,condition):
    if not condition:raise AssertionError(name)
    results.append({'test':name,'pass':True})
def denied(name,fn):
    try:fn()
    except urllib.error.HTTPError as e:
        if e.code in [400,401,403,404]:ok(name,True);return
        raise
    raise AssertionError(name)
if not QA.exists():
    accounts=[];QA.parent.mkdir(parents=True,exist_ok=True)
    for i in range(3):
        acc={'username':'wyqa16'+uuid.uuid4().hex[:13],'password':'Qa16!'+secrets.token_hex(12),'nickname':['小杏','阿与你','隔离验证'][i]}
        cli.command('user','create',acc['username'],'-e',cli.ENV,'--password',acc['password'],'--type','externalUser','--nickname',acc['nickname'],'--json')
        accounts.append(acc);QA.write_text(json.dumps(accounts,ensure_ascii=False),encoding='utf-8')
else:accounts=json.loads(QA.read_text(encoding='utf-8'))
assert len(accounts)==3 and all(a['username'].startswith('wyqa16') for a in accounts)
for i,a in enumerate(accounts):a['session']=request(i,'/auth/v1/signin',{'username':a['username'],'password':a['password']},auth=False)
QA.write_text(json.dumps(accounts,ensure_ascii=False),encoding='utf-8')
a,b,c=[x['session']['sub'] for x in accounts]
for i in range(3):rpc(i,'wy_snapshot',{'nickname':accounts[i]['nickname']})
pair=rpc(0,'wy_snapshot')['pair_id']
if not pair:
    code=rpc(0,'wy_pair_action',{'action':'create'})['code'];rpc(1,'wy_pair_action',{'action':'join','value':code});invite=rpc(0,'wy_snapshot')['invites'][0]['id'];pair=rpc(0,'wy_pair_action',{'action':'confirm','value':invite})['pair_id']
from PIL import Image
stream=io.BytesIO();Image.new('RGB',(120,160),(194,160,126)).save(stream,'JPEG');photo=stream.getvalue()
id=str(uuid.uuid4());path=f'{a}/{id}/{hashlib.sha256(photo).hexdigest()}.jpg'
url='/v1/storages/object/withyou-memories/'+path;download='/v1/storages/object/authenticated/withyou-memories/'+path
request(0,url,photo,binary=True)
ok('real PostgreSQL CloudBase Storage upload accepted',True)
denied('unpublished photo denied to partner',lambda:request(1,download,None,'GET',True))
payload={'id':id,'title':'一起走过的日子','nickname':'小杏与阿与你','date':'2024-02-29','mode':'INCLUSIVE','color':0,'template':'polaroid','pinned':False,'appearance':{'background':'#F4E6D7','tone':'CUSTOM','text':'#49392D','title':'#61493C','days':'#754E3A','auxiliary':'#644F43','overlay':0.62}}
item={'id':id,'pair_id':pair,'payload':payload,'photo_path':path,'expected':0,'active':True}
op=str(uuid.uuid4());r=rpc(0,'wy_memory_apply',{'op_id':op,'item':item});ok('photo and complete appearance published atomically',r.get('revision')==1)
rpc(0,'wy_memory_apply',{'op_id':op,'item':item});ok('retry remains idempotent',rpc(0,'wy_memory_snapshot')['memories'][0]['revision']==1)
peer=rpc(1,'wy_memory_snapshot')['memories'];ok('partner sees exact template palette and mask',any(m['payload']==payload and m['photo_path']==path for m in peer))
ok('partner authenticated photo bytes match',request(1,download,None,'GET',True)==photo)
ok('third account anniversary list isolated',not rpc(2,'wy_memory_snapshot')['memories'])
denied('third account photo denied',lambda:request(2,download,None,'GET',True))
denied('anonymous authenticated photo denied',lambda:request(2,download,None,'GET',True,False))
denied('public URL cannot bypass private bucket',lambda:request(2,'/v1/storages/object/public/withyou-memories/'+path,None,'GET',True,False))
denied('partner cannot replace appearance',lambda:rpc(1,'wy_memory_apply',{'op_id':str(uuid.uuid4()),'item':item}))
ok('stale revision conflicts',rpc(0,'wy_memory_apply',{'op_id':str(uuid.uuid4()),'item':item}).get('conflict'))
for index,template in enumerate(['classic','polaroid','photo','minimal','ticket','note']):
    payload={**payload,'template':template};item={**item,'payload':payload,'expected':index+1}
    rpc(0,'wy_memory_apply',{'op_id':str(uuid.uuid4()),'item':item})
ok('all six templates round trip',rpc(1,'wy_memory_snapshot')['memories'][0]['payload']['template']=='note')
item={**item,'expected':7,'active':False};rpc(0,'wy_memory_apply',{'op_id':str(uuid.uuid4()),'item':item})
ok('unshare removes peer anniversary',not rpc(1,'wy_memory_snapshot')['memories'])
denied('unshare revokes direct photo',lambda:request(1,download,None,'GET',True))
# Leave a published snapshot for Android evidence, then cleanup these QA users only after evidence.
item={**item,'expected':8,'active':True,'payload':{**payload,'template':'polaroid'}};rpc(0,'wy_memory_apply',{'op_id':str(uuid.uuid4()),'item':item})
accounts[0]['qaMemory']={'id':id,'path':path,'pair':pair};QA.write_text(json.dumps(accounts,ensure_ascii=False),encoding='utf-8')
REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps({'actualCloudBase':True,'storage':'private PostgreSQL CloudBase Storage','tests':results},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':len(results),'temporaryQaOnly':True}))
