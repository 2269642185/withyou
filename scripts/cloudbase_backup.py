"""Export CloudBase business tables locally, excluding authentication credentials and activation codes."""
import argparse,json
from pathlib import Path
from cloudbase_sql import execute

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
sql="SELECT jsonb_build_object('schema',2,'exported_at',now(),'memories',(SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM public.wy_memories x),'profiles',(SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM public.wy_profiles x),'pairs',(SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM public.wy_pairs x),'tasks',(SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM public.wy_tasks x),'checks',(SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM public.wy_checks x),'receipts',(SELECT coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) FROM public.wy_receipts x)) AS data"
result=execute(sql)
row=result['Rows'][0]
data=(json.loads(row) if isinstance(row,str) else row)[0]
if isinstance(data,str):data=json.loads(data)
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print('Business data backup written locally; contains private records, do not publish.')
