#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/journey-tests
json_jar=${JOURNEY_JSON_JAR:-/usr/share/java/com.android.json.jar}
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp "$json_jar" -d build/journey-tests tests/journey-stubs/android/content/*.java app/src/main/java/org/openenglish/reader/Journey{Core,Feedback,State}.java tests/JourneyTest.java tests/JourneyConversationTest.java tests/JourneyDraftTest.java
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp "build/journey-tests:$json_jar" -d build/journey-tests tests/JourneyBackupTest.java tests/JourneyPageTest.java
java -cp "build/journey-tests:$json_jar" JourneyTest
java -cp "build/journey-tests:$json_jar" JourneyConversationTest
java -cp "build/journey-tests:$json_jar" JourneyDraftTest
java -cp "build/journey-tests:$json_jar" JourneyBackupTest
java -cp "build/journey-tests:$json_jar" JourneyPageTest
python3 tests/journey-content.py
