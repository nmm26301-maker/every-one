#!/usr/bin/env bash
# Preserve the verified 4.7.1 compiled snapshot; replace only the reviewed streaming class.
set -euo pipefail
base_apk=$(realpath "$1")
output_apk=$(realpath -m "$2")
cd "$(dirname "$0")/.."
sdk_tools="$ANDROID_HOME/build-tools/35.0.0"
build_dir=build/snapshot472
mkdir -p "$build_dir/classes" "$build_dir/dex"
java --add-modules jdk.compiler com.sun.tools.javac.Main -source 8 -target 8 -d "$build_dir/classes" app/src/main/java/org/openenglish/reader/StreamingSpeechChunks.java
"$sdk_tools/d8" --min-api 26 --output "$build_dir/dex" "$build_dir/classes/org/openenglish/reader/StreamingSpeechChunks.class"
python3 - "$base_apk" "$build_dir/base.dex" <<'PY'
import pathlib,sys,zipfile,hashlib
p=pathlib.Path(sys.argv[1])
assert hashlib.sha256(p.read_bytes()).hexdigest()=='d962457ae7440fd977eeebfd19debcc036e9a57ce1251bb792f1ddfdd38f3d82'
with zipfile.ZipFile(p) as z:pathlib.Path(sys.argv[2]).write_bytes(z.read('classes.dex'))
PY
java --add-modules jdk.compiler com.sun.tools.javac.Main -cp '/usr/share/java/*' -d "$build_dir/classes" scripts/DexClassPatch.java
java -cp "$build_dir/classes:/usr/share/java/*" DexClassPatch "$build_dir/base.dex" "$build_dir/dex/classes.dex" "$build_dir/merged.dex" 'Lorg/openenglish/reader/StreamingSpeechChunks;'
python3 scripts/patch-apk-version.py "$base_apk" "$build_dir/merged.dex" "$build_dir/unsigned.apk" d962457ae7440fd977eeebfd19debcc036e9a57ce1251bb792f1ddfdd38f3d82 4.7.1 4.7.2 57 58
"$sdk_tools/zipalign" -f -P 16 4 "$build_dir/unsigned.apk" "$output_apk"
java -cp '/usr/share/java/*' org.jf.baksmali.Main disassemble "$build_dir/base.dex" -o "$build_dir/old-smali"
java -cp '/usr/share/java/*' org.jf.baksmali.Main disassemble "$build_dir/merged.dex" -o "$build_dir/new-smali"
python3 - "$base_apk" "$output_apk" "$build_dir" <<'PY'
import sys,zipfile,pathlib,hashlib,json
base,output,build=sys.argv[1:];build=pathlib.Path(build)
old={str(p.relative_to(build/'old-smali')):p.read_bytes() for p in (build/'old-smali').rglob('*.smali')}
new={str(p.relative_to(build/'new-smali')):p.read_bytes() for p in (build/'new-smali').rglob('*.smali')}
assert old.keys()==new.keys()
changed=[name for name in old if old[name]!=new[name]]
assert changed==['org/openenglish/reader/StreamingSpeechChunks.smali'],changed
with zipfile.ZipFile(base) as a,zipfile.ZipFile(output) as b:
 names=[n for n in a.namelist() if not n.startswith('META-INF/')]
 assert set(names)==set(b.namelist())
 resources=[n for n in names if n not in ('AndroidManifest.xml','classes.dex')]
 assert all(a.read(n)==b.read(n) for n in resources)
 result={'versionName':'4.7.2','versionCode':58,'identical_classes':len(old)-1,'changed_classes':changed,'identical_zip_entries':len(resources),'phone_tested':False}
(build/'verification.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
PY
