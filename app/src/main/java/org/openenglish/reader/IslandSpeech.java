package org.openenglish.reader;
import android.content.*;import java.io.*;import java.util.zip.*;
/** Bundled GPL-3 eSpeak NG fallback. System voices are preferred for natural sound. */
public final class IslandSpeech {
 private static boolean loaded;private IslandSpeech(){}
 public static synchronized File data(Context c)throws IOException{File dir=new File(c.getFilesDir(),"island-speech-152");File ready=new File(dir,"ready");if(ready.isFile())return dir;if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建离线语音目录");try(ZipInputStream z=new ZipInputStream(c.getAssets().open("languages/espeak-data.zip"))){ZipEntry e;byte[] b=new byte[8192];while((e=z.getNextEntry())!=null){File p=new File(dir,e.getName());if(!p.getCanonicalPath().startsWith(dir.getCanonicalPath()+File.separator))throw new IOException("语音路径无效");if(e.isDirectory()){p.mkdirs();continue;}p.getParentFile().mkdirs();try(OutputStream o=new FileOutputStream(p)){int n;while((n=z.read(b))!=-1)o.write(b,0,n);}}}try(OutputStream out=new FileOutputStream(ready)){out.write(1);}return dir;}
 public static float[] synthesize(Context c,String language,String text,CloudClient.Job job)throws Exception{job.check();synchronized(IslandSpeech.class){if(!loaded){System.loadLibrary("english_speech");loaded=true;}}if(language.equals("ja"))text=JapaneseReading.kana(c,text);String dir=data(c).getPath();return render(dir,language.equals("zh")?"cmn":language,text,new LocalNative.Callbacks(){public boolean abort(){return job.cancelled();}public void text(String s){}public void segment(long a,long b,String s){}});}
 public static native float[] render(String directory,String language,String text,LocalNative.Callbacks callback)throws IOException;
}
