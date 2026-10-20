package org.openenglish.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 安卓原生影视精听：只读取用户主动选取的本地视频和字幕。
 * 独立实现，不嵌入 Undertone 闭源扩展，也不绕过网站 DRM。
 */
public final class JourneyImmersionActivity extends Activity {
  private static final int PICK_VIDEO = 61;
  private static final int PICK_SOURCE = 62;
  private static final int PICK_TRANSLATION = 63;
  private static final int EXPORT_ANKI = 64;
  private static final int SPEECH_COMPARE = 65;
  private static final int INK = 0xff19263b;
  private static final int MUTED = 0xff5f6e83;
  private static final int ACCENT = 0xff5948cf;
  private static final int BG = 0xfff5f6fa;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<JourneySubtitles.Cue> source = new ArrayList<>();
  private final List<JourneySubtitles.Cue> translated = new ArrayList<>();
  private final Set<String> saved = new LinkedHashSet<>();
  private SharedPreferences prefs;
  private VideoView player;
  private TextView status, sourceLabel, translationLabel, tipsLabel, counter;
  private TextView languageLabel, cantoneseLesson, speechFeedback;
  private LinearLayout cantonesePanel;
  private String language = "yue";
  private String speechReference = "";
  private int cantoneseLessonIndex = 0;
  private LinearLayout playlist;
  private Button pauseButton, loopButton;
  private Uri videoUri;
  private int activeIndex = -1;
  private int pausedAt = -1;
  private boolean pauseEach = true;
  private boolean loopOne;
  private boolean foreground;

  private final Runnable progress = new Runnable() {
    @Override public void run() {
      if (!foreground || player == null) return;
      int ms = player.getCurrentPosition();
      int next = JourneySubtitles.at(source, ms);
      if (next != activeIndex) showCue(next);
      if (next >= 0 && player.isPlaying()) {
        JourneySubtitles.Cue cue = source.get(next);
        if (ms >= cue.endMs - 120 && ms < cue.endMs + 550) {
          if (loopOne) {
            player.seekTo(cue.startMs + 35);
          } else if (pauseEach && pausedAt != next) {
            pausedAt = next;
            player.pause();
          }
        }
      }
      handler.postDelayed(this, 120);
    }
  };

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getSharedPreferences("everyone-immersion", MODE_PRIVATE);
    language = prefs.getString("language", "yue");
    saved.addAll(prefs.getStringSet("saved-phrases", Collections.emptySet()));
    pauseEach = prefs.getBoolean("pause", true);
    loopOne = prefs.getBoolean("loop", false);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    draw();
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private TextView label(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(color);
    t.setTextSize(size);
    t.setPadding(0, dp(6), 0, dp(6));
    t.setLineSpacing(dp(3), 1.05f);
    t.setTextIsSelectable(true);
    return t;
  }

  private GradientDrawable shape(int color, int corner) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(corner));
    return d;
  }

  private Button button(String s, LinearLayout parent, Runnable action) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(ACCENT);
    b.setMinHeight(dp(48));
    b.setBackground(shape(0xffeeebff, 13));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1);
    p.setMargins(dp(3), dp(4), dp(3), dp(4));
    parent.addView(b, p);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private LinearLayout row(LinearLayout parent) {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    parent.addView(r, new LinearLayout.LayoutParams(-1, -2));
    return r;
  }

  private void draw() {
    LinearLayout outer = new LinearLayout(this);
    outer.setOrientation(1);
    outer.setBackgroundColor(BG);
    ScrollView scroll = new ScrollView(this);
    outer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(1);
    page.setPadding(dp(18), dp(10), dp(18), dp(30));
    scroll.addView(page);

    LinearLayout header = row(page);
    TextView brand = label("影视精听  /  IMMERSION", 22, INK);
    brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    button("返回", header, this::finish);

    page.addView(label("双语字幕 · 逐句复读 · 口语提示 · 收藏导出", 14, MUTED));
    page.addView(label("选择你有权播放的本地视频及 SRT/VTT 字幕。无需上传个人视频。", 13, MUTED));
    languageLabel = label("", 13, MUTED);
    page.addView(languageLabel);
    LinearLayout languages = row(page);
    button("粤语", languages, () -> selectLanguage("yue"));
    button("日语", languages, () -> selectLanguage("ja"));
    button("英语", languages, () -> selectLanguage("en"));
    updateLanguageLabel();

    player = new VideoView(this);
    player.setBackground(shape(0xff182032, 18));
    LinearLayout.LayoutParams videoBox = new LinearLayout.LayoutParams(-1, dp(218));
    videoBox.setMargins(0, dp(8), 0, dp(8));
    page.addView(player, videoBox);
    MediaController controller = new MediaController(this);
    controller.setAnchorView(player);
    player.setMediaController(controller);
    player.setOnErrorListener((mp, what, extra) -> {
      status.setText("视频无法播放：请尝试系统支持的 MP4/H.264 文件。");
      return true;
    });
    status = label("先选择本地视频，再导入原文字幕。", 13, MUTED);
    page.addView(status);

    LinearLayout files1 = row(page);
    button("选视频", files1, () -> pick(PICK_VIDEO, "video/*"));
    button("原文字幕", files1, () -> pick(PICK_SOURCE, "*/*"));
    LinearLayout files2 = row(page);
    button("中文字幕", files2, () -> pick(PICK_TRANSLATION, "*/*"));
    button("导出 Anki", files2, this::exportBookmarks);

    TextView segment = label("当前台词", 13, ACCENT);
    segment.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    page.addView(segment);
    sourceLabel = label("字幕导入后，在这里跟随播放", 21, INK);
    sourceLabel.setMinHeight(dp(72));
    page.addView(sourceLabel);
    translationLabel = label("中文解释会显示在此处", 16, MUTED);
    page.addView(translationLabel);
    tipsLabel = label("粤语口语词条可点选查看；非完整 AI 讲解。", 13, MUTED);
    page.addView(tipsLabel);
    counter = label("0 句字幕", 12, MUTED);
    page.addView(counter);

    LinearLayout control = row(page);
    button("上一句", control, () -> move(-1));
    button("重播", control, this::replay);
    button("下一句", control, () -> move(1));
    LinearLayout options = row(page);
    pauseButton = button("", options, () -> {
      pauseEach = !pauseEach;
      if (pauseEach) loopOne = false;
      storeOptions();
    });
    loopButton = button("", options, () -> {
      loopOne = !loopOne;
      if (loopOne) pauseEach = false;
      storeOptions();
    });
    storeOptions();

    LinearLayout capture = row(page);
    button("收藏这一句", capture, this::saveCue);
    button("显示口语说明", capture, this::explain);
    LinearLayout speakRow = row(page);
    button("跟读识别 · 对照文本", speakRow, this::recognizeSpeech);
    speechFeedback = label("请先选一条视频台词；或使用下方粤语练习句。\n系统识别结果不能视为发音或六声调评分。", 13, MUTED);
    page.addView(speechFeedback);

    cantonesePanel = new LinearLayout(this);
    cantonesePanel.setOrientation(1);
    cantonesePanel.setPadding(dp(14), dp(14), dp(14), dp(14));
    cantonesePanel.setBackground(shape(Color.WHITE, 16));
    LinearLayout.LayoutParams lessonBox = new LinearLayout.LayoutParams(-1, -2);
    lessonBox.setMargins(0, dp(14), 0, dp(14));
    page.addView(cantonesePanel, lessonBox);
    TextView lessonTitle = label("粤语入门 · 粤拼 Jyutping", 18, INK);
    lessonTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    cantonesePanel.addView(lessonTitle);
    cantoneseLesson = label("", 17, INK);
    cantonesePanel.addView(cantoneseLesson);
    cantonesePanel.addView(label(JourneyCantonese.toneHelp(), 12, MUTED));
    LinearLayout lessonControls = row(cantonesePanel);
    button("上一句", lessonControls, () -> changeCantoneseLesson(-1));
    button("练这句", lessonControls, () -> recognizeSpeech(true));
    button("下一句", lessonControls, () -> changeCantoneseLesson(1));
    updateCantoneseLesson();
    cantonesePanel.setVisibility("yue".equals(language) ? View.VISIBLE : View.GONE);

    TextView transcript = label("逐句列表 · 点击跳转", 18, INK);
    transcript.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    page.addView(transcript);
    playlist = new LinearLayout(this);
    playlist.setOrientation(1);
    page.addView(playlist);
    renderTranscript(0);
    setContentView(outer);
  }

  private void storeOptions() {
    prefs.edit().putBoolean("pause", pauseEach).putBoolean("loop", loopOne).apply();
    if (pauseButton != null) pauseButton.setText((pauseEach ? "✓ " : "") + "句末暂停");
    if (loopButton != null) loopButton.setText((loopOne ? "✓ " : "") + "单句循环");
  }

  private void pick(int request, String mime) {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.setType(mime);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    try {
      startActivityForResult(intent, request);
    } catch (Exception e) {
      toast("找不到文件选择器");
    }
  }

  private String readText(Uri uri) throws Exception {
    try (InputStream in = getContentResolver().openInputStream(uri)) {
      if (in == null) throw new IllegalArgumentException("无法打开字幕文件");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] bytes = new byte[8192];
      int n;
      while ((n = in.read(bytes)) != -1) {
        if (out.size() + n > 5 * 1024 * 1024) throw new IllegalArgumentException("字幕超过 5MB 限制");
        out.write(bytes, 0, n);
      }
      byte[] data = out.toByteArray();
      if (data.length >= 2 && (data[0] & 255) == 255 && (data[1] & 255) == 254)
        return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
      if (data.length >= 2 && (data[0] & 255) == 254 && (data[1] & 255) == 255)
        return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
      return new String(data, StandardCharsets.UTF_8);
    }
  }

  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == SPEECH_COMPARE) {
      if (result == RESULT_OK && data != null) showSpeechComparison(data);
      else if (speechFeedback != null) speechFeedback.setText("未取得识别结果；原视频与字幕不受影响。");
      return;
    }
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == EXPORT_ANKI) { writeBookmarks(uri); return; }
    try {
      final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
      getContentResolver().takePersistableUriPermission(uri, flags);
    } catch (SecurityException ignored) {
      // SAF 文件本次仍可读取；某些文件提供方不支持持久权限。
    }
    if (request == PICK_VIDEO) {
      videoUri = uri;
      activeIndex = -1;
      pausedAt = -1;
      player.setVideoURI(uri);
      player.setOnPreparedListener(mp -> status.setText("视频准备好了，点击画面开始播放。"));
      showCue(-1);
      if (foreground) { handler.removeCallbacks(progress); handler.post(progress); }
      return;
    }
    try {
      List<JourneySubtitles.Cue> parsed = JourneySubtitles.parse(readText(uri));
      if (parsed.isEmpty()) throw new IllegalArgumentException("未找到有效字幕时间轴，请使用 SRT/VTT 文件");
      if (request == PICK_SOURCE) {
        source.clear();
        source.addAll(parsed);
        activeIndex = -1;
        pausedAt = -1;
        status.setText("已导入原文字幕 " + source.size() + " 句");
        showCue(-1);
        renderTranscript(0);
      } else if (request == PICK_TRANSLATION) {
        translated.clear();
        translated.addAll(parsed);
        status.setText("已导入译文字幕 " + translated.size() + " 句");
        showCue(-1);
        renderTranscript(Math.max(0, activeIndex));
      }
      counter.setText("原文 " + source.size() + " 句  /  译文 " + translated.size() + " 句");
    } catch (Exception e) {
      toast("字幕导入失败：" + e.getMessage());
    }
  }

  private void showCue(int index) {
    if (activeIndex == index && index >= 0) return;
    activeIndex = index;
    if (index < 0 || index >= source.size()) {
      sourceLabel.setText("字幕导入后，在这里跟随播放");
      translationLabel.setText(translated.isEmpty() ? "可单独导入中文字幕" : "等待对应译文");
      tipsLabel.setText("支持已导入字幕的逐句定位与收藏");
      return;
    }
    JourneySubtitles.Cue cue = source.get(index);
    sourceLabel.setText(cue.text);
    int midpoint = cue.startMs + Math.min(300, (cue.endMs - cue.startMs) / 2);
    String zh = JourneySubtitles.translatedAt(translated, midpoint);
    translationLabel.setText(zh.isEmpty() ? "（当前时间无译文）" : zh);
    tipsLabel.setText(quickTip(cue.text));
    counter.setText((index + 1) + " / " + source.size() + " 句");
    renderTranscript(index);
  }

  private void renderTranscript(int focus) {
    if (playlist == null) return;
    playlist.removeAllViews();
    if (source.isEmpty()) {
      playlist.addView(label("导入原文字幕后，可在这里点击任意句子跳转。", 14, MUTED));
      return;
    }
    int start = Math.max(0, focus - 3);
    int end = Math.min(source.size(), start + 9);
    for (int i = start; i < end; i++) {
      final int n = i;
      JourneySubtitles.Cue cue = source.get(i);
      int midpoint = cue.startMs + Math.min(300, (cue.endMs - cue.startMs) / 2);
      String zh = JourneySubtitles.translatedAt(translated, midpoint);
      TextView line = label((i == focus ? "▶ " : "") + (i + 1) + ". " + cue.text
          + (zh.isEmpty() ? "" : "\n" + zh), 14, i == focus ? ACCENT : INK);
      line.setPadding(dp(12), dp(11), dp(12), dp(11));
      line.setBackground(shape(i == focus ? 0xffe8e4ff : Color.WHITE, 12));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
      p.bottomMargin = dp(5);
      playlist.addView(line, p);
      line.setOnClickListener(v -> jump(n));
    }
  }

  private void jump(int n) {
    if (n < 0 || n >= source.size()) return;
    if (videoUri == null) {
      showCue(n);
      toast("请先选择视频才能播放这句");
      return;
    }
    pausedAt = -1;
    player.seekTo(source.get(n).startMs + 35);
    player.start();
    showCue(n);
  }

  private void move(int delta) {
    if (source.isEmpty()) { toast("请先导入原文字幕"); return; }
    int next = activeIndex < 0 ? 0 : Math.max(0, Math.min(source.size() - 1, activeIndex + delta));
    jump(next);
  }

  private void replay() {
    if (activeIndex < 0) { move(0); return; }
    jump(activeIndex);
  }

  private void saveCue() {
    if (activeIndex < 0) { toast("播放或选择一句字幕后再收藏"); return; }
    JourneySubtitles.Cue cue = source.get(activeIndex);
    int midpoint = cue.startMs + Math.min(300, (cue.endMs - cue.startMs) / 2);
    String zh = JourneySubtitles.translatedAt(translated, midpoint);
    saved.add(cue.text.replace('\t', ' ').replace('\n', ' ') + "\u001f"
        + zh.replace('\t', ' ').replace('\n', ' '));
    prefs.edit().putStringSet("saved-phrases", new LinkedHashSet<>(saved)).apply();
    toast("已收藏，可导出 Anki TSV (" + saved.size() + " 句)");
  }

  private void exportBookmarks() {
    if (saved.isEmpty()) { toast("暂无已收藏句子"); return; }
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/tab-separated-values");
    intent.putExtra(Intent.EXTRA_TITLE, "Everyone-immersion-anki.tsv");
    try { startActivityForResult(intent, EXPORT_ANKI); }
    catch (Exception e) { toast("无法打开导出文件选择器"); }
  }

  private void writeBookmarks(Uri uri) {
    try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
      if (out == null) throw new IllegalArgumentException("文件不可写");
      for (String record : saved) {
        String[] pair = record.split("\u001f", 2);
        String line = pair[0] + "\t" + (pair.length > 1 ? pair[1] : "") + "\n";
        out.write(line.getBytes(StandardCharsets.UTF_8));
      }
      toast("已导出 " + saved.size() + " 句，可从 Anki 导入 TSV");
    } catch (Exception e) { toast("导出失败：" + e.getMessage()); }
  }

  private void updateLanguageLabel() {
    if (languageLabel == null) return;
    String name = "yue".equals(language) ? "粤语 · 粤拼 / 港式口语"
        : "ja".equals(language) ? "日语 · 口语缩略" : "英语 · 字幕精听";
    languageLabel.setText("当前学习语言：" + name + "（原文字幕需自行导入）");
  }

  private void selectLanguage(String target) {
    language = target;
    prefs.edit().putString("language", language).apply();
    updateLanguageLabel();
    if (cantonesePanel != null) {
      cantonesePanel.setVisibility("yue".equals(language) ? View.VISIBLE : View.GONE);
    }
    int at = activeIndex;
    activeIndex = -1;
    showCue(at);
    toast("已切换至" + ("yue".equals(target) ? "粤语" : "ja".equals(target) ? "日语" : "英语"));
  }

  private void updateCantoneseLesson() {
    if (cantoneseLesson == null) return;
    JourneyCantonese.Phrase p = JourneyCantonese.lesson(cantoneseLessonIndex);
    cantoneseLesson.setText((cantoneseLessonIndex + 1) + " / " + JourneyCantonese.lessonCount()
        + "\n" + p.hanzi + "\n" + p.jyutping + "\n" + p.mandarin
        + "\n\n学习提示：" + p.hint);
  }

  private void changeCantoneseLesson(int offset) {
    int count = JourneyCantonese.lessonCount();
    cantoneseLessonIndex = Math.floorMod(cantoneseLessonIndex + offset, count);
    updateCantoneseLesson();
  }

  private void recognizeSpeech() {
    recognizeSpeech(false);
  }

  private void recognizeSpeech(boolean builtInLesson) {
    if (builtInLesson && !"yue".equals(language)) return;
    speechReference = builtInLesson ? JourneyCantonese.lesson(cantoneseLessonIndex).hanzi
        : activeIndex >= 0 && activeIndex < source.size()
          ? source.get(activeIndex).text
          : "yue".equals(language) ? JourneyCantonese.lesson(cantoneseLessonIndex).hanzi : "";
    if (speechReference.isEmpty()) {
      toast("请先选择视频台词");
      return;
    }
    if (player != null && player.isPlaying()) player.pause();
    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
        "yue".equals(language) ? "zh-HK" : "ja".equals(language) ? "ja-JP" : "en-US");
    i.putExtra(RecognizerIntent.EXTRA_PROMPT, "请跟读：" + speechReference);
    try {
      startActivityForResult(i, SPEECH_COMPARE);
      if (speechFeedback != null) speechFeedback.setText("正在调用系统语音识别…");
    } catch (ActivityNotFoundException e) {
      if (speechFeedback != null) speechFeedback.setText(
          "手机未安装支持当前语言的语音识别服务。仍可通过视频原声复读与字幕学习。");
    }
  }

  private void showSpeechComparison(Intent data) {
    ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
    if (results == null || results.isEmpty()) {
      speechFeedback.setText("未能识别这句；请检查系统语音服务。");
      return;
    }
    String recognized = results.get(0);
    String info = "原句：" + speechReference + "\n识别：" + recognized + "\n";
    info += "yue".equals(language)
        ? JourneyCantonese.transcriptFeedback(speechReference, recognized)
        : "仅供转写文本对照。系统识别结果不能当作发音准确度评分。";
    speechFeedback.setText(info);
  }

  private String quickTip(String line) {
    if ("yue".equals(language)) return JourneyCantonese.quickTip(line);
    if ("en".equals(language)) return "英语精听：尝试跟随视频语速、停顿和重音；无自动音素评分。";
    if (line.contains("ちゃった")) return "ちゃった：てしまった 的口语缩略，可能表示完成或遗憾。";
    if (line.contains("じゃん")) return "じゃん：常见口语语尾，可表示确认、强调或提醒。";
    if (line.contains("っす")) return "っす：です 的口语缩略，常见于随意对话。";
    if (line.contains("ちょっと")) return "ちょっと：除“稍微”外，还可能委婉拒绝，要结合上下文。";
    if (line.contains("考えておきます")) return "考えておきます：字面是“考虑一下”，有时是委婉推迟或拒绝。";
    return "逐句精听：观察省略、语气和说话人关系；暂不提供自动逐词解析。";
  }

  private void explain() {
    if (activeIndex < 0) { toast("请先选择一条台词"); return; }
    String line = source.get(activeIndex).text;
    if ("yue".equals(language)) {
      List<String> entries = JourneyCantonese.lookup(line);
      if (entries.isEmpty()) {
        new AlertDialog.Builder(this).setTitle("粤语口语解析")
            .setMessage("当前字幕未命中内置词库，不能据此推断词义。\n\n"
                + JourneyCantonese.toneHelp()).setPositiveButton("知道了", null).show();
        return;
      }
      String[] names = entries.toArray(new String[0]);
      new AlertDialog.Builder(this).setTitle("点击查看粤语词条")
          .setItems(names, (d, which) -> new AlertDialog.Builder(this)
              .setTitle("粤语词条").setMessage(names[which])
              .setPositiveButton("知道了", null).show())
          .setNegativeButton("关闭", null).show();
      return;
    }
    new AlertDialog.Builder(this).setTitle("ja".equals(language) ? "日语口语提示" : "英语精听提示")
        .setMessage(line + "\n\n" + quickTip(line)
            + "\n\n内置少量离线规则，不是完整自动语法解析。")
        .setPositiveButton("知道了", null).show();
  }

  private void toast(String msg) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
  }

  @Override protected void onStart() {
    super.onStart();
    foreground = true;
    handler.removeCallbacks(progress);
    handler.post(progress);
  }

  @Override protected void onStop() {
    foreground = false;
    handler.removeCallbacks(progress);
    if (player != null) player.pause();
    super.onStop();
  }

  @Override protected void onDestroy() {
    handler.removeCallbacks(progress);
    if (player != null) player.stopPlayback();
    super.onDestroy();
  }
}
