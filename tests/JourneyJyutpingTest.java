package org.openenglish.reader;

import java.util.List;

/** 离线粤拼构词拆解与易混音参考测试，严格禁止臆造发音。 */
public final class JourneyJyutpingTest {
  private static void yes(boolean condition, String name) {
    if (!condition) throw new AssertionError(name);
  }

  public static void main(String[] args) {
    JourneyJyutping.Syllable greeting = JourneyJyutping.parse("nei5");
    yes(greeting != null, "nei5 parsed");
    yes("n".equals(greeting.onset) && "e".equals(greeting.nucleus)
        && "i".equals(greeting.coda) && greeting.tone == 5, "nei5 decomposition");
    JourneyJyutping.Syllable nasal = JourneyJyutping.parse("m4");
    yes(nasal != null && nasal.syllabicNasal && nasal.tone == 4, "m4 syllabic nasal");
    JourneyJyutping.Syllable ng = JourneyJyutping.parse("ngo5");
    yes(ng != null && "ng".equals(ng.onset) && "o".equals(ng.nucleus), "ng onset");
    JourneyJyutping.Syllable empty = JourneyJyutping.parse("aa3");
    yes(empty != null && empty.onset.isEmpty() && "aa".equals(empty.nucleus),
        "zero onset");
    JourneyJyutping.Syllable gw = JourneyJyutping.parse("gwaa1");
    yes(gw != null && "gw".equals(gw.onset) && "aa".equals(gw.nucleus),
        "gw long onset");
    JourneyJyutping.Syllable checked = JourneyJyutping.parse("sik1");
    yes(checked != null && "i".equals(checked.nucleus) && "k".equals(checked.coda)
        && checked.isChecked() && checked.detail().contains("短促"), "checked tail");
    JourneyJyutping.Syllable eo = JourneyJyutping.parse("zeoi3");
    yes(eo != null && "z".equals(eo.onset) && "eo".equals(eo.nucleus)
        && "i".equals(eo.coda), "eoi final");
    yes(JourneyJyutping.parse(null) == null, "null");
    yes(JourneyJyutping.parse("bad") == null, "missing tone number");
    yes(JourneyJyutping.parse("abc9") == null, "invalid tone");
    yes(JourneyJyutping.parse("abc1") == null, "unknown final");
    yes(JourneyJyutping.parse("nei5 hou2") == null, "single syllable parser");
    yes(JourneyJyutping.parseLine("nei5 hou2").size() == 2, "line split");
    for (int i = 0; i < JourneyCantonese.lessonCount(); i++) {
      JourneyCantonese.Phrase lesson = JourneyCantonese.lesson(i);
      String[] tokens = lesson.jyutping.split("\\s+");
      List<JourneyJyutping.Syllable> syllables = JourneyJyutping.parseLine(lesson.jyutping);
      yes(syllables.size() == tokens.length, "entire lesson parsed " + i);
    }
    for (int i = 0; i < JourneyJyutping.contrastCount(); i++) {
      JourneyJyutping.Contrast p = JourneyJyutping.contrast(i);
      yes(JourneyJyutping.parse(p.firstJyutping) != null
          && JourneyJyutping.parse(p.secondJyutping) != null, "contrast parsed " + i);
      yes(!p.firstJyutping.equals(p.secondJyutping), "contrast differs " + i);
    }
    yes(JourneyJyutping.contrast(-1) != null, "contrast circular index");
    System.out.println("JourneyJyutping: OK");
  }
}
