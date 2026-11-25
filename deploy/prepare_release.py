"""Unpack the one-time Codemagic secret bundle without logging secrets."""
import base64, json, os, pathlib

root = pathlib.Path(os.environ['CM_BUILD_DIR']) / '.release-private'
root.mkdir(mode=0o700, exist_ok=True)
data = json.loads(base64.b64decode(os.environ['GUILING_RELEASE_BUNDLE_BASE64'], validate=True))
for name in ('release.jks', 'deploy_key', 'known_hosts'):
    path = root / name
    path.write_bytes(base64.b64decode(data[name], validate=True)); path.chmod(0o600)
with open(os.environ['CM_ENV'], 'a') as env:
    for key, value in {
        'CM_KEYSTORE_PATH': str(root / 'release.jks'),
        'CM_KEYSTORE_PASSWORD': data['password'],
        'CM_KEY_ALIAS': 'guiling', 'CM_KEY_PASSWORD': data['password'],
    }.items():
        if '\n' in value or '\r' in value: raise ValueError('Invalid environment value')
        env.write(key + '=' + value + '\n')
