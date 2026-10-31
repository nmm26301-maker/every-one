#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -d "$tmp" app/src/main/java/org/openenglish/reader/JourneySubtitles.java tests/JourneySubtitlesTest.java \
  app/src/main/java/org/openenglish/reader/JourneyCantonese.java tests/JourneyCantoneseTest.java \
  app/src/main/java/org/openenglish/reader/JourneyToneContour.java tests/JourneyToneContourTest.java \
  app/src/main/java/org/openenglish/reader/JourneyToneCompare.java tests/JourneyToneCompareTest.java \
  app/src/main/java/org/openenglish/reader/JourneyJyutping.java tests/JourneyJyutpingTest.java \
  app/src/main/java/org/openenglish/reader/JourneyQuickStart.java tests/JourneyQuickStartTest.java \
  app/src/main/java/org/openenglish/reader/JourneyLineCoach.java tests/JourneyLineCoachTest.java \
  app/src/main/java/org/openenglish/reader/JourneyCaptionDraft.java tests/JourneyCaptionDraftTest.java \
  app/src/main/java/org/openenglish/reader/JourneyMp4Timeline.java tests/JourneyMp4TimelineTest.java \
  app/src/main/java/org/openenglish/reader/JourneyBatchCaption.java \
  app/src/main/java/org/openenglish/reader/JourneyCaptionTranslation.java tests/JourneyBatchCaptionTest.java
java -cp "$tmp" org.openenglish.reader.JourneySubtitlesTest
java -cp "$tmp" org.openenglish.reader.JourneyCantoneseTest
java -cp "$tmp" org.openenglish.reader.JourneyToneContourTest
java -cp "$tmp" org.openenglish.reader.JourneyToneCompareTest
java -cp "$tmp" org.openenglish.reader.JourneyJyutpingTest
java -cp "$tmp" org.openenglish.reader.JourneyQuickStartTest
java -cp "$tmp" org.openenglish.reader.JourneyLineCoachTest
java -cp "$tmp" org.openenglish.reader.JourneyCaptionDraftTest
java -cp "$tmp" org.openenglish.reader.JourneyMp4TimelineTest
java -cp "$tmp" org.openenglish.reader.JourneyBatchCaptionTest
