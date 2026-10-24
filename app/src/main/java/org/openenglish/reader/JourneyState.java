package org.openenglish.reader;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** A bounded, separately versioned learning journal. API keys and audio are never exported. */
public final class JourneyState {
 /** Small transient page descriptor, separate from exported learning records. */
 public static final class Page {
  public final String screen,unit,word;public final int phrase;public final boolean review;
  private Page(String screen,String unit,int phrase,boolean review,String word){this.screen=screen;this.unit=unit;this.phrase=phrase;this.review=review;this.word=word;}
  private static Page home(){return new Page("home","",0,false,"");}
  public static Page of(String screen,String unit,int phrase,boolean review,String word){
   if(!Arrays.asList("home","route","placement","unit","listen","practice","review","lab","progress","conversation","settings","drill").contains(screen))return home();
   if(unit==null||(!unit.isEmpty()&&!unit.matches("u(0[1-9]|[1-3][0-9]|4[0-8])"))||phrase<0||phrase>2||word==null||word.length()>120)return home();
   if(Arrays.asList("unit","listen","practice","conversation").contains(screen)&&unit.isEmpty())return home();
   if(screen.equals("drill")&&word.trim().isEmpty())return home();
   return new Page(screen,unit,phrase,screen.equals("practice")&&review,screen.equals("drill")?word:"");
  }
  public String encode()throws JSONException{return new JSONObject().put("schema",1).put("screen",screen).put("unit",unit).put("phrase",phrase).put("review",review).put("word",word).toString();}
  public static Page decode(String text){
   if(text==null||text.length()>1024)return home();
   try{JSONObject d=new JSONObject(text);integer(d,"schema",1,1,true);integer(d,"phrase",0,2,true);
    if(!(d.opt("screen") instanceof String)||!(d.opt("unit") instanceof String)||!(d.opt("word") instanceof String)||!(d.opt("review") instanceof Boolean))return home();
    return of(d.getString("screen"),d.getString("unit"),d.getInt("phrase"),d.getBoolean("review"),d.getString("word"));
   }catch(Exception e){return home();}
  }
 }
 private final SharedPreferences prefs;private JSONObject data;private String saved;
 public JourneyState(Context c) throws Exception {
  prefs=c.getSharedPreferences("journey_progress_v1",0);
  data=new JSONObject(prefs.getString("data","{\"schema\":1,\"level\":0,\"units\":{},\"cards\":{},\"chats\":{}}"));
  validate(data);
  saved=data.toString();
 }
 private static void integer(JSONObject d,String key,long min,long max,boolean required)throws Exception{
  if(!required&&!d.has(key))return;Object v=d.opt(key);
  if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<min||((Number)v).longValue()>max)throw new JSONException("无效整数记录："+key);
 }
 private static void optional(JSONObject d,String key,Class<?> type)throws Exception{if(d.has(key)&&!type.isInstance(d.opt(key)))throw new JSONException("无效记录类型："+key);}
 private static void scene(String id)throws Exception{if(id==null||!id.matches("u(0[1-9]|[1-3][0-9]|4[0-8])"))throw new JSONException("未知课程场景");}
 public static void validate(JSONObject d)throws Exception{
  integer(d,"schema",1,1,true);integer(d,"level",0,5,true);
  for(String name:new String[]{"units","cards","chats"})if(d.optJSONObject(name)==null)throw new JSONException("缺少学习记录："+name);
  if(d.toString().length()>6000000||d.getJSONObject("units").length()>48||d.getJSONObject("cards").length()>144||d.getJSONObject("chats").length()>48)throw new JSONException("学习备份超出范围");
  if(d.has("drafts")){
   JSONObject drafts=d.optJSONObject("drafts");if(drafts==null||drafts.length()>48)throw new JSONException("草稿记录不正确");
   Iterator<String> it=drafts.keys();while(it.hasNext()){String id=it.next();Object value=drafts.get(id);checkDraft(id,value instanceof String?(String)value:null);}
  }
  Iterator<String> units=d.getJSONObject("units").keys();while(units.hasNext()){String k=units.next();scene(k);JSONObject u=d.getJSONObject("units").getJSONObject(k);integer(u,"heard",0,7,false);integer(u,"spoken",0,7,false);}
  Iterator<String> keys=d.getJSONObject("cards").keys();while(keys.hasNext()){
   String k=keys.next();if(!k.matches("u(0[1-9]|[1-3][0-9]|4[0-8])_[0-2]"))throw new JSONException("未知复习句");JSONObject c=d.getJSONObject("cards").getJSONObject(k);
   integer(c,"stage",0,5,true);integer(c,"due",0,Long.MAX_VALUE,true);integer(c,"attempts",1,Integer.MAX_VALUE,true);
   integer(c,"last",0,Long.MAX_VALUE,false);integer(c,"rated",0,Long.MAX_VALUE,false);
   optional(c,"recall",Boolean.class);optional(c,"assessed",Boolean.class);optional(c,"feedback",String.class);
   if(c.optString("feedback").length()>1500)throw new JSONException("反馈记录过长");
  }
  Iterator<String> chats=d.getJSONObject("chats").keys();while(chats.hasNext()){
   String k=chats.next();scene(k);JSONArray a=d.getJSONObject("chats").getJSONArray(k);if(a.length()>30)throw new JSONException("对话过长");
   for(int i=0;i<a.length();i++){JSONObject m=a.getJSONObject(i);if(!Arrays.asList("user","assistant").contains(m.opt("role"))||!(m.opt("content") instanceof String)||m.getString("content").length()>1500)throw new JSONException("无效对话记录");}
  }
 }
 private void save()throws Exception{
  String next=data.toString();
  try{if(!prefs.edit().putString("data",next).commit())throw new java.io.IOException("学习记录保存失败，请检查剩余空间");}
  catch(Exception e){data=new JSONObject(saved);throw e;}
  saved=next;
 }
 public int level(){return data.optInt("level");}
 public void level(int level)throws Exception{data.put("level",Math.max(0,Math.min(5,level)));save();}
 public JSONObject unit(String id){JSONObject v=data.optJSONObject("units").optJSONObject(id);return v==null?new JSONObject():v;}
 public void mark(String id,String field,int bit)throws Exception{scene(id);if(!Arrays.asList("heard","spoken").contains(field)||bit<1||bit>7)throw new JSONException("无效进度标记");JSONObject u=unit(id);u.put(field,u.optInt(field)|bit);data.getJSONObject("units").put(id,u);save();}
 public void practice(String id,int sentence,String feedback,boolean assessed)throws Exception{
  scene(id);if(sentence<0||sentence>2||feedback==null)throw new JSONException("无效练习参数");
  String key=id+"_"+sentence;JSONObject c=card(key);boolean fresh=c==null;if(fresh)c=new JSONObject();
  c.put("attempts",(int)Math.min(Integer.MAX_VALUE,(long)c.optInt("attempts")+1)).put("last",System.currentTimeMillis()).put("feedback",bounded(feedback)).put("assessed",assessed);
  if(fresh)c.put("stage",0).put("due",System.currentTimeMillis());
  data.getJSONObject("cards").put(key,c);JSONObject u=unit(id);u.put("spoken",u.optInt("spoken")|(1<<sentence));data.getJSONObject("units").put(id,u);save();
 }
 public JSONObject card(String key){return data.optJSONObject("cards").optJSONObject(key);}
 public void recall(String key,boolean remembered,long now)throws Exception{
  if(now<0||now>Long.MAX_VALUE-30*JourneyCore.DAY)throw new JSONException("无效复习时间");
  JSONObject c=card(key);if(c==null)throw new JSONException("请先完成一次开口练习");
  int stage=JourneyCore.nextStage(c.optInt("stage"),remembered,c.optLong("due")<=now);
  long due=remembered&&c.optLong("due")>now?c.optLong("due"):JourneyCore.nextDue(now,stage);
  c.put("stage",stage).put("due",due).put("recall",remembered).put("rated",now);save();
 }
 public List<String> due(long now){List<String> out=new ArrayList<>();JSONObject cards=data.optJSONObject("cards");Iterator<String> it=cards.keys();while(it.hasNext()){String k=it.next();if(cards.optJSONObject(k).optLong("due")<=now)out.add(k);}Collections.sort(out,(a,b)->Long.compare(card(a).optLong("due"),card(b).optLong("due")));return out;}
 public int completed(){int count=0;for(int i=1;i<=48;i++)if(complete(String.format(Locale.US,"u%02d",i)))count++;return count;}
 public boolean complete(String id){JSONObject u=unit(id);if(u.optInt("heard")!=7||u.optInt("spoken")!=7)return false;for(int i=0;i<3;i++){JSONObject c=card(id+"_"+i);if(c==null||!c.optBoolean("recall"))return false;}return true;}
 public JSONArray chat(String id){JSONArray a=data.optJSONObject("chats").optJSONArray(id);return a==null?new JSONArray():a;}
 public String pendingMessage(String id){JSONArray a=chat(id);JSONObject last=a.optJSONObject(a.length()-1);return last!=null&&"user".equals(last.optString("role"))?last.optString("content"):"";}
 public String draft(String id){JSONObject drafts=data.optJSONObject("drafts");return drafts==null?"":drafts.optString(id,"");}
 private static void checkDraft(String id,String value)throws Exception{
  if(id==null||!id.matches("u(0[1-9]|[1-3][0-9]|4[0-8])")||value==null||value.length()>1500)throw new JSONException("草稿场景或长度不正确（最多 1500 字）");
 }
 private void putDraft(String id,String value)throws Exception{
  JSONObject drafts=data.optJSONObject("drafts");if(drafts==null){if(value.isEmpty())return;drafts=new JSONObject();data.put("drafts",drafts);}
  if(value.isEmpty())drafts.remove(id);else drafts.put(id,value);
  if(drafts.length()==0)data.remove("drafts");
 }
 public void draft(String id,String value)throws Exception{checkDraft(id,value);if(value.equals(draft(id)))return;putDraft(id,value);save();}
 /** Commit the outgoing question and its remaining input together. A failed save preserves both. */
 public void sendMessage(String id,String content,String input)throws Exception{
  checkDraft(id,input);if(content==null||content.trim().isEmpty()||content.length()>1500)throw new JSONException("请输入 1500 字以内的消息");
  String question=content.trim();appendMessage(id,"user",question);putDraft(id,input.trim().equals(question)?"":input);save();
 }
 public void reply(String id,String expected,String content)throws Exception{
  if(expected==null||expected.isEmpty()||!pendingMessage(id).equals(expected))throw new JSONException("待回复的消息已经变化，请重新打开当前场景");
  if(content==null||content.trim().isEmpty())throw new JSONException("AI 没有返回内容，请重试上一条消息");
  message(id,"assistant",content);
 }
 public void message(String id,String role,String content)throws Exception{
  appendMessage(id,role,content);save();
 }
 private void appendMessage(String id,String role,String content)throws Exception{scene(id);if(!Arrays.asList("user","assistant").contains(role)||content==null)throw new JSONException("无效消息参数");JSONArray a=chat(id),next=new JSONArray();for(int i=Math.max(0,a.length()-29);i<a.length();i++)next.put(a.getJSONObject(i));next.put(new JSONObject().put("role",role).put("content",bounded(content)));data.getJSONObject("chats").put(id,next);}
 public void clearChat(String id)throws Exception{scene(id);data.getJSONObject("chats").remove(id);save();}
 private static String bounded(String value){if(value.length()<=1500)return value;int end=1470;if(Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end)+"\n[历史记录已截短]";}
 public String export(){return data.toString();}
 public void restore(String text)throws Exception{JSONObject next=new JSONObject(text);validate(next);if(!prefs.edit().putString("data",next.toString()).commit())throw new java.io.IOException("恢复保存失败");data=next;saved=next.toString();}
}
