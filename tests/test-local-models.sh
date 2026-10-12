#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/model-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -encoding UTF-8 -classpath build/classes -d build/model-tests tests/host-stubs/android/net/Uri.java tests/host-stubs/android/content/res/AssetManager.java tests/host-stubs/android/content/ContentResolver.java tests/host-stubs/android/content/SharedPreferences.java tests/host-stubs/android/content/Context.java app/src/main/java/org/openenglish/reader/LocalModels.java tests/LocalModelsTest.java
java -classpath build/model-tests:build/classes org.openenglish.reader.LocalModelsTest app/src/main/assets
