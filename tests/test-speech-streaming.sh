#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/speech-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/speech-tests app/src/main/java/org/openenglish/reader/{StreamingSpeechChunks,SpeechChunks,MagicSpeechChunks}.java tests/SpeechStreamingTest.java
java -cp build/speech-tests SpeechStreamingTest
