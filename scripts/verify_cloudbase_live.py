"""Opt-in live authorization regression. Uses only disposable QA users in an ignored local file."""
from pathlib import Path
import json,uuid,urllib.request,urllib.error,time

ENV='withyou-sync-d0gg468vheead040b'
BASE=f'https://{ENV}.api.tcloudbasegateway.com'
def main():
    accounts=json.loads(Path('build/cloud-private/accounts.json').read_text(encoding='utf-8'))
    results=[]
    def call(index,path,body=None,method='POST',token=True):
        headers={'Content-Type':'application/json','x-device-id':'withyou-live-regression'}
        if token:headers['Authorization']='Bearer '+accounts[index]['session']['access_token']
        request=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
        with urllib.request.urlopen(request,timeout=25) as response:return json.load(response)
    def rpc(index,name,body={}):return call(index,'/v1/rdb/rest/rpc/'+name,body)
    def passed(name):results.append({'test':name,'pass':True})
    def expect(name,condition):
        if not condition:raise AssertionError(name)
        passed(name)
    def denied(name,fn):
        try:fn()
        except urllib.error.HTTPError as error:
            if error.code not in [400,401,403,404]:raise
            passed(name);return
        raise AssertionError(name)
    for i,account in enumerate(accounts):
        account['session']=call(i,'/auth/v1/signin',{'username':account['username'],'password':account['password']},token=False)
    a,b,c=[x['session']['sub'] for x in accounts]
    book=rpc(0,'wy_snapshot');pair=book['pair_id']
    expect('existing pair visible to both users',bool(pair) and rpc(1,'wy_snapshot')['pair_id']==pair)
    task_id=str(uuid.uuid4());private_id=str(uuid.uuid4())
    def task(id,kind='shared',shared=True):return {'type':'task','id':id,'kind':kind,'visibility':'partner' if shared else 'private','pair_id':pair if shared else '', 'title':'真实权限验证','notes':'待清理的测试数据','weekdays':list(range(1,8)),'expected':0}
    def apply(index,payload,op=None):return rpc(index,'wy_apply',{'op_id':op or str(uuid.uuid4()),'payload':payload})
    apply(0,task(task_id));apply(0,task(private_id,'habit',False))
    visible=rpc(1,'wy_snapshot')['tasks']
    expect('partner sees shared task',any(t['id']==task_id for t in visible))
    expect('partner cannot read private task',all(t['id']!=private_id for t in visible))
    expect('third user snapshot isolated',len(rpc(2,'wy_snapshot')['tasks'])==0)
    expect('third user direct RLS read isolated',call(2,'/v1/rdb/rest/wy_tasks?select=*',None,'GET')==[])
    denied('third user cannot edit task',lambda:apply(2,{**task(task_id),'expected':1}))
    denied('partner cannot edit creator title',lambda:apply(1,{**task(task_id),'expected':1}))
    denied('forged owner direct insert denied',lambda:call(2,'/v1/rdb/rest/wy_tasks',{'id':str(uuid.uuid4()),'owner_id':a,'title':'forged'}))
    denied('third user cannot access private helper RPC',lambda:rpc(2,'wy_partner',{'uid':a}))
    day=rpc(0,'wy_snapshot')['server_day']
    check={'type':'check','id':task_id,'pair_id':pair,'day':day,'done':True,'expected':0}
    op=str(uuid.uuid4());apply(0,check,op);apply(0,check,op);apply(1,check)
    checks=[x for x in rpc(0,'wy_snapshot')['checks'] if x['task_id']==task_id]
    expect('both completions stored separately without duplicates',len(checks)==2 and {x['user_id'] for x in checks}=={a,b})
    expect('stale task revision rejected as conflict',apply(0,{**task(task_id),'expected':0}).get('conflict') is True)
    denied('past-date habit check rejected',lambda:apply(0,{**check,'day':'2020-01-01','expected':1}))
    apply(0,{**task(task_id),'expected':1,'deleted':True})
    expect('deleted task cannot be resurrected',apply(0,{**task(task_id),'expected':2}).get('conflict') is True)
    # Revoke last so the UI evidence can first demonstrate actual sharing.
    rpc(0,'wy_pair_action',{'action':'unlink','value':pair})
    expect('unlink immediately revokes shared task access',rpc(1,'wy_snapshot')['tasks']==[])
    denied('queued old-pair check rejected',lambda:apply(1,{**check,'expected':1}))
    denied('anonymous snapshot denied',lambda:call(0,'/v1/rdb/rest/rpc/wy_snapshot',{},token=False))
    # Validate refresh rotation without printing either token.
    refreshed=call(0,'/auth/v1/token',{'grant_type':'refresh_token','refresh_token':accounts[0]['session']['refresh_token']},token=False)
    expect('refresh preserves account identity',refreshed['sub']==a)
    accounts[0]['session']=refreshed
    Path('build/cloud-private/accounts.json').write_text(json.dumps(accounts,ensure_ascii=False),encoding='utf-8')
    report={'environment':ENV,'actualCloudBase':True,'tests':results}
    Path('delivery/v1.5.0/cloud-api-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'passed':len(results),'actualCloudBase':True}))

if __name__=='__main__':main()
