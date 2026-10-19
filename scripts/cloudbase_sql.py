"""Run reviewed SQL through an already logged-in CloudBase CLI; never embeds credentials."""
from pathlib import Path
import argparse,json,os,re,subprocess

ENV='withyou-sync-d0gg468vheead040b'
def cli():
    candidates=list((Path(os.environ['LOCALAPPDATA'])/'npm-cache/_npx').glob('*/node_modules/@cloudbase/cli/bin/tcb'))
    if not candidates: raise RuntimeError('Run npm exec --yes --package=@cloudbase/cli -- tcb login first')
    return max(candidates,key=lambda p:p.stat().st_mtime)
def command(*args):
    result=subprocess.run(['node',str(cli()),*args],capture_output=True,text=True,encoding='utf-8',timeout=180)
    start=result.stdout.find('{')
    if result.returncode or start<0: raise RuntimeError('CloudBase CLI failed: '+result.stderr[-600:])
    data=json.loads(result.stdout[start:])
    if data.get('error'): raise RuntimeError(str(data['error']))
    return data.get('data',data)
def execute(sql,role=None):
    args=['db','execute','-e',ENV,'--sql',sql,'--json']
    if role: args+=['--role',role]
    return command(*args)
def statements(text):
    # Preserve function bodies, dollar strings and quoted SQL literals.
    text=re.sub(r'--[^\n]*','',text)
    parts=[];start=0;i=0;quote=None
    while i<len(text):
        if quote:
            if text.startswith(quote,i):
                if quote=="'" and text.startswith("''",i):i+=2;continue
                i+=len(quote);quote=None;continue
        else:
            if text[i]=="'":quote="'"
            elif text[i]=='$':
                match=re.match(r'\$[A-Za-z_0-9]*\$',text[i:])
                if match:quote=match.group();i+=len(quote);continue
            elif text[i]==';':
                part=text[start:i].strip()
                if part and part.upper() not in ('BEGIN','COMMIT'):parts.append(part)
                start=i+1
        i+=1
    if text[start:].strip():parts.append(text[start:].strip())
    return parts
def apply(path):
    commands=statements(path.read_text(encoding='utf-8'))
    wrapper='DO LANGUAGE plpgsql $migration$ BEGIN\n'+ '\n'.join(f'EXECUTE $statement{i}${sql}$statement{i}$;' for i,sql in enumerate(commands))+'\nEND $migration$;'
    result=execute(wrapper)
    return {'file':path.name,'statements':len(commands),'requestId':result.get('RequestId')}
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',type=Path,nargs='+');parser.add_argument('--query')
    args=parser.parse_args()
    if args.apply: print(json.dumps([apply(p) for p in args.apply],ensure_ascii=False))
    elif args.query: print(json.dumps(execute(args.query),ensure_ascii=False))
