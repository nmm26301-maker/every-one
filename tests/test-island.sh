#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR to org.json 20240303 jar}"
mkdir -p build/magic-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/magic-tests app/src/main/java/org/openenglish/reader/{IslandSrs,IslandPrompt,MagicSpeechChunks,LocalPrompt}.java tests/IslandCoreTest.java
java -cp build/magic-tests IslandCoreTest
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp "$JSON_JAR:build/magic-tests" -d build/magic-tests tests/host-stubs/android/content/{SharedPreferences,Context,ContentResolver}.java tests/host-stubs/android/content/res/AssetManager.java tests/host-stubs/android/net/Uri.java app/src/main/java/org/openenglish/reader/{IslandMemory,IslandReview}.java tests/IslandMemoryTest.java
java -cp "build/magic-tests:$JSON_JAR" IslandMemoryTest
