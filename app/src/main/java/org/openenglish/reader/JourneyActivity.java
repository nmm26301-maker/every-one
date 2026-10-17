package org.openenglish.reader;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Integrated English pathway. Existing Studio and continuous conversation remain available. */
public final class JourneyActivity extends Activity {
 private static final int INK=0xff18263a,MUTED=0xff536176,PAPER=0xfff5f6fa,ACCENT=0xff5948cf;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final LessonRecorder recorder=new LessonRecorder();
 private JourneyState state;private JSONObject catalog,lesson;private JSONArray lessons,levels;
 private LinearLayout page;private TextView status;private TextToSpeech tts;private boolean ttsReady,busy,destroyed;
 private MediaPlayer player;private CloudClient.Job job;private int generation,phraseIndex;private String screen="home";
 private File take;private EditText chatInput;private boolean review;
 private interface Action{void run()throws Exception;}
 private interface Work{String run(CloudClient.Job job)throws Exception;}
 private interface Result{void accept(String result)throws Exception;}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  try{catalog=new JSONObject(read(getAssets().open("journey/course.json"),300000));lessons=catalog.getJSONArray("lessons");levels=catalog.getJSONArray("levels");state=new JourneyState(this);}
  catch(Exception e){new AlertDialog.Builder(this).setTitle("学习记录未能打开").setMessage("未覆盖原有记录。你仍可打开原有工作室。\n"+e.getMessage()).setPositiveButton("打开工作室",(d,w)->{startActivity(new Intent(this,StudioActivity.class));finish();}).setNegativeButton("关闭",(d,w)->finish()).show();return;}
  tts=new TextToSpeech(this,result->main.post(()->{if(destroyed)return;if(result==TextToSpeech.SUCCESS){int lang=tts.setLanguage(Locale.US);ttsReady=lang>=TextToSpeech.LANG_AVAILABLE;}if(!ttsReady)Toast.makeText(this,"未找到英文朗读语音，请在系统文字转语音设置中安装英语语音",Toast.LENGTH_LONG).show();}));
  home();
 }
 private int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
 private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private void safely(Action a){try{a.run();}catch(Exception e){notice("操作未完成",safeError(e));}}
 private String safeError(Exception e){String m=e.getMessage();if(m==null)return "请重试；原有学习进度保留。";if(m.contains("http")||m.length()>240)return "服务或文件处理失败，请检查配置与连接后重试。";return m;}
 private void notice(String title,String text){if(!isFinishing()&&!destroyed)new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show();}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1.08f);t.setPadding(0,dp(5),0,dp(7));t.setTextIsSelectable(true);return t;}
 private void line(String s){page.addView(text(s,16,MUTED));}
 private void title(String s){TextView t=text(s,26,INK);t.setTypeface(null,Typeface.BOLD);page.addView(t);}
 private LinearLayout card(String heading,String body){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(17),dp(12),dp(17),dp(14));c.setBackground(bg(Color.WHITE,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));page.addView(c,p);TextView h=text(heading,19,INK);h.setTypeface(null,Typeface.BOLD);c.addView(h);if(!body.isEmpty())c.addView(text(body,16,MUTED));return c;}
 private Button button(LinearLayout into,String label,Action a,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:ACCENT);b.setMinHeight(dp(50));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setBackground(bg(primary?ACCENT:0xffeeebff,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(3));into.addView(b,p);b.setOnClickListener(v->safely(a));return b;}
 private Button button(String label,Action a){return button(page,label,a,false);}
 private void view(String key,String heading){
  stopTasks();deleteTake();screen=key;
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(PAPER);root.setPadding(dp(18),dp(10),dp(18),0);
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
  TextView brand=text("EVERYONE  /  学会交流",13,ACCENT);brand.setTypeface(null,Typeface.BOLD);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  Button back=new Button(this);back.setText("首页");back.setTextColor(ACCENT);back.setMinHeight(dp(48));back.setOnClickListener(v->home());bar.addView(back);root.addView(bar);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=new LinearLayout(this);page.setOrientation(1);page.setPadding(0,0,0,dp(28));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);title(heading);status=null;chatInput=null;
 }
 private void home(){
  if(state==null)return;view("home","每天开口，逐步说自在");
  JSONObject level=levels.optJSONObject(state.level());line(level.optString("name")+" · 已练习完成 "+state.completed()+" / 48 单元");
  int due=state.due(System.currentTimeMillis()).size();
  LinearLayout c=card("今天的练习 · 约 20 分钟","先复习，再学新内容，最后脱稿交流。进度记录练习表现，不等于英语等级认证。");
  button(c,due>0?"先复习 · "+due+" 句待练":"开始今日课程",()->{if(due>0)reviewList();else openNext();},true);
  button(c,"我的学习路线",this::route,false);
  LinearLayout goal=card("这一阶段要能做到",level.optString("canDo"));button(goal,"选择起点 / 自测任务",this::placement,false);
  button("场景交流 · AI 对话",()->{if(lesson==null)lesson=nextLesson();conversation();});
  button("纠音训练室",this::lab);button("间隔复习 · "+due+" 句",this::reviewList);
  button("进步与阶段挑战",this::progress);button("设置、模型与备份",this::settings);
  button("原有工作室 · 阅读 / 听写 / 素材 / 多语言",()->startActivity(new Intent(this,StudioActivity.class)));
 }
 private JSONObject nextLesson(){for(int i=state.level()*8;i<(state.level()+1)*8;i++){JSONObject u=lessons.optJSONObject(i);if(!state.complete(u.optString("id")))return u;}return lessons.optJSONObject(state.level()*8);}
 private void openNext(){lesson=nextLesson();unit();}
 private void route(){view("route","从第一句话到深入交流");line("可自由选择起点。每单元：听懂 → 跟读纠音 → 脱稿回忆 → 场景运用。48 单元是核心训练路线，需要持续扩展真实听说输入。");for(int l=0;l<6;l++){final int index=l;JSONObject lv=levels.optJSONObject(l);LinearLayout c=card(lv.optString("name"),lv.optString("goal"));button(c,"练习这一阶段",()->{state.level(index);openNext();},true);for(int j=0;j<8;j++){final JSONObject u=lessons.optJSONObject(l*8+j);button(c,(state.complete(u.optString("id"))?"✓ ":"")+String.format(Locale.US,"%02d",l*8+j+1)+"  "+u.optString("title"),()->{lesson=u;unit();},false);}}}
 private void placement(){view("placement","先选一个真实起点");line("不会英语请从零基础开始。如果已有基础，选择你能不看稿完成的任务的下一阶段。这是自选起点，不是测评证书；可以随时调整。");for(int i=0;i<6;i++){final int l=i;JSONObject v=levels.optJSONObject(i);LinearLayout c=card(v.optString("name"),v.optString("canDo"));button(c,"从这里开始",()->{state.level(l);openNext();},true);}}
 private String id(){return lesson.optString("id");}
 private JSONObject sentence(int n){return lesson.optJSONArray("sentences").optJSONObject(n);}
 private void unit(){view("unit",lesson.optString("title"));line(levels.optJSONObject(lesson.optInt("level")).optString("name"));card("先理解这个表达",lesson.optString("note"));card("今天的发音重点",lesson.optString("focus"));
  for(int i=0;i<3;i++){final int n=i;JSONObject s=sentence(i);JSONObject c=state.card(id()+"_"+i);LinearLayout box=card((i+1)+"  "+s.optString("en"),s.optString("zh")+(c==null?"":"\n已开口 "+c.optInt("attempts")+" 次 · "+(c.optBoolean("assessed")?"最近有音频评测":"最近为识别/回听练习")));button(box,"听懂这句话",()->listenQuiz(n),false);button(box,"跟读、纠音和脱稿",()->{phraseIndex=n;review=false;practice();},true);}
  LinearLayout task=card("把句子变成自己的话",lesson.optString("task"));button(task,"进入场景对话",this::conversation,true);button("返回学习路线",this::route);
 }
 private void listenQuiz(int n){view("listen","先听，再选意思");line("第 "+(n+1)+" 句 · 可以重复听。首次练习先正常速度，再用慢速确认。");String en=sentence(n).optString("en");button("播放英语",()->speak(en,1f));button("慢速听",()->speak(en,.78f));List<Integer> options=new ArrayList<>(Arrays.asList(0,1,2));Collections.shuffle(options);TextView feedback=text("选择你听到的意思。",16,MUTED);for(int x:options)button(sentence(x).optString("zh"),()->{if(x==n){state.mark(id(),"heard",1<<n);feedback.setText("听懂了。现在试着不看英文复述，再进入跟读。\n"+en);}else feedback.setText("再听一次，注意关键词。你还可以回到课程看解释。");});page.addView(feedback);button("进入跟读",()->{phraseIndex=n;review=false;practice();});button("返回本单元",this::unit);}
 private void practice(){
  view("practice",review?"到期复习 · 先回忆再看答案":"听示范，开口，再改进");
  JSONObject s=sentence(phraseIndex);String en=s.optString("en"),zh=s.optString("zh");line(lesson.optString("title")+" · 第 "+(phraseIndex+1)+" 句");line(zh);
  TextView target=text(review?"英文已隐藏，请先试着说出来。":en,26,INK);page.addView(target);
  button("显示 / 隐藏英文",()->target.setText(target.getText().toString().equals(en)?"英文已隐藏，请先试着说出来。":en));
  button("听正常示范",()->speak(en,1f));button("听慢速示范",()->speak(en,.78f));
  card("发音提示",lesson.optString("focus"));String mode=getSharedPreferences("journey_settings",0).getString("assessment_mode","recognition");
  line(mode.equals("azure")?"音素评测已启用：录音将发送至你配置的 Azure Speech 服务。参考口音为美式英语。":mode.equals("manual")?"录音回听模式：不上传，不生成发音分数。":"识别对照模式：比较识别文本；识别一致不表示音素正确。" );
  status=text("每次最多录 12 秒。长句请正常语速说；录满会自动结束。",16,MUTED);page.addView(status);
  button(page,"● 开始录音",()->record(false),true);button("■ 结束录音 / 停止处理",()->{if(recorder.busy())recorder.finish();else{stopTasks();status.setText("已停止，可重新录音。");}});
  button("回听我的录音",this::playTake);
  JSONObject previous=state.card(id()+"_"+phraseIndex);if(previous!=null)card("上次反馈",previous.optString("feedback"));
  button("脱稿能说出 · 安排复习",()->rate(true));button("还说不出 · 10 分钟后重练",()->rate(false));
  line("请先隐藏英文，用自己的声音回忆，再选择。‘能说出’是自评记忆表现，不会被记作音素正确。");
  button("纠音方式与服务设置",this::settings);button("返回本单元",this::unit);
 }
 private void rate(boolean good)throws Exception{
  String key=id()+"_"+phraseIndex;state.recall(key,good,System.currentTimeMillis());
  int stage=state.card(key).optInt("stage");String delay=stage==0?"10 分钟":JourneyCore.INTERVALS[stage]+" 天";
  if(review){reviewList();Toast.makeText(this,"已安排 "+delay+" 后复习",Toast.LENGTH_LONG).show();}
  else notice("复习已安排",delay+" 后再试。"+(state.complete(id())?"本单元的听辨、开口和自评回忆已完成，接着做脱稿对话。":"继续完成本单元其余句子和听辨。"));
 }
 private void reviewList(){view("review","把学过的变成能用的");List<String> due=state.due(System.currentTimeMillis());line("按自评回忆安排：10 分钟 → 1 / 3 / 7 / 14 / 30 天。提前重复不会跳级；想不起来会回到短间隔。");if(due.isEmpty()){card("暂时没有到期句子","今天可以学一课，再用它完成三轮场景交流。");button("开始新课",this::openNext);}for(String key:due){String unitId=key.substring(0,3);int n=Integer.parseInt(key.substring(4));JSONObject u=find(unitId);LinearLayout c=card(u.optString("title"),u.optJSONArray("sentences").optJSONObject(n).optString("zh"));button(c,"先脱稿回忆",()->{lesson=u;phraseIndex=n;review=true;practice();},true);}}
 private JSONObject find(String id){for(int i=0;i<lessons.length();i++)if(lessons.optJSONObject(i).optString("id").equals(id))return lessons.optJSONObject(i);throw new IllegalArgumentException("未找到课程");}
 private void lab(){view("lab","纠音训练室");line("目标是让人听懂，并能自然表达。先解决影响理解的音，再练重音、弱读和意群。下面的口形提示是通用指导；你的问题以录音评测与真实交流反馈为准。");
  for(String[] drill:new String[][]{{"θ","think / sink","u06"},{"ð","then / den","u04"},{"r","right / light","u13"},{"v","very / berry","u17"},{"ɪ","ship / sheep","u16"},{"æ","bad / bed","u02"},{"ŋ","sing / sin","u16"},{"ʃ","she / see","u14"},{"p","cap / cab","u06"},{"ə","about / a cup of tea","u09"}}){LinearLayout c=card("/"+drill[0]+"/  ·  "+drill[1],JourneyCore.phoneTip(drill[0]));button(c,"听单词对比",()->speak(drill[1].replace("/",", "),.85f),false);button(c,"放进整句练习",()->{lesson=find(drill[2]);phraseIndex=0;review=false;practice();},false);}
  button("配置真实音素评测",this::settings);
 }
 private void progress(){view("progress","用交流任务检验进步");card("训练记录",state.completed()+" / 48 单元完成听辨、录音与自评回忆\n"+state.due(System.currentTimeMillis()).size()+" 句到期复习\n此记录不推断 CEFR 等级，也不代表发音已正确。");for(int i=0;i<6;i++){final int l=i;JSONObject v=levels.optJSONObject(i);LinearLayout c=card(v.optString("name")+" · 阶段挑战",v.optString("canDo")+"\n练法：不看稿换一个话题，回应至少五轮；检查是否能追问、澄清和总结。让真实交流对象反馈是否容易理解。");button(c,"开始阶段场景",()->{lesson=lessons.optJSONObject(l*8+7);conversation();},true);}line("进阶需要更多词汇、真实语速与不同说话人的输入。可在原有工作室导入自己的音视频，做听写与跟读，再把话题带入交流。应用不提供真人匹配，也不能保证所有场景零障碍。");}
 private void conversation(){
  view("conversation","把英语用起来");line(levels.optJSONObject(lesson.optInt("level")).optString("name")+" · "+lesson.optString("title"));card("本次任务",lesson.optString("task"));
  JSONArray messages=state.chat(id());for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);String content=m.optString("content");LinearLayout c=card(m.optString("role").equals("user")?"我":"AI 练习伙伴",content);if(m.optString("role").equals("assistant"))button(c,"听这段回复",()->speak(content,1f),false);}
  status=text("可打字，也可录一句后确认文字再发送。AI 可能出错；文字反馈不评判发音。",16,MUTED);page.addView(status);chatInput=new EditText(this);chatInput.setTextColor(INK);chatInput.setTextSize(17);chatInput.setHint("说你自己的话，或先点“帮我开场”");chatInput.setMinLines(2);chatInput.setMaxLines(6);chatInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);page.addView(chatInput,new LinearLayout.LayoutParams(-1,-2));
  button(page,"发送并继续对话",()->send(chatInput.getText().toString()),true);
  button("● 录一句 · 最多 12 秒",()->record(true));button("■ 结束录音 / 停止生成",()->{if(recorder.busy())recorder.finish();else{stopTasks();status.setText("已停止，已保存的对话仍在。");}});
  button("帮我开场",()->send("Please start our role-play with one short question."));
  button("给我中文提示和一句示范",()->send("请用简短中文解释刚才的问题，并给我一句可以参考的英文回答。"));
  button("复盘这段对话",()->send("请用中文简短复盘本次对话：一个我做得好的地方、一个重要的表达修改、一个下一步练习任务。只引用我实际说过的内容；不要从文本评价发音或判定CEFR等级。"));
  button("连续语音模式",()->{StudioStore store=new StudioStore(this);long topic;try{topic=store.addSession(lesson.optString("title"),lesson.optString("scene"));}finally{store.close();}startActivity(new Intent(this,DuplexActivity.class).putExtra("topic",topic));});
  button("新一轮场景",()->new AlertDialog.Builder(this).setTitle("开始新一轮？").setMessage("清空这个场景的最近对话，保留课程与复习记录。可先在设置中导出学习备份。").setPositiveButton("开始",(d,w)->safely(()->{state.clearChat(id());conversation();})).setNegativeButton("取消",null).show());
  button("返回本单元",this::unit);
 }
 private void send(String message)throws Exception{
  if(busy){Toast.makeText(this,"请先结束当前录音或处理",Toast.LENGTH_SHORT).show();return;}
  final String content=message.trim();if(content.isEmpty())return;if(content.length()>1500)throw new IOException("请把内容缩短到 1500 字以内，再分段交流。");
  final String unit=id();state.message(unit,"user",content);conversation();JSONArray history=new JSONArray(state.chat(unit).toString());int level=lesson.optInt("level");
  final String system="You are a supportive English role-play partner for a Chinese-speaking learner. Practice band: "+levels.optJSONObject(level).optString("name")+". Scenario: "+lesson.optString("scene")+". "+(level<=1?"Use short everyday sentences, 15-30 words per turn. Offer a brief Chinese hint when needed.":level<=3?"Use natural everyday English, 30-60 words per turn.":"Use varied natural English, 40-90 words per turn, including follow-up questions and clarification.")+" Respond to meaning first, gently recast at most one important language error, and ask one relevant question. Do not merely repeat the lesson. Adapt details and introduce a small unexpected change after several turns. If the learner asks for Chinese help or a recap, comply briefly. Never infer pronunciation, accent, CEFR proficiency, or a score from text. Never invent something the learner said.";
  runJob(j->{if(LocalAI.enabled(this))return LocalAI.chatVoice(this,system,history,j,delta->{});SharedPreferences p=getSharedPreferences("connection",0);return new CloudClient(this).chat(p.getString("base",""),p.getString("model",""),KeyVault.read(this,"connection"),system,history,j,delta->{});},result->{state.message(unit,"assistant",result.length()>12000?result.substring(0,12000):result);conversation();if(getSharedPreferences("journey_settings",0).getBoolean("autoplay",false))speak(result,1f);},"AI 正在回应，可停止；对话保留最近 30 条。");
 }
 private void record(boolean forChat)throws Exception{
  if(busy||recorder.busy()){Toast.makeText(this,"当前录音或处理尚未结束",Toast.LENGTH_SHORT).show();return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},91);status.setText("允许权限后，请再次点击开始录音。");return;}
  if(tts!=null)tts.stop();releasePlayer();deleteTake();busy=true;int token=++generation;status.setText("正在启动麦克风，请等开始提示…");
  boolean started=recorder.start(new LessonRecorder.Listener(){
   public void ready(){post(token,()->status.setText("正在录音，请开始说话。说完点结束，最多 12 秒。"));}
   public void meter(double rms,int ms){post(token,()->status.setText("录音 "+(ms/1000)+" / 12 秒 · "+(rms<.003?"声音较轻，请靠近麦克风":"已收到声音")));}
   public void done(float[] pcm){post(token,()->{busy=false;if(!LocalWav.hasSignal(pcm)){status.setText("没有收到足够清楚的声音，请检查麦克风后重录。");return;}safely(()->processTake(pcm,forChat));});}
   public void error(String error){post(token,()->{busy=false;status.setText(error);});}
  });if(!started){busy=false;status.setText("上次录音正在结束，请稍后再试。");}
 }
 private void post(int token,Runnable r){main.post(()->{if(!destroyed&&generation==token&&!isFinishing())r.run();});}
 private void processTake(float[] pcm,boolean forChat)throws Exception{
  File file=File.createTempFile("journey-",".wav",getCacheDir());take=file;writeWav(file,pcm);
  final String unit=id();final int n=phraseIndex;final String reference=sentence(n).optString("en");
  String mode=getSharedPreferences("journey_settings",0).getString("assessment_mode","recognition");
  if(!forChat&&mode.equals("manual")){String feedback="已完成录音回听练习，未进行自动音素评测。\n"+lesson.optString("focus");state.practice(unit,n,feedback,false);status.setText(feedback);return;}
  final boolean assessment=!forChat&&mode.equals("azure");
  runJob(j->{try{if(assessment){SharedPreferences p=getSharedPreferences("assessment",0);JSONObject result=new CloudClient(this).assess(p.getString("region",""),KeyVault.read(this,"assessment"),file,reference,j);JourneyFeedback f=JourneyFeedback.parse(result);if(!f.usable)throw new IOException(f.text);return f.text;}
   SharedPreferences p=getSharedPreferences("speech",0);JSONObject result=new CloudClient(this).transcribe(p.getString("base",""),p.getString("model",""),KeyVault.read(this,"speech"),file,false,j);String heard=result.optString("text").trim();if(heard.isEmpty())throw new IOException("没有识别到文字。请重录，或在设置切换为录音回听模式。");return heard;
  }finally{ /* The last take remains available until leaving this page. */ }},result->{if(forChat){chatInput.setText(result);status.setText("请检查识别文字，确认后点发送。");}else{String feedback=assessment?result:"识别到："+result+"\n\n"+JourneyCore.difference(reference,result)+"\n\n本课提示："+lesson.optString("focus");state.practice(unit,n,feedback,assessment);status.setText(feedback);}},assessment?"正在分析音频中的单词与音素…":"正在识别录音；失败时可以回听，或切换录音回听模式。");
 }
 private void runJob(Work work,Result result,String label){
  if(busy)return;busy=true;int token=++generation;CloudClient.Job task=new CloudClient.Job();job=task;status.setText(label);
  main.postDelayed(()->{if(generation==token&&busy){stopTasks();if(status!=null)status.setText("处理超时，已取消。可重试或检查模型与网络设置。");}},120000);
  new Thread(()->{try{String value=work.run(task);task.check();post(token,()->{busy=false;job=null;safely(()->result.accept(value));});}catch(Exception e){post(token,()->{busy=false;job=null;status.setText("没有完成："+safeError(e)+"\n可以重试，或前往设置检查服务 / 本地模型。");});}},"journey-work").start();
 }
 private static void writeWav(File file,float[] pcm)throws Exception{try(DataOutputStream o=new DataOutputStream(new FileOutputStream(file))){int bytes=pcm.length*2;o.writeBytes("RIFF");o.writeInt(Integer.reverseBytes(36+bytes));o.writeBytes("WAVEfmt ");o.writeInt(Integer.reverseBytes(16));o.writeShort(Short.reverseBytes((short)1));o.writeShort(Short.reverseBytes((short)1));o.writeInt(Integer.reverseBytes(16000));o.writeInt(Integer.reverseBytes(32000));o.writeShort(Short.reverseBytes((short)2));o.writeShort(Short.reverseBytes((short)16));o.writeBytes("data");o.writeInt(Integer.reverseBytes(bytes));for(float v:pcm)o.writeShort(Short.reverseBytes((short)Math.max(-32768,Math.min(32767,Math.round(v*32767)))));}}
 private void playTake()throws Exception{if(busy)throw new IOException("请先结束录音和处理再回听");if(take==null||!take.isFile())throw new IOException("本页还没有录音，请先录一句");if(tts!=null)tts.stop();releasePlayer();player=new MediaPlayer();player.setDataSource(take.getAbsolutePath());player.setOnCompletionListener(p->releasePlayer());player.prepare();player.start();}
 private void speak(String text,float speed)throws Exception{if(busy)throw new IOException("请先结束录音或处理再播放");if(!ttsReady)throw new IOException("请在系统文字转语音设置中安装英语语音，再重开应用");releasePlayer();tts.setSpeechRate(speed);int max=TextToSpeech.getMaxSpeechInputLength();if(text.length()>max)text=text.substring(0,max);if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"journey")==TextToSpeech.ERROR)throw new IOException("朗读未能启动，请检查系统英语语音");}
 private void releasePlayer(){if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}}
 private void deleteTake(){if(take!=null){take.delete();take=null;}}
 private void stopTasks(){generation++;busy=false;if(job!=null){job.cancel();job=null;}recorder.cancel();if(tts!=null)tts.stop();releasePlayer();}
 private EditText field(LinearLayout box,String label,String value,boolean secret){box.addView(text(label,14,MUTED));EditText e=new EditText(this);e.setTextSize(16);e.setTextColor(INK);e.setSingleLine(true);e.setText(value);e.setHint(secret?"留空保留已保存的密钥":"");if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));return e;}
 private void settings(){
  view("settings","把练习方式配置好");SharedPreferences prefs=getSharedPreferences("journey_settings",0);
  LinearLayout mode=card("纠音方式","真实音素评分需要 Azure Speech 的区域和密钥，服务可能收费。未配置时可用识别对照或纯录音回听；两者不生成发音分数。");
  String[] labels={"识别对照 · 本地或已配置的转写服务","真实音素评测 · 上传录音至 Azure Speech","仅录音回听 · 无自动分数"};String[] modes={"recognition","azure","manual"};
  RadioGroup options=new RadioGroup(this);int selected=0;String stored=prefs.getString("assessment_mode","recognition");for(int i=0;i<3;i++){RadioButton b=new RadioButton(this);b.setText(labels[i]);b.setTextSize(16);b.setTextColor(INK);b.setId(200+i);b.setMinHeight(dp(48));options.addView(b);if(stored.equals(modes[i]))selected=i;}options.check(200+selected);mode.addView(options);
  SharedPreferences ap=getSharedPreferences("assessment",0);EditText region=field(mode,"Azure 区域，例如 eastasia",ap.getString("region",""),false),key=field(mode,"Azure Speech 密钥（加密保存）","",true);
  button(mode,"保存纠音设置",()->{String r=region.getText().toString().trim();String k=key.getText().toString().trim();int choice=options.getCheckedRadioButtonId()-200;if(choice==1){if(!r.matches("[a-z0-9-]{3,40}"))throw new IOException("请填写有效的 Azure Speech 区域");if(k.isEmpty()&&KeyVault.read(this,"assessment").trim().isEmpty())throw new IOException("请填写 Azure Speech 密钥");}if(!k.isEmpty())KeyVault.save(this,"assessment",k);ap.edit().putString("region",r).apply();prefs.edit().putString("assessment_mode",modes[choice]).apply();key.setText("");notice("已保存",choice==1?"此后点击课程录音会把该段音频发往你配置的 Azure Speech 服务用于评测。":"纠音练习方式已更新。");},true);
  button(mode,"清除音素评测密钥",()->new AlertDialog.Builder(this).setTitle("清除评测密钥？").setMessage("将切换为识别对照模式，学习记录保留。").setPositiveButton("清除",(d,w)->safely(()->{KeyVault.save(this,"assessment","");prefs.edit().putString("assessment_mode","recognition").apply();settings();})).setNegativeButton("取消",null).show(),false);
  LinearLayout engine=card("识别与 AI 对话","本地模式需要相应的英语语音识别与对话模型。云端模式会把录音发送到转写服务，把对话文字发送到聊天服务；仅使用你自己配置的 HTTPS 接口。");
  Switch local=new Switch(this);local.setText("使用本地模型（识别和对话）");local.setTextColor(INK);local.setMinHeight(dp(50));local.setChecked(LocalAI.enabled(this));engine.addView(local);
  button(engine,"保存模型模式",()->{getSharedPreferences("local_ai",0).edit().putBoolean("enabled",local.isChecked()).apply();notice("已保存",local.isChecked()?"识别和对话使用本地模型；Azure 音素评测仍需联网。":"识别和对话使用下方配置的云端服务。");},false);
  button(engine,"安装 / 导入 / 管理本地模型",()->startActivity(new Intent(this,LocalModelActivity.class)),true);
  for(String ns:new String[]{"connection","speech"}){
   SharedPreferences p=getSharedPreferences(ns,0);boolean speech=ns.equals("speech");LinearLayout c=card(speech?"云端语音转写":"云端 AI 对话","兼容 OpenAI 格式的 HTTPS 服务。密钥只保存在设备加密存储中。");
   EditText base=field(c,"API 基础地址（通常以 /v1 结尾）",p.getString("base",""),false),model=field(c,"模型名称",p.getString("model",""),false),token=field(c,"API 密钥","",true);
   button(c,"保存此服务",()->{String url=base.getText().toString().trim(),name=model.getText().toString().trim();CloudClient.endpoint(url,speech?"audio/transcriptions":"chat/completions");if(name.isEmpty())throw new IOException("请填写模型名称");String secret=token.getText().toString().trim();if(!secret.isEmpty())KeyVault.save(this,ns,secret);p.edit().putString("base",url).putString("model",name).apply();token.setText("");notice("已保存","服务配置已保存。选择云端模型模式后生效。");},false);
  }
  LinearLayout voice=card("朗读与回放","使用手机已安装的英语文字转语音引擎。若听不到声音，请安装英语语音并检查媒体音量。");Switch auto=new Switch(this);auto.setText("自动朗读完成的 AI 回复");auto.setMinHeight(dp(48));auto.setChecked(prefs.getBoolean("autoplay",false));auto.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean("autoplay",on).apply());voice.addView(auto);button(voice,"打开系统朗读设置",()->startActivity(new Intent("com.android.settings.TTS_SETTINGS")),false);
  LinearLayout backup=card("学习记录备份","包含本路线的课程进度、复习计划、反馈和最近对话。录音仅临时回听，离开页面即删除；备份不含录音和密钥。原有工作室数据仍用其原有备份入口。");
  button(backup,"导出学习记录",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Everyone-learning-backup.json");startActivityForResult(i,100);},false);
  button(backup,"导入学习记录",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),101),false);
  button("返回首页",this::home);
 }
 @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;Uri uri=intent.getData();safely(()->{if(request==100){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入所选文件");out.write(state.export().getBytes("UTF-8"));}notice("已导出","请妥善保管学习记录，其中包含对话文字。");}else if(request==101){String imported=read(getContentResolver().openInputStream(uri),2000000);JourneyState.validate(new JSONObject(imported));new AlertDialog.Builder(this).setTitle("恢复这份学习记录？").setMessage("将替换本学习路线的当前进度与对话。原有工作室数据和服务密钥不受影响，请先确认已导出需要保留的记录。").setPositiveButton("恢复",(d,w)->safely(()->{state.restore(imported);home();Toast.makeText(this,"学习记录已恢复",Toast.LENGTH_LONG).show();})).setNegativeButton("取消",null).show();}});}
 private static String read(InputStream input,int max)throws Exception{if(input==null)throw new IOException("无法打开文件");try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("文件超过允许大小");out.write(b,0,n);}return out.toString("UTF-8");}}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==91&&status!=null)status.setText(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"麦克风已允许，点击开始录音即可。":"未获得麦克风权限。可以继续听读、打字对话，或到系统设置允许麦克风。");}
 @Override protected void onPause(){super.onPause();boolean wasBusy=busy;stopTasks();deleteTake();if(wasBusy&&status!=null)status.setText("已暂停录音或处理，请重新开始。");}
 @Override protected void onDestroy(){destroyed=true;stopTasks();main.removeCallbacksAndMessages(null);deleteTake();if(tts!=null)tts.shutdown();super.onDestroy();}
 @Override public void onBackPressed(){if("home".equals(screen)){super.onBackPressed();return;}if(Arrays.asList("practice","listen","conversation").contains(screen)&&lesson!=null)unit();else home();}
}
