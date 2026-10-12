#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/tests app/src/main/java/org/openenglish/reader/ReviewPlanner.java tests/ReviewPlannerTest.java
java -cp build/tests ReviewPlannerTest
python3 tests/planner-sql.py
