package org.openenglish.reader;
import java.util.List;

/** 粤语教材、口语词条与转写文本对照的无网络测试。 */
public final class JourneyCantoneseTest {
  private static void yes(boolean ok, String name) {
    if (!ok) throw new AssertionError(name);
  }
  public static void main(String[] args) {
    yes(JourneyCantonese.lessonCount() >= 10, "lesson count");
    yes(JourneyCantonese.lesson(0).hanzi.equals("你好！"), "greeting");
    yes(JourneyCantonese.lesson(0).jyutping.equals("nei5 hou2"), "jyutping");
    yes(JourneyCantonese.lesson(10).hanzi.equals("你好！"), "cycle");
    List<String> entries = JourneyCantonese.lookup("我唔知喺邊度呀");
    yes(entries.stream().anyMatch(s -> s.startsWith("唔 · m4")), "negative");
    yes(entries.stream().anyMatch(s -> s.startsWith("喺 · hai2")), "at/location");
    yes(JourneyCantonese.lookup("今日はいい天気です").isEmpty(), "language separation");
    yes(JourneyCantonese.normalize(" 你， 好！").equals("你好"), "punctuation removal");
    yes(JourneyCantonese.transcriptFeedback("你好！", "你好").contains("尚不能判断"), "no fake score");
    yes(JourneyCantonese.transcriptFeedback("我唔知", "我知道").contains("文字差异"), "mistake");
    yes(JourneyCantonese.toneHelp().contains("-p / -t / -k"), "stop finals");
    System.out.println("JourneyCantonese: OK");
  }
}
