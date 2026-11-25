#!/usr/bin/python3
"""Forced SSH command. Accept only a bounded APK+metadata archive, never a shell command."""
import fcntl, hashlib, io, json, os, pathlib, re, sys, tarfile, tempfile, zipfile

ROOT = pathlib.Path('/srv/guiling-android')
MAX = 100 * 1024 * 1024

def publish(stream, root=ROOT):
    data = stream.read(MAX + 65537)
    if len(data) > MAX + 65536:
        raise ValueError('Archive exceeds size limit')
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:') as archive:
        members = archive.getmembers()
        if len(members) != 2 or {m.name for m in members} != {'app.apk', 'latest.json'}:
            raise ValueError('Unexpected archive entries')
        if any(not m.isfile() for m in members):
            raise ValueError('Only regular files accepted')
        if archive.getmember('latest.json').size > 16384 or archive.getmember('app.apk').size > MAX:
            raise ValueError('Entry too large')
        info = json.load(archive.extractfile('latest.json'))
        apk = archive.extractfile('app.apk').read()
    code = info['versionCode']
    if type(code) is not int or not 1 <= code <= 2100000000:
        raise ValueError('Invalid version code')
    if info['file'] != 'guiling-%d.apk' % code or info['size'] != len(apk):
        raise ValueError('Invalid file or size')
    if hashlib.sha256(apk).hexdigest() != info['sha256']:
        raise ValueError('Checksum mismatch')
    if not re.fullmatch(r'[0-9A-Za-z._-]{1,50}', info['versionName']):
        raise ValueError('Invalid version name')
    if not isinstance(info.get('notes', ''), str) or len(info.get('notes', '')) > 2000:
        raise ValueError('Invalid release notes')
    with zipfile.ZipFile(io.BytesIO(apk)) as z:
        if 'AndroidManifest.xml' not in z.namelist() or 'classes.dex' not in z.namelist():
            raise ValueError('Not an APK')
    root.mkdir(exist_ok=True)
    with (root / '.publish.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        latest = root / 'latest.json'
        if latest.exists() and code <= json.loads(latest.read_text())['versionCode']:
            raise ValueError('Version must increase; existing release unchanged')
        def atomic(name, contents):
            with tempfile.NamedTemporaryFile(dir=root, delete=False) as f:
                path = pathlib.Path(f.name); f.write(contents); f.flush(); os.fsync(f.fileno())
            path.chmod(0o644); os.replace(str(path), str(root / name))
        atomic(info['file'], apk)
        atomic('latest.json', json.dumps(info, ensure_ascii=False).encode())
    print('Published version %s' % code)

if __name__ == '__main__':
    try:
        publish(sys.stdin.buffer)
    except Exception as e:
        print('Publish rejected: %s' % e, file=sys.stderr); sys.exit(1)
