import json,pathlib,xml.etree.ElementTree as E
p=pathlib.Path('app/src/main/assets/journey/course.json');data=json.loads(p.read_text());lessons=data['lessons']
assert len(lessons)==48 and len(data['levels'])==6
ids=set();phrases=set()
for i,u in enumerate(lessons):
 assert u['id']=='u%02d'%(i+1) and u['level']==i//8
 assert u['id'] not in ids;ids.add(u['id'])
 assert all(u[k].strip() for k in ['title','note','focus','task','scene'])
 assert len(u['sentences'])==3
 for s in u['sentences']:
  assert s['en'] and s['zh'] and s['en'] not in phrases
  assert len(s['en'].split())<=22,'Long sentence risks 12 second recording cap'
  phrases.add(s['en'])
root=E.parse('app/src/main/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
launch=[a.get(ns+'name') for a in root.findall('application/activity') if any(c.get(ns+'name')=='android.intent.category.LAUNCHER' for c in a.findall('intent-filter/category'))]
assert launch==['.JourneyActivity']
assert (root.get(ns+'versionName'), root.get(ns+'versionCode')) in {('5.0.0','59'), ('5.0.1','60'), ('5.0.2','61'), ('5.0.3','62'), ('5.0.4','63')}
print('Curriculum: 48 distinct lessons / 144 bilingual utterances, level sequence, recording-length budget and unique launcher verified')
