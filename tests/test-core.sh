#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -d build/tests app/src/main/java/org/openenglish/reader/LearningCore.java tests/LearningCoreTest.java
java -cp build/tests LearningCoreTest
