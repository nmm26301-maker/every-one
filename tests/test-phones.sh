#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
json_jar=${JOURNEY_JSON_JAR:-/usr/share/java/com.android.json.jar}
mkdir -p build/phone-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp "$json_jar" -d build/phone-tests app/src/main/java/org/openenglish/reader/Journey{Core,Phones}.java tests/JourneyPhonesTest.java
java -cp "build/phone-tests:$json_jar" JourneyPhonesTest
