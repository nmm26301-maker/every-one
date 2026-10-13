#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/deadline-recovery
java --add-modules jdk.compiler com.sun.tools.javac.Main -d build/deadline-recovery app/src/main/java/org/openenglish/reader/SpeechDeadline.java tests/SpeechDeadlineRecoveryTest.java
java -cp build/deadline-recovery SpeechDeadlineRecoveryTest
