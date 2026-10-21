package org.openenglish.reader;

/** Pure Java quick-launch drills work before importing any video or model. */
public final class JourneyQuickStartTest {
  private static void check(boolean ok, String label) {
    if (!ok) throw new AssertionError(label);
  }
  public static void main(String[] args) {
    check(JourneyQuickStart.size("yue") == JourneyCantonese.lessonCount(), "Cantonese builtin reuse");
    check(JourneyQuickStart.size("ja") >= 8, "Japanese native fast phrases");
    check(JourneyQuickStart.size("en") >= 6, "English native fast phrases");
    check(JourneyQuickStart.at("ja", 0).original.equals("はじめまして。"), "first Japanese");
    check(JourneyQuickStart.at("yue", 0).pronunciation.equals("nei5 hou2"), "tone guidance");
    check(JourneyQuickStart.at("yue", 10).original.equals("你好！"), "cycle");
    check(JourneyQuickStart.at("ja", -1).original.equals(
        JourneyQuickStart.at("ja", JourneyQuickStart.size("ja") - 1).original), "negative index");
    check(JourneyQuickStart.at("en", 1).original.contains("catch"), "practical English");
    check(JourneyQuickStart.normalizeLanguage(null).equals("yue"), "safe language");
    for (String language : new String[]{"yue","ja","en"}) {
      for (int i = 0; i < JourneyQuickStart.size(language); i++) {
        JourneyQuickStart.Phrase p = JourneyQuickStart.at(language, i);
        check(!p.original.isEmpty() && !p.meaning.isEmpty() && !p.tip.isEmpty(),
            "usable exercise " + language + " " + i);
      }
    }
    System.out.println("JourneyQuickStart: OK");
  }
}
