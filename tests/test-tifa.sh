#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JSON_JAR:?Set JSON_JAR}"
: "${ORT_HOST_JAR:?Set ORT_HOST_JAR to official ONNX Runtime 1.22.0 Java host jar}"
mkdir -p build/tifa-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp "$JSON_JAR:$ORT_HOST_JAR" -d build/tifa-tests app/src/main/java/org/openenglish/reader/{LocalTifa,TifaDecoder,SegmentCoach,ArticulationHints,ContourScore,PronunciationCoach,LocalWav}.java tests/TifaIntegrationTest.java
java -Xmx1g -cp "build/tifa-tests:$JSON_JAR:$ORT_HOST_JAR" TifaIntegrationTest "$PWD"
