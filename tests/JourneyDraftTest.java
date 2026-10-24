import org.openenglish.reader.JourneyState;
import org.json.*;
import java.util.*;

/** Exercise actual journal persistence and atomic submission, including storage failures. */
public final class JourneyDraftTest {
 static int checks;
 interface Action {void run()throws Exception;}
 static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 static void reject(JourneyState state,Action action)throws Exception{
  String before=state.export();boolean rejected=false;try{action.run();}catch(Exception e){rejected=true;}
  check(rejected,"invalid operation rejected");check(state.export().equals(before),"rejection preserves journal and draft");
 }
 public static void main(String[] args)throws Exception{
  JourneyTest.FailingMemory memory=new JourneyTest.FailingMemory();JourneyState state=new JourneyState(memory);
  check(state.draft("u01").isEmpty(),"old schema without drafts remains readable");String oldBackup=state.export();
  state.draft("u01","  Where is the station?\n");state.draft("u02","中文提示与 emoji 😀");
  check(new JourneyState(memory).draft("u01").equals("  Where is the station?\n"),"reopening preserves exact whitespace");
  check(state.draft("u02").equals("中文提示与 emoji 😀"),"scenes keep independent Unicode drafts");
  JourneyState restored=new JourneyState(new JourneyTest.Memory());restored.restore(state.export());
  check(restored.draft("u02").equals(state.draft("u02")),"backup includes drafts");
  memory.fail=true;state.draft("u01",state.draft("u01"));check(true,"unchanged draft needs no storage write");
  reject(state,()->state.draft("u01","replacement"));reject(state,()->state.sendMessage("u01","Where is the station?",state.draft("u01")));
  check(new JourneyState(memory).chat("u01").length()==0,"failed submission adds no durable message");
  check(new JourneyState(memory).draft("u01").equals(state.draft("u01")),"failed send keeps durable draft");
  memory.fail=false;state.sendMessage("u01","Where is the station?",state.draft("u01"));
  check(state.draft("u01").isEmpty()&&state.pendingMessage("u01").equals("Where is the station?"),"successful submit consumes matching draft atomically");
  check(new JourneyState(memory).draft("u01").isEmpty()&&new JourneyState(memory).chat("u01").length()==1,"reopen retains submitted question without stale draft");
  state.draft("u01","Next question");state.reply("u01","Where is the station?","Next to the bank.");
  check(state.draft("u01").equals("Next question"),"assistant response preserves newly typed draft");
  state.sendMessage("u01","Please start our role-play.","Next question");
  check(state.draft("u01").equals("Next question"),"helper prompts keep different input");
  state.clearChat("u01");check(state.draft("u01").equals("Next question"),"clearing conversation preserves unsent work");
  state.draft("u01","");check(state.draft("u01").isEmpty()&&!state.draft("u02").isEmpty(),"clearing one draft preserves other scenes");
  restored.restore(oldBackup);check(restored.draft("u02").isEmpty(),"restoring an older backup replaces existing drafts");
  String limit=String.join("",Collections.nCopies(1500,"字"));state.draft("u48",limit);
  check(state.draft("u48").length()==1500,"full-length draft retained");
  reject(state,()->state.draft("u48",limit+"字"));reject(state,()->state.draft("u49","unknown"));reject(state,()->state.draft("u01",null));
  reject(state,()->state.sendMessage("u01"," ","unsent"));reject(state,()->state.sendMessage("u01",limit+"x","unsent"));
  for(Object invalid:new Object[]{JSONObject.NULL,new JSONArray(),"text",7,new JSONObject().put("u01",JSONObject.NULL),new JSONObject().put("u01",42),new JSONObject().put("u00","bad"),new JSONObject().put("u01",limit+"字")}){
   JSONObject bad=new JSONObject(state.export()).put("drafts",invalid);reject(state,()->state.restore(bad.toString()));
  }
  JSONObject full=new JSONObject(oldBackup),drafts=new JSONObject();for(int i=1;i<=48;i++)drafts.put(String.format(Locale.US,"u%02d",i),limit);
  full.put("drafts",drafts);restored.restore(full.toString());check(restored.draft("u48").equals(limit),"all 48 scene drafts round trip");
  drafts.put("u49","overflow");reject(state,()->state.restore(full.toString()));
  memory.throwsError=true;reject(state,()->state.sendMessage("u02","Typed question","Typed question"));memory.throwsError=false;
  state.sendMessage("u02","Typed question","Typed question");check(new JourneyState(memory).pendingMessage("u02").equals("Typed question"),"retry after throwing storage persists once");
  check(state.chat("u02").length()==1&&state.draft("u02").isEmpty(),"retry does not duplicate failed send or restore old draft");
  System.out.println("Conversation draft checks: "+checks);
 }
}
