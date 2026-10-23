package org.openenglish.reader;

/**
 * Zero-account, zero-video quick-start lessons for all three languages.
 * These are original illustrative phrases, not excerpts from paid media.
 */
public final class JourneyQuickStart {
  public static final class Phrase {
    public final String original, pronunciation, meaning, tip;
    Phrase(String a, String b, String c, String d) {
      original = a; pronunciation = b; meaning = c; tip = d;
    }
  }
  private static final Phrase[] JAPANESE = {
      new Phrase("はじめまして。", "hajimemashite", "初次见面。", "第一次认识时使用，注意句尾清晰但不拖长。"),
      new Phrase("ちょっと待って。", "chotto matte", "稍等一下。", "ちょっと 在口语里也能表达委婉语气。"),
      new Phrase("どういうこと？", "dou iu koto?", "什么意思？", "日常会话中用来追问对方的意思。"),
      new Phrase("わかんない。", "wakannai", "我不知道/我不明白。", "わからない 的口语缩约，影视中常见。"),
      new Phrase("そうなんだ。", "sou nan da", "原来如此。", "根据语气可表示理解、惊讶或简单回应。"),
      new Phrase("行かなきゃ。", "ikanakya", "我得走了。", "行かなければ（ならない）的口语缩略。"),
      new Phrase("何してるの？", "nani shiteru no?", "你在干什么？", "している 口语常缩成 してる。"),
      new Phrase("もう一回お願い。", "mou ikkai onegai", "请再来一次。", "适合听不清台词时使用。")
  };
  private static final Phrase[] ENGLISH = {
      new Phrase("Could you say that again?", "", "你可以再说一遍吗？", "留意 could you 的连读与句末礼貌语气。"),
      new Phrase("I didn't catch that.", "", "我刚才没听清。", "自然表达听不懂或没听清，不必逐字翻译。"),
      new Phrase("What do you mean?", "", "你是什么意思？", "注意 what do you 的自然连读。"),
      new Phrase("Give me a second.", "", "稍等我一下。", "second 可表示短暂片刻。"),
      new Phrase("That sounds good.", "", "听起来不错。", "适合响应建议，强调 sounds。"),
      new Phrase("I'm working on it.", "", "我正在处理。", "适合真实工作与沟通场景。")
  };
  private JourneyQuickStart() {}

  public static String normalizeLanguage(String language) {
    if ("ja".equals(language) || "en".equals(language)) return language;
    return "yue";
  }

  public static int size(String language) {
    String lang = normalizeLanguage(language);
    if ("ja".equals(lang)) return JAPANESE.length;
    if ("en".equals(lang)) return ENGLISH.length;
    return JourneyCantonese.lessonCount();
  }

  public static Phrase at(String language, int index) {
    String lang = normalizeLanguage(language);
    int n = size(lang);
    int i = ((index % n) + n) % n;
    if ("ja".equals(lang)) return JAPANESE[i];
    if ("en".equals(lang)) return ENGLISH[i];
    JourneyCantonese.Phrase lesson = JourneyCantonese.lesson(i);
    return new Phrase(lesson.hanzi, lesson.jyutping, lesson.mandarin, lesson.hint);
  }
}
