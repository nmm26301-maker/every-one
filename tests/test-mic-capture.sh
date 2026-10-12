#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/mic-tests
mapfile -t stubs < <(rg --files tests/mic-stubs -g '*.java')
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/mic-tests "${stubs[@]}" app/src/main/java/org/openenglish/reader/VoiceGate.java app/src/main/java/org/openenglish/reader/ManualCapture.java app/src/main/java/org/openenglish/reader/ConversationMic.java tests/ConversationMicHostTest.java
java -cp build/mic-tests ConversationMicHostTest
