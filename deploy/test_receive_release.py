import io, json, hashlib, pathlib, tempfile, tarfile, unittest, zipfile
from receive_release import publish

def payload(code=3, broken_hash=False, name='app.apk'):
    apk=io.BytesIO()
    with zipfile.ZipFile(apk,'w') as z:
        z.writestr('AndroidManifest.xml',b'test');z.writestr('classes.dex',b'test')
    data=apk.getvalue()
    info=dict(versionCode=code,versionName='0.1.2',file='guiling-%d.apk'%code,size=len(data),sha256='0'*64 if broken_hash else hashlib.sha256(data).hexdigest())
    out=io.BytesIO()
    with tarfile.open(fileobj=out,mode='w') as t:
        for n,b in ((name,data),('latest.json',json.dumps(info).encode())):
            item=tarfile.TarInfo(n);item.size=len(b);t.addfile(item,io.BytesIO(b))
    out.seek(0);return out

class PublishTests(unittest.TestCase):
    def test_publish_and_refuse_rollback(self):
        with tempfile.TemporaryDirectory() as d:
            root=pathlib.Path(d);publish(payload(),root)
            old=(root/'latest.json').read_bytes()
            for code in (2,3):
                with self.assertRaises(ValueError):publish(payload(code),root)
                self.assertEqual(old,(root/'latest.json').read_bytes())
            publish(payload(4),root)
            self.assertEqual(4,json.loads((root/'latest.json').read_text())['versionCode'])
    def test_bad_hash_and_traversal_never_publish(self):
        for stream in (payload(broken_hash=True),payload(name='../app.apk')):
            with tempfile.TemporaryDirectory() as d:
                with self.assertRaises(ValueError):publish(stream,pathlib.Path(d))
                self.assertFalse((pathlib.Path(d)/'latest.json').exists())

if __name__=='__main__':unittest.main()
