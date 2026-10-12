#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
T="$ANDROID_HOME/build-tools/35.0.0"
J="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p build/validation/classes build/validation/dex
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -classpath "$J:build/classes" -d build/validation/classes tests/instrumentation/*.java
"$T/d8" --lib "$J" --classpath build/classes --output build/validation/dex build/validation/classes/org/openenglish/validation/*.class
"$T/aapt2" link -o build/validation/base.apk -I "$J" --manifest tests/instrumentation/AndroidManifest.xml --min-sdk-version 23 --target-sdk-version 33
cp build/validation/base.apk build/validation/unsealed.apk
(cd build/validation/dex && zip -q ../unsealed.apk classes.dex)
"$T/zipalign" -f -p 4 build/validation/unsealed.apk build/validation/aligned.apk
"$T/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --out build/validation/validation.apk build/validation/aligned.apk
