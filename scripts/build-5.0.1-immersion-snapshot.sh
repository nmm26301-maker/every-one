#!/usr/bin/env bash
# Restore and patch a verified Everyone 5.0.0 APK using only public source.
# Output is unsigned, and MUST be signed with the existing certificate.
set -euo pipefail
if [[ $# -ne 2 ]]; then
  echo "usage: EXPECTED_BASE_SHA256=<64 hex> $0 Everyone-5.0.0.apk Everyone-5.0.1-unsigned.apk" >&2
  exit 2
fi
if [[ ! "${EXPECTED_BASE_SHA256:-}" =~ ^[a-f0-9]{64}$ ]]; then
  echo "Missing EXPECTED_BASE_SHA256 from the private 5.0 release SHA256SUMS" >&2
  exit 2
fi
base_apk="$(realpath "$1")"
output_apk="$(realpath -m "$2")"
actual="$(sha256sum "$base_apk" | cut -d' ' -f1)"
if [[ "$actual" != "$EXPECTED_BASE_SHA256" ]]; then
  echo "Base APK SHA-256 mismatch; no files written" >&2
  exit 1
fi
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to an installed Android SDK}"
tools="$ANDROID_HOME/build-tools/35.0.0"
android_jar="$ANDROID_HOME/platforms/android-35/android.jar"
for binary in "$tools/d8" "$tools/aapt2" "$tools/zipalign"; do
  [[ -x "$binary" ]] || { echo "missing $binary" >&2; exit 1; }
done
[[ -f "$android_jar" ]] || { echo "missing $android_jar" >&2; exit 1; }
smali_cp="$(dpkg -L libsmali-java libguava-java libjcommander-java libantlr3-runtime-java | grep -E '/[^/]+\\.jar$' | paste -sd:)"
[[ -n "$smali_cp" ]] || { echo "Install libsmali-java and its dependencies" >&2; exit 1; }
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
mkdir -p "$dir/classes" "$dir/dex" "$dir/resources"
# ONNX is compile-only. Runtime libraries remain inside the verified base APK.
onnx=vendor/onnxruntime/classes.jar
if [[ ! -f "$onnx" ]]; then
  echo "Missing compile-only ONNX jar ($onnx). See docs/IMMERSION-ANDROID.md" >&2
  exit 1
fi
javac -encoding UTF-8 -source 8 -target 8 \
  -cp "$android_jar:$onnx" -d "$dir/classes" \
  scripts/journey-stubs/org/openenglish/reader/*.java \
  app/src/main/java/org/openenglish/reader/Journey*.java
mapfile -t compiled < <(find "$dir/classes/org/openenglish/reader" -name 'Journey*.class')
[[ ${#compiled[@]} -gt 0 ]]
"$tools/d8" --min-api 23 --lib "$android_jar" --classpath "$dir/classes" \
  --classpath "$onnx" --output "$dir/dex" "${compiled[@]}"
python3 - "$base_apk" "$dir/base.dex" <<'PY'
import pathlib,sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as pkg:
    pathlib.Path(sys.argv[2]).write_bytes(pkg.read('classes.dex'))
PY
javac -cp "$smali_cp" -d "$dir/classes" scripts/DexImmersionMerge.java
java -cp "$dir/classes:$smali_cp" DexImmersionMerge \
  "$dir/base.dex" "$dir/dex/classes.dex" "$dir/merged.dex"
"$tools/aapt2" dump resources "$base_apk" > "$dir/base-res.txt"
python3 - "$dir/base-res.txt" "$dir/stable.ids" <<'PY'
import pathlib,re,sys
content=pathlib.Path(sys.argv[1]).read_text()
match=re.search(r'resource (0x[0-9a-f]+) (?:org.openenglish.reader:)?drawable/icon',content)
if not match: raise SystemExit('Missing original icon resource ID')
pathlib.Path(sys.argv[2]).write_text('org.openenglish.reader:drawable/icon = '+match[1]+'\n')
PY
"$tools/aapt2" compile --dir app/src/main/res -o "$dir/resources/res.zip"
"$tools/aapt2" link -o "$dir/shell.apk" -I "$android_jar" \
  --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 23 --target-sdk-version 33 --stable-ids "$dir/stable.ids" \
  "$dir/resources/res.zip"
"$tools/aapt2" dump resources "$dir/shell.apk" > "$dir/new-res.txt"
python3 - "$base_apk" "$dir/shell.apk" "$dir/merged.dex" "$dir/unsigned.apk" \
  "$dir/base-res.txt" "$dir/new-res.txt" <<'PY'
import pathlib,re,sys,zipfile
base,shell,dex,output,old_res,new_res=map(pathlib.Path,sys.argv[1:])
def icon(p):
    m=re.search(r'resource (0x[0-9a-f]+) (?:org.openenglish.reader:)?drawable/icon',p.read_text())
    if not m: raise SystemExit('Missing icon in '+str(p))
    return m[1]
assert icon(old_res)==icon(new_res),'Icon resource ID changed'
with zipfile.ZipFile(base) as original, zipfile.ZipFile(shell) as replacement:
    with zipfile.ZipFile(output,'w') as out:
        for entry in original.infolist():
            if entry.filename.startswith('META-INF/'): continue
            payload=original.read(entry.filename)
            if entry.filename=='classes.dex':payload=dex.read_bytes()
            elif entry.filename=='AndroidManifest.xml':payload=replacement.read('AndroidManifest.xml')
            out.writestr(entry,payload)
PY
"$tools/zipalign" -f -P 16 4 "$dir/unsigned.apk" "$output_apk"
java -cp "$smali_cp" org.jf.baksmali.Main disassemble "$dir/base.dex" -o "$dir/old-smali"
java -cp "$smali_cp" org.jf.baksmali.Main disassemble "$dir/merged.dex" -o "$dir/new-smali"
python3 scripts/verify-immersion.py "$base_apk" "$output_apk" "$dir"
echo "Unsigned, aligned APK: $output_apk"
echo "Sign locally with the existing Everyone signing key before any Android install."
