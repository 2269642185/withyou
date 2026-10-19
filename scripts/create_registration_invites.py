"""Create two user-requested one-time registration codes; write raw codes only to a private local file."""
import argparse,hashlib,secrets
from pathlib import Path
from cloudbase_sql import execute

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output',type=Path,default=Path('local-secrets/CloudBase-注册激活码.txt'))
args=parser.parse_args()
if args.output.exists():parser.error('Output already exists; keep the original private codes file instead of overwriting it.')
codes=[secrets.token_hex(24) for _ in range(2)]
hashes=[hashlib.sha256(code.encode()).hexdigest() for code in codes]
values=','.join("('"+value+"')" for value in hashes)
sql="DO LANGUAGE plpgsql $$ BEGIN PERFORM pg_advisory_xact_lock(81420402); IF (SELECT count(*) FROM wy_private.registration_slots WHERE state<>'created' AND expires_at>now())+2>2 THEN RAISE EXCEPTION 'Active registration slots already exist'; END IF; INSERT INTO wy_private.registration_slots(code_hash) VALUES "+values+"; END $$;"
args.output.parent.mkdir(parents=True,exist_ok=True)
# Store before the cloud mutation so a successful commit never loses the only raw copy.
args.output.write_text('与你 · 纪念日：一次性注册激活码\n\n仅供你与伙伴使用，请勿上传 GitHub 或公开分享。\n每个码只能成功注册一个账号，创建后 7 天有效。注册成功后用用户名和密码登录。\n\n你的激活码：\n'+codes[0]+'\n\n伙伴的激活码：\n'+codes[1]+'\n\n这不是配对邀请码。两人注册登录后，在“账号与伙伴”中另行生成配对邀请码。\n',encoding='utf-8')
try:execute(sql)
except Exception:
    with args.output.open('a',encoding='utf-8') as out:out.write('\n注意：云端创建未确认成功，请检查后台状态后再使用。\n')
    raise
print('Two registration codes created. Raw codes are only in the ignored private output file.')
