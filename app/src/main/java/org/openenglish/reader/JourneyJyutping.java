package org.openenglish.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 香港语言学学会粤拼 (Jyutping) 的离线音节拆解与易混音示例。
 * 仅解释已有、明确提供的粤拼，不能由任意汉字推断发音，也没有声学打分。
 */
public final class JourneyJyutping {
  private static final String[] INITIALS = {
      "gw", "kw", "ng", "b", "p", "m", "f", "d", "t", "n", "l",
      "g", "k", "h", "w", "z", "c", "s", "j"
  };
  private static final String[] NUCLEI = {"aa", "yu", "oe", "eo", "a", "e", "i", "o", "u"};
  private static final Set<String> FINALS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      "i", "ip", "it", "ik", "im", "in", "ing", "iu",
      "yu", "yut", "yun",
      "u", "up", "ut", "uk", "um", "un", "ung", "ui",
      "e", "ep", "et", "ek", "em", "en", "eng", "ei", "eu",
      "eot", "eon", "eoi",
      "oe", "oet", "oek", "oeng",
      "o", "ot", "ok", "on", "ong", "oi", "ou",
      "a", "ap", "at", "ak", "am", "an", "ang", "ai", "au",
      "aa", "aap", "aat", "aak", "aam", "aan", "aang", "aai", "aau"
  )));
  private static final String[] TONE = {
      "", "高平", "高升", "中平", "低降", "低升", "低平"
  };
  private JourneyJyutping() {}

  public static final class Syllable {
    public final String jyutping, onset, nucleus, coda;
    public final int tone;
    public final boolean syllabicNasal;
    Syllable(String spelling, String first, String vowel, String ending,
             int number, boolean nasal) {
      jyutping = spelling; onset = first; nucleus = vowel;
      coda = ending; tone = number; syllabicNasal = nasal;
    }
    public String detail() {
      String line = jyutping + "  ·  " + TONE[tone] + "（" + tone + " 调）\n";
      if (syllabicNasal) {
        return line + "成音节鼻音：" + nucleus + "（无需添加元音）";
      }
      line += "声母：" + (onset.isEmpty() ? "零声母" : onset)
          + "  韵腹：" + nucleus + "  韵尾：" + (coda.isEmpty() ? "无" : coda);
      if (isChecked()) line += "\n入声塞音韵尾 -" + coda + "：短促闭塞，不要额外读出一个元音。";
      if ("ng".equals(onset) || "ng".equals(coda))
        line += "\nng 用舌根与软腭形成鼻音，不同于前端舌尖的 n。";
      if ("aa".equals(nucleus)) line += "\naa 为较长的开口元音，与较短的 a 对照练习。";
      return line;
    }
    public boolean isChecked() {
      return "p".equals(coda) || "t".equals(coda) || "k".equals(coda);
    }
  }

  /** 返回 null 表示不是已识别的合规粤拼音节；不推测不完整输入。 */
  public static Syllable parse(String token) {
    if (token == null) return null;
    String raw = token.trim().toLowerCase(Locale.ROOT);
    if (!raw.matches("[a-z]+[1-6]")) return null;
    int tone = raw.charAt(raw.length() - 1) - '0';
    String letters = raw.substring(0, raw.length() - 1);
    if ("m".equals(letters) || "ng".equals(letters))
      return new Syllable(raw, "", letters, "", tone, true);
    for (String initial : INITIALS) {
      if (letters.startsWith(initial)) {
        Syllable found = finalParts(raw, initial, letters.substring(initial.length()), tone);
        if (found != null) return found;
      }
    }
    return finalParts(raw, "", letters, tone);
  }

  private static Syllable finalParts(String spelling, String onset, String remainder, int tone) {
    if (!FINALS.contains(remainder)) return null;
    for (String nucleus : NUCLEI) {
      if (!remainder.startsWith(nucleus)) continue;
      String coda = remainder.substring(nucleus.length());
      return new Syllable(spelling, onset, nucleus, coda, tone, false);
    }
    return null;
  }

  public static List<Syllable> parseLine(String jyutping) {
    if (jyutping == null || jyutping.trim().isEmpty()) return Collections.emptyList();
    List<Syllable> result = new ArrayList<>();
    String[] tokens = jyutping.trim().split("\\s+");
    for (String token : tokens) {
      Syllable part = parse(token);
      if (part != null) result.add(part);
    }
    return Collections.unmodifiableList(result);
  }

  /** 例子是静态教学参考，不宣称代表自然对话的完整语音辨析。 */
  public static final class Contrast {
    public final String title, firstCharacter, firstJyutping;
    public final String secondCharacter, secondJyutping, advice;
    Contrast(String title, String a, String aPron, String b, String bPron, String tip) {
      this.title = title; firstCharacter = a; firstJyutping = aPron;
      secondCharacter = b; secondJyutping = bPron; advice = tip;
    }
    public String display() {
      return title + "\n① " + firstCharacter + " " + firstJyutping + "    ② "
          + secondCharacter + " " + secondJyutping + "\n" + advice;
    }
  }

  private static final Contrast[] CONTRASTS = {
      new Contrast("送气 b / p", "包", "baau1", "拋", "paau1",
          "保持相同的韵母 aau1，p 在开头明显送气；b 不送气。"),
      new Contrast("擦塞音 z / c", "資", "zi1", "雌", "ci1",
          "两者都是舌尖附近的擦塞音；c 送气，z 不送气。"),
      new Contrast("短元音 a / 长元音 aa", "心", "sam1", "三", "saam1",
          "两者都以 -m 收音；听辨 a / aa 的开口度与长短。"),
      new Contrast("韵尾 n / ng", "山", "saan1", "生", "saang1",
          "n 的舌尖抵上齿龈，ng 的舌根抵软腭，不能混为一音。"),
      new Contrast("高平调 1 / 低平调 6", "詩", "si1", "事", "si6",
          "两者走势可能都平稳，但音高层级不同。音高走势图不能单独判对。"),
      new Contrast("入声调 1 / 6", "一", "jat1", "日", "jat6",
          "韵尾 -t 不释放气流，收音短促；两词的音高层级也不同。")
  };

  public static int contrastCount() {
    return CONTRASTS.length;
  }

  public static Contrast contrast(int index) {
    return CONTRASTS[((index % CONTRASTS.length) + CONTRASTS.length) % CONTRASTS.length];
  }
}
