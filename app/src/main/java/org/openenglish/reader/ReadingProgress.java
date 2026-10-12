package org.openenglish.reader;
import org.json.*;
import java.util.*;
public final class ReadingProgress {
 private ReadingProgress(){}
 public static JSONObject fresh()throws JSONException{return new JSONObject().put("version",1).put("completed",0).put("date","").put("seconds",0).put("done_date","").put("positions",new JSONObject()).put("cards",new JSONObject());}
 public static void validate(String raw)throws JSONException{
  if(raw==null||raw.length()>60000)throw new JSONException("阅读备份过大");JSONObject s=new JSONObject(raw);
  Set<String> allowed=new HashSet<>(Arrays.asList("version","completed","date","seconds","done_date","positions","cards"));Iterator<String> it=s.keys();while(it.hasNext())if(!allowed.contains(it.next()))throw new JSONException("阅读字段无效");
  if(s.getInt("version")!=1||s.getInt("completed")<0||s.getInt("completed")>18||s.getLong("seconds")<0||s.getLong("seconds")>86400)throw new JSONException("阅读进度无效");
  for(String k:new String[]{"date","done_date"})if(!s.getString(k).matches("|[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new JSONException("日期无效");
  JSONObject positions=s.getJSONObject("positions"),cards=s.getJSONObject("cards");if(positions.length()>100||cards.length()>500)throw new JSONException("记录过多");it=positions.keys();while(it.hasNext()){String k=it.next();if(!k.matches("[a-z0-9_]{1,40}")||positions.getInt(k)<0||positions.getInt(k)>1000)throw new JSONException("阅读位置无效");}
  it=cards.keys();while(it.hasNext()){String k=it.next();JSONObject c=cards.getJSONObject(k);if(!k.matches("[a-zA-Z -]{1,60}")||c.length()!=4||c.getString("zh").length()>120||c.getLong("due")<0||c.getInt("interval")<0||c.getInt("interval")>30||c.getInt("reviews")<0||c.getInt("reviews")>100000)throw new JSONException("词卡无效");}
 }
 public static void rollDay(JSONObject s,String date)throws JSONException{if(!date.equals(s.getString("date")))s.put("date",date).put("seconds",0);}
 public static boolean finish(JSONObject s,String date)throws JSONException{rollDay(s,date);if(s.getLong("seconds")<600||date.equals(s.getString("done_date"))||s.getInt("completed")>=18)return false;s.put("completed",s.getInt("completed")+1).put("done_date",date);return true;}
 public static JSONObject review(JSONObject c,boolean known,long now)throws JSONException{int days=known?Math.min(30,Math.max(1,c.optInt("interval")*2)):0;return new JSONObject().put("zh",c.getString("zh")).put("interval",days).put("reviews",Math.min(100000,c.optInt("reviews")+1)).put("due",now+(known?days*86400000L:600000L));}
}
