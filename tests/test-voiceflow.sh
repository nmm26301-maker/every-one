#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/voice-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -cp build/classes -d build/voice-tests app/src/main/java/org/openenglish/reader/{VoiceGate,SpeechChunks,StreamingSpeechChunks,VoiceTurns,ManualCapture,PlaybackGuard,VoiceEvents}.java tests/{VoiceFlowTest,PlaybackGuardTest,CloudJobTest}.java
java -cp build/voice-tests:build/classes VoiceFlowTest
java -cp build/voice-tests:build/classes PlaybackGuardTest
java -cp build/voice-tests:build/classes CloudJobTest
