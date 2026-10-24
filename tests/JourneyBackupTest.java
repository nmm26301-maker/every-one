import org.openenglish.reader.*;
import org.json.*;
import java.util.*;

/** Reject corrupt backups and invalid API writes without losing a usable journal. */
public final class JourneyBackupTest {
 static int checks;
 interface Action {void run()throws Exception;}
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void reject(JourneyState s,Action a)throws Exception{
  String before=s.export();boolean failed=false;try{a.run();}catch(Exception e){failed=true;}
  check(failed,"invalid data rejected");check(s.export().equals(before),"invalid operation preserves complete journal");
 }
 public static void main(String[] args)throws Exception{
  JourneyTest.Memory memory=new JourneyTest.Memory();JourneyState s=new JourneyState(memory);
  s.practice("u01",0,"feedback",true);s.mark("u01","heard",1);s.draft("u01","unfinished");s.message("u01","user","question");
  String baseline=s.export();JourneyState copy=new JourneyState(new JourneyTest.Memory());copy.restore(baseline);check(copy.export().equals(baseline),"normal backup round trip");
  for(String field:new String[]{"schema","level"})for(Object value:new Object[]{"1",true,JSONObject.NULL,1.5,new JSONArray()}){
   JSONObject bad=new JSONObject(baseline).put(field,value);reject(s,()->s.restore(bad.toString()));
  }
  for(String field:new String[]{"heard","spoken"})for(Object value:new Object[]{"7",true,JSONObject.NULL,2.5,-1,8,new JSONObject()}){
   JSONObject bad=new JSONObject(baseline);bad.getJSONObject("units").getJSONObject("u01").put(field,value);reject(s,()->s.restore(bad.toString()));
  }
  for(String field:new String[]{"stage","due","attempts","last","rated"})for(Object value:new Object[]{"1",false,JSONObject.NULL,0.5,-1}){
   JSONObject bad=new JSONObject(baseline);bad.getJSONObject("cards").getJSONObject("u01_0").put(field,value);reject(s,()->s.restore(bad.toString()));
  }
  for(String field:new String[]{"recall","assessed"})for(Object value:new Object[]{"true",1,JSONObject.NULL}){
   JSONObject bad=new JSONObject(baseline);bad.getJSONObject("cards").getJSONObject("u01_0").put(field,value);reject(s,()->s.restore(bad.toString()));
  }
  for(Object value:new Object[]{12,new JSONObject(),JSONObject.NULL,String.join("",Collections.nCopies(1501,"x"))}){
   JSONObject bad=new JSONObject(baseline);bad.getJSONObject("cards").getJSONObject("u01_0").put("feedback",value);reject(s,()->s.restore(bad.toString()));
  }
  for(Object value:new Object[]{7,new JSONObject(),JSONObject.NULL}){
   JSONObject bad=new JSONObject(baseline);bad.getJSONObject("chats").getJSONArray("u01").getJSONObject(0).put("content",value);reject(s,()->s.restore(bad.toString()));
  }
  reject(s,()->s.mark("u49","heard",1));reject(s,()->s.mark("u01","unexpected",1));reject(s,()->s.mark("u01","heard",8));reject(s,()->s.mark("u01","heard",0));
  reject(s,()->s.practice("u01",3,"bad",false));reject(s,()->s.practice("u00",0,"bad",false));reject(s,()->s.practice("u01",-1,"bad",false));reject(s,()->s.practice("u01",0,null,false));
  reject(s,()->s.message("u49","user","bad"));reject(s,()->s.message("u01","system","bad"));reject(s,()->s.message("u01","user",null));reject(s,()->s.clearChat(null));
  reject(s,()->s.recall("u01_0",true,-1));reject(s,()->s.recall("u01_0",true,Long.MAX_VALUE));
  check(new JourneyState(memory).export().equals(baseline),"rejections do not poison durable progress");
  JSONObject edge=new JSONObject(baseline),card=edge.getJSONObject("cards").getJSONObject("u01_0");card.put("attempts",Integer.MAX_VALUE).put("due",Long.MAX_VALUE).put("recall",false);
  s.restore(edge.toString());s.practice("u01",0,"still valid",false);check(s.card("u01_0").getInt("attempts")==Integer.MAX_VALUE,"practice count saturates instead of overflowing");
  s.recall("u01_0",true,0);check(s.card("u01_0").getLong("due")==Long.MAX_VALUE,"early recall retains valid distant due date");
  check(new JourneyState(memory).card("u01_0").getInt("attempts")==Integer.MAX_VALUE,"boundary records remain reopenable");
  JSONObject minimal=new JSONObject("{\"schema\":1,\"level\":0,\"units\":{\"u01\":{}},\"cards\":{\"u01_0\":{\"stage\":0,\"due\":0,\"attempts\":1}},\"chats\":{}}");
  s.restore(minimal.toString());check(s.due(0).contains("u01_0"),"legacy optional fields may be absent");
  System.out.println("Backup validation checks: "+checks);
 }
}
