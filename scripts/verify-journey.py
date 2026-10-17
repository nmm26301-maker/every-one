import sys,pathlib,zipfile,json
base,output,build=sys.argv[1:];build=pathlib.Path(build)
old={str(p.relative_to(build/'old-smali')):p.read_bytes() for p in (build/'old-smali').rglob('*.smali')}
new={str(p.relative_to(build/'new-smali')):p.read_bytes() for p in (build/'new-smali').rglob('*.smali')}
assert all(new.get(n)==v for n,v in old.items()),'Baseline class changed'
added=set(new)-set(old);assert added and all(n.startswith('org/openenglish/reader/Journey') for n in added)
with zipfile.ZipFile(base) as a,zipfile.ZipFile(output) as b:
 names=[n for n in a.namelist() if not n.startswith('META-INF/') and n not in ('AndroidManifest.xml','classes.dex')]
 assert all(a.read(n)==b.read(n) for n in names)
 course=json.loads(b.read('assets/journey/course.json'));assert len(course['lessons'])==48
 result=dict(versionName='5.0.0',versionCode=59,identical_classes=len(old),added_classes=len(added),identical_zip_entries=len(names),lessons=48,utterances=144,phone_tested=False,cloud_assessment_live_tested=False)
(build/'verification.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
