package org.openenglish.reader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
  private static final int RECORD_PERMISSION = 66;
  private static final int PICK_TONE_REFERENCE = 67;
  private static final int INK = 0xff19263b;
  private static final int MUTED = 0xff5f6e83;
  private static final int ACCENT = 0xff5948cf;
  private static final int BG = 0xfff5f6fa;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final JourneyPracticeAudio practice = new JourneyPracticeAudio();
  private JourneySpeechGuide speechGuide;
  private final List<JourneySubtitles.Cue> source = new ArrayList<>();
  private final List<JourneySubtitles.Cue> translated = new ArrayList<>();
  private final Set<String> saved = new LinkedHashSet<>();
  private SharedPreferences prefs;
  private VideoView player;
  private TextView status, sourceLabel, translationLabel, tipsLabel, counter;
  private TextView languageLabel, referenceGuideLabel, cantoneseLesson, speechFeedback, timingLabel, toneLabel, toneFeedback;
  private TextView phoneticDetail, contrastDetail;
  private LinearLayout cantonesePanel;
  private JourneyTonePlotView tonePlot;
  private String language = "yue";
  private String speechReference = "";
  private static final String[] TONE_WORDS = {
      "詩 si1 · 高平", "史 si2 · 高升", "試 si3 · 中平",
      "時 si4 · 低降", "市 si5 · 低升", "事 si6 · 低平"
  };
  private int toneTarget = 1;
  private boolean toneBusy;
  private File toneReference;
  private boolean yueAsrBusy;
  private String yueAsrEndpoint = "";
  private String yueAsrToken = "";
  private int cantoneseLessonIndex = 0;
  private int contrastIndex = 0;
  private int subtitleOffsetMs = 0;
  private long recordingStartedAt = 0L;
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
      int next = JourneySubtitles.at(source, ms - subtitleOffsetMs);
      if (next != activeIndex) showCue(next);
      if (next >= 0 && player.isPlaying()) {
        JourneySubtitles.Cue cue = source.get(next);
        int actualEnd = cue.endMs + subtitleOffsetMs;
        if (ms >= actualEnd - 120 && ms < actualEnd + 550) {
          if (loopOne) {
            player.seekTo(Math.max(0, cue.startMs + subtitleOffsetMs + 35));
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
    subtitleOffsetMs = Math.max(-10000, Math.min(10000, prefs.getInt("subtitleOffsetMs", 0)));
    yueAsrEndpoint = prefs.getString("yueAsrEndpoint", "");
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
    referenceGuideLabel = label("", 13, MUTED);
    page.addView(referenceGuideLabel);
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
    timingLabel = label("", 12, MUTED);
    page.addView(timingLabel);
    LinearLayout syncRow = row(page);
    button("字幕提前 0.5s", syncRow, () -> adjustSync(-500));
    button("同步重置", syncRow, () -> adjustSync(-subtitleOffsetMs));
    button("字幕延后 0.5s", syncRow, () -> adjustSync(500));
    showSync();

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
    LinearLayout recordings = row(page);
    button("● 录我的声音 / 停止", recordings, this::togglePractice);
    button("▶ 回放我的录音", recordings, this::playPractice);
    LinearLayout synthesized = row(page);
    button("▷ 系统示范发音", synthesized, () -> speakExample(false, false));
    button("▷ 慢速示范", synthesized, () -> speakExample(false, true));
    speechFeedback = label("请先选一条视频台词；或使用下方粤语练习句。\n系统识别结果不能视为发音或六声调评分。", 13, MUTED);
    page.addView(speechFeedback);
    page.addView(label("录音只保存在临时缓存中，离开这个页面就会删除；最长 30 秒。"
        + " 可以与视频原声反复比较，但不自动评判粤语声调。", 12, MUTED));

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
    LinearLayout parseRow = row(cantonesePanel);
    button("拆解这句粤拼", parseRow, this::pickSyllable);
    phoneticDetail = label("选择「拆解这句粤拼」，逐音节查看声母、韵腹、韵尾及声调。", 14, MUTED);
    cantonesePanel.addView(phoneticDetail);
    cantonesePanel.addView(label(JourneyCantonese.toneHelp(), 12, MUTED));
    LinearLayout lessonControls = row(cantonesePanel);
    button("上一句", lessonControls, () -> changeCantoneseLesson(-1));
    button("练这句", lessonControls, () -> recognizeSpeech(true));
    LinearLayout lessonAudio = row(cantonesePanel);
    button("听粤语示范", lessonAudio, () -> speakExample(true, false));
    button("慢速示范", lessonAudio, () -> speakExample(true, true));
    button("下一句", lessonControls, () -> changeCantoneseLesson(1));
    TextView contrastTitle = label("易混音对照 · 听辨与模仿", 18, INK);
    contrastTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    cantonesePanel.addView(contrastTitle);
    contrastDetail = label("", 15, INK);
    cantonesePanel.addView(contrastDetail);
    LinearLayout contrastControls = row(cantonesePanel);
    button("上一组", contrastControls, () -> changeContrast(-1));
    button("听①", contrastControls, () -> speakContrast(true));
    button("听②", contrastControls, () -> speakContrast(false));
    button("下一组", contrastControls, () -> changeContrast(1));
    cantonesePanel.addView(label("注意：对照说明是静态教学提示。手机离线合成发音因音色而异，"
        + "请优先比较影视真人原声；目前不能自动判断是否发准声母或韵母。", 12, MUTED));
    TextView modelTitle = label("开源粤语识别 · 可选 SenseVoiceSmall", 18, INK);
    modelTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    cantonesePanel.addView(modelTitle);
    cantonesePanel.addView(label("不预装大型模型、不默认上传录音。只有主动配置你自己的 HTTPS 服务，"
        + "并且每次明确确认后，才发送刚录的 M4A。转写文字不能当作音素纠音评分。", 12, MUTED));
    LinearLayout yueAsrRow = row(cantonesePanel);
    button("配置我的识别服务", yueAsrRow, this::configureYueAsr);
    button("用 SenseVoice 识别", yueAsrRow, this::confirmYueAsr);
    cantonesePanel.addView(label("需自行部署开源 SenseVoiceSmall（见仓库 server/）；"
        + "没有服务器时可继续使用本地音高曲线、系统语音识别和影视精听。", 12, MUTED));
    cantonesePanel.addView(label("六声调单音节 · 音高走势实验", 17, INK));
    toneLabel = label("", 15, INK);
    cantonesePanel.addView(toneLabel);
    cantonesePanel.addView(label("练习方法：选一个 si 音节，单独读 0.5–1 秒（最长 3 秒）。"
        + "图表显示相对音高轨迹；无法判定 1/3/6 平调的音区和声母韵母。", 12, MUTED));
    LinearLayout toneControls = row(cantonesePanel);
    button("选择目标调", toneControls, this::pickTone);
    button("录 / 停单音节", toneControls, this::togglePractice);
    LinearLayout toneAnalyzeRow = row(cantonesePanel);
    button("分析刚才的音高走势", toneAnalyzeRow, this::analyzeTone);
    cantonesePanel.addView(label("真人参考音 vs 你的跟读 · 音高曲线对照", 16, INK));
    LinearLayout referenceRow = row(cantonesePanel);
    button("导入参考 M4A", referenceRow, () -> pick(PICK_TONE_REFERENCE, "audio/*"));
    button("将当前录音存为参考", referenceRow, this::saveRecordedReference);
    LinearLayout comparisonRow = row(cantonesePanel);
    button("对比两段音高", comparisonRow, this::compareToneReference);
    cantonesePanel.addView(label("仅接受本地 AAC/M4A 单音节，参考与跟读各 0.25–3 秒。"
        + "参考可以是真人示范音或自己之前的练习音。音频只暂存本机并在退出页面后删除。"
        + "这里按各自中位音高归一化，因此不能判断高平/中平/低平的绝对音区。", 12, MUTED));
    tonePlot = new JourneyTonePlotView(this);
    LinearLayout.LayoutParams chartBox = new LinearLayout.LayoutParams(-1, dp(172));
    chartBox.setMargins(0, dp(12), 0, dp(12));
    cantonesePanel.addView(tonePlot, chartBox);
    LinearLayout toneListenRow = row(cantonesePanel);
    button("听所选单音节", toneListenRow, this::speakToneExample);
    toneFeedback = label("尚未录制。只能分析单音节的音高走势，不能作为完整粤语发音评分。", 13, MUTED);
    cantonesePanel.addView(toneFeedback);
    toneTarget = Math.max(1, Math.min(6, prefs.getInt("toneTarget", 1)));
    contrastIndex = Math.max(0, Math.min(JourneyJyutping.contrastCount() - 1,
        prefs.getInt("contrastIndex", 0)));
    updateContrast();
    updateToneTarget();
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
    if (request == PICK_TONE_REFERENCE) { importToneReference(uri); return; }
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
    player.seekTo(Math.max(0, source.get(n).startMs + subtitleOffsetMs + 35));
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

  /** 正值表示字幕整体后移；视频和中外文字幕统一使用相同偏移量。 */
  private void adjustSync(int delta) {
    subtitleOffsetMs = Math.max(-10000, Math.min(10000, subtitleOffsetMs + delta));
    prefs.edit().putInt("subtitleOffsetMs", subtitleOffsetMs).apply();
    showSync();
    activeIndex = -1;
    if (player != null && videoUri != null) {
      showCue(JourneySubtitles.at(source,
          player.getCurrentPosition() - subtitleOffsetMs));
    }
  }

  private void showSync() {
    if (timingLabel == null) return;
    String label = subtitleOffsetMs > 0 ? "延后"
        : subtitleOffsetMs < 0 ? "提前" : "已同步";
    timingLabel.setText("字幕偏移：" + label + " "
        + String.format(java.util.Locale.US, "%.1f 秒", Math.abs(subtitleOffsetMs / 1000.0)));
  }

  private void speakToneExample() {
    String word = TONE_WORDS[toneTarget - 1].substring(0, 1);
    startGuide(word, "yue", false);
  }

  private void speakExample(boolean lesson, boolean slow) {
    String text = lesson ? JourneyCantonese.lesson(cantoneseLessonIndex).hanzi
        : activeIndex >= 0 && activeIndex < source.size()
          ? source.get(activeIndex).text : "";
    if (text.trim().isEmpty()) {
      if ("yue".equals(language)) text = JourneyCantonese.lesson(cantoneseLessonIndex).hanzi;
      else { toast("请先选择当前视频的一句字幕"); return; }
    }
    startGuide(text, lesson ? "yue" : language, slow);
  }

  private void startGuide(String words, String lang, boolean slow) {
    if (player != null && player.isPlaying()) player.pause();
    if (practice.isRecording()) practice.stop();
    practice.stopPlayback();
    if (speechGuide == null) speechGuide = new JourneySpeechGuide(this, message -> {
      if (speechFeedback != null) speechFeedback.setText(message);
    });
    String info = speechGuide.speak(words, lang, slow);
    if (speechFeedback != null) speechFeedback.setText(info);
  }

  private void configureYueAsr() {
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(dp(20), dp(5), dp(20), 0);
    EditText endpoint = new EditText(this);
    endpoint.setSingleLine(true);
    endpoint.setText(yueAsrEndpoint);
    endpoint.setHint("https://我的域名/v1/yue/transcribe");
    endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    form.addView(endpoint, new LinearLayout.LayoutParams(-1, dp(56)));
    EditText token = new EditText(this);
    token.setSingleLine(true);
    token.setHint("服务端访问令牌（仅本次打开 App 有效）");
    token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    form.addView(token, new LinearLayout.LayoutParams(-1, dp(56)));
    new AlertDialog.Builder(this).setTitle("自建 SenseVoice 粤语识别")
        .setMessage("只接受 HTTPS。地址会保存在本机，但令牌不持久存储；"
            + "每次上传录音前仍需确认，服务端由你自行控制。")
        .setView(form).setPositiveButton("保存设置", (dialog, which) -> {
          String next = endpoint.getText().toString().trim();
          if (!JourneyCantoneseAsr.validEndpoint(next)) {
            toast("地址无效：必须是 HTTPS，路径为 /v1/yue/transcribe");
            return;
          }
          String key = token.getText().toString().trim();
          if (key.length() < 16) { toast("令牌至少需要 16 字符，未保存"); return; }
          yueAsrEndpoint = next;
          yueAsrToken = key;
          prefs.edit().putString("yueAsrEndpoint", next).apply();
          toast("服务地址已保存；令牌仅在本次运行有效");
        }).setNegativeButton("取消", null).show();
  }

  private void confirmYueAsr() {
    if (yueAsrBusy) { toast("粤语转写处理中"); return; }
    if (!JourneyCantoneseAsr.validEndpoint(yueAsrEndpoint)
        || yueAsrToken.length() < 16) {
      configureYueAsr();
      return;
    }
    if (practice.isRecording()) { toast("请先停止录音"); return; }
    java.io.File audio = practice.recordingFile();
    if (audio == null) { toast("请先用“录我的声音”录制一句粤语"); return; }
    if (audio.length() > JourneyCantoneseAsr.MAX_AUDIO_BYTES) {
      toast("录音超过 2MB，不能发送");
      return;
    }
    new AlertDialog.Builder(this).setTitle("确认上传本次录音？")
        .setMessage("将把当前这段 M4A 声音经 HTTPS 上传到你配置的服务器："
            + "\n" + yueAsrEndpoint + "\n"
            + "仅用于转写粤语文本，不分析声母韵母错误，也不会传输其他视频或历史录音。")
        .setPositiveButton("同意并识别", (d, n) -> runYueAsr(audio))
        .setNegativeButton("取消", null).show();
  }

  private void runYueAsr(java.io.File audio) {
    if (yueAsrBusy) return;
    yueAsrBusy = true;
    final String endpoint = yueAsrEndpoint;
    final String token = yueAsrToken;
    final String reference = activeIndex >= 0 && activeIndex < source.size()
        ? source.get(activeIndex).text : JourneyCantonese.lesson(cantoneseLessonIndex).hanzi;
    speechFeedback.setText("正在通过你的 HTTPS 服务识别粤语……");
    new Thread(() -> {
      String result;
      try {
        JourneyCantoneseAsr.Transcript response =
            JourneyCantoneseAsr.transcribe(endpoint, token, audio);
        result = "SenseVoice (" + response.language + ") 识别：" + response.text
            + "\n参考台词：" + reference + "\n"
            + JourneyCantonese.transcriptFeedback(reference, response.text)
            + "\nASR 文字差异不代表发音的声母、韵母或六声调错误。";
      } catch (Exception e) {
        result = "粤语识别失败：" + (e.getMessage() == null ? "网络或模型异常" : e.getMessage())
            + "\n本地录音和音高分析仍可使用。";
      }
      final String message = result;
      runOnUiThread(() -> {
        yueAsrBusy = false;
        if (!isFinishing() && !isDestroyed() && speechFeedback != null)
          speechFeedback.setText(message);
      });
    }, "everyone-self-hosted-yue-asr").start();
  }

  private void importToneReference(Uri uri) {
    if (toneBusy || yueAsrBusy || practice.isRecording()) {
      toast("请先完成当前录音或音高分析");
      return;
    }
    try (InputStream stream = getContentResolver().openInputStream(uri)) {
      if (stream == null) throw new IOException("无法读取所选录音");
      setToneReference(stream);
      toneFeedback.setText("参考音已导入。请录一次同样的单音节，然后点击「对比两段音高」。");
    } catch (Exception e) {
      toneFeedback.setText("导入参考音失败：" + e.getMessage());
    }
  }

  private void saveRecordedReference() {
    if (toneBusy || yueAsrBusy || practice.isRecording()) {
      toast("请先结束录音或分析");
      return;
    }
    File current = practice.recordingFile();
    if (current == null) {
      toneFeedback.setText("还没有可作为参考的录音；请先录一个音节。");
      return;
    }
    try (InputStream stream = new FileInputStream(current)) {
      setToneReference(stream);
      toneFeedback.setText("当前录音已复制为参考。请重新录同一音节，再点「对比两段音高」。");
    } catch (Exception e) {
      toneFeedback.setText("保存参考音失败：" + e.getMessage());
    }
  }

  /** 只有成功完整复制小型录音后才替换旧参考；页面关闭即删除。 */
  private void setToneReference(InputStream stream) throws IOException {
    File candidate = File.createTempFile("everyone-yue-reference-", ".m4a", getCacheDir());
    boolean saved = false;
    try (FileOutputStream out = new FileOutputStream(candidate)) {
      int total = 0;
      byte[] bytes = new byte[8192];
      int count;
      while ((count = stream.read(bytes)) >= 0) {
        total += count;
        if (total > JourneyCantoneseAsr.MAX_AUDIO_BYTES)
          throw new IOException("参考音超过 2MB，无法用于单音节对照");
        out.write(bytes, 0, count);
      }
      if (total < 256) throw new IOException("参考录音内容太短或无法读取");
      saved = true;
    } finally {
      if (!saved) candidate.delete();
    }
    if (toneReference != null) toneReference.delete();
    toneReference = candidate;
  }

  private void compareToneReference() {
    if (toneBusy || yueAsrBusy || practice.isRecording()) {
      toast("请先结束录音与分析任务");
      return;
    }
    File learner = practice.recordingFile();
    if (toneReference == null || !toneReference.isFile() || learner == null) {
      toneFeedback.setText("先导入/保存参考音，再重新录一次同音节跟读。");
      return;
    }
    toneBusy = true;
    toneFeedback.setText("正在本机解码两段录音并对齐音高轮廓，不会上传音频……");
    final File original = toneReference;
    new Thread(() -> {
      String feedback;
      JourneyToneCompare.Result result = null;
      try {
        JourneyToneAudio.Samples reference = JourneyToneAudio.decode(original);
        JourneyToneAudio.Samples attempted = JourneyToneAudio.decode(learner);
        result = JourneyToneCompare.compare(reference.monoPcm, reference.sampleRate,
            attempted.monoPcm, attempted.sampleRate);
        feedback = result.feedback;
      } catch (Exception e) {
        feedback = "对照失败：" + e.getMessage();
      }
      final String message = feedback;
      final JourneyToneCompare.Result show = result;
      runOnUiThread(() -> {
        toneBusy = false;
        if (!isFinishing() && !isDestroyed()) {
          if (tonePlot != null) tonePlot.showComparison(show);
          if (toneFeedback != null) toneFeedback.setText(message);
        }
      });
    }, "everyone-yue-tone-reference").start();
  }

  private void updateToneTarget() {
    if (toneLabel != null) toneLabel.setText("目标：" + TONE_WORDS[toneTarget - 1]
        + " · 预期走势：" + JourneyToneContour.expectedTrend(toneTarget));
  }

  private void pickTone() {
    new AlertDialog.Builder(this).setTitle("选择六声调单音节")
        .setSingleChoiceItems(TONE_WORDS, toneTarget - 1, (dialog, n) -> {
          toneTarget = n + 1;
          prefs.edit().putInt("toneTarget", toneTarget).apply();
          updateToneTarget();
          if (toneFeedback != null)
            toneFeedback.setText("目标已更换。请只录读“" + TONE_WORDS[n] + "”中的那个单音节。");
          dialog.dismiss();
        }).setNegativeButton("取消", null).show();
  }

  private void analyzeTone() {
    if (toneBusy) { toast("正在计算音高走势"); return; }
    if (practice.isRecording()) {
      if (toneFeedback != null) toneFeedback.setText("请先停止录音，再分析。");
      return;
    }
    java.io.File audio = practice.recordingFile();
    if (audio == null) {
      if (toneFeedback != null) toneFeedback.setText("请先在本页录制一段清晰的单音节声音。");
      return;
    }
    toneBusy = true;
    final int tone = toneTarget;
    toneFeedback.setText("正在本机解析录音并计算基频轮廓，不会上传音频……");
    new Thread(() -> {
      String result;
      try {
        JourneyToneAudio.Samples decoded = JourneyToneAudio.decode(audio);
        JourneyToneContour.Result contour =
            JourneyToneContour.analyze(decoded.monoPcm, decoded.sampleRate);
        result = contour.feedback(tone);
        final JourneyToneContour.Result plotted = contour;
        runOnUiThread(() -> {
          if (!isFinishing() && !isDestroyed() && tonePlot != null) tonePlot.show(plotted);
        });
      } catch (Exception e) {
        result = "音高分析失败：" + (e.getMessage() == null ? "请录制更清晰的单音节再试" : e.getMessage());
      }
      final String message = result;
      runOnUiThread(() -> {
        toneBusy = false;
        if (!isFinishing() && !isDestroyed() && toneFeedback != null) {
          toneFeedback.setText("目标：" + TONE_WORDS[tone - 1] + "\n" + message);
        }
      });
    }, "everyone-tone-contour").start();
  }

  private void togglePractice() {
    if (yueAsrBusy) { toast("粤语转写进行中，请勿覆盖录音"); return; }
    if (toneBusy) { toast("请等待本机音高分析完成"); return; }
    if (practice.isRecording()) {
      boolean okay = practice.stop();
      speechFeedback.setText(okay ? "录音已完成。点击“回放我的录音”听差异。"
          : "录音太短或麦克风录音失败，请重试。");
      return;
    }
    if (android.os.Build.VERSION.SDK_INT >= 23
        && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_PERMISSION);
      return;
    }
    startPractice();
  }

  private void startPractice() {
    if (player != null && player.isPlaying()) player.pause();
    if (speechGuide != null) speechGuide.stop();
    try {
      practice.start(getCacheDir());
      if (toneFeedback != null) toneFeedback.setText("开始录制新声音：分析将以新录音为准。");
      if (tonePlot != null) tonePlot.show(null);
      recordingStartedAt = android.os.SystemClock.elapsedRealtime();
      speechFeedback.setText("● 正在录你的跟读……再次点击“录我的声音 / 停止”结束。");
      handler.postDelayed(() -> {
        if (!practice.isRecording()) return;
        if (android.os.SystemClock.elapsedRealtime() - recordingStartedAt < 30000L) return;
        boolean ok = practice.stop();
        if (speechFeedback != null) speechFeedback.setText(ok
            ? "已录满 30 秒并自动停止。现在可以回放。"
            : "录音未完成，请重试。");
      }, 30050);
    } catch (Exception e) {
      speechFeedback.setText("录音启动失败：" + e.getMessage());
    }
  }

  private void playPractice() {
    try {
      if (!practice.play()) {
        speechFeedback.setText("没有可回放的录音。请先录制一遍。");
      }
    } catch (Exception e) {
      speechFeedback.setText("回放失败：" + e.getMessage());
    }
  }

  @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request != RECORD_PERMISSION) return;
    if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startPractice();
    else if (speechFeedback != null) speechFeedback.setText(
        "没有录音权限，可继续看字幕、听原声及使用外部系统语音识别。");
  }

  private void updateLanguageLabel() {
    if (languageLabel == null) return;
    String name = "yue".equals(language) ? "粤语 · 粤拼 / 港式口语"
        : "ja".equals(language) ? "日语 · 口语缩略" : "英语 · 字幕精听";
    languageLabel.setText("当前学习语言：" + name + "（原文字幕需自行导入）");
    if (referenceGuideLabel != null) {
      String guide = "yue".equals(language)
          ? "粤语学习参考：DualSub Replay（安卓 YouTube 双语字幕、逐句回放）"
              + " + Cantonese Learner V2（港式生活对话、粤拼注音、听后跟读）。"
              + "本页现有本地视频/字幕学习与录音分析，并未直接集成这两个外部产品。"
          : "ja".equals(language)
          ? "日语学习参考：Undertone 弦外（日语影视台词的口语缩约、语气、话外含义）。"
              + "它需要电脑 Chrome；此安卓页只实现了少量独立日语提示，不是 Undertone 插件。"
          : "英语使用 Everyone 原有课程及本地影视精听，不把日语 Undertone"
              + " 或粤语 Cantonese Learner V2 标作英语功能。";
      referenceGuideLabel.setText(guide);
    }
  }

  private void selectLanguage(String target) {
    language = target;
    prefs.edit().putString("language", language).apply();
    updateLanguageLabel();
    if (speechGuide != null) speechGuide.stop();
    if (cantonesePanel != null) {
      cantonesePanel.setVisibility("yue".equals(language) ? View.VISIBLE : View.GONE);
    }
    int at = activeIndex;
    activeIndex = -1;
    showCue(at);
    toast("已切换至" + ("yue".equals(target) ? "粤语" : "ja".equals(target) ? "日语" : "英语"));
  }

  private void updateContrast() {
    if (contrastDetail == null) return;
    JourneyJyutping.Contrast c = JourneyJyutping.contrast(contrastIndex);
    contrastDetail.setText((contrastIndex + 1) + " / " + JourneyJyutping.contrastCount()
        + "\n" + c.display());
  }

  private void changeContrast(int direction) {
    int size = JourneyJyutping.contrastCount();
    contrastIndex = ((contrastIndex + direction) % size + size) % size;
    prefs.edit().putInt("contrastIndex", contrastIndex).apply();
    updateContrast();
  }

  private void speakContrast(boolean first) {
    JourneyJyutping.Contrast c = JourneyJyutping.contrast(contrastIndex);
    startGuide(first ? c.firstCharacter : c.secondCharacter, "yue", true);
  }

  private void pickSyllable() {
    JourneyCantonese.Phrase lesson = JourneyCantonese.lesson(cantoneseLessonIndex);
    List<JourneyJyutping.Syllable> tokens = JourneyJyutping.parseLine(lesson.jyutping);
    if (tokens.isEmpty()) {
      toast("这句粤拼未能拆解，不会猜测未知拼音");
      return;
    }
    String[] choices = new String[tokens.size()];
    for (int i = 0; i < tokens.size(); i++) {
      choices[i] = (i + 1) + ". " + tokens.get(i).jyutping;
    }
    new AlertDialog.Builder(this).setTitle("选择要练习的音节")
        .setItems(choices, (d, which) -> {
          phoneticDetail.setText(tokens.get(which).detail());
        }).setNegativeButton("关闭", null).show();
  }

  private void updateCantoneseLesson() {
    if (cantoneseLesson == null) return;
    JourneyCantonese.Phrase p = JourneyCantonese.lesson(cantoneseLessonIndex);
    cantoneseLesson.setText((cantoneseLessonIndex + 1) + " / " + JourneyCantonese.lessonCount()
        + "\n" + p.hanzi + "\n" + p.jyutping + "\n" + p.mandarin
        + "\n\n学习提示：" + p.hint);
    if (phoneticDetail != null)
      phoneticDetail.setText("这句有 " + JourneyJyutping.parseLine(p.jyutping).size()
          + " 个粤拼音节。点击「拆解这句粤拼」选择音节练习。");
  }

  private void changeCantoneseLesson(int offset) {
    int count = JourneyCantonese.lessonCount();
    cantoneseLessonIndex = ((cantoneseLessonIndex + offset) % count + count) % count;
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
    if (practice.isRecording()) practice.stop();
    if (speechGuide != null) speechGuide.stop();
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
    if (speechGuide != null) speechGuide.stop();
    if (practice.isRecording()) {
      boolean result = practice.stop();
      if (speechFeedback != null) speechFeedback.setText(result
          ? "已暂停麦克风录音，可继续回放。"
          : "录音中断，请重新录制。");
    }
    practice.stopPlayback();
    super.onStop();
  }

  @Override protected void onDestroy() {
    handler.removeCallbacks(progress);
    if (player != null) player.stopPlayback();
    practice.close();
    if (toneReference != null) toneReference.delete();
    if (speechGuide != null) speechGuide.close();
    super.onDestroy();
  }
}
