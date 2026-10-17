#!/usr/bin/env bash
set -euo pipefail
base_apk=$(realpath "$1")
output_apk=$(realpath -m "$2")
cd "$(dirname "$0")/.."
sdk_tools="$ANDROID_HOME/build-tools/35.0.0"
android_jar="$ANDROID_HOME/platforms/android-35/android.jar"
build_dir=build/journey500
smali_cp=$(dpkg -L libsmali-java libguava-java libjcommander-java libantlr3-runtime-java | sed -n '/\.jar$/p' | paste -sd:)
mkdir -p "$build_dir/classes" "$build_dir/dex" "$build_dir/res"
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -cp "$android_jar" -d "$build_dir/classes" scripts/journey-stubs/org/openenglish/reader/*.java app/src/main/java/org/openenglish/reader/Journey*.java
mapfile -t new_classes < <(find "$build_dir/classes/org/openenglish/reader" -name 'Journey*.class' -print)
"$sdk_tools/d8" --min-api 23 --lib "$android_jar" --classpath "$build_dir/classes" --output "$build_dir/dex" "${new_classes[@]}"
python3 - "$base_apk" "$build_dir/base.dex" <<'PY'
import pathlib,sys,zipfile,hashlib
assert hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest()=='ea2cfa871c48d73ead8c1c58940858ec01f9f9c9ef0b15a1ed8ebe1978ceb2e6'
with zipfile.ZipFile(sys.argv[1]) as z:pathlib.Path(sys.argv[2]).write_bytes(z.read('classes.dex'))
PY
java --add-modules jdk.compiler com.sun.tools.javac.Main -cp "$smali_cp" -d "$build_dir/classes" scripts/DexJourneyMerge.java
java -cp "$build_dir/classes:$smali_cp" DexJourneyMerge "$build_dir/base.dex" "$build_dir/dex/classes.dex" "$build_dir/merged.dex"
"$sdk_tools/aapt2" dump resources "$base_apk" > "$build_dir/base-res.txt"
python3 - "$build_dir" <<'PY'
import pathlib,re,sys
p=pathlib.Path(sys.argv[1]);s=(p/'base-res.txt').read_text();m=re.search(r'resource (0x[0-9a-f]+) (?:org.openenglish.reader:)?drawable/icon',s)
assert m,s[:3000]
(p/'stable.ids').write_text('org.openenglish.reader:drawable/icon = '+m[1]+'\n')
PY
"$sdk_tools/aapt2" compile --dir app/src/main/res -o "$build_dir/res.zip"
"$sdk_tools/aapt2" link -o "$build_dir/shell.apk" -I "$android_jar" --manifest app/src/main/AndroidManifest.xml --min-sdk-version 23 --target-sdk-version 33 --stable-ids "$build_dir/stable.ids" "$build_dir/res.zip"
"$sdk_tools/aapt2" dump resources "$build_dir/shell.apk" > "$build_dir/new-res.txt"
"$sdk_tools/aapt2" dump xmltree --file AndroidManifest.xml "$base_apk" > "$build_dir/base-manifest.txt"
"$sdk_tools/aapt2" dump xmltree --file AndroidManifest.xml "$build_dir/shell.apk" > "$build_dir/new-manifest.txt"
python3 scripts/package-journey.py "$base_apk" "$build_dir"
"$sdk_tools/zipalign" -f -P 16 4 "$build_dir/unsigned.apk" "$output_apk"
java -cp "$smali_cp" org.jf.baksmali.Main disassemble "$build_dir/base.dex" -o "$build_dir/old-smali"
java -cp "$smali_cp" org.jf.baksmali.Main disassemble "$build_dir/merged.dex" -o "$build_dir/new-smali"
python3 scripts/verify-journey.py "$base_apk" "$output_apk" "$build_dir"
