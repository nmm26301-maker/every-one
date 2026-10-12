package org.openenglish.reader;
import android.content.Context;
import android.os.Build;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.json.*;

/** Real on-device ASR and tutor generation; never silently falls back to a paid service. */
public final class LocalAI {
 private static final Object WHISPER_LOCK=new Object(),LLAMA_LOCK=new Object();
 private LocalAI() {}
 public static String islandTranscribe(Context c,float[] pcm,String language,CloudClient.Job job)throws Exception {
  synchronized(WHISPER_LOCK){device();job.check();if(!LocalWav.hasSignal(pcm))throw new IOException("没有有效声音，请重新说一句");LocalNative.loadWhisper();String result=LocalNative.whisperLanguage(LocalModels.multilingual(c,job).getPath(),pcm,2,"ja".equals(language)?"ja":"zh",callback(job,s->{}));job.check();if(result==null||result.trim().isEmpty())throw new IOException("没有识别出文字，请按住说话或改用文字输入");return result.trim();}
 }
 private static LocalNative.Callbacks callback(CloudClient.Job job,CloudClient.Delta delta){return new LocalNative.Callbacks(){public boolean abort(){try{job.check();return false;}catch(CancellationException e){return true;}}public void segment(long a,long b,String s){}public void text(String s){String visible=LocalPrompt.visible(s);if(!visible.isEmpty())delta.text(visible);}};}
 public static String islandChat(Context c,String instruction,JSONArray history,CloudClient.Job job,CloudClient.Delta delta)throws Exception {
  synchronized(LLAMA_LOCK){device();job.check();LocalModels.Spec spec=LocalModels.islandModel(c);if(!LocalModels.ready(c,spec))throw new IOException("先安装选中的 "+spec.name);List<String[]> messages=new ArrayList<>();for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i);messages.add(new String[]{m.optString("role"),m.optString("content")});}
   String prompt=spec!=LocalModels.DEEPSEEK?IslandPrompt.qwen(instruction,messages):IslandPrompt.deepseek(instruction,messages);LocalNative.loadLlama();String reply=LocalNative.chat(LocalModels.file(c,spec).getPath(),prompt,3,160,callback(job,delta));job.check();reply=LocalPrompt.visible(reply);if(reply.isEmpty())throw new IOException("模型没有生成可用回复，请再试一句");delta.text(reply);return reply;}
 }
 public interface WarmProgress {void stage(String value);}
 public static void warmIsland(Context c,CloudClient.Job job)throws Exception {warmIsland(c,job,true,s->{});}
 public static void warmIsland(Context c,CloudClient.Job job,boolean voice,WarmProgress progress)throws Exception {
  device();job.check();
  if(voice){progress.stage("准备人声检测");LocalModels.vad(c,job);job.check();progress.stage("准备三语 Whisper");synchronized(WHISPER_LOCK){job.check();LocalNative.loadWhisper();LocalNative.warmWhisper(LocalModels.multilingual(c,job).getPath(),callback(job,s->{}));}job.check();}
  LocalModels.Spec spec=LocalModels.islandModel(c);if(!LocalModels.ready(c,spec))throw new IOException("请先安装 "+spec.name);progress.stage("载入 "+spec.name);synchronized(LLAMA_LOCK){job.check();LocalNative.loadLlama();LocalNative.warmLlama(LocalModels.file(c,spec).getPath(),callback(job,s->{}));}job.check();progress.stage("本地老师已就绪");
 }


 public static boolean enabled(Context c){return c.getSharedPreferences("local_ai",0).getBoolean("enabled",true);}
 public static boolean supported(){return Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");}
 private static void device()throws IOException {if(!supported())throw new IOException("本地 AI 需要 64 位 ARM 安卓手机");}
 public static JSONObject transcribe(Context c,File wav,boolean timestamps,CloudClient.Job job)throws Exception {
  return transcribePCM(c,LocalWav.read(wav),job);
 }
 public static JSONObject transcribePCM(Context c,float[] pcm,CloudClient.Job job)throws Exception {
  synchronized(WHISPER_LOCK){device();job.check();if(!LocalWav.hasSignal(pcm))throw new IOException("录音没有有效声音，请靠近麦克风重新录制");
   File model=LocalModels.whisper(c,job);try{LocalNative.loadWhisper();}catch(UnsatisfiedLinkError e){throw new IOException("本地 Whisper 引擎无法加载，请安装完整新版 APK");}
   JSONArray segments=new JSONArray();String raw=LocalNative.whisper(model.getPath(),pcm,2,new LocalNative.Callbacks(){
    public boolean abort(){try{job.check();return false;}catch(CancellationException e){return true;}}
    public void text(String complete){}
    public void segment(long start,long end,String text){String t=text.trim();if(t.isEmpty() || end<=start || start<0)return;try{segments.put(new JSONObject().put("start",start/1000.0).put("end",Math.min(end,pcm.length/16L)/1000.0).put("text",t));}catch(JSONException e){throw new IllegalStateException(e);}}
   });job.check();if(raw==null || raw.trim().isEmpty())throw new IOException("Whisper 没有识别出英文，请确认录音语言和清晰度");
   return new JSONObject().put("text",raw.trim()).put("segments",segments).put("engine","whisper.cpp-local");
  }
 }
 public static String chat(Context c,String instruction,JSONArray history,CloudClient.Job job,CloudClient.Delta delta)throws Exception {
  return chatImpl(c,instruction,history,job,delta,false);
 }
 public static String chatVoice(Context c,String instruction,JSONArray history,CloudClient.Job job,CloudClient.Delta delta)throws Exception {return chatImpl(c,instruction,history,job,delta,true);}
 private static String chatImpl(Context c,String instruction,JSONArray history,CloudClient.Job job,CloudClient.Delta delta,boolean voice)throws Exception {
  synchronized(LLAMA_LOCK){device();job.check();if(!LocalModels.ready(c,LocalModels.DEEPSEEK))throw new IOException("请先在‘本地 AI 模型’下载或导入 DeepSeek 1.5B 模型");
   List<String[]> messages=new ArrayList<>();for(int i=0;i<history.length();i++){JSONObject item=history.getJSONObject(i);messages.add(new String[]{item.optString("role"),item.optString("content")});}
   String prompt=LocalPrompt.build(instruction,messages);try{LocalNative.loadLlama();}catch(UnsatisfiedLinkError e){throw new IOException("本地 DeepSeek 引擎无法加载，请安装完整新版 APK");}
   LocalNative.Callbacks callbacks=new LocalNative.Callbacks(){
    public boolean abort(){try{job.check();return false;}catch(CancellationException e){return true;}}
    public void segment(long start,long end,String text){}
    public void text(String complete){String visible=LocalPrompt.visible(complete);if(!visible.isEmpty())delta.text(visible);}
   };String path=LocalModels.file(c,LocalModels.DEEPSEEK).getPath();String reply=voice?LocalNative.chatFast(path,prompt,3,160,callbacks):LocalNative.chat(path,prompt,4,320,callbacks);job.check();reply=LocalPrompt.visible(reply);if(reply.isEmpty())throw new IOException("本地模型未生成可用回复，请缩短问题再试");delta.text(reply);return reply;
  }
 }
 public static void warm(Context c,CloudClient.Job job)throws Exception {
  device();LocalModels.vad(c,job);LocalNative.Callbacks cb=new LocalNative.Callbacks(){public boolean abort(){try{job.check();return false;}catch(CancellationException e){return true;}}public void text(String s){}public void segment(long a,long b,String s){}};
  LocalNative.loadWhisper();LocalNative.warmWhisper(LocalModels.whisper(c,job).getPath(),cb);job.check();
  if(!LocalModels.ready(c,LocalModels.DEEPSEEK))throw new IOException("请先安装 DeepSeek 模型");LocalNative.loadLlama();LocalNative.warmLlama(LocalModels.file(c,LocalModels.DEEPSEEK).getPath(),cb);job.check();
 }
 public static void releaseCaches(){try{LocalNative.releaseWhisper();}catch(UnsatisfiedLinkError ignored){}try{LocalNative.releaseLlama();}catch(UnsatisfiedLinkError ignored){}}
}
