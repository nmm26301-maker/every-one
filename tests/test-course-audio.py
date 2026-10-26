"""Validate every bundled sample against curriculum, content hashes and optional real decoding."""
import argparse, hashlib, io, json, pathlib, subprocess, wave, zipfile, array, math
p=argparse.ArgumentParser();p.add_argument('--decode',action='store_true');args=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];assets=root/'app/src/main/assets/journey'
course=json.loads((assets/'course.json').read_text());folder=assets/'course-audio';index=json.loads((folder/'index.json').read_text())
expected={u['id']+'_'+str(n):(s['en'],'level'+str(u['level'])+'.zip') for u in course['lessons'] for n,s in enumerate(u['sentences'])}
assert len(expected)==len(index['samples'])==144
seen=set();total=0;duration=0
for sample in index['samples']:
 key=sample['key'];assert key not in seen;seen.add(key)
 assert (sample['text'],sample['pack'])==expected[key]
 with zipfile.ZipFile(folder/sample['pack']) as z:data=z.read(key+'.mp3')
 assert len(data)==sample['bytes'] and hashlib.sha256(data).hexdigest()==sample['sha256']
 total+=len(data)
 if args.decode:
  decoded=subprocess.run(['ffmpeg','-v','error','-i','pipe:0','-f','s16le','-ac','1','-ar','16000','pipe:1'],input=data,stdout=subprocess.PIPE,check=True).stdout
  pcm=array.array('h',decoded);seconds=len(pcm)/16000;assert .25<seconds<15
  rms=math.sqrt(sum(x*x for x in pcm)/len(pcm))/32768;assert rms>.003,(key,rms)
  duration+=seconds
for level in range(6):
 with zipfile.ZipFile(folder/('level'+str(level)+'.zip')) as z:
  assert len(z.namelist())==24 and set(z.namelist())=={k+'.mp3' for k,v in expected.items() if v[1]=='level'+str(level)+'.zip'}
assert index['telemetry_disabled_before_runtime_import'] is True
assert 'public domain' in (folder/'MODEL_CARD.txt').read_text().lower()
print(json.dumps(dict(samples=144,mp3_bytes=total,decoded=args.decode,duration_seconds=round(duration,3))))
