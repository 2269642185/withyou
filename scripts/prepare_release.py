"""Generate a release bundle locally. Never uploads or publishes anything."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--repo', default='2269642185/withyou')
    parser.add_argument('--version', default='1.4.0')
    parser.add_argument('--code', type=int, default=6)
    parser.add_argument('--notes', type=Path, required=True)
    parser.add_argument('--out', type=Path, default=Path('release-output'))
    args=parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9-]+/[A-Za-z0-9_.-]+',args.repo) or not re.fullmatch(r'\d+\.\d+\.\d+',args.version):
        parser.error('Invalid repository or version')
    if not args.apk.is_file() or not 0<args.apk.stat().st_size<=150*1024*1024 or args.code<=0:
        parser.error('Invalid APK or version code')
    root=Path(__file__).resolve().parents[1]
    output=args.out.resolve()
    site=output/'site'; assets=output/'assets'
    site.mkdir(parents=True,exist_ok=True); assets.mkdir(parents=True,exist_ok=True)
    apk=assets/'withyou.apk'; shutil.copyfile(args.apk,apk)
    manifest={'schemaVersion':1,'versionCode':args.code,'versionName':args.version,
        'minSdk':26,'apkUrl':f'https://github.com/{args.repo}/releases/download/v{args.version}/withyou.apk',
        'size':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'notes':args.notes.read_text(encoding='utf-8').strip()}
    text=json.dumps(manifest,ensure_ascii=False,indent=2)+'\n'
    for folder in (site,assets): (folder/'update.json').write_text(text,encoding='utf-8')
    page=(root/'web/index.html').read_text(encoding='utf-8').replace('2269642185/withyou',args.repo)
    (site/'index.html').write_text(page,encoding='utf-8'); (site/'.nojekyll').touch()
    shutil.copyfile(args.notes,assets/'release-notes.md')
    print(json.dumps({'output':str(output),'sha256':manifest['sha256'],'size':manifest['size']},ensure_ascii=False))

if __name__=='__main__': main()
