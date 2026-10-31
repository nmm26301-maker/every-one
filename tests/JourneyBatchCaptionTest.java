package org.openenglish.reader;

import java.util.Arrays;
import java.util.List;

public final class JourneyBatchCaptionTest {
  private static void yes(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    List<JourneyBatchCaption.Chunk> chunks = JourneyBatchCaption.plan(250000, 120000);
    yes(chunks.size() == 3, "chunk count");
    yes(chunks.get(0).startMs == 0 && chunks.get(0).endMs == 120000, "first chunk");
    yes(chunks.get(2).startMs == 240000 && chunks.get(2).endMs == 250000, "last chunk");

    JourneySubtitles.Cue old = new JourneySubtitles.Cue(0, 10000, "old");
    JourneySubtitles.Cue fresh = new JourneySubtitles.Cue(5000, 15000, "new");
    List<JourneySubtitles.Cue> merged = JourneyBatchCaption.merge(
        Arrays.asList(old), Arrays.asList(fresh));
    yes(merged.size() == 2 && merged.get(0).endMs == 5000, "merge trims overlap");
    yes(JourneyBatchCaption.normalize(Arrays.asList(
        new JourneySubtitles.Cue(0, 1000, "a"),
        new JourneySubtitles.Cue(900, 1500, "b"))).get(1).startMs == 1000,
        "normalize trims boundary");

    String prompt = JourneyCaptionTranslation.prompt(Arrays.asList(old), "简体中文");
    yes(prompt.contains("只返回 JSON 数组"), "strict prompt");
    List<String> translations = JourneyCaptionTranslation.parse(
        "[{\"index\":1,\"text\":\"旧字幕\"}]", 1);
    yes("旧字幕".equals(translations.get(0)), "parse translation");
    translations = JourneyCaptionTranslation.parse(
        "```json\n[{\"index\":1,\"text\":\"\\u65e7\\u5b57\\u5e55 \\\"A\\\"\"}]\n```", 1);
    yes("旧字幕 \"A\"".equals(translations.get(0)), "decode escaped translation");
    yes(JourneyCaptionTranslation.apply(Arrays.asList(old), translations).get(0).startMs == 0,
        "preserve timing");
    boolean rejected = false;
    try { JourneyCaptionTranslation.parse("[{\"index\":1,\"text\":\"a\"}]", 2); }
    catch (java.io.IOException expected) { rejected = true; }
    yes(rejected, "reject incomplete translation");
    rejected = false;
    try { JourneyCaptionTranslation.parse("[{\"index\":1,\"text\":\"a\"},]", 1); }
    catch (java.io.IOException expected) { rejected = true; }
    yes(rejected, "reject trailing comma");
    rejected = false;
    try { JourneyCaptionTranslation.parse("[{\"index\":1,\"text\":\"a\"},{\"index\":1,\"text\":\"b\"}]", 2); }
    catch (java.io.IOException expected) { rejected = true; }
    yes(rejected, "reject duplicate index");
    rejected = false;
    try { JourneyBatchCaption.normalize(Arrays.asList((JourneySubtitles.Cue) null)); }
    catch (java.io.IOException expected) { rejected = true; }
    yes(rejected, "reject null cue");
    System.out.println("JourneyBatchCaption: OK");
  }
}
