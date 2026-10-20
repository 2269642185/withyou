"""Deploy reviewed 006 migration and atomically grandfather existing Auth UIDs.

Uses the existing CloudBase CLI login; does not print user details or credentials.
New friend admission stays closed until explicitly enabled for validation/release.
"""
import json
from pathlib import Path
import cloudbase_sql as c


def main():
    state=c.execute("SELECT to_regclass('wy_private.members') IS NOT NULL")['Rows'][0]
    exists=(json.loads(state) if isinstance(state,str) else state)[0] in (True,'true','t','1',1)
    users=[]
    if not exists:
        for offset in range(0,1000,100):
            batch=c.command('user','list','-e',c.ENV,'--offset',str(offset),'--limit','100','--json')
            if not isinstance(batch,list):raise RuntimeError('Unexpected user response; no migration applied')
            users.extend(batch)
            if len(batch)<100:break
        else:raise RuntimeError('User paging limit reached; no migration applied')
    ids=sorted({str(x['Uid']) for x in users})
    if (not exists and not ids) or any(not x.isdigit() for x in ids):raise RuntimeError('Unexpected UID format; no migration applied')
    directory=Path('build/email-auth');directory.mkdir(parents=True,exist_ok=True)
    old=c.execute("SELECT pg_get_functiondef('public.wy_uid()'::regprocedure)")
    (directory/'uid-before.json').write_text(json.dumps(old),encoding='utf-8')
    statements=c.statements(Path('cloudbase/sql/006_email_admission.sql').read_text(encoding='utf-8'))
    if ids:statements.append('INSERT INTO wy_private.members(user_id) VALUES '+','.join("('"+uid+"')" for uid in ids)+' ON CONFLICT DO NOTHING')
    wrapper='DO LANGUAGE plpgsql $migration$ BEGIN\n'+'\n'.join(f'EXECUTE $s{i}${sql}$s{i}$;' for i,sql in enumerate(statements))+'\nEND $migration$;'
    applied=c.execute(wrapper)
    rows=c.execute('SELECT count(*) FROM wy_private.members'+(' WHERE user_id IN('+','.join("'"+uid+"'" for uid in ids)+')' if ids else ''))['Rows']
    row=rows[0];count=int((json.loads(row) if isinstance(row,str) else row)[0])
    if ids:assert count==len(ids),'Existing-account preservation check failed'
    report={'migration':'006_email_admission.sql','existingAccountsPreserved':count,'atomicMigration':True,'requestId':applied.get('RequestId'),'registrationOpenedByThisScript':False,'repeatDoesNotAdmitUnknownIdentities':exists}
    (directory/('migration-repeat.json' if exists else 'migration.json')).write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))


if __name__=='__main__':main()
