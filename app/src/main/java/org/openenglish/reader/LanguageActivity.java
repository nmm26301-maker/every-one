package org.openenglish.reader;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Three-language native learning garden, entirely local; independent of English ASR. */
public class LanguageActivity extends Activity {
 private static final int INK=0xfff5eeff,MUTED=0xffbab4d3,PURPLE=0xffbca8ff,PALE=0xff302b50,WHITE=0xffffffff;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ExecutorService checkWorker=Executors.newSingleThreadExecutor();
 private volatile CloudClient.Job checking;private TextView checkStatus,checkCancel;
 private final ExecutorService alignmentWorker=Executors.newSingleThreadExecutor();
 private volatile LocalTifa.Control aligning;private TextView segmentsStatus,segmentsCancel;private LinearLayout segmentRows;
 private List<SegmentCoach.Unit> units=Collections.emptyList();private List<File[]> clips=Collections.emptyList();private File clipDir,focusTake;private int focusIndex=-1;
 private final LessonRecorder recorder=new LessonRecorder();
 private final List<JSONObject> lessons=new ArrayList<>();
 private IslandReview review;private String selectedStage="";private SharedPreferences prefs;private LinearLayout body,root;private ScrollView scroll;
 private String page="hub",lang="yue";private JSONObject lesson;private PracticePath practicePath;
 private int epoch;private boolean recording,working,foreground;private boolean firstResume=true;
 private TextView recordButton,feedbackTitle,feedbackDetail,meter,playStatus,starLabel;private PitchView graph;
 private PronunciationCoach.Track reference,take;private File referenceFile,takeFile;private double middleHz;
 private MediaPlayer player;private AudioManager audio;
 private final AudioManager.OnAudioFocusChangeListener focus=change->{if(change<0)ui.post(()->{stopPlayer();cancelRecording();if(meter!=null)meter.setText("音频被其他应用打断，可以再试一次");});};

 @Override public void onCreate(Bundle state){
  super.onCreate(state);foreground=true;review=new IslandReview(this);prefs=getSharedPreferences("language_magic",0);audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  getWindow().setStatusBarColor(0xff10152c);getWindow().setNavigationBarColor(0xff151b32);
  getWindow().getDecorView().setSystemUiVisibility(0);
  setVolumeControlStream(AudioManager.STREAM_MUSIC);
  try(InputStream in=getAssets().open("languages/lessons.json")){
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);
   JSONArray data=new JSONArray(bytes.toString("UTF-8"));for(int i=0;i<data.length();i++)lessons.add(data.getJSONObject(i));
  }catch(Exception e){new AlertDialog.Builder(this).setMessage("课程未能打开，请重新安装最新版").setPositiveButton("返回",(d,n)->finish()).show();return;}
  if(state!=null&&state.getStringArrayList("practice_ids")!=null)practicePath=new PracticePath(state.getStringArrayList("practice_ids"),state.getInt("practice_position",0));
  String p=state==null?"hub":state.getString("page","hub");lang=state==null?prefs.getString("lang","yue"):state.getString("lang","yue");
  JSONObject saved=state==null?null:find(state.getString("lesson",""));
  if(p.equals("celebrate")&&practicePath!=null&&practicePath.finished())practiceComplete();else if(saved!=null&&p.equals("lesson"))openLesson(saved);else if(p.equals("track"))track(lang);else if(p.equals("favorites"))favorites();else if(p.equals("history"))history();else hub();
 }
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");v.setLineSpacing(dp(3),1);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 private GradientDrawable rounded(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
 private GradientDrawable gradient(int a,int b,int r){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(r));return d;}
 private LinearLayout col(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);return c;}
 private void add(LinearLayout parent,View v,int gap){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(gap);parent.addView(v,lp);}
 private TextView button(String s,boolean primary,Runnable click){
  TextView b=text(s,15,primary?WHITE:PURPLE,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(52));b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x228460d6),primary?gradient(0xff785cb4,0xff466d8e,18):rounded(PALE,18),null));b.setOnClickListener(v->click.run());return b;
 }
 private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(18),dp(17),dp(18),dp(18));c.setBackground(rounded(0xff222442,24));c.setElevation(dp(2));return c;}
 private void pair(LinearLayout target,String a,Runnable ac,String b,Runnable bc){LinearLayout row=new LinearLayout(this);View av=button(a,false,ac),bv=button(b,false,bc);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.rightMargin=dp(8);row.addView(av,p);row.addView(bv,new LinearLayout.LayoutParams(0,-2,1));add(target,row,10);}
 private void shell(String p,String title,String subtitle){
  leave();page=p;body=col();root=col();root.setBackground(gradient(0xff161b35,0xff2a2144,0));android.widget.FrameLayout sky=new android.widget.FrameLayout(this);sky.addView(new CosmicUi.Sky(this),new android.widget.FrameLayout.LayoutParams(-1,-1));root.setBackgroundColor(0xcc10152c);sky.addView(root);setContentView(sky);
  LinearLayout top=new LinearLayout(this);top.setPadding(dp(18),dp(10),dp(18),dp(9));top.setGravity(Gravity.CENTER_VERTICAL);
  TextView back=button("‹",false,this::back);back.setTextSize(26);top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
  LinearLayout titles=col();add(titles,text(title,19,INK,true),0);add(titles,text(subtitle,11,MUTED,false),1);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1);tl.leftMargin=dp(12);top.addView(titles,tl);root.addView(top);
  scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body.setPadding(dp(18),dp(8),dp(18),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(8),dp(7),dp(8),dp(7));nav.setBackgroundColor(0xff151b32);
  String[] names={"✦ 魔法岛","♡ 收藏","◷ 足迹","英语"};for(int i=0;i<4;i++){final int n=i;TextView v=text(names[i],12,((i==0&&(p.equals("hub")||p.equals("track")||p.equals("lesson")))||i==1&&p.equals("favorites")||i==2&&p.equals("history"))?PURPLE:MUTED,true);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setOnClickListener(w->{if(n==0)hub();else if(n==1)favorites();else if(n==2)history();else finish();});nav.addView(v,new LinearLayout.LayoutParams(0,-2,1));}root.addView(nav);
 }
 private String name(String l){return l.equals("yue")?"粤语":l.equals("ja")?"日语":"普通话";}
 private String day(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
 private Set<String> doneToday(){return new HashSet<>(prefs.getStringSet("done_"+day(),Collections.emptySet()));}
 private Set<String> favoritesSet(){return new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));}
 private JSONObject find(String id){for(JSONObject l:lessons)if(l.optString("id").equals(id))return l;return null;}
 private int countDone(String code){int n=0;Set<String> completed=prefs.getStringSet("completed",Collections.emptySet());for(JSONObject l:lessons)if(l.optString("lang").equals(code)&&completed.contains(l.optString("id")))n++;return n;}
 private int countLessons(String code){int n=0;for(JSONObject l:lessons)if(l.optString("lang").equals(code))n++;return n;}
 private void hub(){
  practicePath=null;lesson=null;shell("hub","语言魔法岛","学习 × 对话 × 记忆 · 今天也有小魔法");
  LinearLayout hero=card();hero.setBackground(gradient(0xff463576,0xff285364,28));
  LinearLayout hr=new LinearLayout(this);LinearLayout words=col();add(words,text("YOUR LITTLE VOICE SPELL",9,0xffefe6ff,true),0);add(words,text("用声音\n打开魔法岛",28,WHITE,true),10);add(words,text("露娜陪你，从一小句开始",12,0xfff3ecff,false),12);hr.addView(words,new LinearLayout.LayoutParams(0,-2,1));Mascot mascot=new Mascot();hr.addView(mascot,new LinearLayout.LayoutParams(dp(112),dp(148)));add(hero,hr,0);
  JSONObject next=find(prefs.getString("last_lesson","yue_si3"));if(next==null)next=lessons.get(0);final JSONObject resume=next;
  add(hero,button("继续小冒险  ·  "+name(next.optString("lang")),false,()->openLesson(resume)),17);add(body,hero,0);
  LinearLayout daily=card();int done=doneToday().size();add(daily,text("✧ 今日星光  "+Math.min(3,done)+" / 3",17,INK,true),0);add(daily,text(done>=3?"今天的小目标完成啦。休息一下，或再玩一关。":"听一遍、跟一遍，就是一次小进步。",12,MUTED,false),6);ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(3);progress.setProgress(Math.min(3,done));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));add(daily,progress,8);add(body,daily,14);
  add(body,text("选一座喜欢的岛",22,INK,true),22);
  String[] codes={"yue","ja","zh"},titles={"粤语 · 星月森林","日语 · 樱花结界","普通话 · 云朵城堡"},sub={"六声调 → 入声 → 茶餐厅短句","元音 → 长音 / 促音 → 生活问候","四声 → 平翘舌 / n、l → 词句"};int[][] colors={{0xff3e315e,0xff2d284a},{0xff55324f,0xff332d50},{0xff25494d,0xff263046}};
  for(int i=0;i<3;i++){final String code=codes[i];LinearLayout c=card();c.setBackground(gradient(colors[i][0],colors[i][1],24));add(c,text(titles[i],20,INK,true),0);add(c,text(sub[i],12,MUTED,false),7);add(c,text(countLessons(code)+" 个小关卡  ·  已练 "+countDone(code)+"  →",12,PURPLE,true),13);c.setOnClickListener(v->track(code));c.setContentDescription(titles[i]+"，进入课程");add(body,c,12);}
  pair(body,"✦ 和露娜聊天",this::chooseDialogue,"◷ 今日复习",()->dailyReview(lang));add(body,button("唤醒本地老师 · 模型与语音",false,()->startActivity(new Intent(this,IslandModelActivity.class))),12);
  String lastScene=prefs.getString("last_scene_"+lang,"");if(!lastScene.isEmpty())add(body,button("☾ 继续上次聊天 · "+name(lang),false,()->launchDialogue(lastScene)),12);
  add(body,button("✧ 保存小岛 · 学习备份",false,()->startActivity(new Intent(this,StudioActivity.class).putExtra("open_portable",true))),12);
  add(body,button("☾ 记忆星图 · 查找对话 / 继续聊",false,()->startActivity(new Intent(this,IslandMemoryActivity.class).putExtra("language",lang))),12);
  add(body,button("纠音怎么练？ · 开源与能力说明",false,this::about),18);
 }
 private void track(String code){
  practicePath=null;lang=code;lesson=null;prefs.edit().putString("lang",lang).apply();shell("track",name(lang)+"的小冒险","听得清，再开口；每次只纠正一点");
  LinearLayout tabs=new LinearLayout(this);for(String c:new String[]{"yue","ja","zh"}){TextView v=button(name(c),c.equals(lang),()->track(c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);tabs.addView(v,p);}add(body,tabs,0);
  LinearLayout welcome=card();add(welcome,text(lang.equals("yue")?"先高中低，再练升调与入声":lang.equals("ja")?"一拍一拍，像小花轻轻开": "单音 → 对照词 → 短句",19,INK,true),0);add(welcome,text(lang.equals("yue")?"中音是起点。先练試 si3，再比较詩 si1、事 si6。":lang.equals("ja")?"先把元音读清楚；长音、促音都有自己的那一拍。":"每关都有舌位和口型提示。轻松慢读，不需要高速绕口令。",13,MUTED,false),8);add(body,welcome,16);
  pair(body,"✦ 场景对话",this::chooseDialogue,"◷ 今日复习",()->dailyReview(lang));
  LinkedHashSet<String> stages=new LinkedHashSet<>();for(JSONObject item:lessons)if(item.optString("lang").equals(lang))stages.add(item.optString("stage"));if(!stages.contains(selectedStage))selectedStage=stages.iterator().next();Spinner stagePicker=new Spinner(this);ArrayAdapter<String> choices=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(stages));stagePicker.setAdapter(choices);stagePicker.setBackgroundColor(0xffc5b5e8);stagePicker.setSelection(new ArrayList<>(stages).indexOf(selectedStage));add(body,stagePicker,15);final String displayed=selectedStage;stagePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View v,int n,long id){String chosen=new ArrayList<>(stages).get(n);if(!chosen.equals(displayed)){selectedStage=chosen;track(lang);}}});
  String stage="";Set<String> completed=prefs.getStringSet("completed",Collections.emptySet());
  for(JSONObject l:lessons){if(!l.optString("lang").equals(lang)||!l.optString("stage").equals(selectedStage))continue;if(!l.optString("stage").equals(stage)){stage=l.optString("stage");add(body,text(stage,17,INK,true),23);}LinearLayout c=card();add(c,text((completed.contains(l.optString("id"))?"✦ ":"✧ ")+l.optString("title"),18,INK,true),0);add(c,text(l.optString("text")+"   "+l.optString("reading"),14,PURPLE,true),7);add(c,text("示范 · 跟读 · 纠音辅助  →",11,MUTED,false),8);c.setOnClickListener(v->openLesson(l));add(body,c,10);}
 }
 private void openLesson(JSONObject l){
  if(practicePath!=null&&!practicePath.current().equals(l.optString("id")))practicePath=null;lesson=l;lang=l.optString("lang");prefs.edit().putString("lang",lang).putString("last_lesson",l.optString("id")).apply();
  shell("lesson",name(lang)+" · 小小练声室",l.optString("stage"));reference=null;take=null;referenceFile=null;takeFile=null;working=true;middleHz=0;final int token=epoch;
  if(practicePath!=null){LinearLayout pathCard=card();add(pathCard,text("✧ 今日小冒险 · 第 "+(practicePath.position()+1)+" / "+practicePath.size()+" 关",17,PURPLE,true),0);add(pathCard,text("听一遍 → 跟一遍 → 选掌握程度，露娜会带你去下一关。",12,MUTED,false),7);ProgressBar pathProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pathProgress.setMax(practicePath.size());pathProgress.setProgress(practicePath.position());pathProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));add(pathCard,pathProgress,8);add(body,pathCard,0);}
  LinearLayout spell=card();spell.setBackground(gradient(0xff302b50,0xff1e3142,24));add(spell,text(l.optString("title"),13,MUTED,true),0);TextView original=text(l.optString("text"),34,INK,true);original.setTextIsSelectable(true);add(spell,original,13);add(spell,text(l.optString("reading"),18,PURPLE,true),7);add(spell,text(l.optString("meaning"),13,MUTED,false),7);add(body,spell,0);
  LinearLayout controls=card();add(controls,text("① 先听小精灵示范",16,INK,true),0);pair(controls,"▶ 听示范",()->playReference(1),"慢一点 · 0.8×",()->playReference(.8f));JSONObject contrast=find(l.optString("pair"));if(contrast!=null)add(controls,button("对照听 · "+contrast.optString("text")+" "+contrast.optString("reading"),false,()->playOther(contrast)),10);playStatus=text("离线示范准备中…",11,MUTED,false);add(controls,playStatus,9);add(body,controls,13);
  LinearLayout tip=card();add(tip,text("② 这次只注意一件事",16,INK,true),0);add(tip,text(l.optString("tip"),14,INK,false),10);add(body,tip,13);
  LinearLayout practice=card();add(practice,text("③ 跟读，让曲线发光",16,INK,true),0);graph=new PitchView();graph.setContentDescription("示范与跟读的相对音高曲线");practice.addView(graph,new LinearLayout.LayoutParams(-1,dp(166)));add(practice,text("紫色：示范  ·  薄荷色：你\n曲线看相对走势；平调高低需中音校准。",11,MUTED,false),4);
  recordButton=button("● 点一下开始跟读",true,this::recordClick);LinearLayout recordBar=col();recordBar.setPadding(dp(18),dp(8),dp(18),dp(8));recordBar.setBackgroundColor(0xff151b32);recordBar.addView(recordButton,new LinearLayout.LayoutParams(-1,-2));root.addView(recordBar,root.getChildCount()-1);
  meter=text("再点一次结束 · 最长 12 秒 · 不会自动抢话",11,MUTED,false);add(practice,meter,8);if(lang.equals("yue"))add(practice,button("校准我的中音",false,this::calibrate),10);add(body,practice,13);
  LinearLayout segments=card();segments.setBackground(gradient(0xff303256,0xff1f3d45,24));add(segments,text("④ 纠音魔法镜 · 一段一段练",18,INK,true),0);
  segmentsStatus=text("完整跟读后会自动切分音节 / 拍子。点一段对照听，再只练这一段。\n这次先改一个动作，不急着追求分数。",13,MUTED,false);add(segments,segmentsStatus,9);
  add(segments,button("✧ 分析最近的完整跟读",false,this::analyseSegments),12);segmentsCancel=button("取消逐段分析 · 录音仍保留",false,()->cancelSegments("已取消逐段分析；可以继续听示范和练习。"));segmentsCancel.setVisibility(View.GONE);add(segments,segmentsCancel,8);
  segmentRows=col();add(segments,segmentRows,8);add(segments,button("回到完整跟读",false,this::resetFocus),10);add(segments,text("本地音素对齐与音高 / 节奏辅助。不能自动诊断全部声母、韵母或元音；切分也可能有误。",11,MUTED,false),10);add(body,segments,13);
  LinearLayout feedback=card();feedbackTitle=text("小精灵的鼓励",17,INK,true);feedbackDetail=text("跟读后，这里会给你音高和节奏反馈。先听一遍，不急着开口。",13,MUTED,false);add(feedback,feedbackTitle,0);add(feedback,feedbackDetail,9);pair(feedback,"听我的声音",this::playTake,"示范 / 我 · 对照",this::comparePlayback);add(feedback,button("回到完整跟读",false,this::resetFocus),8);starLabel=text("",12,PURPLE,true);add(feedback,starLabel,8);if(practicePath!=null)add(feedback,button("我练完啦 · 选掌握程度并继续",true,()->rateLesson(l)),12);add(body,feedback,13);
  LinearLayout words=card();add(words,text("字样核对 · 小精灵听到了什么？",17,INK,true),0);checkStatus=text(TranscriptCheck.suitable(l.optString("text"))?"完整跟读后，点下面用本地 Whisper 核对字样。不会自动开启麦克风，也不影响星光或自评。":"这关是单音或很短的字词，Whisper 不适合可靠核对。请用示范 / 我对照听，注意上方的口型、声调和拍子提示。",13,MUTED,false);checkStatus.setTextIsSelectable(true);add(words,checkStatus,9);if(TranscriptCheck.suitable(l.optString("text")))add(words,button("✧ 核对刚才的跟读 · 本地 Whisper",false,this::checkTake),12);checkCancel=button("取消核对 · 继续练习",false,()->cancelCheck("已取消核对，录音仍保留，可以继续练习。"));checkCancel.setVisibility(View.GONE);add(words,checkCancel,10);add(body,words,13);
  pair(body,"♡ 收藏这一关",()->toggleFavorite(l),"玩个听辨小测",this::quiz);
  if(practicePath==null)add(body,button("给这关一个掌握程度 · 加入复习",false,()->rateLesson(l)),14);add(body,button("把这句用起来 · 和露娜聊天",false,()->launchDialogue(l.optString("scenario",l.optString("meaning"))+"，练习用上："+l.optString("text"))),10);
  JSONObject next=practicePath==null?nextLesson(l):null;if(next!=null)add(body,button("下一小关  →  "+next.optString("title"),true,()->openLesson(next)),14);add(body,button("回课程地图",false,()->track(lang)),10);
  worker.execute(()->{
   try{
    File ref=assetFile(l.optString("audio"));PronunciationCoach.Track rt=PronunciationCoach.extract(LocalWav.read(ref));
    double mid=0;if(l.optString("lang").equals("yue"))mid=PronunciationCoach.extract(LocalWav.read(assetFile("yue_si3.wav"))).medianHz;
    File saved=MagicBackup.take(this,l.optString("id"));PronunciationCoach.Track savedTrack=null;try{if(saved.isFile())savedTrack=PronunciationCoach.extract(LocalWav.read(saved));}catch(IOException ignored){}final PronunciationCoach.Track st=savedTrack;final double mh=mid;
    ui.post(()->{if(!valid(token))return;referenceFile=ref;reference=rt;middleHz=mh;working=false;playStatus.setText("内置示范 · 无需联网");if(st!=null){take=st;takeFile=saved;showFeedback(l,false);}graph.setTracks(reference,take);});
   }catch(Exception e){ui.post(()->{if(!valid(token))return;working=false;playStatus.setText("示范未能打开，请重新进入这一关");});}
  });
 }

 private void analyseSegments(){
  if(working||recording||recorder.busy()||checking!=null||aligning!=null){toast("先完成或取消当前练习");return;}
  if(takeFile==null||referenceFile==null){if(segmentsStatus!=null)segmentsStatus.setText("先完整跟读一次，小精灵才能对照这次的声音。");return;}
  if(Build.VERSION.SDK_INT<24){segmentsStatus.setText("逐段模型需要 Android 7.0 或更新版本。仍可听示范、录音回放和按口型提示练习。");return;}
  stopPlayer();resetFocus();clearClips();units=Collections.emptyList();clips=Collections.emptyList();segmentRows.removeAllViews();
  final int token=epoch;final JSONObject current=lesson;final File own=takeFile,ref=referenceFile;final LocalTifa.Control control=new LocalTifa.Control();aligning=control;
  segmentsStatus.setText("小精灵正在逐段听 · 首次会准备内置模型，不需联网…");segmentsCancel.setVisibility(View.VISIBLE);final long start=SystemClock.elapsedRealtime();watchSegments(control,token,start);
  alignmentWorker.execute(()->{File dir=null;try{
   JSONObject template=TifaAssets.json(this,"segments.json").getJSONObject(current.optString("id"));JSONArray ids=template.getJSONArray("tokens"),gs=template.getJSONArray("groups"),ls=template.getJSONArray("labels");
   long[] phones=new long[ids.length()];int[] groups=new int[phones.length];String[] labels=new String[ls.length()];for(int i=0;i<phones.length;i++){phones[i]=ids.getLong(i);groups[i]=gs.getInt(i);}for(int i=0;i<labels.length;i++)labels[i]=ls.getString(i);
   JSONArray rs=TifaAssets.json(this,"reference-segments.json").getJSONObject(current.optString("id")).getJSONArray("spans");int[][] referenceSpans=new int[rs.length()][2];for(int i=0;i<rs.length();i++){referenceSpans[i][0]=rs.getJSONArray(i).getInt(0);referenceSpans[i][1]=rs.getJSONArray(i).getInt(1);}
   float[] pcm=LocalWav.read(own),rpcm=LocalWav.read(ref);if(!PronunciationCoach.extract(pcm).usable())throw new IOException("录音太轻、爆音或可用音高太少，请在安静处再完整读一次");control.check();File spec=TifaAssets.model(this,"spectrogram.onnx",control),model=TifaAssets.model(this,"model.onnx",control);int[][] spans;
   try(LocalTifa tifa=new LocalTifa(spec,model,control)){spans=tifa.align(pcm,phones,groups,control);}control.check();
   List<SegmentCoach.Unit> result=SegmentCoach.compare(current.optString("lang"),labels,groups,referenceSpans,spans,rpcm,pcm);List<File[]> audioClips=new ArrayList<>();dir=new File(getCacheDir(),"tifa-clips-"+UUID.randomUUID());if(!dir.mkdirs())throw new IOException("片段目录不可用");
   for(int i=0;i<result.size();i++){control.check();SegmentCoach.Unit u=result.get(i);File r=new File(dir,i+"-ref.wav"),t=new File(dir,i+"-take.wav");if(u.available){LessonAudio.write(r,TifaDecoder.crop(rpcm,u.refStart,u.refEnd,800));LessonAudio.write(t,TifaDecoder.crop(pcm,u.start,u.end,800));}audioClips.add(new File[]{r,t});}
   control.complete();final File prepared=dir;
   ui.post(()->{if(!valid(token)||aligning!=control||control.cancelled()){deleteClips(prepared);return;}aligning=null;segmentsCancel.setVisibility(View.GONE);clipDir=prepared;units=result;clips=audioClips;renderSegments();});
  }catch(Throwable e){deleteClips(dir);ui.post(()->{if(!valid(token)||aligning!=control)return;aligning=null;segmentsCancel.setVisibility(View.GONE);segmentsStatus.setText("这次未能切分："+(e.getMessage()==null?"请稍后再试":e.getMessage())+"\n完整录音仍保留；可以先对照听，不会扣星光。");});}});
 }
 private void watchSegments(LocalTifa.Control c,int token,long start){ui.postDelayed(()->{if(!valid(token)||aligning!=c||c.cancelled()||c.completed())return;long elapsed=SystemClock.elapsedRealtime()-start;if(elapsed>=120000){cancelSegments("逐段分析等待超时，已取消。录音保留，可继续对照听。");return;}segmentsStatus.setText("正在逐段分析 · 已等待 "+elapsed/1000+" 秒\n可以取消，或直接录下一遍。");watchSegments(c,token,start);},1000);}
 private void cancelSegments(String text){LocalTifa.Control c=aligning;aligning=null;if(c!=null)c.cancel();if(segmentsCancel!=null)segmentsCancel.setVisibility(View.GONE);if(c!=null&&segmentsStatus!=null)segmentsStatus.setText(text);}
 private void renderSegments(){
  segmentRows.removeAllViews();segmentsStatus.setText("切出了 "+units.size()+" 个练习片段。先选一个，听差异，再练一个动作。\n切分按课程的预期发音进行，不表示这些音都读对了。");scroll.smoothScrollTo(0,((View)segmentsStatus.getParent()).getTop());
  for(int i=0;i<units.size();i++){final int index=i;SegmentCoach.Unit u=units.get(i);LinearLayout c=card();add(c,text((i+1)+" · "+u.label,17,PURPLE,true),0);add(c,text(u.observation,12,MUTED,false),7);add(c,text(ArticulationHints.forUnit(lang,u.label,lesson.optString("tip")),13,INK,false),9);
   if(u.available){pair(c,"听这段示范",()->playUnit(index,false),"听我这一段",()->playUnit(index,true));add(c,button("示范 / 我 · 这一段对照",false,()->compareUnit(index)),8);add(c,button("● 只练 "+u.label,true,()->focusUnit(index)),8);}add(segmentRows,c,10);
  }
 }
 private void playUnit(int i,boolean own){if(i>=clips.size())return;play(clips.get(i)[own?1:0],1,null);}
 private void compareUnit(int i){if(i>=clips.size())return;File[] files=clips.get(i);play(files[0],1,()->play(files[1],1,null));}
 private void focusUnit(int i){if(working||recording||checking!=null||aligning!=null){toast("先完成当前练习");return;}if(i>=units.size()||!units.get(i).available)return;focusIndex=i;SegmentCoach.Unit u=units.get(i);graph.setTracks(u.reference,null);recordButton.setText("● 只跟读 "+u.label);feedbackTitle.setText("这一遍，只练 "+u.label);feedbackDetail.setText(ArticulationHints.forUnit(lang,u.label,lesson.optString("tip"))+"\n点底部录音按钮开始，再点一下结束；完整跟读录音仍保留。");meter.setText("单段专练 · 回到完整跟读可退出");playUnit(i,false);}
 private void resetFocus(){if(working||recording){return;}stopPlayer();focusIndex=-1;if(graph!=null)graph.setTracks(reference,take);if(recordButton!=null)recordButton.setText("● 跟读完整这一关");if(meter!=null)meter.setText("完整跟读 · 再点一下结束 · 最长 12 秒");if(lesson!=null&&take!=null&&feedbackTitle!=null)showFeedback(lesson,false);}
 private static void deleteClips(File dir){if(dir==null)return;File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();dir.delete();}
 private void clearClips(){deleteClips(clipDir);clipDir=null;if(focusTake!=null){focusTake.delete();focusTake=null;}}

 private JSONObject nextLesson(JSONObject current){for(int i=0;i<lessons.size()-1;i++)if(lessons.get(i)==current&&lessons.get(i+1).optString("lang").equals(current.optString("lang")))return lessons.get(i+1);return null;}
 private boolean valid(int token){return token==epoch&&foreground&&!isFinishing();}
 private File assetFile(String name)throws IOException{
  if(!name.matches("[a-z0-9_]+\\.wav"))throw new IOException("示范路径无效");File dir=new File(getCacheDir(),"language-references-v41");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("示范目录不可用");File f=new File(dir,name);
  if(f.isFile()&&f.length()>44)return f;File tmp=new File(dir,name+".part");
  try(InputStream in=getAssets().open("languages/"+name);OutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(!tmp.renameTo(f))throw new IOException("示范无法打开");return f;
 }
 private void recordClick(){
  if(recording){recorder.finish();recordButton.setText("正在分析你的声音…");recordButton.setEnabled(false);return;}
  if(working||recorder.busy()){toast("稍等片刻，声音正在准备");return;}if(reference==null){toast("先让示范准备好，再开始");return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},80);return;}
  beginTake(false);
 }
 private void beginTake(boolean calibration){
  if(checking!=null){toast("先取消或等本次文字核对完成");return;}
  if(!foreground||lesson==null||recordButton==null||working||recorder.busy())return;cancelSegments("录音结束后，会重新分析完整跟读。");stopPlayer();if(audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){toast("麦克风声音被其他应用占用，稍后再试");return;}epoch++;if(!calibration&&checkStatus!=null)checkStatus.setText("正在录新一遍；结束后可重新核对字样。");recording=true;working=true;recordButton.setText("正在打开麦克风…");final int token=epoch;final JSONObject current=lesson;final int recordedFocus=focusIndex;final SegmentCoach.Unit focused=recordedFocus>=0&&recordedFocus<units.size()?units.get(recordedFocus):null;
  boolean started=recorder.start(new LessonRecorder.Listener(){
   public void ready(){ui.post(()->{if(!valid(token))return;recordButton.setText("■ 点一下结束");meter.setText(calibration?"舒服地读“啊”一秒，不用升高或压低":focused!=null?"只读 "+focused.label+"，再点一下结束":"开始啦，按自己的节奏完整读一遍");});}
   public void meter(double rms,int millis){ui.post(()->{if(!valid(token))return;meter.setText(String.format(Locale.CHINA,"已录 %.1f 秒 · %s",millis/1000.0,rms<.003?"声音有点轻":"小精灵正在听"));});}
   public void done(float[] pcm){ui.post(()->{if(!valid(token))return;recording=false;recordButton.setText("正在分析你的声音…");recordButton.setEnabled(false);});worker.execute(()->{
    try{
     PronunciationCoach.Track got=PronunciationCoach.extract(pcm);File f=null;if(!calibration&&got.usable()){f=focused==null?MagicBackup.take(LanguageActivity.this,current.optString("id")):new File(getCacheDir(),"focused-take-"+token+".wav");LessonAudio.write(f,pcm);}final File saved=f;
     ui.post(()->{if(!valid(token))return;working=false;recordButton.setEnabled(true);recordButton.setText("● 再跟读一次");meter.setText("录音已结束 · 你的声音只保存在本机");
      if(calibration){if(got.usable()&&PronunciationCoach.spread(got.pitch)<3){prefs.edit().putFloat("middle_hz",(float)got.medianHz).apply();feedbackTitle.setText("中音校准好了");feedbackDetail.setText("记住这个舒服的中音。接下来比较高平、中平、低平，不要用力喊。\n校准只是粗略位置辅助，不是精确声调评分。");}else{feedbackTitle.setText("校准再来一次");feedbackDetail.setText("平稳读“啊”一秒，别刻意抬高或压低。声音要清楚，不用喊。");}return;}
      if(focused!=null){if(focusTake!=null)focusTake.delete();focusTake=saved;graph.setTracks(focused.reference,got);feedbackTitle.setText("这一遍，只练 "+focused.label);feedbackDetail.setText(SegmentCoach.focused(lang,focused.reference,got,ArticulationHints.forUnit(lang,focused.label,current.optString("tip"))));recordButton.setText("● 再练 "+focused.label);scroll.smoothScrollTo(0,((View)feedbackTitle.getParent()).getTop());starLabel.setText("单段专练不覆盖完整录音，也不会重复计入星光");return;}
      take=got;takeFile=saved;focusIndex=-1;units=Collections.emptyList();clips=Collections.emptyList();if(segmentRows!=null)segmentRows.removeAllViews();graph.setTracks(reference,take);showFeedback(current,true);if(saved!=null)analyseSegments();
     });
    }catch(Exception e){ui.post(()->{if(valid(token))recordError("这次声音未能保存，请再试一次");});}
   });}
   public void error(String reason){ui.post(()->{if(valid(token))recordError(reason);});}
  });if(!started)recordError("麦克风还在结束上一段录音，稍等再试");
 }
 private void recordError(String reason){recording=false;working=false;recordButton.setEnabled(true);recordButton.setText("● 再试一次");meter.setText(reason);feedbackTitle.setText("慢慢来，我们再试一次");feedbackDetail.setText(reason);}
 private void checkTake(){
  if(working||recording||recorder.busy()||checking!=null||aligning!=null){toast("先完成或取消当前练习 / 逐段分析");return;}if(takeFile==null||!takeFile.isFile()){toast("先完整跟读并保存一段录音");return;}if(!LocalAI.supported()){toast("本地 Whisper 需要 64 位 ARM 安卓手机");return;}
  stopPlayer();final int token=epoch;final JSONObject current=lesson;final File file=takeFile;final WorkBudget budget=new WorkBudget(SystemClock.elapsedRealtime(),WorkBudget.WARM_MS+WorkBudget.ASR_MS,0);
  CloudClient.Job job=new CloudClient.Job(){@Override public void check(){super.check();if(!budget.completed()&&budget.expired(SystemClock.elapsedRealtime()))throw new CancellationException("核对等待超时");}};
  checking=job;checkCancel.setVisibility(View.VISIBLE);checkStatus.setText("准备本地 Whisper，核对刚才的录音…");watchCheck(job,budget,token);
  checkWorker.execute(()->{try{
   job.check();float[] pcm=LocalWav.read(file);job.check();String heard=IslandPrompt.clean(LocalAI.islandTranscribe(getApplicationContext(),pcm,current.optString("lang"),job),1000);job.check();TranscriptCheck.Result result=TranscriptCheck.compare(current.optString("text"),heard);budget.complete();
   ui.post(()->{if(!valid(token)||checking!=job||job.cancelled())return;checking=null;checkCancel.setVisibility(View.GONE);checkStatus.setText("课程："+current.optString("text")+"\nWhisper 听到："+heard+"\n\n"+result.message(current.optString("lang"))+"\n\n这关只注意："+current.optString("tip"));});
  }catch(Throwable e){ui.post(()->{if(!valid(token)||checking!=job||job.cancelled())return;checking=null;checkCancel.setVisibility(View.GONE);checkStatus.setText("这次未能核对："+(e.getMessage()==null?"请稍后重试":e.getMessage())+"\n录音仍保留，可以对照听或重试。识别失败不会扣星光。");});}});
 }
 private void watchCheck(CloudClient.Job job,WorkBudget budget,int token){ui.postDelayed(()->{if(!valid(token)||checking!=job||job.cancelled()||budget.completed())return;if(budget.expired(SystemClock.elapsedRealtime())){cancelCheck("核对等待超时，已取消。录音仍保留，可以稍后重试或对照听。");return;}checkStatus.setText("Whisper 正在核对 · 已等待 "+budget.seconds(SystemClock.elapsedRealtime())+" 秒\n可以取消；课程和跟读录音仍保留。");watchCheck(job,budget,token);},1000);}
 private void cancelCheck(String reason){CloudClient.Job job=checking;checking=null;if(job!=null)job.cancel();if(checkCancel!=null)checkCancel.setVisibility(View.GONE);if(checkStatus!=null)checkStatus.setText(reason);}
 private void showFeedback(JSONObject current,boolean save){
  PronunciationCoach.Feedback f=PronunciationCoach.compare(reference,take,current.optString("focus"),prefs.getFloat("middle_hz",0),middleHz);
  feedbackTitle.setText(f.title);feedbackDetail.setText(f.detail+"\n\n这关的动作："+current.optString("tip"));
  if(save)feedbackTitle.post(()->{if(foreground&&lesson==current&&feedbackTitle!=null&&scroll!=null)scroll.smoothScrollTo(0,((View)feedbackTitle.getParent()).getTop());});
  if(save&&f.valid){String id=current.optString("id");Set<String> completed=new HashSet<>(prefs.getStringSet("completed",Collections.emptySet()));completed.add(id);Set<String> today=doneToday();boolean fresh=today.add(id);SharedPreferences.Editor e=prefs.edit().putStringSet("completed",completed).putStringSet("done_"+day(),today);if(fresh)e.putInt("stars",prefs.getInt("stars",0)+3);e.apply();starLabel.setText(fresh?"✦ +3 星光 · 开口练习的奖励，不是准确率":"✦ 这一关今天已经点亮，可以放心再练");saveHistory(current,f);final TextView animated=feedbackTitle;animated.animate().scaleX(1.03f).scaleY(1.03f).setDuration(160).withEndAction(()->{if(animated.isAttachedToWindow())animated.animate().scaleX(1).scaleY(1).setDuration(160).start();}).start();}else if(!f.valid)starLabel.setText("这次没有计入学习星光，听清楚以后再试");
 }
 private void saveHistory(JSONObject l,PronunciationCoach.Feedback f){try{JSONArray old=new JSONArray(prefs.getString("history","[]")),out=new JSONArray();out.put(new JSONObject().put("id",l.optString("id")).put("time",System.currentTimeMillis()).put("shape",f.shape).put("title",f.title));for(int i=0;i<Math.min(29,old.length());i++)out.put(old.get(i));prefs.edit().putString("history",out.toString()).apply();}catch(Exception ignored){}}
 private void calibrate(){resetFocus();if(working||recording||recorder.busy()){toast("先结束这次跟读");return;}new AlertDialog.Builder(this).setTitle("找一个舒服的中音").setMessage("用平常说话的音高，平稳读“啊”约一秒。不刻意升高或压低。\n这能辅助比较粤语一、三、六声的相对位置。").setNegativeButton("先不校准",null).setPositiveButton("开始校准",(d,n)->{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){toast("先点跟读允许麦克风，再来校准");requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},81);}else beginTake(true);}).show();}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==80||request==81){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&foreground&&lesson!=null){if(request==81)beginTake(true);else recordClick();}else toast("允许麦克风后，就可以跟读和纠音；现在仍能听示范");}}
 private void playReference(float speed){if(referenceFile==null){toast("示范正在准备");return;}play(referenceFile,speed,null);}
 private void playOther(JSONObject other){if(working||recording){toast("先结束这次跟读");return;}final int token=epoch;worker.execute(()->{try{File f=assetFile(other.optString("audio"));ui.post(()->{if(valid(token))play(f,1,null);});}catch(Exception e){ui.post(()->{if(valid(token))toast("对照示范未能打开");});}});}
 private void playTake(){if(focusIndex>=0&&focusTake!=null){play(focusTake,1,null);return;}if(takeFile==null){toast("先录一遍自己的声音");return;}play(takeFile,1,null);}
 private void comparePlayback(){if(focusIndex>=0&&focusTake!=null&&focusIndex<clips.size()){final File own=focusTake;play(clips.get(focusIndex)[0],1,()->play(own,1,null));return;}if(referenceFile==null||takeFile==null){toast("先完成一次跟读");return;}final File own=takeFile;play(referenceFile,1,()->play(own,1,null));}
 private void play(File file,float speed,Runnable next){
  if(recording||working){toast("先完成这次跟读");return;}stopPlayer();int token=epoch;
  if(audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){toast("声音被其他应用占用，稍后再听");return;}
  try{MediaPlayer p=new MediaPlayer();player=p;p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());p.setDataSource(file.getAbsolutePath());p.setOnPreparedListener(m->{if(!valid(token)||player!=m){return;}try{m.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1));m.start();if(playStatus!=null)playStatus.setText(speed==1?"正在播放 · 录音按钮可随时开始跟读":"慢速播放 · 保持原来的音高");}catch(Exception e){stopPlayer();toast("播放未能开始，请再点一次");}});p.setOnCompletionListener(m->{if(player!=m)return;stopPlayer();if(valid(token)){if(playStatus!=null)playStatus.setText("听完啦，可以开口试试");if(next!=null)next.run();}});p.setOnErrorListener((m,a,b)->{if(player==m){stopPlayer();if(valid(token))toast("声音未能播放，请再点一次");}return true;});p.prepareAsync();}catch(Exception e){stopPlayer();toast("声音未能打开，请再点一次");}
 }
 private void stopPlayer(){MediaPlayer p=player;player=null;if(p!=null){try{p.stop();}catch(Exception ignored){}p.release();}if(audio!=null)audio.abandonAudioFocus(focus);}
 private void toggleFavorite(JSONObject l){Set<String> f=favoritesSet();String id=l.optString("id");boolean had=f.remove(id);if(!had)f.add(id);prefs.edit().putStringSet("favorites",f).apply();toast(had?"已从收藏取下":"♡ 收进魔法口袋啦");}
 private void favorites(){lesson=null;shell("favorites","魔法口袋","喜欢的声音，随时回来练");Set<String> f=favoritesSet();boolean any=false;for(JSONObject l:lessons)if(f.contains(l.optString("id"))){any=true;LinearLayout c=card();add(c,text(name(l.optString("lang"))+" · "+l.optString("title"),18,INK,true),0);add(c,text(l.optString("text")+"  "+l.optString("reading"),13,PURPLE,false),8);c.setOnClickListener(v->openLesson(l));add(body,c,12);}if(!any){add(body,text("口袋还空着 ♡",24,INK,true),25);add(body,text("在跟读页点“收藏这一关”，喜欢的练习就会住在这里。",14,MUTED,false),12);add(body,button("去选一关",true,this::hub),22);}}
 private void history(){lesson=null;shell("history","星光足迹","每一次开口，都有一点点进步");LinearLayout summary=card();add(summary,text("✦ "+prefs.getInt("stars",0)+" 颗学习星光",23,PURPLE,true),0);add(summary,text("星光奖励认真练习；不是发音准确率。\n保留最近 30 次足迹，每关保留最近一段录音。",12,MUTED,false),8);add(body,summary,0);try{JSONArray history=new JSONArray(prefs.getString("history","[]"));if(history.length()==0)add(body,text("还没有足迹。今天从一句开始吧。",14,MUTED,false),20);for(int i=0;i<history.length();i++){JSONObject h=history.getJSONObject(i),l=find(h.optString("id"));if(l==null)continue;LinearLayout c=card();add(c,text(name(l.optString("lang"))+" · "+l.optString("title"),16,INK,true),0);add(c,text(new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(h.optLong("time")))+"  ·  "+h.optString("title"),12,MUTED,false),8);add(c,text("再练 / 听本关最近录音  →",12,PURPLE,true),9);c.setOnClickListener(v->openLesson(l));add(body,c,12);}}catch(Exception ignored){}add(body,button("开口练一小关",true,this::hub),18);}
 private void quiz(){
  if(lesson==null||recording||working){toast("先完成这次跟读");return;}JSONObject current=lesson;JSONObject contrast=find(current.optString("pair"));
  if(contrast!=null){boolean chooseOther=(System.nanoTime()&1)==0;JSONObject heard=chooseOther?contrast:current;final int token=epoch;worker.execute(()->{try{File f=assetFile(heard.optString("audio"));ui.post(()->{if(valid(token))play(f,1,()->listeningQuestion(current,contrast,heard));});}catch(Exception e){ui.post(()->{if(valid(token))toast("示范未能打开，请再试一次");});}});return;}
  new AlertDialog.Builder(this).setTitle("先听，再选一选").setMessage("听完这段示范，做一个轻松的小测。没有倒计时。").setNegativeButton("先继续跟读",null).setPositiveButton("听一遍",(d,n)->play(referenceFile,1,()->question(current))).show();
 }
 private void listeningQuestion(JSONObject current,JSONObject other,JSONObject heard){if(lesson!=current||!foreground)return;JSONObject[] options={current,other};new AlertDialog.Builder(this).setTitle("刚才听到哪一个声音？").setItems(new String[]{current.optString("text")+" · "+current.optString("reading"),other.optString("text")+" · "+other.optString("reading")},(d,n)->{boolean right=options[n]==heard;new AlertDialog.Builder(this).setTitle(right?"✦ 耳朵捕捉到小魔法啦":"再听一遍，就会更熟悉").setMessage("刚才是："+heard.optString("text")+" · "+heard.optString("reading")+"\n"+heard.optString("tip")).setPositiveButton("再听示范",(a,b)->playOther(heard)).setNegativeButton("继续跟读",null).show();}).setNegativeButton("再听一次",(d,n)->playOther(heard)).show();}
 private void question(JSONObject l){if(lesson!=l||!foreground)return;boolean reverse=(System.nanoTime()&1)==0;String a=l.optString("answer"),b=l.optString("wrong");new AlertDialog.Builder(this).setTitle(l.optString("question")).setItems(reverse?new String[]{b,a}:new String[]{a,b},(d,n)->{boolean right=reverse?n==1:n==0;new AlertDialog.Builder(this).setTitle(right?"✦ 记住这个小诀窍啦":"没关系，再记一个小诀窍").setMessage(a+"。\n"+l.optString("tip")).setPositiveButton("再跟读一遍",(x,y)->{}).show();}).setNegativeButton("回跟读页",null).show();}

 private void dailyReview(String code){practicePath=null;lang=code;List<JSONObject> queue=review.queue(lessons,lang);shell("review",name(lang)+" · 今日复习","先温习到期的声音，再认识三个新朋友");if(queue.isEmpty()){add(body,text("今天没有到期的复习啦 ✦",20,INK,true),16);add(body,button("挑一关自由练习",true,()->track(lang)),16);return;}add(body,text(queue.size()+" 个小魔法等待点亮",18,INK,true),10);add(body,button("✧ 开始三关小冒险 · 先复习再学新",true,()->startPractice(queue)),14);for(JSONObject l:queue){LinearLayout c=card();add(c,text(l.optString("text")+" · "+l.optString("reading"),18,INK,true),0);add(c,text(review.seen(l.optString("id"))?"到期复习 · 听一遍，再跟读":"新朋友 · 今天最多加入三关",12,MUTED,false),6);c.setOnClickListener(v->openLesson(l));add(body,c,12);}}
 private void rateLesson(JSONObject l){if(working||recording||recorder.busy()){toast("先结束这次跟读，再安排复习");return;}String[] names={"再练一次","有点难","我会了","很轻松"};String[] labels=new String[4];for(int i=0;i<4;i++)labels[i]=names[i]+" · "+IslandSrs.schedule(review.card(l.optString("id")),i,System.currentTimeMillis()).interval+" 天后复习";new AlertDialog.Builder(this).setTitle("这次你感觉怎么样？").setItems(labels,(d,n)->{review.rate(l.optString("id"),n);if(practicePath!=null&&practicePath.rate(l.optString("id"))){if(practicePath.finished())practiceComplete();else{JSONObject next=find(practicePath.current());if(next!=null)openLesson(next);else{practicePath=null;track(lang);}}}else toast("已经安排好下次复习 ✦");}).setNegativeButton("再听一次",(d,n)->playReference(1)).show();}
 private void startPractice(List<JSONObject> queue){
  List<String> ids=new ArrayList<>();Set<String> sounds=new HashSet<>();for(JSONObject l:queue){String same=l.optString("lang")+"|"+l.optString("text")+"|"+l.optString("reading");if(sounds.add(same))ids.add(l.optString("id"));if(ids.size()==3)break;}
  practicePath=new PracticePath(ids,0);if(!practicePath.finished()){JSONObject first=find(practicePath.current());if(first!=null)openLesson(first);}
 }
 private void practiceComplete(){
  lesson=null;shell("celebrate","✦ 小冒险完成啦","按自己的节奏，声音就会越来越熟悉");LinearLayout celebration=card();celebration.setBackground(gradient(0xff463576,0xff285364,28));Mascot mascot=new Mascot();celebration.addView(mascot,new LinearLayout.LayoutParams(dp(140),dp(160)));add(celebration,text("你温习了 "+practicePath.size()+" 个小魔法 ✦",24,INK,true),10);add(celebration,text("每关的下次复习已按你的自评安排。\n开口练习的星光另计；自评不是发音准确率。",14,MUTED,false),12);add(body,celebration,16);add(body,button("把小魔法用起来 · 和露娜聊聊",true,()->{practicePath=null;chooseDialogue();}),18);add(body,button("再挑几关轻松练",false,()->dailyReview(lang)),12);add(body,button("回魔法岛休息一下",false,()->{practicePath=null;hub();}),12);
 }
 private void chooseDialogue(){String[] scenes=lang.equals("yue")?new String[]{"茶餐厅点餐","打招呼","问路","购物","自我介绍","自由聊天"}:lang.equals("ja")?new String[]{"咖啡店点餐","初次见面","问路","旅行住宿","练习长音与促音","自由聊天"}:new String[]{"咖啡店点餐","自我介绍","日常聊天","练习平翘舌","练习 n / l","自由聊天"};new AlertDialog.Builder(this).setTitle(name(lang)+" · 今天玩哪个场景？").setItems(scenes,(d,n)->launchDialogue(scenes[n])).setNegativeButton("返回",null).show();}
 private void launchDialogue(String scene){stopPlayer();cancelRecording();String code=lang;String key="scene_"+code+"_"+scene;StudioStore store=new StudioStore(this);long id=prefs.getLong(key,0);JSONObject existing=store.session(id);if(existing==null||!("magic:"+code+":"+scene).equals(existing.optString("scene"))){id=store.addSession("魔法岛 · "+name(code)+" · "+scene,"magic:"+code+":"+scene);prefs.edit().putLong(key,id).apply();}store.close();prefs.edit().putString("last_scene_"+code,scene).apply();String difficulty=review.difficulty(code,lessons);startActivity(new Intent(this,MagicDuplexActivity.class).putExtra("topic",id).putExtra("language",code).putExtra("difficulty",difficulty));}
 private void about(){String s="4.5 逐段纠音魔法镜：内置 TIFA 1.0 ST，按课程预期音素定位音节和日语拍子，提供逐段对照听、单段重练与口型 / 舌位 / 节奏动作。Android 7.0+，无需联网。TIFA 是对齐模型，不能自动诊断全部音素或给出经过校准的发音准确率。n/l 听辨在部分示范上不稳定，未用作自动纠音结论。\n\nTIFA 代码 MIT；模型与本项目的 ONNX 转换为 CC BY-NC-SA 4.0，仅限非商业用途，署名并相同方式共享。https://github.com/openvpi/TIFA\nhttps://creativecommons.org/licenses/by-nc-sa/4.0/\n\n三种语言都可以离线听示范、录音回放、比较音高走势与总时长，并查看口型、舌位或拍子提示。\n\n音高曲线由原始录音提取，不通过英文识别器。走势分只表示曲线接近程度，不能识别你是否念了正确的字，也不能自动诊断全部声母、韵母或日语音素。\n\n平调的高低位置先校准自己的中音；位置判断是粗略辅助。安静环境、不耳语，通常更容易提取曲线。\n\n开源接入：Jyut Dojo 的 MIT 声调曲线 DTW 算法已改写为 Java；粤语示范来自该项目。日语、普通话示范为构建时合成，已内置。示范尚未经过母语老师逐条审核。\n\n练习过程无需账号、API Key 或联网。三语对话由手机内 Whisper base 与 DeepSeek / Qwen 驱动；首次需要安装对话模型。支持长期记忆、耳机全双工、外放手动打断。Whisper 的粤语识别可能转成普通话文字；识别文本不作为音素评分。\n\n学习系统增加 Jyut Dojo 的 SM-2 间隔重复、完整词句与声调家族。Cantonese Dialogue 没有明确代码许可，本应用独立实现类似的角色对话流程，未复制其代码或教材。";new AlertDialog.Builder(this).setTitle("轻松练，也清楚知道在练什么").setMessage(s).setNegativeButton("知道啦",null).setPositiveButton("开源许可",(d,n)->licenses()).show();}
 private void licenses(){new AlertDialog.Builder(this).setTitle("开源许可").setItems(new String[]{"Jyut Dojo · MIT","TIFA 代码 · MIT","TIFA 模型 · CC BY-NC-SA 4.0","ONNX Runtime · MIT"},(d,n)->showLicense(new String[]{"Jyut-Dojo-MIT.txt","TIFA-MIT.txt","TIFA-MODEL-NOTICE.txt","ONNX-Runtime-MIT.txt"}[n])).setNegativeButton("关闭",null).show();}
 private void showLicense(String file){try(InputStream in=getAssets().open("licenses/"+file)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)out.write(b,0,n);new AlertDialog.Builder(this).setTitle(file).setMessage(out.toString("UTF-8")).setPositiveButton("关闭",null).show();}catch(Exception e){toast("许可文件未能打开");}}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
 private void cancelRecording(){if(recording||recorder.busy()){epoch++;recorder.cancel();recording=false;working=false;if(recordButton!=null){recordButton.setEnabled(true);recordButton.setText("● 再试一次");}}}
 private void leave(){epoch++;cancelSegments("离开练习页，逐段分析已取消");clearClips();focusIndex=-1;units=Collections.emptyList();clips=Collections.emptyList();segmentsStatus=null;segmentsCancel=null;segmentRows=null;cancelCheck("离开练习页，核对已取消");recorder.cancel();recording=false;working=false;stopPlayer();recordButton=null;meter=null;playStatus=null;feedbackTitle=null;feedbackDetail=null;graph=null;checkStatus=null;checkCancel=null;}
 private void back(){practicePath=null;if(page.equals("lesson"))track(lang);else if(page.equals("hub"))finish();else hub();}
 @Override public void onBackPressed(){back();}
 @Override public void onResume(){super.onResume();foreground=true;if(firstResume){firstResume=false;return;}if(lesson!=null&&page.equals("lesson"))openLesson(lesson);else if(page.equals("hub"))hub();else if(page.equals("track"))track(lang);else if(page.equals("favorites"))favorites();else if(page.equals("history"))history();}
 @Override public void onPause(){foreground=false;leave();super.onPause();}
 @Override public void onDestroy(){leave();ui.removeCallbacksAndMessages(null);worker.shutdownNow();checkWorker.shutdownNow();alignmentWorker.shutdownNow();super.onDestroy();}
 @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("page",page);b.putString("lang",lang);b.putString("lesson",lesson==null?"":lesson.optString("id"));if(practicePath!=null){b.putStringArrayList("practice_ids",new ArrayList<>(practicePath.ids()));b.putInt("practice_position",practicePath.position());}}

 private final class PitchView extends View {
  private PronunciationCoach.Track ref,own;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  PitchView(){super(LanguageActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  void setTracks(PronunciationCoach.Track r,PronunciationCoach.Track o){ref=r;own=o;invalidate();}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setColor(0xff191e37);c.drawRoundRect(dp(1),dp(10),w-dp(1),h-dp(9),dp(18),dp(18),p);p.setStrokeWidth(dp(1));p.setColor(0xff3f3e64);for(int i=1;i<4;i++)c.drawLine(dp(18),h*i/4,w-dp(18),h*i/4,p);draw(c,ref,PURPLE,w,h);draw(c,own,0xff39ae9d,w,h);if(ref==null){p.setColor(MUTED);p.setTextSize(dp(13));p.setTypeface(Typeface.DEFAULT);c.drawText("声音准备好后，曲线会出现在这里",dp(18),h/2,p);}}
  private void draw(Canvas c,PronunciationCoach.Track t,int color,float w,float h){if(t==null||t.voiced<2)return;double med=ContourScore.median(t.pitch);float left=dp(18),top=dp(25);double minT=t.time[0],maxT=t.time[t.time.length-1];Path path=new Path();for(int i=0;i<t.pitch.length;i++){float x=(float)(left+(t.time[i]-minT)/Math.max(.02,maxT-minT)*(w-2*left)),y=(float)(h/2-Math.max(-10,Math.min(10,t.pitch[i]-med))*(h-2*top)/20);if(i==0||t.time[i]-t.time[i-1]>.12)path.moveTo(x,y);else path.lineTo(x,y);}p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
 }
 private final class Mascot extends View {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Mascot(){super(LanguageActivity.this);setContentDescription("戴星星帽的兔子小精灵");}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/112f,getHeight()/148f);p.setColor(0x55ffffff);c.drawCircle(62,74,47,p);p.setColor(0xfffff0bc);star(c,16,28,6);star(c,95,111,5);star(c,103,34,4);p.setColor(0xfff4e9ff);c.drawOval(29,69,89,132,p);c.drawOval(34,36,48,93,p);c.drawOval(69,30,83,92,p);p.setColor(0xffffcbdc);c.drawOval(38,44,44,76,p);c.drawOval(73,39,79,74,p);p.setColor(0xfffff9ff);c.drawOval(25,77,96,129,p);p.setColor(0xff674eac);Path hat=new Path();hat.moveTo(31,83);hat.lineTo(69,20);hat.lineTo(89,83);hat.close();c.drawPath(hat,p);c.drawOval(24,76,98,88,p);p.setColor(0xffffdfa0);star(c,65,55,7);p.setColor(0xff49345f);c.drawCircle(46,102,3,p);c.drawCircle(77,102,3,p);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);c.drawArc(57,101,67,113,0,180,false,p);p.setStyle(Paint.Style.FILL);p.setColor(0xffffcfdd);c.drawOval(32,108,45,115,p);c.drawOval(79,108,92,115,p);p.setColor(0xffeee4ff);c.drawOval(21,117,42,130,p);c.drawOval(80,117,102,130,p);c.restore();}
  private void star(Canvas c,float x,float y,float r){Path path=new Path();for(int i=0;i<8;i++){double angle=-Math.PI/2+i*Math.PI/4;float radius=i%2==0?r:r*.35f;float a=x+(float)Math.cos(angle)*radius,b=y+(float)Math.sin(angle)*radius;if(i==0)path.moveTo(a,b);else path.lineTo(a,b);}path.close();c.drawPath(path,p);}
 }
}
