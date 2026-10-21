package org.openenglish.reader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;
import java.util.Set;

/**
 * 手机离线示范朗读，只允许使用已安装且不依赖网络的语言匹配音色。
 * 粤语必须匹配 zh-HK，绝不静默用普通话替代。
 */
public final class JourneySpeechGuide {
  public interface Notice {
    void show(String message);
  }

  private final Handler main = new Handler(Looper.getMainLooper());
  private final Notice notice;
  private TextToSpeech engine;
  private boolean ready;
  private boolean stopped;
  private Pending pending;

  private static final class Pending {
    final String text, tag;
    final boolean slow;
    Pending(String t, String lang, boolean s) { text = t; tag = lang; slow = s; }
  }

  public JourneySpeechGuide(Context context, Notice callback) {
    notice = callback;
    engine = new TextToSpeech(context.getApplicationContext(), status ->
        main.post(() -> {
          if (stopped) return;
          ready = status == TextToSpeech.SUCCESS;
          if (!ready) {
            pending = null;
            notice.show("系统语音引擎初始化失败，仍可播放影视原声。");
            return;
          }
          Pending next = pending;
          pending = null;
          if (next != null) start(next);
        }));
  }

  /** 返回排队/发音提示，实际成功与失败另由 Notice 通知。 */
  public String speak(String text, String language, boolean slow) {
    if (stopped) return "语音引擎已关闭";
    if (text == null || text.trim().isEmpty()) return "请先选择一句字幕或练习句";
    String tag = "yue".equals(language) ? "zh-HK"
        : "ja".equals(language) ? "ja-JP" : "en-US";
    Pending item = new Pending(text.trim(), tag, slow);
    if (!ready) {
      pending = item;
      return "正在初始化手机语音引擎……";
    }
    return start(item);
  }

  private String start(Pending item) {
    if (engine == null || stopped) return "手机语音引擎暂不可用";
    Voice voice = offlineVoice(item.tag);
    if (voice == null) {
      String name = "zh-HK".equals(item.tag) ? "粤语 zh-HK"
          : "ja-JP".equals(item.tag) ? "日语 ja-JP" : "英语 en-US";
      String failure = "没有检测到已安装的离线" + name
          + "音色。请在安卓「设置 → 文字转语音输出」中安装对应语音；"
          + "应用不会用普通话假装粤语，也不会自动联网下载。";
      notice.show(failure);
      return failure;
    }
    if (engine.setVoice(voice) != TextToSpeech.SUCCESS) {
      notice.show("系统不支持切换到所选的离线音色，请使用影视原声。");
      return "无法切换离线音色";
    }
    engine.setSpeechRate(item.slow ? 0.78f : 1.0f);
    String text = item.text.length() > 400 ? item.text.substring(0, 400) : item.text;
    int result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "everyone-learning");
    if (result != TextToSpeech.SUCCESS) {
      notice.show("系统发音启动失败，请检查手机的语音服务。");
      return "朗读启动失败";
    }
    String prompt = item.slow ? "正在慢速朗读（系统离线合成）" : "正在朗读（系统离线合成）";
    notice.show(prompt + "。合成语音不能代替真人母语者的发音示范。");
    return prompt;
  }

  private Voice offlineVoice(String tag) {
    Set<Voice> voices = engine.getVoices();
    if (voices == null) return null;
    Voice best = null;
    for (Voice v : voices) {
      if (v == null || v.getLocale() == null || v.isNetworkConnectionRequired()) continue;
      Locale locale = v.getLocale();
      boolean matched = "zh-HK".equals(tag)
          ? ("HK".equalsIgnoreCase(locale.getCountry())
              && ("zh".equalsIgnoreCase(locale.getLanguage())
                  || "yue".equalsIgnoreCase(locale.getLanguage())))
          : tag.equalsIgnoreCase(locale.toLanguageTag());
      if (!matched) continue;
      if (best == null || ("yue".equals(locale.getLanguage())
          && !"yue".equals(best.getLocale().getLanguage()))
          || (locale.getLanguage().equals(best.getLocale().getLanguage())
              && v.getQuality() > best.getQuality())) best = v;
    }
    return best;
  }

  public void stop() {
    pending = null;
    if (engine != null && ready) engine.stop();
  }

  public void close() {
    stopped = true;
    stop();
    if (engine != null) engine.shutdown();
    engine = null;
  }
}
