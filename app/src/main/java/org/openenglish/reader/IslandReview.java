package org.openenglish.reader;
import android.content.*;
import org.json.*;
import java.util.*;
/** Explicit self-ratings control review spacing; acoustic scores are not mastery ratings. */
public final class IslandReview {
 private final SharedPreferences prefs;public IslandReview(Context c){prefs=c.getSharedPreferences("island_review",0);}
 public IslandSrs.Card card(String id){try{JSONObject a=new JSONObject(prefs.getString(id,"{}"));return new IslandSrs.Card(a.optLong("due",0),a.optInt("interval",0),a.optDouble("ease",2.5),a.optInt("reps",0),a.optInt("lapses",0));}catch(Exception e){return IslandSrs.Card.fresh(0);}}
 public boolean seen(String id){return prefs.contains(id);}
 public void rate(String id,int grade){IslandSrs.Card c=IslandSrs.schedule(card(id),grade,System.currentTimeMillis());try{prefs.edit().putString(id,new JSONObject().put("due",c.due).put("interval",c.interval).put("ease",c.ease).put("reps",c.reps).put("lapses",c.lapses).toString()).apply();}catch(Exception e){throw new IllegalStateException(e);}}
 public List<JSONObject> queue(List<JSONObject> lessons,String lang){List<JSONObject> due=new ArrayList<>(),fresh=new ArrayList<>();long now=System.currentTimeMillis();for(JSONObject l:lessons)if(lang.equals(l.optString("lang"))){String id=l.optString("id");if(seen(id)){if(card(id).due<=now)due.add(l);}else fresh.add(l);}Collections.sort(due,(a,b)->Long.compare(card(a.optString("id")).due,card(b.optString("id")).due));due.addAll(fresh.subList(0,Math.min(3,fresh.size())));return due;}
 public String difficulty(String lang,List<JSONObject> lessons){StringBuilder b=new StringBuilder();for(JSONObject l:lessons)if(lang.equals(l.optString("lang"))&&card(l.optString("id")).lapses>0){b.append(l.optString("text")).append(" (").append(l.optString("reading")).append(") ");if(b.length()>200)break;}return b.toString();}
}
