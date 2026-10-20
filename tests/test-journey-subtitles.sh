#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -d "$tmp" app/src/main/java/org/openenglish/reader/JourneySubtitles.java tests/JourneySubtitlesTest.java \
  app/src/main/java/org/openenglish/reader/JourneyCantonese.java tests/JourneyCantoneseTest.java
java -cp "$tmp" org.openenglish.reader.JourneySubtitlesTest
java -cp "$tmp" org.openenglish.reader.JourneyCantoneseTest
