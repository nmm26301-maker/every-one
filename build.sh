#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0}"
TOOLS="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
command -v java >/dev/null
test -f "$ANDROID_JAR" && test -x "$TOOLS/aapt2"
node scripts/render-book.mjs
rm -rf build/classes build/dex
mkdir -p build/classes build/dex dist
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR:vendor/kuromoji/core.jar:vendor/kuromoji/ipadic.jar:vendor/onnxruntime/classes.jar" -d build/classes app/src/main/java/org/openenglish/reader/*.java
"$TOOLS/d8" --lib "$ANDROID_JAR" --output build/dex build/classes/org/openenglish/reader/*.class vendor/kuromoji/core.jar vendor/kuromoji/ipadic.jar vendor/onnxruntime/classes.jar
"$TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$TOOLS/aapt2" link -o build/base.apk -I "$ANDROID_JAR" --manifest app/src/main/AndroidManifest.xml --min-sdk-version 23 --target-sdk-version 33 -A app/src/main/assets build/resources.zip
cp build/base.apk build/unsealed.apk
(cd build/dex && zip -q ../unsealed.apk classes.dex)
test -f app/src/main/jniLibs/arm64-v8a/libenglish_whisper.so
test -f app/src/main/jniLibs/arm64-v8a/libenglish_llama.so
mkdir -p build/native-apk/lib/arm64-v8a
cp app/src/main/jniLibs/arm64-v8a/*.so build/native-apk/lib/arm64-v8a/
(cd build/native-apk && zip -q -0 ../unsealed.apk lib/arm64-v8a/*.so)
"$TOOLS/zipalign" -f -P 16 4 build/unsealed.apk build/aligned.apk
SIGNING_KEYSTORE="${SIGNING_KEYSTORE:?Point to the original private signing keystore}"
SIGNING_ALIAS="${SIGNING_ALIAS:?Set the signing alias}"
: "${SIGNING_STORE_PASSWORD:?Set signing store password}"
: "${SIGNING_KEY_PASSWORD:?Set signing key password}"
export SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD
"$TOOLS/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_ALIAS" --ks-pass env:SIGNING_STORE_PASSWORD --key-pass env:SIGNING_KEY_PASSWORD --out dist/Everyone-AI-English-4.6.1.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose dist/Everyone-AI-English-4.6.1.apk
echo "Built dist/Everyone-AI-English-4.6.1.apk"
