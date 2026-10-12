#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
mkdir -p build/magic-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp "$JSON_JAR" -d build/magic-tests tests/host-stubs/android/content/{SharedPreferences,Context,ContentResolver}.java tests/host-stubs/android/content/res/AssetManager.java tests/host-stubs/android/net/Uri.java app/src/main/java/org/openenglish/reader/{IslandMemory,IslandReview,IslandSrs,IslandPrompt,LocalPrompt,MagicBackup,PortableZip,LessonAudio,PronunciationCoach,ContourScore,LocalWav,SpeechDeadline}.java tests/{IslandContinuationTest,MagicBackupTest}.java
java -cp "build/magic-tests:$JSON_JAR" IslandContinuationTest
java -cp "build/magic-tests:$JSON_JAR" MagicBackupTest
