#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/buffer-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/buffer-tests app/src/main/java/org/openenglish/reader/VoiceGate.java tests/VoiceGateOld.java tests/VoiceGateBufferTest.java
java -cp build/buffer-tests org.openenglish.reader.VoiceGateBufferTest
