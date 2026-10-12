package org.openenglish.reader;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import org.json.*;

/** Model setup stays on the phone; the only network operation is the chosen model download. */
public final class LocalModelActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private CloudClient.Job task;
 private TextView status, models;
 private ProgressBar progress;
 private Button download,importButton,test,prepare,cancel;
 private volatile long lastProgress;
 private static final int IMPORT=351;
 private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
 private void label(LinearLayout root,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff173b36);t.setPadding(0,dp(10),0,dp(10));root.addView(t);}
 private Button button(LinearLayout root,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));return b;}
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xffeff5f1);getWindow().setNavigationBarColor(0xffeff5f1);
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(20),dp(22),dp(30));root.setBackgroundColor(0xffeff5f1);scroll.addView(root);setContentView(scroll);
  button(root,"‹ 返回学习",this::finish);label(root,"免费的本地 AI 外教",27);
  label(root,"Whisper 听你说英语，DeepSeek 纠正表达并接着聊。模型安装后，对话和转写都在手机内运行，不需要账号或 API 密钥。",16);
  Switch mode=new Switch(this);mode.setText("使用手机本地 AI（默认）");mode.setChecked(LocalAI.enabled(this));root.addView(mode);
  mode.setOnCheckedChangeListener((v,yes)->{getSharedPreferences("local_ai",0).edit().putBoolean("enabled",yes).apply();status.setText(yes?"已选择本地 AI":"已选择你配置的云服务；服务商可能收费。返回学习设置填写连接。");});
  models=new TextView(this);models.setTextSize(15);models.setPadding(0,dp(18),0,dp(16));root.addView(models);
  prepare=button(root,"准备内置 Whisper（约 78 MB）",()->run(j->LocalModels.whisper(getApplicationContext(),j),"准备 Whisper"));
  download=button(root,"下载 / 继续下载 DeepSeek（约 1.12 GB）",()->new AlertDialog.Builder(this).setTitle("安装外教模型")
   .setMessage("将下载指定的 DeepSeek-R1 1.5B Q4_K_M 文件，建议使用 Wi-Fi，并至少空出 1.3 GB。下载可取消，下次继续；完成后校验文件。")
   .setPositiveButton("开始下载",(d,n)->run(j->LocalModels.download(getApplicationContext(),LocalModels.DEEPSEEK,j,this::update),"下载模型"))
   .setNegativeButton("返回",null).show());
  importButton=button(root,"从手机文件导入 DeepSeek GGUF",()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException e){status.setText("请先安装系统文件选择器");}});
  button(root,"浏览器打开指定模型文件",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(LocalModels.DEEPSEEK.url)));}catch(ActivityNotFoundException e){status.setText("未找到浏览器");}});
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(14)));
  status=new TextView(this);status.setTextSize(15);status.setTextIsSelectable(true);status.setPadding(0,dp(12),0,dp(12));root.addView(status);
  cancel=button(root,"取消当前任务（下载可续传）",()->{if(task!=null){task.cancel();status.setText("正在停止…");}});
  test=button(root,"测试本地外教",()->run(j->{JSONArray h=new JSONArray().put(new JSONObject().put("role","user").put("content","I goed to school yesterday. Please correct my sentence."));String reply=LocalAI.chat(getApplicationContext(),"You are an English tutor. Correct the learner's English in one short sentence.",h,j,answer->ui.post(()->{if(!isDestroyed())status.setText(answer);}));update(reply,1,1);},"加载本地外教…"));
  button(root,"释放本地模型内存（不删除模型文件）",()->{if(task==null)run(j->LocalAI.releaseCaches(),"释放内存");});
  label(root,"Whisper tiny.en 只识别英语。DeepSeek 是 1.5B 蒸馏模型，能力和速度与云端大模型有差距；长文只带入最近内容。首次加载可能较慢，可随时停止。",14);
  label(root,"需要 64 位 ARM 安卓手机，建议至少 4 GB 内存。朗读使用系统英语 TTS；要完全离线，请先安装离线英语语音包。本地转写不提供发音准确度评分。",14);
  label(root,"下载受网络条件影响。若下载站无法访问，可把指定 GGUF 文件复制到手机，再点“导入”。导入必须通过固定长度和 SHA-256 校验，不接受其他模型。",14);
  refresh();status.setText(LocalAI.supported()?"先准备 Whisper，再下载或导入 DeepSeek；返回“对话”新建话题，点击“全双工外教”即可持续交流。":"这台设备不支持 arm64-v8a 本地引擎，可选择云服务。");
 }
 private void refresh(){models.setText("Whisper："+(LocalModels.ready(this,LocalModels.WHISPER)?"已准备":"已内置，首次使用解包")+"\nDeepSeek："+(LocalModels.ready(this,LocalModels.DEEPSEEK)?"已校验并安装":"待下载 / 导入")+"\n可用存储："+String.format(java.util.Locale.US,"%.2f GB",getFilesDir().getUsableSpace()/1e9));boolean idle=task==null;boolean supported=LocalAI.supported();prepare.setEnabled(idle&&supported);download.setEnabled(idle&&supported);importButton.setEnabled(idle&&supported);test.setEnabled(idle&&supported&&LocalModels.ready(this,LocalModels.DEEPSEEK));cancel.setEnabled(!idle);}
 private void update(String text,long bytes,long total){long now=SystemClock.elapsedRealtime();if(bytes!=total&&now-lastProgress<250)return;lastProgress=now;ui.post(()->{if(isDestroyed())return;status.setText(total>1?text+" · "+String.format(java.util.Locale.US,"%.1f / %.1f MB",bytes/1e6,total/1e6):text);progress.setProgress(total>0?(int)Math.min(1000,bytes*1000/total):0);});}
 private interface Work{void execute(CloudClient.Job job)throws Exception;}
 private void run(Work work,String title){if(task!=null)return;CloudClient.Job job=new CloudClient.Job();task=job;lastProgress=0;status.setText(title);progress.setProgress(0);refresh();Context app=getApplicationContext();worker.execute(()->{try{work.execute(job);ui.post(()->{if(!isDestroyed()&&!job.cancelled()&&!title.startsWith("加载"))status.setText(title.startsWith("释放")?"已释放模型缓存，下一次会重新加载。":"模型已准备，可以返回对话练习。");});}catch(Exception e){ui.post(()->{if(!isDestroyed())status.setText(job.cancelled()?"已取消。下载进度保留，可继续下载。":e.getMessage()==null?"操作失败，请重试":e.getMessage());});}catch(OutOfMemoryError e){ui.post(()->{if(!isDestroyed())status.setText("内存不足，请关闭其他应用后重试。");});}finally{ui.post(()->{if(!isDestroyed()){if(task==job)task=null;refresh();}});}});}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==IMPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();run(j->LocalModels.importModel(getApplicationContext(),LocalModels.DEEPSEEK,uri,j,this::update),"导入并校验模型");}}
 @Override protected void onDestroy(){if(task!=null)task.cancel();worker.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
