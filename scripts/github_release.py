"""Manual GitHub publication. Uses the existing Git credential helper, never stores credentials.

Run only after explicit approval to publish this specific version.
"""
import argparse
import json
from pathlib import Path
import subprocess
import urllib.error
import urllib.request

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo',default='2269642185/withyou')
    parser.add_argument('--bundle',type=Path,required=True)
    parser.add_argument('--commit',required=True)
    args=parser.parse_args()
    bundle=args.bundle.resolve(); assets=bundle/'assets'
    manifest=json.loads((assets/'update.json').read_text(encoding='utf-8'))
    tag='v'+manifest['versionName']
    if not manifest['apkUrl'].startswith(f'https://github.com/{args.repo}/releases/download/{tag}/'):
        parser.error('Manifest repository mismatch')
    import hashlib
    apk=assets/'withyou.apk'
    if apk.stat().st_size!=manifest['size'] or hashlib.sha256(apk.read_bytes()).hexdigest()!=manifest['sha256']:
        parser.error('APK integrity mismatch')
    result=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',capture_output=True,text=True,check=True)
    fields=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
    token=fields.get('password')
    if not token: parser.error('GitHub authentication is required')
    def api(path,method='GET',body=None,raw=None,content_type='application/json'):
        url=path if path.startswith('https://') else 'https://api.github.com'+path
        if not url.startswith(('https://api.github.com/','https://uploads.github.com/')): raise ValueError('Unsupported API host')
        data=raw if raw is not None else json.dumps(body).encode() if body is not None else None
        request=urllib.request.Request(url,data=data,method=method,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','Content-Type':content_type,'User-Agent':'WithYouRelease'})
        with urllib.request.urlopen(request,timeout=120) as response:
            value=response.read()
            return json.loads(value) if value else {}
    # An existing tag is deliberately not replaced: releases must be immutable in our workflow.
    release=api(f'/repos/{args.repo}/releases','POST',{'tag_name':tag,'target_commitish':args.commit,'name':f'与你 · 纪念日 {manifest["versionName"]}',
        'body':manifest['notes'],'draft':True,'prerelease':False})
    upload=release['upload_url'].split('{')[0]
    uploads=[('withyou.apk','application/vnd.android.package-archive'),('update.json','application/json')]
    if (assets/'withyou-source.zip').is_file(): uploads.append(('withyou-source.zip','application/zip'))
    for name,mime in uploads:
        api(upload+'?name='+name,'POST',raw=(assets/name).read_bytes(),content_type=mime)
    published=api(f'/repos/{args.repo}/releases/{release["id"]}','PATCH',{'draft':False,'make_latest':'true'})
    print(json.dumps({'release':published['html_url'],'version':manifest['versionName']},ensure_ascii=False))

if __name__=='__main__': main()
