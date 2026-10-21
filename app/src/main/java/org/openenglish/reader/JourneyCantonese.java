package org.openenglish.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 粤语入门素材与离线口语词条。
 * 使用香港语言学学会粤拼（Jyutping）；此类没有音频评分或云服务。
 */
public final class JourneyCantonese {
  /** 示范台词、普通话释义与带调数字的粤拼。 */
  public static final class Phrase {
    public final String hanzi, jyutping, mandarin, hint;
    Phrase(String text, String phonetic, String translation, String tip) {
      hanzi = text;
      jyutping = phonetic;
      mandarin = translation;
      hint = tip;
    }
  }

  /** 从真实影视对话常见句式中选取的入门练习（不是影视作品字幕）。 */
  private static final Phrase[] PHRASES = {
    new Phrase("你好！", "nei5 hou2", "你好！",
        "nei5 的 5 调从低往中上升；hou2 的 2 调向高处上扬。"),
    new Phrase("唔該。", "m4 goi1", "谢谢（别人帮忙）、劳驾。",
        "m4 是鼻音单独成音节，唔係普通话的“嗯”。"),
    new Phrase("多謝！", "do1 ze6", "多谢（收到礼物、称赞等）。",
        "多謝与唔該的使用语境不完全一样。"),
    new Phrase("我唔知。", "ngo5 m4 zi1", "我不知道。",
        "唔常用于否定；ngo5 注意 ng 鼻音声母。"),
    new Phrase("冇問題。", "mou5 man6 tai4", "没问题。",
        "冇表示“没有”；粤语通常不用普通话的“没”。"),
    new Phrase("你喺邊度呀？", "nei5 hai2 bin1 dou6 aa3", "你在哪里呀？",
        "喺表示“在”；邊度表示“哪里”。"),
    new Phrase("等陣先。", "dang2 zan6 sin1", "等一会儿再说。",
        "陣指短暂的时间；先在句尾可以表示“先这样”。"),
    new Phrase("搞掂喇！", "gaau2 dim6 laa3", "搞定啦！",
        "掂表示办妥，喇是常见语气助词。"),
    new Phrase("真係？", "zan1 hai6", "真的吗？",
        "係意为“是”；注意它不同于表示位置的“喺”。"),
    new Phrase("點解呀？", "dim2 gaai2 aa3", "为什么呀？",
        "點解是询问原因的口语表达。")
  };

  private static final String[][] WORDS = {
    {"唔該", "m4 goi1", "谢谢、劳驾；常用于接受帮助或请求帮忙"},
    {"多謝", "do1 ze6", "多谢；常用于收礼物、得到称赞等"},
    {"唔", "m4", "不，否定词"},
    {"冇", "mou5", "没有"},
    {"喺", "hai2", "在，表示地点"},
    {"係", "hai6", "是"},
    {"咗", "zo2", "动作完成体标记，常相当于“了”"},
    {"緊", "gan2", "表示动作正在进行等"},
    {"啲", "di1", "一些，或表达程度"},
    {"嘅", "ge3", "的，表示所属或修饰"},
    {"咩", "me1", "什么；也可作疑问、惊讶语气"},
    {"佢", "keoi5", "他、她、它"},
    {"哋", "dei6", "复数后缀，常见于我哋、你哋、佢哋"},
    {"喇", "laa3", "语气助词，用法依上下文变化"},
    {"掂", "dim6", "行、妥当、搞定"},
    {"點解", "dim2 gaai2", "为什么"},
    {"邊度", "bin1 dou6", "哪里"},
    {"等陣", "dang2 zan6", "等一会儿"},
    {"嘢", "je5", "东西、事物"},
    {"攰", "gui6", "累"},
    {"犀利", "sai1 lei6", "厉害"}
  };

  private JourneyCantonese() {}

  public static int lessonCount() {
    return PHRASES.length;
  }

  public static Phrase lesson(int index) {
    return PHRASES[((index % PHRASES.length) + PHRASES.length) % PHRASES.length];
  }

  /** 返回字幕中找到的少量常见粤语词条；不对陌生词臆造释义。 */
  public static List<String> lookup(String line) {
    if (line == null || line.isEmpty()) return Collections.emptyList();
    List<String> found = new ArrayList<>();
    for (String[] word : WORDS) {
      if (line.contains(word[0])) {
        found.add(word[0] + " · " + word[1] + "\n" + word[2]);
      }
    }
    return found;
  }

  public static String quickTip(String line) {
    List<String> found = lookup(line);
    if (found.isEmpty()) return "这句未命中内置词条；可结合字幕理解，不自动推测发音和语气。";
    return found.get(0).replace("\n", " · ");
  }

  /** 忽略标点与空格进行文本对照；不能当作音准或声调评分。 */
  public static String normalize(String text) {
    return text == null ? "" : text.replaceAll("[\\p{P}\\p{Z}\\s\\p{S}]+", "")
        .toLowerCase(Locale.ROOT);
  }

  /** 反馈 Android 语音识别出的字词差异，明确不声称测出真实发音好坏。 */
  public static String transcriptFeedback(String reference, String recognized) {
    String ref = normalize(reference), got = normalize(recognized);
    if (got.isEmpty()) return "没有识别到台词，请检查语音识别服务或重试。";
    if (ref.equals(got)) return "识别文本相同。尚不能判断粤语声调、辅音和韵母是否准确。";
    int head = 0;
    while (head < ref.length() && head < got.length()
        && ref.charAt(head) == got.charAt(head)) head++;
    String expected = head < ref.length() ? ref.substring(head) : "（句末）";
    String heard = head < got.length() ? got.substring(head) : "（句末）";
    return "识别文本有差异\n期望："+ expected + "\n听写结果：" + heard
        + "\n请回听视频原音，重点检查听辨。文字差异不等于声调错误。";
  }

  public static String toneHelp() {
    return "粤拼 Jyutping 六个标调数字：1 高平、2 高升、3 中平、4 低降、"
        + "5 低升、6 低平。入声尾 -p / -t / -k 收音要短促，"
        + "声调识别需要专门的音频模型，普通语音转文字无法可靠评估。";
  }
}
