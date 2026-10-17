package org.openenglish.reader;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** A bounded, separately versioned learning journal. API keys and audio are never exported. */
public final class JourneyState {
 private final SharedPreferences prefs;private JSONObject data;
 public JourneyState(Context c) throws Exception {
  prefs=c.getSharedPreferences("journey_progress_v1",0);
  data=new JSONObject(prefs.getString("data","{\"schema\":1,\"level\":0,\"units\":{},\"cards\":{},\"chats\":{}}"));
  validate(data);
 }
 public static void validate(JSONObject d)throws Exception{
  if(d.optInt("schema")!=1||d.optInt("level",-1)<0||d.optInt("level")>5)throw new JSONException("学习备份版本或级别不正确");
  for(String name:new String[]{"units","cards","chats"})if(d.optJSONObject(name)==null)throw new JSONException("缺少学习记录："+name);
  if(d.toString().length()>6000000||d.getJSONObject("units").length()>48||d.getJSONObject("cards").length()>200||d.getJSONObject("chats").length()>48)throw new JSONException("学习备份超出范围");
  Iterator<String> units=d.getJSONObject("units").keys();while(units.hasNext()){String k=units.next();if(!k.matches("u(0[1-9]|[1-3][0-9]|4[0-8])"))throw new JSONException("未知课程");JSONObject u=d.getJSONObject("units").getJSONObject(k);if(u.optInt("heard",0)<0||u.optInt("heard",0)>7||u.optInt("spoken",0)<0||u.optInt("spoken",0)>7)throw new JSONException("无效学习进度");}
  Iterator<String> keys=d.getJSONObject("cards").keys();while(keys.hasNext()){String k=keys.next();if(!k.matches("u(0[1-9]|[1-3][0-9]|4[0-8])_[0-2]"))throw new JSONException("未知复习句");JSONObject c=d.getJSONObject("cards").getJSONObject(k);if(c.optInt("stage",-1)<0||c.optInt("stage")>5||c.optLong("due",-1)<0||c.optInt("attempts",-1)<1)throw new JSONException("无效复习记录");}
  Iterator<String> chats=d.getJSONObject("chats").keys();while(chats.hasNext()){String k=chats.next();if(!k.matches("u(0[1-9]|[1-3][0-9]|4[0-8])"))throw new JSONException("未知对话");JSONArray a=d.getJSONObject("chats").getJSONArray(k);if(a.length()>30)throw new JSONException("对话过长");for(int i=0;i<a.length();i++){JSONObject m=a.getJSONObject(i);if(!Arrays.asList("user","assistant").contains(m.getString("role"))||m.getString("content").length()>1500)throw new JSONException("无效对话记录");}}
 }
 private void save()throws Exception{if(!prefs.edit().putString("data",data.toString()).commit())throw new java.io.IOException("学习记录保存失败，请检查剩余空间");}
 public int level(){return data.optInt("level");}
 public void level(int level)throws Exception{data.put("level",Math.max(0,Math.min(5,level)));save();}
 public JSONObject unit(String id){JSONObject v=data.optJSONObject("units").optJSONObject(id);return v==null?new JSONObject():v;}
 public void mark(String id,String field,int bit)throws Exception{JSONObject u=unit(id);u.put(field,u.optInt(field)|bit);data.getJSONObject("units").put(id,u);save();}
 public void practice(String id,int sentence,String feedback,boolean assessed)throws Exception{
  String key=id+"_"+sentence;JSONObject c=card(key);boolean fresh=c==null;if(fresh)c=new JSONObject();
  c.put("attempts",c.optInt("attempts")+1).put("last",System.currentTimeMillis()).put("feedback",bounded(feedback)).put("assessed",assessed);
  if(fresh)c.put("stage",0).put("due",System.currentTimeMillis());
  data.getJSONObject("cards").put(key,c);JSONObject u=unit(id);u.put("spoken",u.optInt("spoken")|(1<<sentence));data.getJSONObject("units").put(id,u);save();
 }
 public JSONObject card(String key){return data.optJSONObject("cards").optJSONObject(key);}
 public void recall(String key,boolean remembered,long now)throws Exception{
  JSONObject c=card(key);if(c==null)throw new JSONException("请先完成一次开口练习");
  int stage=JourneyCore.nextStage(c.optInt("stage"),remembered,c.optLong("due")<=now);
  long due=remembered&&c.optLong("due")>now?c.optLong("due"):JourneyCore.nextDue(now,stage);
  c.put("stage",stage).put("due",due).put("recall",remembered).put("rated",now);save();
 }
 public List<String> due(long now){List<String> out=new ArrayList<>();JSONObject cards=data.optJSONObject("cards");Iterator<String> it=cards.keys();while(it.hasNext()){String k=it.next();if(cards.optJSONObject(k).optLong("due")<=now)out.add(k);}Collections.sort(out,(a,b)->Long.compare(card(a).optLong("due"),card(b).optLong("due")));return out;}
 public int completed(){int count=0;for(int i=1;i<=48;i++)if(complete(String.format(Locale.US,"u%02d",i)))count++;return count;}
 public boolean complete(String id){JSONObject u=unit(id);if(u.optInt("heard")!=7||u.optInt("spoken")!=7)return false;for(int i=0;i<3;i++){JSONObject c=card(id+"_"+i);if(c==null||!c.optBoolean("recall"))return false;}return true;}
 public JSONArray chat(String id){JSONArray a=data.optJSONObject("chats").optJSONArray(id);return a==null?new JSONArray():a;}
 public void message(String id,String role,String content)throws Exception{
  JSONArray a=chat(id),next=new JSONArray();for(int i=Math.max(0,a.length()-29);i<a.length();i++)next.put(a.getJSONObject(i));next.put(new JSONObject().put("role",role).put("content",bounded(content)));data.getJSONObject("chats").put(id,next);save();
 }
 public void clearChat(String id)throws Exception{data.getJSONObject("chats").remove(id);save();}
 private static String bounded(String value){if(value.length()<=1500)return value;int end=1470;if(Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end)+"\n[历史记录已截短]";}
 public String export(){return data.toString();}
 public void restore(String text)throws Exception{JSONObject next=new JSONObject(text);validate(next);if(!prefs.edit().putString("data",next.toString()).commit())throw new java.io.IOException("恢复保存失败");data=next;}
}
