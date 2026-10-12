package org.openenglish.reader;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.speech.RecognizerIntent;
import android.speech.tts.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
  private static final int INK = 0xff193b36,
      TEAL = 0xff177c6d,
      MUTED = 0xff71827c,
      BG = 0xfff5f8f4,
      PALE = 0xffe2eee7;
  private static final int SPEECH = 11,
      VIDEO = 12,
      SUBTITLES = 13,
      EXPORT = 14,
      IMPORT = 15,
      RECORD_PERMISSION = 16;
  private static final String[] CHAPTERS = {
    "简介",
    "第一章 · 起点",
    "第二章 · 口语",
    "第三章 · 语音",
    "第四章 · 朗读",
    "第五章 · 词典",
    "第六章 · 语法",
    "第七章 · 精读",
    "第八章 · 叮嘱",
    "后记"
  };
  private static final String[] FILES = {
    "README",
    "chapter1",
    "chapter2",
    "chapter3",
    "chapter4",
    "chapter5",
    "chapter6",
    "chapter7",
    "chapter8",
    "end"
  };
  private boolean bridged;
  private LinearLayout root, body;
  private ScrollView scroll;
  private int tab = 0, index = 0;
  private String screen = "", scene = "日常聊天";
  private JSONArray lessons = new JSONArray();
  private LearningStore store;
  private android.content.SharedPreferences prefs;
  private TextToSpeech tts;
  private boolean ttsReady = false,
      foreground = true,
      aiBusy = false,
      recording = false,
      showMeaning = false,
      loop = false;
  private CloudClient.Job localJob;
  private long aiGeneration = 0, recordStarted = 0;
  private MediaRecorder recorder;
  private MediaPlayer playback;
  private File lastRecording;
  private TextView recordButton, matchLabel, subtitleLabel, videoInfo, chatStatus;
  private EditText chatInput;
  private OrbView orb;
  private VideoView video;
  private MediaController controller;
  private MediaPlayer videoPlayer;
  private int activeCue = -1, chosenCue = -1;
  private List<LearningCore.Cue> cues = new ArrayList<>();
  private WebView bookPage;
  private int bookIndex = -1;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          if (video != null && foreground && "".equals(screen) && tab == 2) {
            int pos = video.getCurrentPosition();
            if (loop && chosenCue >= 0 && chosenCue < cues.size()) {
              LearningCore.Cue c = cues.get(chosenCue);
              if (video.isPlaying() && (pos >= c.end || pos < c.start - 100)) {
                video.seekTo((int) c.start);
                video.start();
              }
            }
            int at = -1;
            for (int i = 0; i < cues.size(); i++)
              if (pos >= cues.get(i).start && pos < cues.get(i).end) {
                at = i;
                break;
              }
            if (at != activeCue) {
              activeCue = at;
              if (subtitleLabel != null)
                subtitleLabel.setText(at < 0 ? "字幕会跟随视频播放" : cues.get(at).text);
            }
            ui.postDelayed(this, 160);
          }
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getPreferences(0);
    store = new StudioStore(this);
    index = Math.max(0, prefs.getInt("sentence", 0));
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(0xffffffff);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    try {
      lessons = new JSONArray(AiClient.read(getAssets().open("lessons.json"), 200000));
    } catch (Exception e) {
      toast("课程文件加载失败");
    }
    if (index >= lessons.length()) index = 0;
    tts =
        new TextToSpeech(
            this,
            status -> {
              if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.US);
                ttsReady =
                    r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                tts.setOnUtteranceProgressListener(
                    new UtteranceProgressListener() {
                      public void onStart(String id) {
                        ui.post(() -> setOrb(true));
                      }

                      public void onDone(String id) {
                        ui.post(() -> setOrb(false));
                      }

                      public void onError(String id) {
                        ui.post(
                            () -> {
                              setOrb(false);
                              toast("朗读失败，请检查英语语音包");
                            });
                      }
                    });
              }
            });
    if (state != null) {
      tab = state.getInt("tab");
      index = Math.max(0, Math.min(lessons.length() - 1, state.getInt("index")));
      scene = state.getString("scene", "日常聊天");
    }
    String section = getIntent().getStringExtra("section");
    bridged = section != null;
    if (bridged) {
      if (section.equals("words")) render(3);
      else if (section.equals("practice")) {
        render(0);
        practice();
      } else if (section.equals("notes")) {
        render(4);
        notes();
      } else {
        render(4);
        books();
      }
    } else render(tab);
    handleShare(getIntent());
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private GradientDrawable bg(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(1);
    return l;
  }

  private TextView text(String s, int size, int color, boolean bold) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setLineSpacing(dp(3), 1.03f);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return v;
  }

  private void add(LinearLayout l, View v, int margin) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(margin);
    l.addView(v, p);
  }

  private LinearLayout card() {
    LinearLayout l = col();
    l.setPadding(dp(20), dp(20), dp(20), dp(20));
    l.setBackground(bg(0xffffffff, 24));
    return l;
  }

  private TextView button(String title, boolean primary, Runnable action) {
    TextView b = text(title, 15, primary ? 0xffffffff : TEAL, true);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(12), dp(14), dp(12), dp(14));
    b.setMinHeight(dp(48));
    b.setBackground(bg(primary ? TEAL : PALE, 16));
    b.setContentDescription(title);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void row(LinearLayout parent, String a, Runnable aa, String b, Runnable bb) {
    LinearLayout r = new LinearLayout(this);
    TextView x = button(a, false, aa), y = button(b, false, bb);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    r.addView(x, p);
    LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, -2, 1);
    q.leftMargin = dp(10);
    r.addView(y, q);
    add(parent, r, 10);
  }

  private EditText input(String hint, int type) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setTextSize(16);
    e.setPadding(dp(14), dp(12), dp(14), dp(12));
    e.setBackground(bg(BG, 14));
    e.setInputType(type);
    return e;
  }

  private void heading(String title, String sub) {
    add(body, text(title, 27, INK, true), 4);
    add(body, text(sub, 13, MUTED, false), 7);
  }

  private void teardown() {
    if (chatInput != null) prefs.edit().putString("draft", chatInput.getText().toString()).apply();
    stopRecording();
    stopPlayback();
    if (tts != null) tts.stop();
    if (bookPage != null) {
      saveBookPosition();
      bookPage.destroy();
      bookPage = null;
      bookIndex = -1;
    }
    if (video != null) {
      try {
        prefs.edit().putInt("video_position", video.getCurrentPosition()).apply();
        video.stopPlayback();
      } catch (Exception ignored) {
      }
      video = null;
      videoPlayer = null;
    }
    ui.removeCallbacks(ticker);
    recordButton = null;
    matchLabel = null;
    subtitleLabel = null;
    videoInfo = null;
    chatStatus = null;
    chatInput = null;
    orb = null;
  }

  private void render(int selected) {
    teardown();
    screen = "";
    tab = selected;
    showMeaning = false;
    root = col();
    root.setBackgroundColor(BG);
    setContentView(root);
    LinearLayout top = new LinearLayout(this);
    top.setPadding(dp(22), dp(16), dp(20), dp(12));
    top.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout brand = col();
    add(brand, text("Everyone", 23, INK, true), 0);
    add(brand, text("AI ENGLISH  /  随时开口", 10, MUTED, true), 2);
    top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    TextView settings = button("设置", false, this::settings);
    top.addView(settings, new LinearLayout.LayoutParams(dp(64), dp(46)));
    root.addView(top);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = col();
    body.setPadding(dp(20), dp(6), dp(20), dp(26));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout nav = new LinearLayout(this);
    nav.setPadding(dp(8), dp(9), dp(8), dp(9));
    nav.setBackgroundColor(0xffffffff);
    String[] tabs = {"今日", "老师", "视频", "词库", "我的"};
    for (int i = 0; i < 5; i++) {
      final int n = i;
      TextView v = text(tabs[i], 13, n == tab ? TEAL : MUTED, n == tab);
      v.setGravity(Gravity.CENTER);
      v.setPadding(0, dp(12), 0, dp(12));
      if (n == tab) v.setBackground(bg(PALE, 16));
      v.setOnClickListener(w -> render(n));
      nav.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
    }
    if (!bridged) root.addView(nav);
    switch (tab) {
      case 0:
        home();
        break;
      case 1:
        teacher();
        break;
      case 2:
        media();
        break;
      case 3:
        words();
        break;
      case 4:
        mine();
        break;
    }
  }

  private JSONObject lesson() {
    return lessons.optJSONObject(index) == null ? new JSONObject() : lessons.optJSONObject(index);
  }

  private void home() {
    heading("今天，从一句开始", "每天 5 分钟，让英语走进生活");
    LinearLayout hero = card();
    hero.setBackground(bg(PALE, 26));
    add(hero, text("YOUR DAILY PRACTICE", 10, TEAL, true), 0);
    add(hero, text("不用准备好\n现在就可以开口", 26, INK, true), 13);
    OrbView o = new OrbView(this);
    add(hero, o, 2);
    o.getLayoutParams().height = dp(146);
    o.setOnClickListener(v -> render(1));
    add(hero, button("开始今日跟读  →", true, this::practice), 8);
    add(body, hero, 20);
    LinearLayout stat = new LinearLayout(this);
    String[] titles = {"今日练习", "已学短句", "待复习"};
    int[] nums = {store.today(), store.total(), store.due()};
    for (int i = 0; i < 3; i++) {
      LinearLayout c = col();
      c.setGravity(Gravity.CENTER);
      add(c, text("" + nums[i], 25, TEAL, true), 0);
      add(c, text(titles[i], 11, MUTED, false), 4);
      stat.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
    }
    add(body, stat, 23);
    LinearLayout daily = card();
    add(daily, text("今天的一句 · " + lesson().optString("scene"), 12, TEAL, true), 0);
    add(daily, text(lesson().optString("en"), 22, INK, true), 12);
    add(daily, text(lesson().optString("zh"), 14, MUTED, false), 8);
    row(
        daily,
        "听原句",
        () -> speak(lesson().optString("en"), .85f),
        "收藏表达",
        () -> saveExpression(lesson().optString("en"), lesson().optString("zh")));
    add(body, daily, 20);
    row(body, "口语老师  ↗", () -> render(1), "离线读书  ↗", this::books);
    row(body, "英语碎片本", this::notes, "复习生词", () -> render(3));
  }

  private void subpage(String name) {
    teardown();
    screen = name;
    root.removeAllViews();
    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(dp(18), dp(12), dp(18), dp(10));
    TextView back = button("‹ 返回", false, this::onBackPressed);
    bar.addView(back, new LinearLayout.LayoutParams(dp(86), dp(46)));
    TextView t = text(name, 18, INK, true);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = dp(16);
    bar.addView(t, p);
    root.addView(bar);
    scroll = new ScrollView(this);
    body = col();
    body.setPadding(dp(20), dp(16), dp(20), dp(24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private void practice() {
    subpage("跟读练习");
    heading(
        lesson().optString("scene"),
        "第 " + (index + 1) + " / " + lessons.length() + " 句 · 听清、开口、回放");
    add(body, button("选择场景 / 句子", false, this::chooseLesson), 14);
    LinearLayout c = card();
    add(c, text(lesson().optString("en"), 28, INK, true), 0);
    TextView translation = text(lesson().optString("zh"), 16, MUTED, false);
    add(c, translation, 14);
    row(
        c,
        "正常朗读",
        () -> speak(lesson().optString("en"), .88f),
        "慢速朗读",
        () -> speak(lesson().optString("en"), .65f));
    row(
        c,
        "识别我说的",
        () -> recognize(false),
        "收藏生词",
        () -> {
          store.add("word", lesson().optString("word"), lesson().optString("meaning"));
          toast("已保存到词库");
        });
    matchLabel = text("识别文字对照会显示在这里。它不是发音评分。", 13, MUTED, false);
    add(c, matchLabel, 16);
    recordButton = button("● 录一遍我的声音", true, this::toggleRecord);
    add(c, recordButton, 16);
    row(c, "回放我的录音", this::playRecording, "听原句对比", () -> speak(lesson().optString("en"), .88f));
    add(c, text("录音保存在本机。切换页面会停止录音；最长 60 秒。", 12, MUTED, false), 12);
    add(body, c, 18);
    add(
        body,
        button(
            "练好了，下一句  →",
            true,
            () -> {
              store.complete(index);
              index = (index + 1) % lessons.length();
              prefs.edit().putInt("sentence", index).apply();
              practice();
            }),
        18);
    row(
        body,
        "← 上一句",
        () -> {
          index = (index + lessons.length() - 1) % lessons.length();
          prefs.edit().putInt("sentence", index).apply();
          practice();
        },
        "保存到碎片本",
        () -> {
          store.add("note", lesson().optString("en"), lesson().optString("zh"));
          toast("已保存");
        });
  }

  private void chooseLesson() {
    String[] a = new String[lessons.length()];
    for (int i = 0; i < a.length; i++) {
      JSONObject l = lessons.optJSONObject(i);
      a[i] = l.optString("scene") + " · " + l.optString("en");
    }
    new AlertDialog.Builder(this)
        .setTitle("60 句生活英语")
        .setItems(
            a,
            (d, n) -> {
              index = n;
              prefs.edit().putInt("sentence", n).apply();
              practice();
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void speak(String s, float speed) {
    stopRecording();
    stopPlayback();
    if (!ttsReady) {
      new AlertDialog.Builder(this)
          .setTitle("需要英语语音包")
          .setMessage("请在系统文字转语音设置中安装英语语音。读书、生词和录音仍可使用。")
          .setPositiveButton(
              "语音设置",
              (d, n) -> {
                try {
                  startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                } catch (Exception e) {
                  toast("请到系统设置搜索文字转语音");
                }
              })
          .setNegativeButton("稍后", null)
          .show();
      return;
    }
    if (LocalAI.enabled(this) && !OfflineVoice.select(tts)) {toast("请先在系统 TTS 设置中安装离线英语语音包");return;}
    tts.setSpeechRate(speed);
    int result =
        tts.speak(
            s.length() > 3500 ? s.substring(0, 3500) : s,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "english");
    if (result == TextToSpeech.ERROR) toast("朗读启动失败");
  }

  private void setOrb(boolean active) {
    if (orb != null) {
      orb.active = active;
      orb.invalidate();
    }
  }

  private void recognize(boolean forChat) {
    stopRecording();
    stopPlayback();
    if (tts != null) tts.stop();
    if (LocalAI.enabled(this)) {
      if (forChat) { Intent local = new Intent(this,StudioActivity.class);local.putExtra("free_tutor",true);startActivity(local);return; }
      if (lastRecording == null || !lastRecording.isFile()) {toast("请先录音，再用本地 Whisper 识别");return;}
      if (localJob != null) {toast("请先等待当前本地任务完成");return;}
      File audio=lastRecording;String reference=lesson().optString("en");CloudClient.Job request=new CloudClient.Job();localJob=request;
      toast("Whisper 正在识别本句录音…");
      worker.execute(()->{File wav=null;try {wav=AudioDecoder.clip(this,Uri.fromFile(audio),0,30000,request);String heard=LocalAI.transcribe(this,wav,false,request).getString("text");ui.post(()->{if(!isDestroyed() && matchLabel!=null && "跟读练习".equals(screen))matchLabel.setText("识别到："+heard+"\n文字匹配："+LearningCore.textMatch(reference,heard)+"%（不是发音评分）");});}catch(Exception e){ui.post(()->{if(!isDestroyed())toast(e.getMessage()==null?"本地识别失败":e.getMessage());});}finally{if(wav!=null)wav.delete();ui.post(()->{if(localJob==request)localJob=null;});}});return;
    }
    prefs.edit().putBoolean("speech_for_chat", forChat).apply();
    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
    i.putExtra(RecognizerIntent.EXTRA_PROMPT, "说一句英语；识别结果会先显示给你");
    try {
      startActivityForResult(i, SPEECH);
    } catch (ActivityNotFoundException e) {
      toast("手机未安装语音识别服务，可先录音回放，或手动输入");
    }
  }

  private void toggleRecord() {
    if (recording) {
      stopRecording();
      return;
    }
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, RECORD_PERMISSION);
      return;
    }
    stopPlayback();
    if (tts != null) tts.stop();
    try {
      File dir = PortableBackup.legacyDirectory(this, store);
      dir.mkdirs();
      File f = new File(dir, "sentence_" + index + ".m4a");
      MediaRecorder r = new MediaRecorder();
      recorder = r;
      r.setAudioSource(MediaRecorder.AudioSource.MIC);
      r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      r.setAudioEncodingBitRate(96000);
      r.setAudioSamplingRate(44100);
      r.setOutputFile(f.getAbsolutePath());
      r.prepare();
      r.start();
      lastRecording = f;
      recording = true;
      recordStarted = SystemClock.elapsedRealtime();
      if (recordButton != null) recordButton.setText("■ 停止录音");
      ui.postDelayed(recordLimit, 60000);
    } catch (Exception e) {
      releaseRecorder();
      toast("无法录音，请检查麦克风权限及占用");
    }
  }

  private final Runnable recordLimit =
      () -> {
        if (recording) {
          stopRecording();
          toast("已保存 60 秒录音");
        }
      };

  private void releaseRecorder() {
    if (recorder != null) {
      try {
        recorder.release();
      } catch (Exception ignored) {
      }
      recorder = null;
    }
    recording = false;
    ui.removeCallbacks(recordLimit);
    if (recordButton != null) recordButton.setText("● 录一遍我的声音");
  }

  private void stopRecording() {
    if (!recording) return;
    boolean valid = SystemClock.elapsedRealtime() - recordStarted >= 700;
    try {
      recorder.stop();
    } catch (Exception e) {
      valid = false;
    }
    releaseRecorder();
    if (!valid) {
      if (lastRecording != null) lastRecording.delete();
      lastRecording = null;
      toast("录音太短，请再试一次");
    } else toast("录音已保存，可回放对比");
  }

  private void playRecording() {
    stopRecording();
    stopPlayback();
    if (tts != null) tts.stop();
    File f = new File(PortableBackup.legacyDirectory(this, store), "sentence_" + index + ".m4a");
    if (!f.exists()) {
      toast("请先录一遍声音");
      return;
    }
    try {
      playback = new MediaPlayer();
      playback.setAudioAttributes(
          new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
      playback.setDataSource(f.getAbsolutePath());
      playback.setOnPreparedListener(MediaPlayer::start);
      playback.setOnCompletionListener(p -> stopPlayback());
      playback.setOnErrorListener(
          (p, w, e) -> {
            stopPlayback();
            toast("录音无法播放");
            return true;
          });
      playback.prepareAsync();
    } catch (Exception e) {
      stopPlayback();
      toast("录音无法播放");
    }
  }

  private void stopPlayback() {
    if (playback != null) {
      try {
        playback.release();
      } catch (Exception ignored) {
      }
      playback = null;
    }
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == RECORD_PERMISSION
        && g.length > 0
        && g[0] == PackageManager.PERMISSION_GRANTED
        && "跟读练习".equals(screen)) toggleRecord();
    else if (r == RECORD_PERMISSION) toast("未授予麦克风权限，仍可使用其他学习功能");
  }

  private void teacher() {
    heading("和老师，聊一句", "先说出来，再慢慢说得更好");
    LinearLayout hero = card();
    orb = new OrbView(this);
    add(hero, orb, 0);
    orb.getLayoutParams().height = dp(170);
    orb.active = aiBusy;
    orb.setOnClickListener(v -> recognize(true));
    add(hero, text("当前场景 · " + scene, 14, TEAL, true), 2);
    row(
        hero,
        "换个场景",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("选择场景")
                .setItems(
                    new String[] {"日常聊天", "咖啡店点单", "旅行住宿", "学习与工作", "聊我的猫"},
                    (d, n) -> {
                      scene = new String[] {"日常聊天", "咖啡店点单", "旅行住宿", "学习与工作", "聊我的猫"}[n];
                      render(1);
                    })
                .show(),
        "模型设置",
        this::settings);
    add(body, hero, 18);
    boolean configured = !getSharedPreferences("connection", 0).getString("base", "").isEmpty();
    add(
        body,
        text(
            LocalAI.enabled(this) ? "本地 DeepSeek 外教 · 语音输入使用新版 Whisper 对话" : configured ? "AI 自由对话 · 发送文字与最近 30 条对话给所配置的服务" : "离线场景练习 · 自由对话请先配置模型",
            12,
            MUTED,
            false),
        16);
    chatStatus = text(aiBusy ? "老师正在回复…" : "", 13, TEAL, false);
    add(body, chatStatus, 8);
    JSONArray history = store.conversation();
    if (history.length() == 0) {
      LinearLayout hello = card();
      add(hello, text("Hello! What would you like to talk about today?", 19, INK, true), 0);
      add(hello, text("离线示例：今天想聊些什么？你也可以用下面的场景短句开始。", 13, MUTED, false), 10);
      add(body, hello, 12);
    } else
      for (int i = Math.max(0, history.length() - 12); i < history.length(); i++) {
        JSONObject o = history.optJSONObject(i);
        boolean user = "user".equals(o.optString("role"));
        LinearLayout b = card();
        b.setBackground(bg(user ? PALE : 0xffffffff, 20));
        add(b, text(user ? "我" : "老师", 11, TEAL, true), 0);
        TextView msg = text(o.optString("content"), 16, INK, false);
        msg.setTextIsSelectable(true);
        add(b, msg, 8);
        if (!user) {
          final String s = o.optString("content");
          row(
              b,
              "朗读",
              () -> speak(s, .85f),
              "存到碎片本",
              () -> {
                store.add("note", s, "口语老师 · " + scene);
                toast("已保存");
              });
        }
        add(body, b, 12);
      }
    chatInput =
        input("输入一句英语，或点语音输入", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    chatInput.setMinLines(2);
    chatInput.setMaxLines(4);
    chatInput.setText(prefs.getString("draft", ""));
    add(body, chatInput, 18);
    row(body, "语音输入", () -> recognize(true), aiBusy ? "正在回复…" : "发送", this::sendChat);
    row(
        body,
        "离线场景短句",
        this::offlinePrompts,
        "清空对话记忆",
        () -> {
          if (aiBusy) {
            toast("请等待当前回复完成");
            return;
          }
          new AlertDialog.Builder(this)
              .setTitle("清空本地对话？")
              .setMessage("学习记录、词库和碎片本会保留。")
              .setPositiveButton(
                  "清空",
                  (d, n) -> {
                    store.clearChat();
                    if (chatInput != null) chatInput.setText("");
                    prefs.edit().remove("draft").apply();
                    render(1);
                  })
              .setNegativeButton("取消", null)
              .show();
        });
  }

  private void offlinePrompts() {
    String[] samples = {
      "I'd like a coffee, please.\n我想要一杯咖啡。",
      "I have a reservation.\n我有预订。",
      "Could you explain this part?\n你能解释这一部分吗？",
      "I have a cat at home.\n我家养了一只猫。",
      "I'm still learning English.\n我还在学英语。"
    };
    new AlertDialog.Builder(this)
        .setTitle("离线示范短句")
        .setItems(
            samples,
            (d, n) -> {
              String s = samples[n].split("\n")[0];
              new AlertDialog.Builder(this)
                  .setTitle(s)
                  .setMessage(samples[n].split("\n")[1])
                  .setPositiveButton("听原句", (w, j) -> speak(s, .8f))
                  .setNeutralButton(
                      "跟读练习",
                      (w, j) -> {
                        for (int i = 0; i < lessons.length(); i++)
                          if (s.equals(lessons.optJSONObject(i).optString("en"))) index = i;
                        practice();
                      })
                  .setNegativeButton("关闭", null)
                  .show();
            })
        .show();
  }

  private void sendChat() {
    if (localJob != null) {
      toast("本地任务正在运行，请稍等");
      return;
    }
    if (chatInput == null) return;
    String s = chatInput.getText().toString().trim();
    if (s.isEmpty()) return;
    if (s.length() > 4000) {
      toast("一次最多输入 4000 字");
      return;
    }
    android.content.SharedPreferences connection = getSharedPreferences("connection", 0);
    String base = connection.getString("base", ""), model = connection.getString("model", "");
    if (!LocalAI.enabled(this) && (base.isEmpty() || model.isEmpty())) {
      prefs.edit().putString("draft", s).apply();
      new AlertDialog.Builder(this)
          .setTitle("先连接口语老师")
          .setMessage("AI 自由对话需要 HTTPS 模型地址、模型名称和服务所需的密钥。也可先练离线场景短句。")
          .setPositiveButton("配置模型", (d, n) -> settings())
          .setNegativeButton("离线练习", (d, n) -> offlinePrompts())
          .show();
      return;
    }
    if (LocalAI.enabled(this) && !LocalModels.ready(this,LocalModels.DEEPSEEK)) {prefs.edit().putString("draft",s).apply();startActivity(new Intent(this,LocalModelActivity.class));return;}
    final String key;
    try {
      if (!LocalAI.enabled(this)) AiClient.endpoint(base);
      key = LocalAI.enabled(this) ? "" : KeyVault.read(this);
    } catch (Exception e) {
      toast("模型配置无法读取，请重新设置");
      return;
    }
    JSONArray before = store.conversation();
    JSONObject last = before.optJSONObject(before.length() - 1);
    if (last == null
        || !"user".equals(last.optString("role"))
        || !s.equals(last.optString("content"))) store.add("user", s, "");
    chatInput.setText("");
    prefs.edit().remove("draft").apply();
    aiBusy = true;
    final CloudClient.Job request = new CloudClient.Job();localJob=request;
    final long gen = ++aiGeneration;
    final JSONArray history = store.conversation();
    final String chosenScene = scene;
    render(1);
    worker.execute(
        () -> {
          try {
            String answer = new CloudClient(getApplicationContext()).chat(base, model, key, "You are an English tutor. Scenario: "+chosenScene+". Correct one useful error, then ask a short English question. Reply briefly.", history, request, complete -> {});
            ui.post(
                () -> {
                  if (isDestroyed() || gen != aiGeneration) return;
                  store.add("assistant", answer, "");
                  aiBusy = false;
                  if(localJob==request)localJob=null;
                  if (foreground && tab == 1 && screen.isEmpty()) {
                    render(1);
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                    if (prefs.getBoolean("auto_speak", LocalAI.enabled(this))) speak(answer, .88f);
                  }
                });
          } catch (Exception e) {
            ui.post(
                () -> {
                  if (isDestroyed() || gen != aiGeneration) return;
                  aiBusy = false;
                  if(localJob==request)localJob=null;
                  prefs.edit().putString("draft", s).apply();
                  if (foreground) {
                    toast(
                        e instanceof java.net.SocketTimeoutException
                            ? "模型响应超时，可稍后重新发送"
                            : e.getMessage() == null ? "模型连接失败，请检查网络与配置" : e.getMessage());
                    if (tab == 1 && screen.isEmpty()) render(1);
                  }
                });
          }
        });
  }

  private void media() {
    heading("把喜欢的视频，变成课堂", "本地视频 + SRT / VTT 字幕 · 可离线练习");
    row(body, "导入视频", () -> pick("video/*", VIDEO), "导入字幕", () -> pick("*/*", SUBTITLES));
    String uri = prefs.getString("video_uri", "");
    if (uri.isEmpty()) {
      LinearLayout empty = card();
      add(empty, text("选一段你喜欢的视频", 23, INK, true), 0);
      add(empty, text("导入对应的字幕后，点击一句即可定位、循环播放并收藏。不会自动转写或下载网络视频。", 15, MUTED, false), 14);
      add(empty, button("试试内置字幕演示", true, this::demoVideo), 18);
      add(body, empty, 20);
      return;
    }
    try {
      String path = prefs.getString("subtitle_path", "");
      if (!path.isEmpty()) {
        File f = new File(getFilesDir(), path);
        if (f.exists())
          cues = LearningCore.parseSubtitles(AiClient.read(new FileInputStream(f), 2000000));
      } else cues = new ArrayList<>();
    } catch (Exception e) {
      cues = new ArrayList<>();
      toast("已保存的字幕无法读取，请重新导入");
    }
    video = new VideoView(this);
    video.setBackgroundColor(0xff153a36);
    add(body, video, 18);
    video.getLayoutParams().height = dp(215);
    controller = new MediaController(this);
    controller.setAnchorView(video);
    video.setMediaController(controller);
    videoInfo = text("正在准备视频…", 12, MUTED, false);
    add(body, videoInfo, 8);
    video.setOnPreparedListener(
        p -> {
          videoPlayer = p;
          int saved = prefs.getInt("video_position", 0);
          if (saved > 0 && saved < p.getDuration()) video.seekTo(saved);
          videoInfo.setText(prefs.getString("video_name", "本地视频") + " · 点击画面显示播放控制");
          ui.removeCallbacks(ticker);
          ui.post(ticker);
        });
    video.setOnErrorListener(
        (p, w, e) -> {
          videoInfo.setText("视频无法播放。请重新选择文件，或使用常见的 MP4 / H.264 格式。");
          ui.removeCallbacks(ticker);
          return true;
        });
    video.setOnCompletionListener(
        p -> {
          if (loop && chosenCue >= 0) {
            video.seekTo((int) cues.get(chosenCue).start);
            video.start();
          }
        });
    try {
      video.setVideoURI(Uri.parse(uri));
    } catch (Exception e) {
      videoInfo.setText("视频读取失败，请重新导入");
    }
    subtitleLabel = text("字幕会跟随视频播放", 20, INK, true);
    subtitleLabel.setGravity(Gravity.CENTER);
    subtitleLabel.setPadding(dp(16), dp(20), dp(16), dp(20));
    subtitleLabel.setBackground(bg(PALE, 20));
    add(body, subtitleLabel, 14);
    row(
        body,
        "播放 / 暂停",
        () -> {
          if (video == null) return;
          if (video.isPlaying()) video.pause();
          else video.start();
        },
        "0.75 倍速",
        () -> {
          try {
            if (videoPlayer != null) {
              boolean playing = video.isPlaying();
              videoPlayer.setPlaybackParams(videoPlayer.getPlaybackParams().setSpeed(.75f));
              if (!playing) video.pause();
              toast("已切换为 0.75 倍速");
            }
          } catch (Exception e) {
            toast("当前视频不支持变速");
          }
        });
    row(
        body,
        "正常速度",
        () -> {
          try {
            if (videoPlayer != null) {
              boolean playing = video.isPlaying();
              videoPlayer.setPlaybackParams(videoPlayer.getPlaybackParams().setSpeed(1));
              if (!playing) video.pause();
            }
          } catch (Exception e) {
            toast("当前视频不支持变速");
          }
        },
        loop ? "关闭单句循环" : "单句循环",
        () -> {
          if (chosenCue < 0) {
            toast("先点选下方的一句字幕");
            return;
          }
          loop = !loop;
          if (videoInfo != null)
            videoInfo.setText(
                prefs.getString("video_name", "本地视频") + (loop ? " · 单句循环已开启" : " · 单句循环已关闭"));
          toast(loop ? "已开启所选句子的循环" : "已关闭单句循环");
        });
    row(
        body,
        "朗读当前字幕",
        () -> {
          int at = activeCue >= 0 ? activeCue : chosenCue;
          if (at >= 0) {
            video.pause();
            speak(cues.get(at).text, .8f);
          } else toast("请先选择一句字幕");
        },
        "收藏当前句子",
        () -> {
          int at = activeCue >= 0 ? activeCue : chosenCue;
          if (at >= 0) saveExpression(cues.get(at).text, "视频字幕");
          else toast("请先选择一句字幕");
        });
    add(body, text(cues.isEmpty() ? "请导入与视频对应的字幕文件" : "字幕列表 · 点选一句开始播放", 16, INK, true), 22);
    for (int i = 0; i < Math.min(1000, cues.size()); i++) {
      final int n = i;
      LearningCore.Cue c = cues.get(i);
      TextView t =
          text(
              String.format(Locale.US, "%02d:%02d", c.start / 60000, (c.start / 1000) % 60)
                  + "   "
                  + c.text,
              15,
              INK,
              false);
      t.setBackground(bg(0xffffffff, 14));
      t.setPadding(dp(14), dp(14), dp(14), dp(14));
      t.setOnClickListener(
          v -> {
            chosenCue = n;
            video.seekTo((int) c.start);
            video.start();
            subtitleLabel.setText(c.text);
          });
      t.setOnLongClickListener(
          v -> {
            saveExpression(c.text, "视频字幕");
            return true;
          });
      add(body, t, 8);
    }
    if (cues.size() > 1000) add(body, text("仅展示前 1000 句；完整字幕仍随视频同步。", 12, MUTED, false), 12);
  }

  private void demoVideo() {
    worker.execute(
        () -> {
          try {
            File f = new File(getFilesDir(), "demo.mp4");
            try (InputStream in = getAssets().open("demo.mp4");
                OutputStream out = new FileOutputStream(f)) {
              byte[] b = new byte[8192];
              int n;
              while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
            File s = new File(getFilesDir(), "demo.srt");
            String sub =
                "1\n"
                    + "00:00:00,000 --> 00:00:04,000\n"
                    + "Hello! Let's practice English.\n"
                    + "你好！一起练英语。\n\n"
                    + "2\n"
                    + "00:00:04,000 --> 00:00:08,000\n"
                    + "A little practice every day.\n"
                    + "每天练习一点点。\n\n"
                    + "3\n"
                    + "00:00:08,000 --> 00:00:12,000\n"
                    + "One sentence at a time.\n"
                    + "一次学会一句。\n";
            try (OutputStream out = new FileOutputStream(s)) {
              out.write(sub.getBytes("UTF-8"));
            }
            ui.post(
                () -> {
                  if (isDestroyed()) return;
                  if (video != null) {
                    video.stopPlayback();
                    video = null;
                    videoPlayer = null;
                  }
                  prefs
                      .edit()
                      .putString("video_uri", Uri.fromFile(f).toString())
                      .putString("video_name", "内置无声演示 · 可点字幕朗读")
                      .putString("subtitle_path", "demo.srt")
                      .putInt("video_position", 0)
                      .apply();
                  chosenCue = -1;
                  loop = false;
                  render(2);
                });
          } catch (Exception e) {
            ui.post(() -> toast("演示视频加载失败"));
          }
        });
  }

  private void words() {
    heading("词库", "把遇到的表达，慢慢变成自己的");
    row(body, "＋ 添加生词", () -> editItem("word", null), "复习 " + store.due() + " 个", this::review);
    EditText search = input("搜索英文或释义", InputType.TYPE_CLASS_TEXT);
    add(body, search, 16);
    LinearLayout list = col();
    add(body, list, 12);
    Runnable filter =
        () -> {
          list.removeAllViews();
          JSONArray a = store.items("word");
          String q = search.getText().toString().trim().toLowerCase(Locale.US);
          int shown = 0;
          for (int i = 0; i < a.length(); i++) {
            JSONObject item = a.optJSONObject(i);
            if ((item.optString("en") + item.optString("zh")).toLowerCase(Locale.US).contains(q)) {
              itemCard(list, item, "word");
              shown++;
            }
          }
          if (shown == 0)
            add(
                list,
                text(q.isEmpty() ? "还没有生词。跟读和字幕中的表达都可以收藏进来。" : "没有匹配的生词", 15, MUTED, false),
                18);
        };
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            filter.run();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    filter.run();
  }

  private void itemCard(LinearLayout parent, JSONObject item, String kind) {
    LinearLayout c = card();
    TextView en = text(item.optString("en"), 20, INK, true);
    en.setTextIsSelectable(true);
    add(c, en, 0);
    add(c, text(item.optString("zh"), 14, MUTED, false), 8);
    row(c, "朗读", () -> speak(item.optString("en"), .85f), "编辑", () -> editItem(kind, item));
    TextView remove = text("删除", 12, MUTED, false);
    remove.setPadding(0, dp(14), 0, dp(5));
    remove.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("删除这条记录？")
                .setPositiveButton(
                    "删除",
                    (d, n) -> {
                      store.delete(item.optLong("id"));
                      if ("note".equals(kind)) notes();
                      else render(3);
                    })
                .setNegativeButton("取消", null)
                .show());
    add(c, remove, 0);
    add(parent, c, 10);
  }

  private void saveExpression(String en, String zh) {
    new AlertDialog.Builder(this)
        .setTitle("保存这句表达")
        .setItems(
            new String[] {"加入词库", "存到英语碎片本"},
            (d, n) -> {
              long id = store.add(n == 0 ? "word" : "note", en, zh);
              toast(id < 0 ? "词库中已有这条表达" : "已保存");
            })
        .show();
  }

  private void editItem(String kind, JSONObject item) {
    LinearLayout l = col();
    l.setPadding(dp(22), dp(8), dp(22), dp(8));
    EditText en =
        input(
            "note".equals(kind) ? "英文句子 / 想法" : "单词或表达",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    EditText zh =
        input("释义、来源或笔记", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    add(l, en, 4);
    add(l, zh, 12);
    if (item != null) {
      en.setText(item.optString("en"));
      zh.setText(item.optString("zh"));
    }
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(item == null ? "添加记录" : "编辑记录")
            .setView(l)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      String s = en.getText().toString().trim(), z = zh.getText().toString().trim();
                      if (s.isEmpty() || s.length() > 10000 || z.length() > 10000) {
                        toast("内容不能为空，每个字段最多 10000 字");
                        return;
                      }
                      if (item == null) {
                        if (store.add(kind, s, z) < 0) {
                          toast("已存在相同表达");
                          return;
                        }
                      } else {
                        ContentValues values = new ContentValues();
                        values.put("en", s);
                        values.put("zh", z);
                        try {
                          store
                              .getWritableDatabase()
                              .update(
                                  "items", values, "id=?", new String[] {"" + item.optLong("id")});
                        } catch (Exception e) {
                          toast("已存在相同表达");
                          return;
                        }
                      }
                      d.dismiss();
                      if ("note".equals(kind)) notes();
                      else render(3);
                    }));
    d.show();
  }

  private void review() {
    JSONArray a = store.items("word");
    JSONObject due = null;
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o.optLong("due") <= System.currentTimeMillis()) {
        due = o;
        break;
      }
    }
    if (due == null) {
      toast("当前没有到期生词，可以去添加新词");
      return;
    }
    final JSONObject item = due;
    subpage("生词复习");
    heading("先想一想它的意思", "剩余 " + store.due() + " 条到期表达");
    LinearLayout c = card();
    add(c, text(item.optString("en"), 30, INK, true), 8);
    TextView meaning = text("点击下方查看释义", 17, MUTED, false);
    add(c, meaning, 22);
    add(
        c,
        button(
            "显示释义",
            false,
            () -> {
              showMeaning = true;
              meaning.setText(
                  item.optString("zh").isEmpty() ? "这条表达还没有释义，可以在词库编辑。" : item.optString("zh"));
            }),
        20);
    add(c, button("听发音", false, () -> speak(item.optString("en"), .82f)), 10);
    add(body, c, 22);
    row(
        body,
        "还不熟",
        () -> {
          store.review(item.optLong("id"), false);
          reviewOrDone();
        },
        "记住了",
        () -> {
          if (!showMeaning) {
            toast("先查看释义，再确认记住了");
            return;
          }
          store.review(item.optLong("id"), true);
          reviewOrDone();
        });
    add(body, text("记住后：1、2、4、8…天复习；还不熟：10 分钟后再来。", 12, MUTED, false), 20);
  }

  private void reviewOrDone() {
    showMeaning = false;
    if (store.due() == 0) {
      toast("本轮复习完成");
      render(3);
    } else review();
  }

  private void mine() {
    heading("我的学习空间", "你的进度、表达和对话，都留在本机");
    LinearLayout c = card();
    add(c, text("已学 " + store.total() + " / " + lessons.length() + " 句生活英语", 22, INK, true), 0);
    add(
        c,
        text(
            "今日练习 " + store.today() + " 次 · 已收藏 " + store.items("word").length() + " 条表达",
            14,
            MUTED,
            false),
        12);
    add(body, c, 20);
    add(body, button("英语碎片本 · " + store.items("note").length() + " 条", true, this::notes), 18);
    add(body, button("离线原著 · 继续精读", false, this::books), 10);
    row(body, "导出学习备份", () -> createExport(), "导入学习备份", () -> pick("*/*", IMPORT));
    add(body, text("备份包含词库、碎片本、学习记录和对话文字。录音、视频、字幕文件和模型密钥不包含在其中。", 12, MUTED, false), 14);
    add(body, button("口语老师与朗读设置", false, this::settings), 16);
    add(body, button("开源说明与使用帮助", false, this::about), 10);
  }

  private void notes() {
    subpage("英语碎片本");
    heading("留住想说的那句话", "支持手动记录，以及其他 App 分享过来的文字");
    add(body, button("＋ 添加英语碎片", true, () -> editItem("note", null)), 18);
    JSONArray a = store.items("note");
    if (a.length() == 0)
      add(body, text("看到有意思的表达时，用系统分享选择本 App，或者复制文字后在这里粘贴。", 16, MUTED, false), 24);
    for (int i = 0; i < a.length(); i++) itemCard(body, a.optJSONObject(i), "note");
  }

  private void books() {
    subpage("离线读书");
    heading("人人都能用英语", "李笑来 · 前言、八章、后记与原书插图");
    int chapter = Math.max(0, Math.min(9, prefs.getInt("chapter", 0)));
    add(body, button("继续阅读：" + CHAPTERS[chapter], true, () -> openChapter(chapter)), 18);
    for (int i = 0; i < 10; i++) {
      final int n = i;
      add(body, button(CHAPTERS[i] + "  ›", false, () -> openChapter(n)), 8);
    }
    add(body, text("阅读位置自动保存；长按正文可复制，随后记入英语碎片本。", 13, MUTED, false), 18);
  }

  private void openChapter(int n) {
    subpage(CHAPTERS[n]);
    screen = "book";
    prefs.edit().putInt("chapter", n).apply();
    bookIndex = n;
    root.removeView(scroll);
    LinearLayout tools = new LinearLayout(this);
    tools.setPadding(dp(16), dp(4), dp(16), dp(8));
    TextView size =
        button(
            "字号",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("阅读字号")
                    .setItems(
                        new String[] {"标准", "大", "更大"},
                        (d, i) -> {
                          prefs.edit().putInt("book_zoom", new int[] {100, 120, 140}[i]).apply();
                          if (bookPage != null)
                            bookPage.getSettings().setTextZoom(new int[] {100, 120, 140}[i]);
                        })
                    .show());
    tools.addView(size, new LinearLayout.LayoutParams(0, dp(46), 1));
    TextView note = button("记精读笔记", false, () -> editItem("note", null));
    LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, dp(46), 1);
    q.leftMargin = dp(8);
    tools.addView(note, q);
    root.addView(tools);
    bookPage = new WebView(this);
    bookPage.setBackgroundColor(BG);
    bookPage.getSettings().setJavaScriptEnabled(false);
    bookPage.getSettings().setAllowFileAccess(true);
    bookPage.getSettings().setAllowFileAccessFromFileURLs(false);
    bookPage.getSettings().setAllowUniversalAccessFromFileURLs(false);
    bookPage.getSettings().setTextZoom(prefs.getInt("book_zoom", 100));
    bookPage.setWebViewClient(
        new WebViewClient() {
          @Override
          public void onPageFinished(WebView v, String u) {
            v.postDelayed(
                () -> {
                  if (bookPage == v) v.scrollTo(0, prefs.getInt("book_y_" + n, 0));
                },
                150);
          }

          @Override
          public boolean shouldOverrideUrlLoading(WebView v, String url) {
            if (url.startsWith("file:///android_asset/book/")) {
              for (int i = 0; i < FILES.length; i++)
                if (url.contains("/" + FILES[i] + ".html")) {
                  openChapter(i);
                  return true;
                }
              return false;
            }
            if (url.startsWith("https://") || url.startsWith("http://")) external(url);
            return true;
          }
        });
    bookPage.loadUrl("file:///android_asset/book/" + FILES[n] + ".html");
    root.addView(bookPage, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout r = new LinearLayout(this);
    r.setPadding(dp(16), dp(8), dp(16), dp(12));
    if (n > 0)
      r.addView(
          button("← 上一章", false, () -> openChapter(n - 1)),
          new LinearLayout.LayoutParams(0, dp(48), 1));
    if (n < 9) {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
      p.leftMargin = dp(8);
      r.addView(button("下一章 →", true, () -> openChapter(n + 1)), p);
    }
    root.addView(r);
  }

  private void saveBookPosition() {
    if (bookPage != null && bookIndex >= 0)
      prefs.edit().putInt("book_y_" + bookIndex, bookPage.getScrollY()).apply();
  }

  private void settings() {
    subpage("学习设置");
    add(body,button("本地 Whisper + DeepSeek 模型设置",true,()->startActivity(new Intent(this,LocalModelActivity.class))),16);
    heading("连接你的口语老师", "兼容 Chat Completions 的 HTTPS 模型接口");
    android.content.SharedPreferences c = getSharedPreferences("connection", 0);
    LinearLayout box = card();
    EditText base =
        input(
            "API 基础地址，如 https://服务域名/v1",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    base.setText(c.getString("base", ""));
    EditText model = input("模型名称（由服务商提供）", InputType.TYPE_CLASS_TEXT);
    model.setText(c.getString("model", ""));
    EditText key =
        input(
            "API 密钥（按服务要求填写）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    try {
      key.setText(KeyVault.read(this));
    } catch (Exception e) {
      toast("旧密钥无法读取，请重新输入");
    }
    add(box, text("模型 API", 16, INK, true), 0);
    add(box, base, 14);
    add(box, model, 12);
    add(box, key, 12);
    add(box, text("密钥加密保存在本机，不会出现在学习备份中。自由对话会将输入和最近对话发送给你填写的服务。", 12, MUTED, false), 12);
    add(
        box,
        button(
            "保存连接",
            true,
            () -> {
              try {
                String b = base.getText().toString().trim(), m = model.getText().toString().trim();
                if (!b.isEmpty()) {
                  AiClient.endpoint(b);
                  if (m.isEmpty()) {
                    toast("请填写模型名称");
                    return;
                  }
                }
                KeyVault.save(this, key.getText().toString());
                c.edit().putString("base", b).putString("model", m).apply();
                toast(b.isEmpty() ? "已关闭 AI 连接" : "已保存，回到老师页面发送一句话即可测试");
                render(1);
              } catch (Exception e) {
                toast(e.getMessage() == null ? "配置保存失败" : e.getMessage());
              }
            }),
        16);
    add(body, box, 20);
    Switch auto = new Switch(this);
    auto.setText("收到 AI 回复后自动朗读");
    auto.setTextColor(INK);
    auto.setChecked(prefs.getBoolean("auto_speak", LocalAI.enabled(this)));
    auto.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("auto_speak", on).apply());
    add(body, auto, 20);
    add(
        body,
        button("测试英语朗读", false, () -> speak("Hello! Let's learn English together.", .85f)),
        16);
    add(body, text("朗读需要手机的英语语音包；语音输入需要可用的系统识别服务。离线录音回放不依赖这些服务。", 12, MUTED, false), 12);
    add(
        body,
        button(
            "系统文字转语音设置",
            false,
            () -> {
              try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
              } catch (Exception e) {
                toast("请在系统设置搜索文字转语音");
              }
            }),
        12);
  }

  private void about() {
    subpage("帮助与开源说明");
    heading("Everyone AI English 2.0", "独立 Android 学习应用 · Android 6.0 及以上");
    add(
        body,
        text(
            "已实现：60 句离线跟读、录音回放、系统语音输入、配置模型后的 AI 对话与本地记忆、本地视频与字幕同步、单句循环、生词复习、英语碎片本、原书阅读及学习备份。\n\n"
                + "本版使用 Android 原生 View 界面和 SQLite。本版没有内置 Whisper / FishAudio /"
                + " 离线大模型，也没有发音音素评分或自动视频转写。\n\n"
                + "原书与插图来自 ZuodaoTech/everyone-can-use-english，保留原作者署名与 GPL-3.0 许可证。应用代码也以 GPL-3.0"
                + " 发布。本应用不是 Enjoy 官方 Android 客户端。",
            15,
            INK,
            false),
        22);
    add(
        body,
        button(
            "查看 GPL-3.0 许可证",
            false,
            () -> {
              subpage("GPL-3.0");
              WebView w = new WebView(this);
              w.getSettings().setJavaScriptEnabled(false);
              w.loadUrl("file:///android_asset/license.html");
              root.removeView(scroll);
              root.addView(w, new LinearLayout.LayoutParams(-1, 0, 1));
              bookPage = w;
              bookIndex = -1;
            }),
        18);
    add(
        body,
        button(
            "上游开源项目",
            false,
            () -> external("https://github.com/ZuodaoTech/everyone-can-use-english")),
        12);
  }

  private void pick(String type, int request) {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType(type);
    i.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    try {
      startActivityForResult(i, request);
    } catch (Exception e) {
      toast("没有可用的文件选择器");
    }
  }

  private void createExport() {
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("application/json");
    i.putExtra(
        Intent.EXTRA_TITLE,
        "Everyone-English-learning-"
            + new java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date())
            + ".json");
    try {
      startActivityForResult(i, EXPORT);
    } catch (Exception e) {
      toast("无法打开保存对话框");
    }
  }

  private String displayName(Uri uri) {
    try (android.database.Cursor c =
        getContentResolver()
            .query(
                uri,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception ignored) {
    }
    return "本地视频";
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) return;
    if (request == SPEECH) {
      ArrayList<String> a = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (a == null || a.isEmpty()) {
        toast("没有识别到文字，请重试");
        return;
      }
      String s = a.get(0);
      if (prefs.getBoolean("speech_for_chat", false)) {
        prefs.edit().putString("draft", s).apply();
        if (tab == 1 && screen.isEmpty() && chatInput != null) chatInput.setText(s);
        else render(1);
      } else if ("跟读练习".equals(screen) && matchLabel != null)
        matchLabel.setText(
            "识别到："
                + s
                + "\n文字匹配："
                + LearningCore.textMatch(lesson().optString("en"), s)
                + "%（不是发音评分）");
      return;
    }
    Uri uri = data.getData();
    if (uri == null) return;
    if (request == VIDEO) {
      if (video != null) {
        video.stopPlayback();
        video = null;
        videoPlayer = null;
      }
      try {
        getContentResolver()
            .takePersistableUriPermission(
                uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Exception ignored) {
      }
      prefs
          .edit()
          .putString("video_uri", uri.toString())
          .putString("video_name", displayName(uri))
          .putInt("video_position", 0)
          .remove("subtitle_path")
          .apply();
      cues.clear();
      chosenCue = -1;
      activeCue = -1;
      loop = false;
      render(2);
    }
    if (request == SUBTITLES) {
      toast("正在读取字幕…");
      worker.execute(
          () -> {
            try {
              String s = AiClient.read(getContentResolver().openInputStream(uri), 2000000);
              List<LearningCore.Cue> parsed = LearningCore.parseSubtitles(s);
              File f = new File(getFilesDir(), "imported.srt");
              try (OutputStream out = new FileOutputStream(f)) {
                out.write(s.getBytes("UTF-8"));
              }
              ui.post(
                  () -> {
                    if (isDestroyed()) return;
                    prefs.edit().putString("subtitle_path", "imported.srt").apply();
                    cues = parsed;
                    chosenCue = -1;
                    activeCue = -1;
                    loop = false;
                    render(2);
                    toast("已导入 " + parsed.size() + " 句字幕");
                  });
            } catch (Exception e) {
              ui.post(() -> toast("字幕导入失败：" + e.getMessage()));
            }
          });
    }
    if (request == EXPORT) {
      worker.execute(
          () -> {
            try (StudioStore backupStore = new StudioStore(getApplicationContext());
                 OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
              if (out == null) throw new IOException("无法写入文件");
              JSONObject snapshot=backupStore.backup();
              byte[] bytes=snapshot.toString(2).getBytes("UTF-8");
              if (bytes.length>20000000) throw new IOException("学习 JSON 超过 20 MB，请使用完整迁移包或先整理学习数据");
              out.write(bytes);
              ui.post(() -> toast("学习备份已导出"));
            } catch (Exception e) {
              ui.post(() -> toast("导出失败，请选择其他位置"));
            }
          });
    }
    if (request == IMPORT) {
      if (aiBusy) {
        toast("请等待老师回复后再导入备份");
        return;
      }
      new AlertDialog.Builder(this)
          .setTitle("用备份恢复学习数据？")
          .setMessage("词库、碎片本、材料、对话、训练和复习计划会替换为备份内容。旧 schema 1 备份不含材料数据。建议先导出当前数据；媒体请使用首页的完整迁移包。")
          .setPositiveButton(
              "恢复",
              (d, n) ->
                  worker.execute(
                      () -> {
                        try {
                          JSONObject json =
                              new JSONObject(
                                  AiClient.read(
                                      getContentResolver().openInputStream(uri), 20000000));
                          if (isDestroyed()) throw new IOException("导入已取消，请重新选择备份");
                          store.restore(json);
                          ui.post(
                              () -> {
                                if (!isDestroyed()) {
                                  toast("学习数据已恢复");
                                  render(4);
                                }
                              });
                        } catch (Exception e) {
                          ui.post(() -> toast("导入失败，原数据保留：" + e.getMessage()));
                        }
                      }))
          .setNegativeButton("取消", null)
          .show();
    }
  }

  private void handleShare(Intent i) {
    if (Intent.ACTION_SEND.equals(i.getAction()) && "text/plain".equals(i.getType())) {
      String t = i.getStringExtra(Intent.EXTRA_TEXT);
      if (t != null && !t.trim().isEmpty()) {
        if (t.length() > 10000) {
          toast("分享文字超过 10000 字，请分段保存");
          return;
        }
        new AlertDialog.Builder(this)
            .setTitle("保存到英语碎片本？")
            .setMessage(t.length() > 500 ? t.substring(0, 500) + "…" : t)
            .setPositiveButton(
                "保存",
                (d, n) -> {
                  store.add("note", t, "来自系统分享");
                  tab = 4;
                  render(4);
                  notes();
                })
            .setNegativeButton("取消", null)
            .show();
      }
    }
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    handleShare(i);
  }

  private void external(String s) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(s)));
    } catch (Exception e) {
      toast("没有可用的浏览器");
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    b.putInt("tab", tab);
    b.putInt("index", index);
    b.putString("scene", scene);
    saveBookPosition();
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onPause() {
    foreground = false;
    if (chatInput != null) prefs.edit().putString("draft", chatInput.getText().toString()).apply();
    stopRecording();
    stopPlayback();
    if (tts != null) tts.stop();
    saveBookPosition();
    if (video != null) {
      prefs.edit().putInt("video_position", video.getCurrentPosition()).apply();
      video.pause();
    }
    ui.removeCallbacks(ticker);
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    foreground = true;
    if (video != null) ui.post(ticker);
    if (tab == 1
        && screen.isEmpty()
        && chatStatus != null
        && !aiBusy
        && chatStatus.getText().length() > 0) render(1);
  }

  @Override
  public void onBackPressed() {
    if (bridged) {
      if ("book".equals(screen)) {
        books();
        return;
      }
      finish();
      return;
    }
    if ("book".equals(screen)) {
      books();
      return;
    }
    if (!screen.isEmpty()) {
      render(tab);
      return;
    }
    if (tab != 0) render(0);
    else super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    aiGeneration++;
    if(localJob!=null)localJob.cancel();
    teardown();
    ui.removeCallbacksAndMessages(null);
    if (tts != null) tts.shutdown();
    worker.shutdownNow();
    store.close();
    super.onDestroy();
  }
  @Override public void onTrimMemory(int level) {super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW && !worker.isShutdown()){try{worker.execute(LocalAI::releaseCaches);}catch(java.util.concurrent.RejectedExecutionException ignored){}}}

}
