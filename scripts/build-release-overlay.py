"""Rebuild a release from verified immutable assets and the recovered code snapshot."""
import argparse,hashlib,json,pathlib,zipfile
parser=argparse.ArgumentParser()
parser.add_argument('base_apk');parser.add_argument('overlay_zip');parser.add_argument('output_apk')
args=parser.parse_args()
digest=lambda data:hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(args.overlay_zip) as overlay,zipfile.ZipFile(args.base_apk) as base:
    manifest=json.loads(overlay.read('overlay.json'))
    expected=manifest['assets_and_native']
    actual={n for n in base.namelist() if n.startswith(('assets/','lib/')) and not n.endswith('/')}
    if actual!=set(expected):raise ValueError('Baseline asset/native file set differs')
    for name,sha in expected.items():
        if digest(base.read(name))!=sha:raise ValueError('Baseline checksum differs: '+name)
    payload={}
    for name,sha in manifest['payload'].items():
        if name not in ('classes.dex','AndroidManifest.xml','resources.arsc') and not name.startswith('res/'):
            raise ValueError('Unexpected replacement entry: '+name)
        data=overlay.read(name)
        if digest(data)!=sha:raise ValueError('Overlay checksum differs: '+name)
        payload[name]=data
    target=pathlib.Path(args.output_apk);target.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(target,'w') as result:
        for entry in base.infolist():
            if entry.filename.startswith('META-INF/') or entry.is_dir():continue
            result.writestr(entry,payload.pop(entry.filename,base.read(entry.filename)))
        for name,data in payload.items():result.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
print('Verified assets/native hashes and rebuilt Everyone '+manifest['versionName'])
