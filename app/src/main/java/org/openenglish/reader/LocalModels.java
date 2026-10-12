package org.openenglish.reader;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.security.*;

/** Fixed, hash-verified models. Installation is atomic; cancelled download parts may resume. */
public final class LocalModels {
 public static final class Spec {
  public final String id,name,file,url,sha;public final long bytes;
  Spec(String id,String name,String file,String url,String sha,long bytes){this.id=id;this.name=name;this.file=file;this.url=url;this.sha=sha;this.bytes=bytes;}
 }
 public static final Spec WHISPER=new Spec("whisper","Whisper tiny.en · 英语离线识别","ggml-tiny.en.bin","https://huggingface.co/ggerganov/whisper.cpp/resolve/80da2d8bfee42b0e836fc3a9890373e5defc00a6/ggml-tiny.en.bin","921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f",77704715L);
 public static final Spec DEEPSEEK=new Spec("deepseek","DeepSeek-R1 1.5B · Q4_K_M","deepseek-r1-1.5b-q4.gguf","https://huggingface.co/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/e98af4b201848f8d674d4ae74eb199702d119e43/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf","f3bdf9cf31dee4b57ae4e455a1cb0d01b5c2c1b50d72d3112141c195506c2840",1117321312L);
 public static final Spec VAD=new Spec("vad","Silero VAD 6.2 · 流式人声检测","ggml-silero-v6.2.0.bin","https://huggingface.co/ggml-org/whisper-vad/resolve/9ffd54a1e1ee413ddf265af9913beaf518d1639b/ggml-silero-v6.2.0.bin","2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987",885098L);
 public static final Spec MULTI=new Spec("whisper_multi","Whisper base · 三语离线识别","ggml-base.bin","https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin","60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",147951465L);
 public static final Spec QWEN_SMALL=new Spec("qwen_small","Qwen2.5 0.5B · 轻量对话","qwen2.5-0.5b-instruct-q4_k_m.gguf","https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf","74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",491400032L);
 public static final Spec QWEN=new Spec("qwen","Qwen2.5 1.5B · 推荐对话","qwen2.5-1.5b-instruct-q4_k_m.gguf","https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf","6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",1117320736L);
 public static Spec islandModel(Context c){String id=c.getSharedPreferences("language_magic",0).getString("model","qwen");return id.equals("deepseek")?DEEPSEEK:id.equals("qwen_small")?QWEN_SMALL:QWEN;}
 public static File multilingual(Context c,CloudClient.Job job)throws Exception{return bundled(c,MULTI,job);}
 public interface Progress {void update(String status,long bytes,long total);}
 private LocalModels() {}
 public static File file(Context c,Spec model){return new File(new File(c.getFilesDir(),"local-models"),model.file);}
 public static boolean ready(Context c,Spec model){File f=file(c,model);return f.isFile() && f.length()==model.bytes;}
 private static File part(Context c,Spec model)throws IOException {File target=file(c,model);if(!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())throw new IOException("无法创建模型目录");return new File(target.getPath()+".part");}
 private static void space(Context c,long size)throws IOException {if(c.getFilesDir().getUsableSpace()<size+64L*1024*1024)throw new IOException("存储空间不足，请至少空出约 1.3 GB");}
 private static void verify(Context c,Spec m,File part,CloudClient.Job job,Progress progress)throws Exception {
  if(part.length()!=m.bytes)throw new IOException("模型长度不符，未安装");MessageDigest hash=MessageDigest.getInstance("SHA-256");long read=0;
  try(InputStream in=new FileInputStream(part)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1){job.check();hash.update(b,0,n);read+=n;progress.update("校验模型",read,m.bytes);}}
  StringBuilder digest=new StringBuilder();for(byte x:hash.digest())digest.append(String.format(java.util.Locale.US,"%02x",x&255));
  if(!m.sha.equals(digest.toString())){part.delete();throw new IOException("模型校验失败，请重新下载官方文件");}
  job.check();if(!part.renameTo(file(c,m)))throw new IOException("无法安装模型；原有模型保留");
 }
 public static synchronized File whisper(Context c,CloudClient.Job job)throws Exception {return bundled(c,WHISPER,job);}
 public static synchronized File vad(Context c,CloudClient.Job job)throws Exception {return bundled(c,VAD,job);}
 private static File bundled(Context c,Spec model,CloudClient.Job job)throws Exception {
  if(ready(c,model))return file(c,model);File part=part(c,model);space(c,model.bytes);
  try(InputStream in=c.getAssets().open("models/"+model.file);OutputStream out=new FileOutputStream(part)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){job.check();out.write(b,0,n);}}
  verify(c,model,part,job,(s,n,t)->{});return file(c,model);
 }
 public static synchronized void importModel(Context c,Spec m,Uri uri,CloudClient.Job job,Progress progress)throws Exception {
  File importing=new File(part(c,m).getPath()+".import");space(c,m.bytes);long count=0;
  try {
   try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(importing)){if(in==null)throw new IOException("无法读取模型文件");byte[] b=new byte[262144];int n;
    while((n=in.read(b))!=-1){job.check();count+=n;if(count>m.bytes)throw new IOException("模型过大，请选择指定的模型文件");out.write(b,0,n);progress.update("导入模型",count,m.bytes);}}
   verify(c,m,importing,job,progress);
  }finally{if(importing.isFile())importing.delete();}
 }
 public static synchronized void download(Context c,Spec m,CloudClient.Job job,Progress progress)throws Exception {
  if(ready(c,m))return;File part=part(c,m);long offset=part.length();if(offset>m.bytes){part.delete();offset=0;}space(c,m.bytes-offset);
  if(offset==m.bytes){verify(c,m,part,job,progress);return;}
  HttpURLConnection connection=null;URL url=new URL(m.url);
  try{
   for(int redirects=0;redirects<9;redirects++){
    job.check();if(!"https".equals(url.getProtocol()))throw new IOException("模型下载必须使用 HTTPS");
    connection=(HttpURLConnection)url.openConnection();job.attach(connection);connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestProperty("Accept-Encoding","identity");if(offset>0)connection.setRequestProperty("Range","bytes="+offset+"-");
    int code=connection.getResponseCode();if(code==301 || code==302 || code==303 || code==307 || code==308){String next=connection.getHeaderField("Location");if(next==null)throw new IOException("模型下载重定向缺失");URL target=new URL(url,next);connection.disconnect();connection=null;url=target;continue;}
    if(code!=200 && code!=206)throw new IOException("模型下载返回 HTTP "+code+"，可改用文件导入");
    if(code==206){String range=connection.getHeaderField("Content-Range");if(range==null || !range.matches("bytes "+offset+"-[0-9]+/"+m.bytes))throw new IOException("模型续传范围不符");}
    else offset=0;
    long count=offset;
    try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(part,offset>0)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){job.check();count+=n;if(count>m.bytes)throw new IOException("下载文件过大");out.write(b,0,n);progress.update("下载模型",count,m.bytes);}}
    verify(c,m,part,job,progress);return;
   }throw new IOException("模型下载重定向过多");
  }finally{if(connection!=null)connection.disconnect();}
 }
}
