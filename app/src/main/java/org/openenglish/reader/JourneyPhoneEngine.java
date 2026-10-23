package org.openenglish.reader;

import ai.onnxruntime.*;
import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline audio-to-phone inference through the app's existing ONNX Runtime. */
public final class JourneyPhoneEngine {
 private static final AtomicBoolean RUNNING=new AtomicBoolean();
 public interface Progress{void update(long bytes,long total);}
 public static final class Control {
  private volatile boolean cancelled;private OrtSession.RunOptions run;private HttpURLConnection connection;private InputStream input;
  public void cancel(){
   OrtSession.RunOptions r;HttpURLConnection h;InputStream in;
   synchronized(this){if(cancelled)return;cancelled=true;r=run;h=connection;in=input;run=null;connection=null;input=null;}
   release(r,h,in);
  }
  // Provider close/disconnect and native termination must never block the UI thread.
  private static void release(OrtSession.RunOptions r,HttpURLConnection h,InputStream in){
   if(r==null&&h==null&&in==null)return;
   Thread cleanup=new Thread(()->{
    if(r!=null)try{r.setTerminate(true);}catch(Exception ignored){}
    if(h!=null)try{h.disconnect();}catch(Exception ignored){}
    if(in!=null)try{in.close();}catch(Exception ignored){}
   },"journey-phone-cancel");cleanup.setDaemon(true);cleanup.start();
  }
  public void check(){if(cancelled||Thread.currentThread().isInterrupted())throw new CancellationException("已停止开源纠音");}
  synchronized void attach(OrtSession.RunOptions r){check();run=r;}
  synchronized void attach(HttpURLConnection c){if(cancelled||Thread.currentThread().isInterrupted()){release(null,c,null);check();}connection=c;}
  synchronized void attach(InputStream in){if(cancelled||Thread.currentThread().isInterrupted()){release(null,null,in);check();}input=in;}
  synchronized void clear(){run=null;connection=null;input=null;}
 }
 public static JSONObject asset(Context c,String name)throws Exception{try(InputStream in=c.getAssets().open("journey/"+name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>2000000)throw new IOException("音素配置过大");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}}
 public static File modelFile(Context c){return new File(c.getNoBackupFilesDir(),"openpronounce-en-int8.onnx");}
 public static boolean installed(Context c){try{return modelFile(c).isFile()&&modelFile(c).length()==asset(c,"phone-model.json").getLong("bytes");}catch(Exception e){return false;}}
 public static void supported()throws IOException{if(Build.VERSION.SDK_INT<24||!Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a"))throw new IOException("开源离线纠音需要 Android 7 或以上的 64 位 ARM 手机");}
 private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
 private static String digest(File file,Control c)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(file))){byte[] b=new byte[131072];int n;while((n=in.read(b))!=-1){c.check();d.update(b,0,n);}}return hex(d.digest());}
 /** Only fixed package metadata may choose the URL or checksum. No remote manifest execution. */
 public static void download(Context ctx,Control c,Progress progress)throws Exception{
  supported();if(!RUNNING.compareAndSet(false,true))throw new IOException("上一次纠音任务正在释放，请稍后重试");
  File target=modelFile(ctx),partial=new File(target.getPath()+".part");HttpURLConnection connection=null;
  try{JSONObject meta=asset(ctx,"phone-model.json");long size=meta.getLong("bytes");if(target.getParentFile().getUsableSpace()<size+64*1024*1024)throw new IOException("空间不足，请至少腾出模型大小加 64 MB 的空间");
   URL url=new URL(meta.getString("url"));
   for(int redirects=0;;redirects++){
    if(!url.getProtocol().equals("https"))throw new IOException("模型地址必须使用 HTTPS");
    c.check();connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(20000);connection.setReadTimeout(20000);c.attach(connection);int code=connection.getResponseCode();
    if(code>=300&&code<400){if(redirects>=5)throw new IOException("模型下载重定向过多");String next=connection.getHeaderField("Location");if(next==null)throw new IOException("模型下载地址无效");url=new URL(url,next);connection.disconnect();continue;}
    if(code!=200)throw new IOException("模型下载失败（"+code+"），可稍后重试或从文件导入");break;
   }
   try(InputStream in=connection.getInputStream()){c.attach(in);copyChecked(in,partial,meta,c,progress);}
   c.check();if(!partial.renameTo(target))throw new IOException("模型安装失败，请检查存储空间");
  }finally{if(connection!=null)connection.disconnect();c.clear();partial.delete();RUNNING.set(false);}
 }
 public static void importModel(Context ctx,InputStream input,Control c,Progress progress)throws Exception{
  try(InputStream in=input){
   supported();c.check();if(!RUNNING.compareAndSet(false,true))throw new IOException("请等待上一次任务结束");
   File partial=null;
   try{c.attach(in);File target=modelFile(ctx);partial=new File(target.getPath()+".part");JSONObject meta=asset(ctx,"phone-model.json");if(target.getParentFile().getUsableSpace()<meta.getLong("bytes")+64*1024*1024)throw new IOException("空间不足，无法导入模型");copyChecked(in,partial,meta,c,progress);c.check();if(!partial.renameTo(target))throw new IOException("模型保存失败");}
   finally{c.clear();if(partial!=null)partial.delete();RUNNING.set(false);}
  }
 }
 private static void copyChecked(InputStream in,File partial,JSONObject meta,Control c,Progress progress)throws Exception{
  long total=meta.getLong("bytes"),count=0,last=0;MessageDigest hash=MessageDigest.getInstance("SHA-256");
  try(OutputStream out=new BufferedOutputStream(new FileOutputStream(partial))){byte[] b=new byte[131072];int n;while((n=in.read(b))!=-1){c.check();count+=n;if(count>total)throw new IOException("模型大小不匹配，已停止安装");hash.update(b,0,n);out.write(b,0,n);if(count-last>1048576){progress.update(count,total);last=count;}}}
  if(count!=total||!hex(hash.digest()).equals(meta.getString("sha256")))throw new IOException("模型校验不通过，未安装此文件；请重新下载");progress.update(total,total);
 }
 public static JSONObject assess(Context c,float[] pcm,String text,Control control)throws Exception{
  supported();if(!RUNNING.compareAndSet(false,true))throw new IOException("上一次纠音任务正在释放，请稍后重试");
  try{File model=modelFile(c);JSONObject meta=asset(c,"phone-model.json");if(!model.isFile())throw new IOException("请先在设置中安装开源纠音模型，安装后可离线使用");if(!digest(model,control).equals(meta.getString("sha256")))throw new IOException("纠音模型校验失败，请重新安装");
   JSONObject ref=asset(c,"phone-reference.json").optJSONObject(text);if(ref==null)throw new IOException("当前离线纠音支持内置课程和易混音练习，请从这两个入口练习");
   JourneyPhones core=new JourneyPhones(asset(c,"phone-rules.json"));return core.compare(core.decode(infer(model,pcm,control)),ref);
  }catch(UnsatisfiedLinkError e){throw new IOException("这台设备无法加载音素引擎，可使用录音回听或已配置的云端评测");}
  catch(OutOfMemoryError e){throw new IOException("运行内存不足，请关闭其他应用后用更短的句子重试");}
  finally{control.clear();RUNNING.set(false);}
 }
 public static float[] normalize(float[] pcm){
  if(pcm.length<8000||pcm.length>192000)throw new IllegalArgumentException("请录 0.5 至 12 秒清楚的声音");double mean=0,var=0;for(float x:pcm){if(!Float.isFinite(x)||Math.abs(x)>1.01)throw new IllegalArgumentException("录音格式无效");mean+=x;}mean/=pcm.length;for(float x:pcm)var+=(x-mean)*(x-mean);var/=pcm.length;if(var<1e-7)throw new IllegalArgumentException("声音过轻，请靠近麦克风重录");float[] data=new float[pcm.length];double scale=Math.sqrt(var+1e-7);for(int i=0;i<pcm.length;i++)data[i]=(float)((pcm[i]-mean)/scale);return data;
 }
 public static float[][] infer(File model,float[] pcm,Control c)throws Exception{
  float[] input=normalize(pcm);c.check();OrtEnvironment env=OrtEnvironment.getEnvironment();
  try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
   options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
   try(OrtSession session=env.createSession(model.getAbsolutePath(),options);OnnxTensor tensor=OnnxTensor.createTensor(env,new float[][]{input});OrtSession.RunOptions run=new OrtSession.RunOptions()){
    c.attach(run);try(OrtSession.Result result=session.run(Collections.singletonMap("input_values",tensor),Collections.singleton("logits"),run)){c.check();return ((float[][][])result.get("logits").get().getValue())[0];}finally{c.clear();}
   }
  }
 }
}
