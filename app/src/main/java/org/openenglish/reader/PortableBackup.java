package org.openenglish.reader;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.*;
import java.util.*;
import org.json.*;

/** Learning JSON plus referenced media, native takes, legacy takes and safe preferences. */
public final class PortableBackup {
  private PortableBackup() {}
  private static PortableZip.Source bytes(String text) throws Exception {
    final byte[] data = text.getBytes("UTF-8"); return () -> new ByteArrayInputStream(data);
  }
  private static File owned(Context c, String path) throws IOException {
    File root = c.getFilesDir().getCanonicalFile(), file = new File(root, path).getCanonicalFile();
    if (!file.getPath().startsWith(root.getPath() + File.separator)) throw new IOException("材料路径不属于本应用");
    return file;
  }
  private static PortableZip.Source local(File file) {
    return () -> {
      if (!file.isFile()) throw new IOException("录音文件不在本机；请重新关联材料或使用 JSON 文字备份");
      return new FileInputStream(file);
    };
  }
  public static JSONObject settings(Context c) throws JSONException {
    SharedPreferences p = c.getSharedPreferences("studio", 0);
    return new JSONObject().put("daily_goal", p.getInt("daily_goal", 5))
        .put("playback_speed", p.getFloat("playback_speed", 1f)).put("blind_listening", p.getBoolean("blind_listening", false))
        .put("article_font", p.getInt("article_font", 21)).put("auto_speak", p.getBoolean("auto_speak", false))
        .put("last_material", p.getLong("last_material", 0)).put("reading_state", p.getString("reading_state", ReadingProgress.fresh().toString()));
  }
  private static void validateSettings(JSONObject s) throws JSONException {
    Set<String> allowed = new HashSet<>(Arrays.asList("daily_goal", "playback_speed", "blind_listening", "article_font", "auto_speak", "last_material", "reading_state"));
    Iterator<String> keys = s.keys(); while (keys.hasNext()) if (!allowed.contains(keys.next())) throw new JSONException("迁移偏好包含未知项目");
    if (!Arrays.asList(3,5,10,15).contains(s.getInt("daily_goal")) || !Arrays.asList(18,21,24,28).contains(s.getInt("article_font"))) throw new JSONException("学习偏好无效");
    double speed = s.getDouble("playback_speed");
    if (speed != .5 && speed != .75 && speed != 1 && speed != 1.25 && speed != 1.5) throw new JSONException("播放速度无效");
    if(s.has("reading_state"))ReadingProgress.validate(s.getString("reading_state"));
    s.getBoolean("blind_listening"); s.getBoolean("auto_speak"); if (s.getLong("last_material") < 0) throw new JSONException("材料编号无效");
  }
  public static File legacyDirectory(Context c, LearningStore store) {
    try (Cursor cursor = store.getReadableDatabase().rawQuery("SELECT root FROM portable_state WHERE id=1", null)) {
      if (cursor.moveToFirst()) {
        String root = cursor.getString(0);
        if (root.matches("restored-media/[0-9a-f-]{36}")) return new File(c.getFilesDir(), root + "/recordings");
      }
    } catch (Exception ignored) {}
    return new File(c.getFilesDir(), "recordings");
  }

  public static void export(Context c, Uri target, CloudClient.Job job, PortableZip.Progress progress) throws Exception {
    StudioStore db = new StudioStore(c); File archive = File.createTempFile("portable-export-", ".zip", c.getCacheDir());
    try {
      JSONObject snapshot;
      SQLiteDatabase sql = db.getWritableDatabase(); sql.beginTransaction();
      try { snapshot = db.backup(); sql.setTransactionSuccessful(); } finally { sql.endTransaction(); }
      snapshot.put("portable", 1); snapshot.put("magic", 1);
      Map<String, PortableZip.Source> files = new LinkedHashMap<>();
      JSONArray materials = snapshot.getJSONArray("materials");
      for (int i=0;i<materials.length();i++) {
        JSONObject m = materials.getJSONObject(i); if (m.getString("type").equals("text")) continue;
        String name = "media/material-" + m.getLong("id") + ".bin"; Uri uri = Uri.parse(m.getString("uri"));
        if ("file".equals(uri.getScheme())) {
          File actual = new File(uri.getPath()).getCanonicalFile();
          if (!actual.getPath().startsWith(c.getFilesDir().getCanonicalPath() + File.separator)) throw new IOException("本地材料路径无效，请重新关联文件");
          files.put(name, local(actual));
        } else if ("content".equals(uri.getScheme())) files.put(name, () -> {
          try { return c.getContentResolver().openInputStream(uri); }
          catch (Exception e) { throw new IOException("材料读取失败，请重新关联文件后再导出完整迁移包"); }
        });
        else throw new IOException("材料没有可读取的本地文件，请先重新关联");
        m.put("uri", "portable:" + name);
      }
      JSONArray practices = snapshot.getJSONArray("practice");
      for (int i=0;i<practices.length();i++) {
        String path = practices.getJSONObject(i).getString("recording");
        if (!path.isEmpty()) {
          if (!PortableZip.allowed(path) || !path.startsWith("studio-recordings/")) throw new IOException("录音路径无效");
          files.put(path, local(owned(c, path)));
        }
      }
      File[] legacy = legacyDirectory(c, db).listFiles();
      if (legacy != null) for (File file : legacy) {
        String name = "recordings/" + file.getName();
        if (file.isFile() && PortableZip.allowed(name)) files.put(name, local(file));
      }
      JSONObject magic = MagicBackup.snapshot(c);
      files.put("magic.json", bytes(magic.toString()));
      JSONArray magicTakes = magic.getJSONArray("takes");
      for(int i=0;i<magicTakes.length();i++){String path=magicTakes.getString(i);String id=path.substring("language-takes/".length(),path.length()-4);files.put(path,local(MagicBackup.take(c,id)));}
      files.put("learning.json", bytes(snapshot.toString()));
      files.put("settings.json", bytes(settings(c).toString()));
      try (OutputStream out = new FileOutputStream(archive)) { PortableZip.write(out, files, job::check, progress); }
      // Publish only a complete archive; a provider-copy failure can leave a partial document.
      try (InputStream in = new FileInputStream(archive); OutputStream out = c.getContentResolver().openOutputStream(target, "wt")) {
        if (out == null) throw new IOException("此保存位置无法写入");
        byte[] buffer = new byte[65536]; int n;
        while ((n=in.read(buffer))!=-1) { job.check(); out.write(buffer,0,n); }
      }
      job.check();
    } finally { archive.delete(); db.close(); }
  }

  private static JSONObject json(File file, int max) throws Exception {
    return new JSONObject(AiClient.read(new FileInputStream(file), max));
  }
  public static boolean applyRestoredSettings(Context c, StudioStore db) {
    try (Cursor cursor = db.getReadableDatabase().rawQuery("SELECT root,settings FROM portable_state WHERE id=1", null)) {
      if (!cursor.moveToFirst()) return true;
      String root = cursor.getString(0);
      if(root.matches("restored-media/[0-9a-f-]{36}")){
        File magicFile=new File(c.getFilesDir(),root+"/magic.json");
        if(magicFile.isFile())MagicBackup.apply(c,json(magicFile,4*1024*1024),root);
      }
      SharedPreferences p = c.getSharedPreferences("studio", 0);
      if (root.equals(p.getString("portable_applied", ""))) return true;
      JSONObject s = new JSONObject(cursor.getString(1)); validateSettings(s);
      boolean saved=p.edit().putInt("daily_goal", s.getInt("daily_goal")).putFloat("playback_speed", (float)s.getDouble("playback_speed"))
          .putBoolean("blind_listening", s.getBoolean("blind_listening")).putInt("article_font", s.getInt("article_font"))
          .putBoolean("auto_speak", s.getBoolean("auto_speak")).putLong("last_material", s.getLong("last_material"))
          .putString("reading_state",s.optString("reading_state",p.getString("reading_state",ReadingProgress.fresh().toString()))).putBoolean("seeded_v3", true).putString("portable_applied", root).commit();
      return saved;
    } catch (Exception e) { return false; }
  }

  public static void restore(Context c, Uri source, CloudClient.Job job, PortableZip.Progress progress) throws Exception {
    String token = UUID.randomUUID().toString();
    File stage = new File(c.getFilesDir(), ".portable-stage-" + token);
    if (!stage.mkdirs()) throw new IOException("无法创建恢复暂存目录");
    String relativeRoot = "restored-media/" + token;
    File installed = new File(c.getFilesDir(), relativeRoot); List<File> movedTakes = new ArrayList<>();
    String validationName = "portable-validation-" + token + ".db";
    StudioStore validation = null, db = null; boolean committed = false;
    try {
      Map<String, PortableZip.Receipt> receipts;
      try (InputStream in = c.getContentResolver().openInputStream(source)) {
        if (in == null) throw new IOException("迁移包无法读取");
        receipts = PortableZip.read(in, stage, job::check, progress);
      }
      JSONObject data = json(new File(stage,"learning.json"), 20*1024*1024);
      JSONObject settings = json(new File(stage,"settings.json"), 65536); validateSettings(settings);
      if (data.getInt("portable")!=1 || data.getInt("schema")!=2 || !"org.openenglish.reader".equals(data.getString("app"))) throw new JSONException("不是本应用的完整迁移包");
      Set<String> referenced = new HashSet<>(Arrays.asList("learning.json","settings.json","manifest.tsv"));
      if(data.has("magic")){
        if(data.getInt("magic")!=1||!receipts.containsKey("magic.json"))throw new IOException("魔法岛备份缺失");
        JSONObject magic=json(new File(stage,"magic.json"),4*1024*1024);MagicBackup.validate(magic);referenced.add("magic.json");JSONArray a=magic.getJSONArray("takes");
        for(int i=0;i<a.length();i++){String path=a.getString(i);if(!receipts.containsKey(path))throw new IOException("魔法岛录音关联缺失");LocalWav.read(new File(stage,path));referenced.add(path);}
      }
      JSONArray materials = data.getJSONArray("materials");
      for (int i=0;i<materials.length();i++) {
        JSONObject m = materials.getJSONObject(i); if (m.getString("type").equals("text")) continue;
        String name = "media/material-" + m.getLong("id") + ".bin";
        if (!m.getString("uri").equals("portable:"+name) || !receipts.containsKey(name)) throw new IOException("材料媒体关联缺失");
        referenced.add(name); m.put("uri", Uri.fromFile(new File(installed,name)).toString());
      }
      JSONArray practices = data.getJSONArray("practice"); Map<String,String> takeNames = new LinkedHashMap<>();
      for (int i=0;i<practices.length();i++) {
        JSONObject p = practices.getJSONObject(i); String path=p.getString("recording"); if (path.isEmpty()) continue;
        if (!receipts.containsKey(path) || !path.startsWith("studio-recordings/") || !PortableZip.allowed(path)) throw new IOException("训练录音关联缺失");
        referenced.add(path);
        if (!takeNames.containsKey(path)) takeNames.put(path, "studio-recordings/r-"+token+"-"+takeNames.size()+".m4a");
        p.put("recording",takeNames.get(path));
      }
      for (String name : receipts.keySet()) if (!referenced.contains(name) && !name.startsWith("recordings/")) throw new IOException("迁移包包含未关联的媒体");
      // Validate the complete restore against a disposable database before touching current rows.
      validation = new StudioStore(c,validationName); validation.restore(data); validation.close(); validation=null; c.deleteDatabase(validationName);
      job.check();
      File parent=installed.getParentFile(); if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建媒体目录");
      if (!stage.renameTo(installed)) throw new IOException("无法安装恢复媒体");
      File takes=new File(c.getFilesDir(),"studio-recordings"); if (!takes.isDirectory() && !takes.mkdirs()) throw new IOException("无法创建录音目录");
      for (Map.Entry<String,String> entry:takeNames.entrySet()) {
        job.check(); File destination=owned(c,entry.getValue());
        if (destination.exists() || !new File(installed,entry.getKey()).renameTo(destination)) throw new IOException("无法安装训练录音");
        movedTakes.add(destination);
      }
      db=new StudioStore(c); SQLiteDatabase sql=db.getWritableDatabase();
      // Once commit begins, cancel/Activity destruction cannot misreport a successful restore.
      job.beginCommit(); progress.update("正在完成恢复",receipts.size(),0);
      sql.beginTransaction();
      try {
        db.restore(data);
        if (sql.insertWithOnConflict("portable_state",null,StudioStore.values("id",1,"root",relativeRoot,"settings",settings.toString()),SQLiteDatabase.CONFLICT_REPLACE) < 0) throw new IOException("恢复状态无法保存");
        sql.setTransactionSuccessful();
      } finally { sql.endTransaction(); }
      committed=true;
      if(!applyRestoredSettings(c,db))throw new IOException("学习数据已安装，偏好恢复尚未完成。请释放存储空间并重新打开应用，继续完成恢复。");
    } finally {
      if (validation!=null) validation.close(); c.deleteDatabase(validationName);
      if (db!=null) db.close();
      PortableZip.deleteTree(stage);
      if (!committed) { for (File file:movedTakes) file.delete(); PortableZip.deleteTree(installed); }
    }
  }
}
