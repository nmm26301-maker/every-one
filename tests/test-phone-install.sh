#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
json_jar=${JOURNEY_JSON_JAR:-/usr/share/java/com.android.json.jar}
ort_jar=${JOURNEY_ORT_JAR:-vendor/onnxruntime/classes.jar}
if [[ ! -f "$ort_jar" ]]; then
  mkdir -p "$(dirname "$ort_jar")"
  curl --fail --location --retry 3 https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.22.0/onnxruntime-1.22.0.jar -o "$ort_jar"
fi
mkdir -p build/phone-install-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -cp "$json_jar:$ort_jar" -d build/phone-install-tests tests/phone-stubs/android/content/Context.java tests/phone-stubs/android/os/Build.java tests/host-stubs/android/content/res/AssetManager.java app/src/main/java/org/openenglish/reader/Journey{Core,Phones,PhoneEngine}.java tests/JourneyPhoneInstallTest.java
java -cp "build/phone-install-tests:$json_jar:$ort_jar" org.openenglish.reader.JourneyPhoneInstallTest
