import hashlib, json, os, pathlib, subprocess, tarfile

root = pathlib.Path(os.environ['CM_BUILD_DIR'])
out = root / 'app/build/outputs/apk/release'
meta = json.loads((out / 'output-metadata.json').read_text())
if meta['applicationId'] != 'top.george888.guiling' or len(meta['elements']) != 1:
    raise ValueError('Unexpected package')
element = meta['elements'][0]
apk = out / element['outputFile']
data = apk.read_bytes()
info = dict(versionCode=element['versionCode'], versionName=element['versionName'],
            file='guiling-%s.apk' % element['versionCode'], size=len(data),
            sha256=hashlib.sha256(data).hexdigest(), notes='新增应用内检查更新、下载与系统安装；包含采集日志。')
(out / 'latest.json').write_text(json.dumps(info, ensure_ascii=False))
archive = out / 'release.tar'
with tarfile.open(archive, 'w') as t:
    t.add(apk, arcname='app.apk'); t.add(out / 'latest.json', arcname='latest.json')
private = root / '.release-private'
with archive.open('rb') as stream:
    subprocess.run(['ssh', '-T', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
                    '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=20',
                    '-o', 'UserKnownHostsFile='+str(private / 'known_hosts'),
                    '-i', str(private / 'deploy_key'), 'guiling-publisher@101.201.101.93'],
                   stdin=stream, check=True, timeout=180)
print('Update published: https://tasks.george888.top/guiling-android/latest.json')
