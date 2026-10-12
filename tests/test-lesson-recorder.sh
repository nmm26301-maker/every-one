#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/lesson-capture-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/lesson-capture-tests tests/mic-stubs/android/media/AudioRecord.java tests/mic-stubs/android/media/AudioRecordingConfiguration.java tests/mic-stubs/android/media/AudioFormat.java tests/mic-stubs/android/media/MediaRecorder.java app/src/main/java/org/openenglish/reader/ContourScore.java app/src/main/java/org/openenglish/reader/PronunciationCoach.java app/src/main/java/org/openenglish/reader/LessonRecorder.java tests/LessonRecorderTest.java
java -cp build/lesson-capture-tests LessonRecorderTest
