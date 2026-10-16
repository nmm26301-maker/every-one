#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/prefix-benchmark
git show 01404cb2fc10fb3b08b5128b847ae4bdfca2c44e:app/src/main/java/org/openenglish/reader/StreamingSpeechChunks.java | sed 's/StreamingSpeechChunks/PreviousSpeechChunks/g' > build/prefix-benchmark/PreviousSpeechChunks.java
java --add-modules jdk.compiler com.sun.tools.javac.Main -d build/prefix-benchmark app/src/main/java/org/openenglish/reader/StreamingSpeechChunks.java build/prefix-benchmark/PreviousSpeechChunks.java tests/StreamingAllocationBenchmark.java
java -cp build/prefix-benchmark StreamingAllocationBenchmark
