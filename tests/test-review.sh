#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/tests app/src/main/java/org/openenglish/reader/LearningCore.java app/src/main/java/org/openenglish/reader/TrainingCore.java tests/ReviewCoreTest.java
java -cp build/tests ReviewCoreTest
python3 tests/review-sql.py
