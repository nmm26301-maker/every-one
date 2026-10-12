package org.openenglish.reader;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.*;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Continuous capture overlaps TTS and generation. Speech onset invalidates the previous turn. */
public final class DuplexActivity extends Activity {
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final VoiceTurns turns=new VoiceTurns();
 private final PlaybackGuard playback=new PlaybackGuard();private final VoiceEvents events=new VoiceEvents();private boolean replyComplete=true;private int spokenChunks;private String activeChunk="",completedReply="";private long ttsBlockedTurn=-1;private volatile long replayUntil;private int ttsUsage=-1;private volatile boolean speakerBargeIn;
 private final SpeechChunks splitter=new SpeechChunks();
 private final ArrayDeque<String> speechQueue=new ArrayDeque<>();
 private final ExecutorService warmWorker=Executors.newSingleThreadExecutor();
 private final ThreadPoolExecutor asrWorker=worker(),chatWorker=worker();
 private static ThreadPoolExecutor worker(){return new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1),new ThreadPoolExecutor.DiscardOldestPolicy());}
 private volatile CloudClient.Job recognition,generation,warming;
 private volatile boolean running,closed;private volatile boolean automaticBusy;
 private volatile ConversationMic microphone;
 private volatile boolean headphones,echoActive,outputBusy;
 private volatile long outputTail;
 private AudioDeviceCallback deviceCallback;
 private AudioManager.OnCommunicationDeviceChangedListener communicationCallback;
 private AudioDeviceInfo previousDevice;
 private TextToSpeech tts;private boolean ttsReady;private final SpeechDeadline speechDeadline=new SpeechDeadline();private final ExecutorService speechWorker=Executors.newSingleThreadExecutor();private volatile CloudClient.Job speechJob;private MediaPlayer fallbackPlayer;private String fallbackId="";private java.io.File fallbackFile;private final String speechPrefix="english-tts-"+UUID.randomUUID()+"-";private boolean utteranceStarted;
 private AudioManager audio;private int previousMode;private boolean previousSpeaker,ownsAudio;

 private StudioStore store;private long topicId;
 private String scene="日常聊天",utterance="";private int utteranceSequence;private long utteranceTurn=-1;
 private TextView status,heard,answer,metrics,route,inputInfo;private EditText typed;private Button manual,inputMode;private volatile boolean manualHeld;private long inputStarted;private int actualSource=MediaRecorder.AudioSource.VOICE_RECOGNITION;
 private ProgressBar level;private Button start,stop;private Switch quick,speakerDuplex;
 private long asrMs=-1,firstTokenMs=-1,ttsStartMs=-1,modelAt,firstTokenAt;private boolean tokenMeasured,ttsMeasured;
 private final AudioManager.OnAudioFocusChangeListener focus=change->{if(change<0)ui.post(()->stopSession("音频被其他应用占用，会话已暂停"));};
 private int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
 private TextView text(LinearLayout root,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff173d35);t.setPadding(0,dp(10),0,dp(10));root.addView(t);return t;}
 private Button button(LinearLayout root,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));return b;}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);store=new StudioStore(this);topicId=getIntent().getLongExtra("topic",0);JSONObject topic=store.session(topicId);if(topic==null){closed=true;finish();return;}scene=topic.optString("scene","日常聊天");
  audio=(AudioManager)getSystemService(AUDIO_SERVICE);getWindow().setStatusBarColor(0xffeff5f1);getWindow().setNavigationBarColor(0xffeff5f1);
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(16),dp(22),dp(24));root.setBackgroundColor(0xffeff5f1);scroll.addView(root);setContentView(scroll);
  button(root,"‹ 返回话题",this::finish);text(root,"全双工 · AI 外教",27);text(root,topic.optString("title")+" · 耳机可边听边说；外放可按按钮打断",16);
  status=text(root,"点开始，模型准备好后会持续收听英语",18);level=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);level.setMax(1000);root.addView(level);
  start=button(root,"开始连续对话",this::startSession);stop=button(root,"暂停收听与回复",()->stopSession("已暂停，麦克风已关闭"));stop.setEnabled(false);
  button(root,"立即打断外教",()->{if(running){turns.next();cancelTasks();resetSpeech();ConversationMic mic=microphone;if(mic!=null)mic.reset();automaticBusy=false;status.setText("已打断，可以直接说英语");}});
  manual=button(root,"按住说话 · 松开发送",()->{});manual.setEnabled(false);
  manual.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){ConversationMic mic=microphone;if(!running||mic==null)return true;manualHeld=true;v.getParent().requestDisallowInterceptTouchEvent(true);turns.next();cancelTasks();resetSpeech();automaticBusy=false;mic.beginManual();manual.setText("正在录音 · 松开立即发送");status.setText("正在录音，松开后直接交给 Whisper");return true;}
   if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(manualHeld){manualHeld=false;ConversationMic mic=microphone;if(mic!=null){if(action==MotionEvent.ACTION_UP)mic.finishManual();else mic.cancelManual();}manual.setText("按住说话 · 松开发送");v.getParent().requestDisallowInterceptTouchEvent(false);if(action==MotionEvent.ACTION_UP)v.performClick();}return true;}return manualHeld;});
  inputInfo=text(root,"麦克风信号和人声检测会显示在这里",13);
  inputMode=button(root,"录音输入："+sourceName(savedSource()),this::selectInputMode);
  typed=new EditText(this);typed.setHint("也可以输入英语，例如：I'd like a coffee.");typed.setSingleLine(false);typed.setMaxLines(3);root.addView(typed);button(root,"发送文字给外教",this::sendTyped);
  quick=new Switch(this);quick.setText("快速接话：静音约 0.4 秒后识别（关闭为约 0.6 秒）");quick.setChecked(getSharedPreferences("local_ai",0).getBoolean("quick_voice",true));root.addView(quick);quick.setOnCheckedChangeListener((v,on)->getSharedPreferences("local_ai",0).edit().putBoolean("quick_voice",on).apply());
  speakerBargeIn=getSharedPreferences("local_ai",0).getBoolean("speaker_barge_in",false);speakerDuplex=new Switch(this);speakerDuplex.setText("外放语音打断（实验；误打断时关闭）");speakerDuplex.setChecked(speakerBargeIn);root.addView(speakerDuplex);speakerDuplex.setOnCheckedChangeListener((v,on)->{speakerBargeIn=on;getSharedPreferences("local_ai",0).edit().putBoolean("speaker_barge_in",on).apply();updateRoute();});
  route=text(root,"建议使用耳机，避免扬声器声音进入麦克风",13);button(root,"选择通话音频设备",this::selectAudioDevice);metrics=text(root,"实际耗时：识别 — · 首字 — · TTS 启动 —",13);
  text(root,"你说",13);heard=text(root,"识别后会自动发送，无需再确认",20);heard.setTextIsSelectable(true);
  text(root,"外教",13);answer=text(root,"会先朗读生成的短句，同时继续生成与收听",20);answer.setTextIsSelectable(true);
  button(root,"重新朗读完整回复",this::replayAnswer);
  button(root,"复制本次延迟记录",()->{String report="Everyone English 4.4 · "+Build.MANUFACTURER+" "+Build.MODEL+"\n"+metrics.getText()+"\n"+inputInfo.getText()+"\n"+route.getText()+"\n"+events.report()+"\n静音判定约 "+(quick.isChecked()?384:608)+" ms（不计在识别耗时内）";((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("外教延迟记录",report));Toast.makeText(this,"延迟记录已复制",Toast.LENGTH_SHORT).show();});
  button(root,"离线英语语音包设置",()->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){status.setText("请在系统设置搜索文字转语音");}});
  text(root,"本地模式不调用收费 API。麦克风仅在此页面的活动会话中使用，离开页面即关闭。英语每次最多识别前 18 秒，建议一句一句说。延迟来自静音判断、Whisper、DeepSeek 和语音合成，具体以手机实测为准。",13);
  tts=new TextToSpeech(this,result->ui.post(()->{if(closed)return;if(result==TextToSpeech.SUCCESS){tts.setLanguage(Locale.US);ttsReady=OfflineVoice.select(tts);tts.setSpeechRate(.95f);configureTtsRoute();}if(!ttsReady)status.setText("未找到系统离线英语语音，将使用内置备用语音");}));
  tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
   public void onStart(String id){long at=SystemClock.elapsedRealtime();ui.post(()->{if(!valid(utteranceTurn)||!id.equals(utterance)||!speechDeadline.accepts(id,false))return;speechDeadline.started(id,false,at,35000);utteranceStarted=true;if(!ttsMeasured){ttsMeasured=true;ttsStartMs=at-firstTokenAt;updateMetrics();}events.add(at,"TTS_START "+id);status.setText(headphones||speakerBargeIn?"外教在说话，你可开口打断":"外教在说话 · 外放保护；可按按钮打断");});}
   public void onDone(String id){speechDone(id,false);}public void onError(String id){speechDone(id,true);}
   @Override public void onError(String id,int code){events.add(SystemClock.elapsedRealtime(),"TTS_ERROR code="+code);speechDone(id,true);}
   @Override public void onStop(String id,boolean interrupted){speechStopped(id,interrupted);}
  });
  deviceCallback=new AudioDeviceCallback(){public void onAudioDevicesAdded(AudioDeviceInfo[] d){updateRoute();}public void onAudioDevicesRemoved(AudioDeviceInfo[] d){updateRoute();}};audio.registerAudioDeviceCallback(deviceCallback,ui);
  if(Build.VERSION.SDK_INT>=31){communicationCallback=d->updateRoute();audio.addOnCommunicationDeviceChangedListener(command->ui.post(command),communicationCallback);}
  updateRoute();
 }
 private void updateMetrics(){metrics.setText("识别 "+ms(asrMs)+" · 首字 "+ms(firstTokenMs)+" · TTS 启动 "+ms(ttsStartMs));}
 private static String ms(long value){return value<0?"—":value+" ms";}
 private void startSession(){if(running||warming!=null||closed||microphone!=null)return;if(!LocalAI.supported()){status.setText("本地全双工需要 64 位 ARM 安卓手机");return;}if(!LocalModels.ready(this,LocalModels.DEEPSEEK)){startActivity(new Intent(this,LocalModelActivity.class));return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},361);return;}
  if(tts!=null){ttsReady=OfflineVoice.select(tts);}status.setText("预热 Whisper 和 DeepSeek，首次需要等待…");start.setEnabled(false);stop.setEnabled(true);quick.setEnabled(false);inputMode.setEnabled(false);CloudClient.Job job=new CloudClient.Job();warming=job;
  warmWorker.execute(()->{try{LocalAI.warm(getApplicationContext(),job);ui.post(()->{if(closed||warming!=job||job.cancelled())return;warming=null;beginCapture();});}catch(Exception e){ui.post(()->{if(closed||warming!=job)return;warming=null;stopSession(message(e));});}catch(OutOfMemoryError|UnsatisfiedLinkError e){ui.post(()->{if(!closed&&warming==job){warming=null;stopSession("本地模型准备失败，请检查模型或可用内存");}});}});
 }
 private void beginCapture(){try{
  if(audio.requestAudioFocus(focus,AudioManager.STREAM_VOICE_CALL,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new IllegalStateException("无法取得音频焦点，请稍后重试");
  previousMode=audio.getMode();previousSpeaker=audio.isSpeakerphoneOn();if(Build.VERSION.SDK_INT>=31)previousDevice=audio.getCommunicationDevice();ownsAudio=true;
  audio.setMode(AudioManager.MODE_IN_COMMUNICATION);updateRoute();
  running=true;turns.next();status.setText("正在打开麦克风…");start.setEnabled(false);stop.setEnabled(true);
  events.add(SystemClock.elapsedRealtime(),"SESSION_BEGIN");long[] capturedTurn={-1};ConversationMic[] holder=new ConversationMic[1];
  ConversationMic mic=new ConversationMic(LocalModels.file(this,LocalModels.VAD).getPath(),quick.isChecked()?360:600,savedSource(),.40f,
   aec->running&&!closed&&(!automaticBusy||speakerBargeIn&&(headphones||aec))&&SystemClock.elapsedRealtime()>=replayUntil&&playback.allows(headphones,aec,speakerBargeIn,SystemClock.elapsedRealtime()),
   new ConversationMic.Listener(){
    private boolean mine(){return running&&!closed&&microphone==holder[0];}
    public void ready(boolean aec,int source){ui.post(()->{if(mine()){actualSource=source;inputStarted=SystemClock.elapsedRealtime();manual.setEnabled(true);echoActive=aec;updateRoute();status.setText("正在收听；未自动接话时，按住说话再松开发送");}});}
    public void start(){if(!mine()||automaticBusy&&!manualHeld&&!speakerBargeIn)return;events.add(SystemClock.elapsedRealtime(),"VOICE_ONSET learner recording");long turn=turns.next();capturedTurn[0]=turn;cancelTasks();ui.post(()->{if(valid(turn)&&mine()){if(manualHeld||speakerBargeIn){resetSpeech();if(!manualHeld)playback.captureStarted();}asrMs=firstTokenMs=ttsStartMs=-1;tokenMeasured=ttsMeasured=false;updateMetrics();status.setText(manualHeld?"正在录音，松开后立即发送":"正在听你说，停顿后自动识别");}});}
    public void end(float[] pcm,boolean clipped){long turn=capturedTurn[0];if(mine()&&valid(turn)){automaticBusy=true;recognize(turn,pcm,clipped);}}
    public void meter(int value,double rms,float probability,boolean guarded,boolean silenced){ui.post(()->{if(mine()){level.setProgress(value);String state=silenced?"系统已静音录音输入":guarded?"外放回声保护":manualHeld?"手动录音":Float.isNaN(probability)?"等待检测":"人声 "+Math.round(probability*100)+"%";
     inputInfo.setText(sourceName(actualSource)+" · 信号 "+String.format(Locale.US,"%.0f dB",20*Math.log10(Math.max(rms,.000001)))+" · "+state);
     if(silenced&&!manualHeld)status.setText("系统正在屏蔽录音，请检查麦克风开关或占用麦克风的应用");
     else if(SystemClock.elapsedRealtime()-inputStarted>6000&&!manualHeld&&recognition==null&&generation==null&&utterance.isEmpty()&&capturedTurn[0]<0)status.setText(rms<.0001?"麦克风暂无有效信号；暂停后可切换录音输入":"未自动接话？按住说话，松开即可发送");
    }});}
    public void notice(String message){ui.post(()->{if(mine())status.setText(message);});}
    public void error(String message){ui.post(()->{if(mine())stopSession(message);});}
    public void released(){ui.post(()->{if(microphone==holder[0]){microphone=null;echoActive=false;manual.setEnabled(false);if(!closed&&!running&&warming==null)start.setEnabled(true);}});}
   });holder[0]=mic;microphone=mic;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);mic.start();
 }catch(Exception e){stopSession(message(e));}}
 private void recognize(long turn,float[] pcm,boolean clipped){
  final long ended=SystemClock.elapsedRealtime();CloudClient.Job job=turnJob(turn);recognition=job;
  ui.post(()->{if(valid(turn))status.setText(clipped?"正在识别前 18 秒；请尽量用短句":"Whisper 正在识别；环境声不会打断这一句…");});
  try{asrWorker.execute(()->{try{job.check();String sentence=LocalAI.transcribePCM(getApplicationContext(),pcm,job).getString("text").trim();job.check();
    if(sentence.isEmpty())throw new IllegalStateException("没有识别出英语，请重新说一句");
    long elapsed=SystemClock.elapsedRealtime()-ended;
    if(!turns.commit(turn,()->{if(running&&!closed)store.message(topicId,"user",sentence);}))return;
    ui.post(()->{if(valid(turn)&&!job.cancelled()){asrMs=elapsed;heard.setText(sentence);reply(turn,sentence);}});
   }catch(Exception e){ui.post(()->{if(valid(turn)&&!job.cancelled()){asrMs=SystemClock.elapsedRealtime()-ended;updateMetrics();automaticBusy=false;status.setText(message(e)+"；仍在收听，可直接重说");}});}
   catch(OutOfMemoryError|UnsatisfiedLinkError e){ui.post(()->{if(valid(turn))stopSession("本地识别失败，请检查模型或可用内存");});}
   finally{if(recognition==job)recognition=null;}});}catch(RejectedExecutionException ignored){}
 }
 private int savedSource(){return getSharedPreferences("local_ai",0).getInt("conversation_source",MediaRecorder.AudioSource.VOICE_RECOGNITION);}
 private static String sourceName(int source){return source==MediaRecorder.AudioSource.MIC?"普通麦克风":source==MediaRecorder.AudioSource.VOICE_COMMUNICATION?"通话回声模式":"清晰识别模式";}
 private void selectInputMode(){new AlertDialog.Builder(this).setTitle("录音输入（暂停后可切换）").setItems(new String[]{"清晰识别模式（默认）","普通麦克风（收不到声音时试试）","通话回声模式"},(d,n)->{int source=n==0?MediaRecorder.AudioSource.VOICE_RECOGNITION:n==1?MediaRecorder.AudioSource.MIC:MediaRecorder.AudioSource.VOICE_COMMUNICATION;getSharedPreferences("local_ai",0).edit().putInt("conversation_source",source).apply();inputMode.setText("录音输入："+sourceName(source));}).show();}
 private void sendTyped(){String sentence=typed.getText().toString().trim();if(sentence.isEmpty()){typed.setError("请输入一句英语");return;}if(!running){status.setText("请先点开始连续对话，等模型准备完成后发送");return;}long turn=turns.next();cancelTasks();resetSpeech();ConversationMic mic=microphone;if(mic!=null)mic.reset();manualHeld=false;manual.setText("按住说话 · 松开发送");asrMs=-1;heard.setText(sentence);typed.setText("");automaticBusy=true;
  // Keep database I/O off the UI thread and serialize it with turn invalidation.
  asrWorker.execute(()->{if(turns.commit(turn,()->{if(running&&!closed)store.message(topicId,"user",sentence);}))ui.post(()->{if(valid(turn))reply(turn,sentence);});});
 }
 private void updateRoute(){
  if(audio==null||route==null)return;headphones=false;String name=audio.isSpeakerphoneOn()?"扬声器":"听筒";
  try{if(Build.VERSION.SDK_INT>=31){AudioDeviceInfo device=audio.getCommunicationDevice();if(device!=null){int type=device.getType();name=device.getProductName().toString();headphones=type==AudioDeviceInfo.TYPE_WIRED_HEADSET||type==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||type==AudioDeviceInfo.TYPE_USB_HEADSET||type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||type==AudioDeviceInfo.TYPE_BLE_HEADSET;}}
   else headphones=audio.isWiredHeadsetOn()||audio.isBluetoothScoOn();
  }catch(SecurityException ignored){}
  configureTtsRoute();route.setText(name+" · "+(headphones?"耳机：边听边说":speakerBargeIn&&echoActive?"外放语音打断已开启，误打断时关闭":"外放保护：朗读期间用按钮打断，接耳机可边听边说"));
 }
 private void selectAudioDevice(){
  if(Build.VERSION.SDK_INT>=31){
   if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},372);return;}
   List<AudioDeviceInfo> options=audio.getAvailableCommunicationDevices();if(options.isEmpty()){status.setText("系统没有提供可选择的通话设备");return;}
   String[] names=new String[options.size()];for(int i=0;i<names.length;i++)names[i]=options.get(i).getProductName().toString()+" · "+deviceName(options.get(i).getType());
   new AlertDialog.Builder(this).setTitle("通话音频设备").setItems(names,(d,n)->{if(!audio.setCommunicationDevice(options.get(n)))status.setText("系统未能切换设备，请重新连接耳机");updateRoute();}).show();
  }else new AlertDialog.Builder(this).setTitle("选择手机输出").setItems(new String[]{"扬声器","听筒"},(d,n)->{audio.setSpeakerphoneOn(n==0);updateRoute();}).show();
 }
 private static String deviceName(int type){switch(type){case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:return "扬声器";case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:return "听筒";case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:case AudioDeviceInfo.TYPE_BLE_HEADSET:return "蓝牙通话";case AudioDeviceInfo.TYPE_WIRED_HEADSET:case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:return "有线耳机";case AudioDeviceInfo.TYPE_USB_HEADSET:return "USB 耳机";default:return "通话设备";}}
 private CloudClient.Job turnJob(long turn){return new CloudClient.Job(){@Override public void check(){super.check();if(!valid(turn))throw new CancellationException("已被新一轮对话替代");}};}
 private boolean valid(long turn){return running&&!closed&&turns.current(turn);}
 private void reply(long turn,String sentence){if(!valid(turn))return;automaticBusy=true;resetSpeech();firstTokenMs=ttsStartMs=-1;tokenMeasured=ttsMeasured=false;modelAt=SystemClock.elapsedRealtime();updateMetrics();replyComplete=false;completedReply="";ttsBlockedTurn=-1;spokenChunks=0;events.add(SystemClock.elapsedRealtime(),"GENERATION_BEGIN turn="+turn);status.setText("DeepSeek 正在回复，完成后继续收听…");CloudClient.Job job=turnJob(turn);generation=job;final long started=modelAt;generationProgress(turn,job,started);
  chatWorker.execute(()->{String[] partial={""};long[] lastPost={0};try{
   job.check();if(!valid(turn))return;JSONArray history=store.context(topicId);
   partial[0]=LocalAI.chatVoice(getApplicationContext(),"You are an English conversation tutor. Scenario: "+scene+". Reply only in simple English, under 45 words. Gently correct one error and ask one short question. Never assess pronunciation from text.",history,job,complete->{partial[0]=complete;long at=SystemClock.elapsedRealtime();if(at-lastPost[0]<40)return;lastPost[0]=at;ui.post(()->{if(valid(turn)&&!job.cancelled()){if(!tokenMeasured){tokenMeasured=true;events.add(at,"FIRST_VISIBLE_TEXT turn="+turn);firstTokenAt=at;firstTokenMs=at-started;updateMetrics();}answer.setText(complete);enqueue(turn,splitter.accept(complete,false));}});});
   String finalText=partial[0];job.check();if(!turns.commit(turn,()->{if(running&&!closed&&!job.cancelled())store.message(topicId,"assistant",finalText);}))return;ui.post(()->{if(valid(turn)&&!job.cancelled()){if(!tokenMeasured){tokenMeasured=true;firstTokenAt=SystemClock.elapsedRealtime();firstTokenMs=firstTokenAt-started;updateMetrics();}replyComplete=true;completedReply=finalText;events.add(SystemClock.elapsedRealtime(),"GENERATION_DONE turn="+turn);answer.setText(finalText);enqueue(turn,splitter.accept(finalText,true));finishPlayback(turn);if(utterance.isEmpty()&&speechQueue.isEmpty())status.setText("回复完成，继续听你说");}});
  }catch(Exception e){ui.post(()->{if(valid(turn)&&!job.cancelled()){resetSpeech();automaticBusy=false;status.setText(message(e)+"；仍在收听，可直接重说");}});}
  catch(OutOfMemoryError|UnsatisfiedLinkError e){ui.post(()->{if(valid(turn))stopSession("本地回复失败，请检查模型或可用内存");});}
  finally{if(generation==job)generation=null;}
  });
 }
 private void enqueue(long turn,List<String> chunks){if(!valid(turn)||ttsBlockedTurn==turn)return;for(String chunk:chunks){if(speechQueue.size()>=8){status.setText("朗读待播内容较多，后续回复保留为文字；你可以开口打断");break;}speechQueue.addLast(chunk);}speakNext(turn);}
 private void configureTtsRoute(){int usage=headphones?AudioAttributes.USAGE_VOICE_COMMUNICATION:AudioAttributes.USAGE_MEDIA;if(tts!=null&&ttsUsage!=usage){ttsUsage=usage;tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());}}
 private void generationProgress(long turn,CloudClient.Job job,long started){ui.postDelayed(()->{if(!valid(turn)||job.cancelled()||replyComplete)return;if(utterance.isEmpty())status.setText("DeepSeek 正在生成 · 已等待 "+((SystemClock.elapsedRealtime()-started)/1000)+" 秒"+(spokenChunks>0?" · 已朗读 "+spokenChunks+" 段":""));generationProgress(turn,job,started);},1000);}
 private void speakNext(long turn){if(!valid(turn)||!utterance.isEmpty()||speechQueue.isEmpty() )return;String chunk=speechQueue.removeFirst().replace("**","").replace("`","");String id="duplex:"+turn+":"+(++utteranceSequence);utterance=id;utteranceTurn=turn;activeChunk=chunk;utteranceStarted=false;speechDeadline.system(id,SystemClock.elapsedRealtime());watchSpeech(turn,id);outputBusy=true;playback.begin(turn);events.add(SystemClock.elapsedRealtime(),"TTS_QUEUED "+id+" chars="+chunk.length());if(!ttsReady){speakFallback(turn,id,chunk);return;}int result=tts.speak(chunk,TextToSpeech.QUEUE_ADD,null,id);if(result==TextToSpeech.ERROR){ttsReady=false;speakFallback(turn,id,chunk);return;}}

 private void finishPlayback(long turn){if(replyComplete&&utterance.isEmpty()&&speechQueue.isEmpty()){playback.finish(turn,SystemClock.elapsedRealtime());replayUntil=0;automaticBusy=false;ConversationMic mic=microphone;if(mic!=null)mic.reset();}}
 private void speechDone(String id,boolean failed){ui.post(()->{if(!speechDeadline.accepts(id,false))return;finishSpeech(id,failed);});}
 private void finishSpeech(String id,boolean failed){if(!valid(utteranceTurn)||!id.equals(utterance))return;events.add(SystemClock.elapsedRealtime(),(failed?"TTS_ERROR ":"TTS_DONE ")+id);long turn=utteranceTurn;if(failed&&ttsReady&&!activeChunk.isEmpty()){ttsReady=false;speakFallback(turn,id,activeChunk);return;}speechDeadline.clear();utterance="";activeChunk="";outputBusy=false;if(failed){ttsBlockedTurn=turn;replayUntil=0;speechQueue.clear();playback.finish(turn,SystemClock.elapsedRealtime());finishPlayback(turn);status.setText("朗读失败，点击重新朗读完整回复可重试");return;}spokenChunks++;speakNext(turn);finishPlayback(turn);if(utterance.isEmpty()&&replyComplete)status.setText("外教说完了，仍在听你说");}
 private void speechStopped(String id,boolean interrupted){ui.post(()->{if(!speechDeadline.accepts(id,false))return;if(!valid(utteranceTurn)||!id.equals(utterance))return;events.add(SystemClock.elapsedRealtime(),"TTS_STOP interrupted="+interrupted+" "+id);long turn=utteranceTurn;if(ttsReady&&!activeChunk.isEmpty()){ttsReady=false;speakFallback(turn,id,activeChunk);return;}ttsBlockedTurn=turn;replayUntil=0;utterance="";activeChunk="";outputBusy=false;speechQueue.clear();playback.finish(turn,SystemClock.elapsedRealtime());finishPlayback(turn);status.setText("系统停止了朗读；文字仍在生成，完成后可重新朗读完整回复");});}
 private void replayAnswer(){String text=completedReply;if(!running){status.setText("请先开始会话，再重新朗读");return;}if(!replyComplete){status.setText("回复还在生成，请等生成完成后重新朗读");return;}if(text.isEmpty()){status.setText("暂时没有可朗读的回复");return;}if(tts!=null)ttsReady=OfflineVoice.select(tts);long turn=turns.next();cancelTasks();resetSpeech();ConversationMic mic=microphone;if(mic!=null)mic.reset();replayUntil=SystemClock.elapsedRealtime()+120000;replyComplete=true;speechQueue.add(text);events.add(SystemClock.elapsedRealtime(),"REPLAY_COMPLETE_REPLY turn="+turn);speakNext(turn);}
 private void resetSpeech(){replyComplete=true;replayUntil=0;utteranceTurn=-1;speechDeadline.clear();outputBusy=false;outputTail=SystemClock.elapsedRealtime()+650;utterance="";activeChunk="";speechQueue.clear();splitter.clear();playback.clear(SystemClock.elapsedRealtime());if(tts!=null)tts.stop();fallbackId="";if(speechJob!=null)speechJob.cancel();if(fallbackFile!=null){fallbackFile.delete();fallbackFile=null;}if(fallbackPlayer!=null){try{fallbackPlayer.release();}catch(Exception ignored){}fallbackPlayer=null;}}
 private void cancelTasks(){CloudClient.Job job=recognition;if(job!=null)job.cancel();job=generation;if(job!=null)job.cancel();asrWorker.getQueue().clear();chatWorker.getQueue().clear();}
 private void stopSession(String reason){automaticBusy=false;events.add(SystemClock.elapsedRealtime(),"SESSION_STOP "+reason);running=false;turns.next();CloudClient.Job job=warming;warming=null;if(job!=null)job.cancel();cancelTasks();resetSpeech();
  ConversationMic mic=microphone;if(mic!=null)mic.close();manualHeld=false;if(manual!=null){manual.setEnabled(false);manual.setText("按住说话 · 松开发送");}
  if(ownsAudio){try{if(Build.VERSION.SDK_INT>=31){if(previousDevice==null)audio.clearCommunicationDevice();else audio.setCommunicationDevice(previousDevice);}audio.setSpeakerphoneOn(previousSpeaker);audio.setMode(previousMode);}catch(Exception ignored){}ownsAudio=false;}
  if(audio!=null)try{audio.abandonAudioFocus(focus);}catch(Exception ignored){}
  getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  if(start!=null){start.setEnabled(microphone==null);stop.setEnabled(false);quick.setEnabled(true);inputMode.setEnabled(true);level.setProgress(0);status.setText(reason+(microphone==null?"":" · 正在释放麦克风…"));}
 }
 private void speakFallback(long turn,String id,String text){
  if(!valid(turn)||!id.equals(utterance)||id.equals(fallbackId))return;
  fallbackId=id;speechDeadline.fallback(id,SystemClock.elapsedRealtime());status.setText("外教切换到内置离线语音，继续说完…");events.add(SystemClock.elapsedRealtime(),"ESPEAK_QUEUED "+id);
  CloudClient.Job job=turnJob(turn);speechJob=job;
  try{speechWorker.execute(()->{java.io.File wave=null;try{
   float[] pcm=IslandSpeech.synthesize(getApplicationContext(),"en-us",text,job);job.check();final long duration=pcm.length*1000L/16000;
   wave=java.io.File.createTempFile(speechPrefix, ".wav",getCacheDir());LessonAudio.writeSpeech(wave,pcm);job.check();final java.io.File file=wave;
   ui.post(()->{if(!valid(turn)||!id.equals(fallbackId)||!speechDeadline.accepts(id,true)||job.cancelled()){file.delete();return;}
    try{MediaPlayer player=new MediaPlayer();fallbackPlayer=player;fallbackFile=file;
     player.setAudioAttributes(new AudioAttributes.Builder().setUsage(headphones?AudioAttributes.USAGE_VOICE_COMMUNICATION:AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(file.getPath());
     player.setOnPreparedListener(p->{if(!valid(turn)||!id.equals(fallbackId)||fallbackPlayer!=p||!speechDeadline.accepts(id,true))return;try{
      p.start();speechDeadline.started(id,true,SystemClock.elapsedRealtime(),duration);if(!ttsMeasured){ttsMeasured=true;ttsStartMs=SystemClock.elapsedRealtime()-firstTokenAt;updateMetrics();}events.add(SystemClock.elapsedRealtime(),"ESPEAK_START "+id);status.setText("外教正在说话 · 内置离线语音");
     }catch(Exception e){fallbackDone(id,true);}});
     player.setOnCompletionListener(p->fallbackDone(id,false));player.setOnErrorListener((p,a,b)->{fallbackDone(id,true);return true;});player.prepareAsync();
    }catch(Exception e){file.delete();fallbackDone(id,true);}
   });
  }catch(Throwable e){if(wave!=null)wave.delete();if(!job.cancelled())fallbackDone(id,true);}finally{if(speechJob==job)speechJob=null;}});}catch(RejectedExecutionException e){fallbackDone(id,true);}
 }
 private void fallbackDone(String id,boolean failed){ui.post(()->{
  if(!id.equals(fallbackId)||!speechDeadline.accepts(id,true)||!id.equals(utterance))return;
  if(speechJob!=null){speechJob.cancel();speechJob=null;}
  if(fallbackPlayer!=null){try{fallbackPlayer.release();}catch(Exception ignored){}fallbackPlayer=null;}
  if(fallbackFile!=null){fallbackFile.delete();fallbackFile=null;}fallbackId="";finishSpeech(id,failed);
 });}
 private void watchSpeech(long turn,String id){ui.postDelayed(()->{
  if(!valid(turn)||!id.equals(utterance))return;
  if(speechDeadline.expired(id,SystemClock.elapsedRealtime())){
   events.add(SystemClock.elapsedRealtime(),"SPEECH_TIMEOUT "+id+" fallback="+speechDeadline.isFallback());
   if(speechDeadline.isFallback()){fallbackDone(id,true);return;}
   ttsReady=false;speakFallback(turn,id,activeChunk);if(tts!=null)tts.stop();
  }
  watchSpeech(turn,id);
 },1000);}
 private static String message(Exception e){return e.getMessage()==null?"本地对话暂时失败，请重试":e.getMessage();}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==372&&results.length>0){if(results[0]==PackageManager.PERMISSION_GRANTED)selectAudioDevice();else status.setText("未允许蓝牙控制，可使用手机或有线耳机");}if(request==361&&results.length>0){if(results[0]==PackageManager.PERMISSION_GRANTED)startSession();else status.setText("需要麦克风权限才能连续对话");}}
 @Override protected void onPause(){stopSession("已暂停，返回后点开始继续");super.onPause();}
 @Override protected void onDestroy(){closed=true;stopSession("已结束");if(audio!=null&&deviceCallback!=null)audio.unregisterAudioDeviceCallback(deviceCallback);if(Build.VERSION.SDK_INT>=31&&audio!=null&&communicationCallback!=null)audio.removeOnCommunicationDeviceChangedListener(communicationCallback);ui.removeCallbacksAndMessages(null);warmWorker.shutdownNow();asrWorker.shutdownNow();chatWorker.shutdownNow();speechWorker.shutdownNow();if(tts!=null)tts.shutdown();new Thread(()->{try{boolean done=warmWorker.awaitTermination(30,TimeUnit.SECONDS)&asrWorker.awaitTermination(30,TimeUnit.SECONDS)&chatWorker.awaitTermination(30,TimeUnit.SECONDS);speechWorker.awaitTermination(30,TimeUnit.SECONDS);java.io.File[] leftovers=getCacheDir().listFiles();if(leftovers!=null)for(java.io.File f:leftovers)if(f.getName().startsWith(speechPrefix))f.delete();if(done){LocalAI.releaseCaches();store.close();}}catch(InterruptedException ignored){}},"english-duplex-cleanup").start();super.onDestroy();}
}
