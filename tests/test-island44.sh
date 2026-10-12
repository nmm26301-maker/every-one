#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/completion-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp "$JSON_JAR" -d build/completion-tests tests/host-stubs/android/content/{SharedPreferences,Context,ContentResolver}.java tests/host-stubs/android/content/res/AssetManager.java tests/host-stubs/android/net/Uri.java tests/warm-stubs/android/os/Build.java tests/warm-stubs/org/openenglish/reader/*.java app/src/main/java/org/openenglish/reader/{LocalAI,LocalPrompt,IslandPrompt,LocalWav,TranscriptCheck,IslandScene,IslandMemory}.java tests/IslandCompletionTest.java
java -cp "build/completion-tests:$JSON_JAR" IslandCompletionTest
