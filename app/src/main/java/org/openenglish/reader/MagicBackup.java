package org.openenglish.reader;

import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Optional v4.2 extension to the checksummed portable package. No models or API secrets. */
public final class MagicBackup {
 private static final String[] NAMES={"language_magic","island_memory","island_review"};
 private MagicBackup(){}
 private static boolean allowed(String group,String key){
  if(group.equals("language_magic"))return Arrays.asList("lang","last_lesson","favorites","completed","stars","history","middle_hz").contains(key)||key.matches("done_[0-9]{4}-[0-9]{2}-[0-9]{2}")||key.matches("last_scene_(yue|ja|zh)")||(key.startsWith("scene_")&&key.length()<=550);
  if(group.equals("island_memory"))return key.matches("(yue|ja|zh)_(note|pairs)");
  return group.equals("island_review")&&key.matches("[a-zA-Z0-9_-]{1,100}");
 }
 public static File take(Context c,String id)throws IOException{
  if(!id.matches("[a-zA-Z0-9_-]{1,100}"))throw new IOException("课程录音编号无效");
  String root=c.getSharedPreferences("language_magic",0).getString("_take_root","");
  if(!root.isEmpty()&&!root.matches("restored-media/[0-9a-f-]{36}"))throw new IOException("恢复录音目录无效");
  return new File(c.getFilesDir(),(root.isEmpty()?"":root+"/")+"language-takes/"+id+".wav");
 }
 private static JSONObject encode(Map<String,?> values,String group)throws JSONException{
  JSONObject out=new JSONObject();for(Map.Entry<String,?> e:values.entrySet())if(allowed(group,e.getKey())){
   Object v=e.getValue();String type=v instanceof Set?"set":v instanceof Integer?"int":v instanceof Long?"long":v instanceof Float?"float":v instanceof String?"string":"invalid";
   out.put(e.getKey(),new JSONObject().put("type",type).put("value",v instanceof Set?new JSONArray((Set<?>)v):v));
  }return out;
 }
 public static JSONObject snapshot(Context c)throws Exception{
  JSONObject groups=new JSONObject();for(String name:NAMES)groups.put(name,encode(c.getSharedPreferences(name,0).getAll(),name));
  JSONArray takes=new JSONArray();File dir=take(c,"probe").getParentFile();File[] files=dir.listFiles();if(files!=null){Arrays.sort(files);for(File f:files)if(f.isFile()&&f.getName().matches("[a-zA-Z0-9_-]{1,100}\\.wav"))takes.put("language-takes/"+f.getName());}
  JSONObject out=new JSONObject().put("version",1).put("preferences",groups).put("takes",takes);validate(out);return out;
 }
 public static void validate(JSONObject data)throws Exception{
  if(data.getInt("version")!=1||data.toString().getBytes("UTF-8").length>4*1024*1024)throw new IOException("魔法岛备份版本或大小无效");
  JSONObject groups=data.getJSONObject("preferences");if(groups.length()!=3)throw new IOException("魔法岛学习数据缺失");
  for(String name:NAMES){JSONObject group=groups.getJSONObject(name);if(group.length()>5000)throw new IOException("学习偏好过多");Iterator<String> keys=group.keys();while(keys.hasNext()){
   String key=keys.next();if(!allowed(name,key))throw new IOException("学习备份包含未知偏好");JSONObject e=group.getJSONObject(key);String type=e.getString("type");Object v=e.get("value");
   String expected=name.equals("island_memory")||name.equals("island_review")?"string":Arrays.asList("favorites","completed").contains(key)||key.startsWith("done_")?"set":key.equals("stars")?"int":key.equals("middle_hz")?"float":key.startsWith("scene_")?"long":"string";
   if(!expected.equals(type))throw new IOException("学习偏好类型无效");
   if(type.equals("string")){if(!(v instanceof String)||((String)v).length()>650000)throw new IOException("学习文字大小无效");String text=(String)v;
    if(name.equals("island_memory")){if(key.endsWith("_note")){if(text.length()>500)throw new IOException("学习偏好过长");}else{JSONArray pairs=new JSONArray(text);if(pairs.length()>300)throw new IOException("记忆轮数过多");for(int i=0;i<pairs.length();i++){JSONObject p=pairs.getJSONObject(i);if(p.getString("user").length()>1000||p.getString("assistant").length()>1000||p.getString("scene").length()>500||p.getLong("time")<0)throw new IOException("对话记忆无效");}}}
    else if(name.equals("island_review")){JSONObject card=new JSONObject(text);double ease=card.getDouble("ease");if(card.getLong("due")<0||card.getInt("interval")<0||card.getInt("interval")>36500||(Double.isNaN(ease)||Double.isInfinite(ease))||ease<1.3||ease>10000||card.getInt("reps")<0||card.getInt("lapses")<0)throw new IOException("复习计划无效");}
    else if(key.equals("history")){if(new JSONArray(text).length()>30)throw new IOException("练习历史过多");}else if(text.length()>500||key.equals("lang")&&!Arrays.asList("yue","ja","zh").contains(text))throw new IOException("学习偏好文字无效");
   }else if(type.equals("set")){JSONArray a=e.getJSONArray("value");if(a.length()>1000)throw new IOException("课程列表过长");Set<String> seen=new HashSet<>();for(int i=0;i<a.length();i++){String id=a.getString(i);if(!id.matches("[a-zA-Z0-9_-]{1,100}")||!seen.add(id))throw new IOException("课程列表无效");}}
   else {if(!(v instanceof Number))throw new IOException("学习偏好数值无效");double n=((Number)v).doubleValue();if((Double.isNaN(n)||Double.isInfinite(n))||n<0||n>Long.MAX_VALUE||!type.equals("float")&&n!=Math.rint(n)||type.equals("int")&&n>Integer.MAX_VALUE||type.equals("float")&&(n<60||n>500))throw new IOException("学习偏好数值范围无效");}
  }}
  JSONArray takes=data.getJSONArray("takes");if(takes.length()>1000)throw new IOException("录音数量过多");Set<String> seen=new HashSet<>();for(int i=0;i<takes.length();i++){String path=takes.getString(i);if(!path.matches("language-takes/[a-zA-Z0-9_-]{1,100}\\.wav")||!seen.add(path))throw new IOException("课程录音路径无效");}
 }
 private static boolean replace(SharedPreferences p,JSONObject group,String root)throws Exception{
  SharedPreferences.Editor e=p.edit().clear();Iterator<String> keys=group.keys();while(keys.hasNext()){String key=keys.next();JSONObject v=group.getJSONObject(key);switch(v.getString("type")){
   case "string":e.putString(key,v.getString("value"));break;case "int":e.putInt(key,v.getInt("value"));break;case "long":e.putLong(key,v.getLong("value"));break;case "float":e.putFloat(key,(float)v.getDouble("value"));break;
   case "set":JSONArray a=v.getJSONArray("value");Set<String> set=new HashSet<>();for(int i=0;i<a.length();i++)set.add(a.getString(i));e.putStringSet(key,set);break;default:throw new IOException("未知偏好类型");
  }}if(root!=null)e.putString("_take_root",root).putString("_restore_applied",root);return e.commit();
 }
 /** Pending DB restore is retried on startup; the marker is committed last. */
 public static void apply(Context c,JSONObject data,String root)throws Exception{
  validate(data);if(!root.matches("restored-media/[0-9a-f-]{36}"))throw new IOException("学习恢复编号无效");
  SharedPreferences magic=c.getSharedPreferences("language_magic",0);if(root.equals(magic.getString("_restore_applied","")))return;
  JSONObject groups=data.getJSONObject("preferences");
  // Retry is idempotent after process death. Only launch learning after these commits succeed.
  for(String name:new String[]{"island_memory","island_review","language_magic"})if(!replace(c.getSharedPreferences(name,0),groups.getJSONObject(name),name.equals("language_magic")?root:null))throw new IOException("魔法岛数据尚未写入，请释放存储空间后重新打开应用");
 }
}
