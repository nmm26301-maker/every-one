package org.openenglish.validation;

import android.app.Instrumentation;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.openenglish.reader.*;

/** Disposable private fixture only. Never reads or restores a user's learning database. */
public class PortableValidation extends Instrumentation {
  int checks;
  void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  public void onCreate(Bundle b){super.onCreate(b);start();}
  static final class Isolated extends ContextWrapper {
    final File root;final String prefix;final Set<String> prefs=new HashSet<>();
    Isolated(Context c)throws Exception{super(c);prefix="portable-test-"+UUID.randomUUID();root=new File(c.getFilesDir(),prefix);if(!root.mkdirs())throw new IOException("fixture directory");}
    File directory(String name){File f=new File(root,name);f.mkdirs();return f;}
    public File getFilesDir(){return directory("files");}
    public File getCacheDir(){return directory("cache");}
    public File getDatabasePath(String name){return new File(directory("databases"),name);}
    public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory);}
    public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
    public boolean deleteDatabase(String name){return SQLiteDatabase.deleteDatabase(getDatabasePath(name));}
    public SharedPreferences getSharedPreferences(String name,int mode){prefs.add(name);return super.getSharedPreferences(prefix+"-"+name,mode);}
    void clean(){for(String name:prefs)super.getSharedPreferences(prefix+"-"+name,0).edit().clear().commit();PortableZip.deleteTree(root);}
  }
  static void write(File file,byte[] bytes)throws Exception{file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}}
  static byte[] read(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
  public void onStart(){Bundle result=new Bundle();Isolated c=null;StudioStore db=null;
    try{
      c=new Isolated(getTargetContext());db=new StudioStore(c);
      byte[] media;try(InputStream in=c.getAssets().open("studio-demo.m4a");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);media=out.toByteArray();}
      File original=new File(c.getFilesDir(),"original.m4a");write(original,media);
      long id=db.addMaterial("audio","fixture audio",Uri.fromFile(original).toString(),"");
      db.updateMaterial(id,StudioStore.values("cues","[{\"start\":0,\"end\":2000,\"text\":\"Take your time\"}]","position",500));
      File take=new File(c.getFilesDir(),"studio-recordings/take-fixture.m4a");write(take,media);
      db.practice(id,0,"Take your time","Take time","studio-recordings/take-fixture.m4a","");
      File legacy=new File(c.getFilesDir(),"recordings/sentence_0.m4a");write(legacy,media);
      db.add("word","fixture-word","fixture");long topic=db.addSession("fixture topic","日常聊天");db.draft(topic,"unsent fixture");
      c.getSharedPreferences("studio",0).edit().putInt("daily_goal",10).putFloat("playback_speed",.75f).putBoolean("blind_listening",true).commit();
      c.getSharedPreferences("language_magic",0).edit().putString("lang","yue").putInt("stars",9).putStringSet("favorites",new HashSet<>(Arrays.asList("yue_si3"))).commit();
      new IslandMemory(c,"yue").complete("奶茶","少甜？","茶餐厅");new IslandReview(c).rate("yue_si3",0);
      float[] magicPcm=new float[16000];for(int i=0;i<magicPcm.length;i++)magicPcm[i]=(float)Math.sin(i*.1)*.2f;LessonAudio.write(MagicBackup.take(c,"yue_si3"),magicPcm);byte[] magicAudio=read(MagicBackup.take(c,"yue_si3"));
      File archive=new File(c.getCacheDir(),"portable.zip");PortableBackup.export(c,Uri.fromFile(archive),new CloudClient.Job(),(n,f,b)->{});
      check(archive.length()>0,"archive created");
      db.add("word","after-export","not in backup");write(take,new byte[]{9});write(legacy,new byte[]{9});
      c.getSharedPreferences("studio",0).edit().putInt("daily_goal",3).commit();
      c.getSharedPreferences("language_magic",0).edit().putInt("stars",77).commit();new IslandMemory(c,"yue").clear();
      PortableBackup.restore(c,Uri.fromFile(archive),new CloudClient.Job(),(n,f,b)->{});
      check(db.items("word").length()==1,"learning rows restored");
      check(c.getSharedPreferences("language_magic",0).getInt("stars",0)==9,"magic progress restored");check(new IslandMemory(c,"yue").scenePairs("茶餐厅",1).length()==1,"scene memory restored");check(new IslandReview(c).card("yue_si3").lapses==1,"magic review restored");check(Arrays.equals(read(MagicBackup.take(c,"yue_si3")),magicAudio),"magic audio restored via installed root");
      check(db.practiceCount()==1,"practice restored");
      JSONObject m=db.material(id);File restored=new File(Uri.parse(m.getString("uri")).getPath());
      check(Arrays.equals(read(restored),media),"material bytes restored and URI relocated");
      check(m.getInt("position")==500,"saved position restored");
      JSONObject p=db.practices(id,0).getJSONObject(0);File restoredTake=new File(c.getFilesDir(),p.getString("recording"));
      check(Arrays.equals(read(restoredTake),media),"practice file relocated");
      check(Arrays.equals(read(take),new byte[]{9}),"old take not overwritten");
      check(Arrays.equals(read(new File(PortableBackup.legacyDirectory(c,db),"sentence_0.m4a")),media),"legacy recording mapping restored");
      check(Arrays.equals(read(legacy),new byte[]{9}),"old legacy files not overwritten");
      check(c.getSharedPreferences("studio",0).getInt("daily_goal",0)==10,"daily goal restored");
      check(c.getSharedPreferences("studio",0).getFloat("playback_speed",0)==.75f,"speed restored");
      check(db.session(topic).getString("draft").equals("unsent fixture"),"topic draft restored");
      c.getSharedPreferences("studio",0).edit().putInt("daily_goal",3).commit();PortableBackup.applyRestoredSettings(c,db);
      check(c.getSharedPreferences("studio",0).getInt("daily_goal",0)==3,"preferences only applied once per restore");
      String root=db.query("SELECT root FROM portable_state WHERE id=1").getJSONObject(0).getString("root");
      CloudClient.Job cancelled=new CloudClient.Job();cancelled.cancel();boolean failed=false;
      try{PortableBackup.restore(c,Uri.fromFile(archive),cancelled,(n,f,b)->{});}catch(java.util.concurrent.CancellationException e){failed=true;}
      check(failed && root.equals(db.query("SELECT root FROM portable_state WHERE id=1").getJSONObject(0).getString("root")),"cancelled restore preserves active mapping");
      File second=new File(c.getCacheDir(),"second.zip");PortableBackup.export(c,Uri.fromFile(second),new CloudClient.Job(),(n,f,b)->{});
      PortableBackup.restore(c,Uri.fromFile(second),new CloudClient.Job(),(n,f,b)->{});
      check(db.practiceCount()==1 && Arrays.equals(read(new File(c.getFilesDir(),db.practices(id,0).getJSONObject(0).getString("recording"))),media),"re-export and second restore preserve relocated audio");
      check(Arrays.equals(read(MagicBackup.take(c,"yue_si3")),magicAudio),"magic recording re-export and second restore");
      // Strip the optional extension to construct a valid pre-4.2 archive.
      File legacyStage=new File(c.getCacheDir(),"legacy-stage");legacyStage.mkdirs();try(InputStream in=new FileInputStream(second)){PortableZip.read(in,legacyStage,()->{},(n,f,b)->{});}
      JSONObject oldData=new JSONObject(new String(read(new File(legacyStage,"learning.json")),"UTF-8"));oldData.remove("magic");write(new File(legacyStage,"learning.json"),oldData.toString().getBytes("UTF-8"));Map<String,PortableZip.Source> oldFiles=new LinkedHashMap<>();java.util.List<File> pending=new ArrayList<>();pending.add(legacyStage);while(!pending.isEmpty()){File f=pending.remove(0);if(f.isDirectory()){for(File child:f.listFiles())pending.add(child);}else{String name=legacyStage.toPath().relativize(f.toPath()).toString();if(!name.equals("magic.json")&&!name.startsWith("language-takes/")&&!name.equals("manifest.tsv"))oldFiles.put(name,()->new FileInputStream(f));}}
      File oldArchive=new File(c.getCacheDir(),"old.zip");try(OutputStream out=new FileOutputStream(oldArchive)){PortableZip.write(out,oldFiles,()->{},(n,f,b)->{});}c.getSharedPreferences("language_magic",0).edit().putInt("stars",88).commit();PortableBackup.restore(c,Uri.fromFile(oldArchive),new CloudClient.Job(),(n,f,b)->{});check(c.getSharedPreferences("language_magic",0).getInt("stars",0)==88&&new IslandMemory(c,"yue").count()==1,"old package preserves current magic progress and memory");
      result.putString("stream","PASS: "+checks+" isolated Android portable integration checks\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");finish(0,result);}
    finally{if(db!=null)db.close();if(c!=null)c.clean();}
  }
}
