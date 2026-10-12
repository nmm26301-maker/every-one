#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/language-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/language-tests app/src/main/java/org/openenglish/reader/ContourScore.java app/src/main/java/org/openenglish/reader/PronunciationCoach.java app/src/main/java/org/openenglish/reader/LessonAudio.java app/src/main/java/org/openenglish/reader/LocalWav.java tests/PronunciationCoachTest.java
java -cp build/language-tests PronunciationCoachTest
