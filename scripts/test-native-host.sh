#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${WHISPER_MODEL:?Path to the verified ggml-tiny.en.bin}"
: "${DEEPSEEK_MODEL:?Path to the verified DeepSeek 1.5B Q4_K_M GGUF}"
CMAKE_EXEC="${CMAKE_EXEC:-cmake}"
NINJA_EXEC="${NINJA_EXEC:-ninja}"
for engine in whisper llama; do
 args=()
 if [ -n "${JNI_HEADER_DIR:-}" ]; then args+=("-DJNI_HEADER_DIR=$JNI_HEADER_DIR"); fi
 "$CMAKE_EXEC" -S native -B "build/native-$engine-host" -G Ninja -DCMAKE_MAKE_PROGRAM="$NINJA_EXEC" -DLOCAL_ENGINE="$engine" -DCMAKE_BUILD_TYPE=Release "${args[@]}"
 "$CMAKE_EXEC" --build "build/native-$engine-host" -j4 --target "english_$engine"
done
mkdir -p build/native-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -d build/native-tests app/src/main/java/org/openenglish/reader/LocalNative.java app/src/main/java/org/openenglish/reader/LocalWav.java app/src/main/java/org/openenglish/reader/LocalPrompt.java app/src/main/java/org/openenglish/reader/VoiceGate.java app/src/main/java/org/openenglish/reader/SpeechChunks.java app/src/main/java/org/openenglish/reader/VoiceTurns.java tests/LocalNativeTest.java tests/LocalDuplexNativeTest.java tests/VoiceFlowTest.java
java -Djava.library.path=build/native-whisper-host:build/native-llama-host -cp build/native-tests LocalNativeTest "$WHISPER_MODEL" "$DEEPSEEK_MODEL" vendor/whisper.cpp/samples/jfk.wav

java -cp build/native-tests VoiceFlowTest
java -Djava.library.path=build/native-whisper-host:build/native-llama-host -cp build/native-tests LocalDuplexNativeTest "$WHISPER_MODEL" "$DEEPSEEK_MODEL" vendor/whisper.cpp/samples/jfk.wav "${VAD_MODEL:-$PWD/app/src/main/assets/models/ggml-silero-v6.2.0.bin}"
