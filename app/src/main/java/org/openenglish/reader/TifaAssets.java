package org.openenglish.reader;
import android.content.Context;
import java.io.*;
import java.security.*;
import org.json.*;

/** Checked, cancellable extraction. Never downloads audio or sends it to a server. */
public final class TifaAssets {
 private TifaAssets(){}
 public static JSONObject json(Context c,String name)throws Exception{
  try(InputStream in=c.getAssets().open("languages/"+name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}
 }
 public static File model(Context c,String name,LocalTifa.Control control)throws Exception{
  if(!name.equals("model.onnx")&&!name.equals("spectrogram.onnx"))throw new IOException("模型名称无效");
  JSONObject info=json(c,"tifa-manifest.json").getJSONObject(name);String hash=info.getString("sha256");long size=info.getLong("bytes");
  File dir=new File(c.getFilesDir(),"tifa-1.0-st");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("模型目录不可用，请释放存储空间");
  File f=new File(dir,hash+"-"+name);if(f.isFile()&&f.length()==size)return f;File temp=new File(dir,name+".part");
  try{
   MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
   try(InputStream in=c.getAssets().open("languages/tifa/"+name);OutputStream out=new FileOutputStream(temp)){
    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){control.check();out.write(b,0,n);digest.update(b,0,n);count+=n;}
   }
   StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.US,"%02x",b&255));
   if(count!=size||!hex.toString().equals(hash))throw new IOException("纠音模型校验失败，请重新安装");control.check();if(!temp.renameTo(f))throw new IOException("纠音模型无法保存，请释放存储空间");return f;
  }finally{temp.delete();}
 }
}
