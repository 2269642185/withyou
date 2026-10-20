"""Administrator-only controls via the existing CloudBase CLI login.

Admission stop prevents new friends from accessing business data; it does not
prevent native Auth identities being created. Email provider off stops native
verification mail. Neither control claims a daily total mail hard limit.
"""
import argparse,json
from cloudbase_sql import ENV,command,execute

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--admission',choices=['open','closed'])
parser.add_argument('--email',choices=['on','off'])
args=parser.parse_args()
if args.admission:
    execute('UPDATE wy_private.email_registration SET registration_open='+('true' if args.admission=='open' else 'false')+' WHERE singleton')
if args.email:
    command('api','tcb','ModifyProvider','--body',json.dumps({'EnvId':ENV,'Id':'email','On':'TRUE' if args.email=='on' else 'FALSE'}),'--json')
print(json.dumps({'environment':ENV,'admissionRequested':args.admission,'emailProviderRequested':args.email,'dailyTotalHardLimit':False}))
