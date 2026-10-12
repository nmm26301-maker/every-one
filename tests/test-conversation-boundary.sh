#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/boundary-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/boundary-tests app/src/main/java/org/openenglish/reader/{VoiceGate,PlaybackGuard,SpeechChunks,VoiceTurns,ManualCapture,VoiceEvents}.java tests/{ConversationBoundaryTest,VoiceFlowTest,PlaybackGuardTest}.java
java -cp build/boundary-tests ConversationBoundaryTest
java -cp build/boundary-tests VoiceFlowTest
java -cp build/boundary-tests PlaybackGuardTest
