import org.openenglish.reader.*;import org.json.*;

/** Production learning-state tests for failed, cancelled and retried conversations. */
public final class JourneyConversationTest {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 interface Action{void run()throws Exception;}
 static void reject(JourneyState state,Action action)throws Exception{
  String before=state.export();boolean rejected=false;try{action.run();}catch(Exception e){rejected=true;}
  check(rejected,"invalid reply is rejected");check(state.export().equals(before),"rejected reply leaves pending message and history intact");
 }
 public static void main(String[] args)throws Exception{
  JourneyTest.Memory memory=new JourneyTest.Memory();JourneyState state=new JourneyState(memory);
  check(state.pendingMessage("u01").isEmpty(),"new scene has nothing to retry");
  state.message("u01","user","Where is the station?");String pending=state.pendingMessage("u01"),before=state.export();
  for(int i=0;i<3;i++)check(state.pendingMessage("u01").equals(pending),"retry reuses the stored user message");
  check(state.export().equals(before)&&state.chat("u01").length()==1,"building retries does not append user messages");
  check(new JourneyState(memory).pendingMessage("u01").equals(pending),"unanswered message survives reopening");
  JourneyState restored=new JourneyState(new JourneyTest.Memory());restored.restore(state.export());check(restored.pendingMessage("u01").equals(pending),"backup retains retryable conversation");
  reject(state,()->state.reply("u01",pending,null));reject(state,()->state.reply("u01",pending," \n\t "));
  reject(state,()->state.reply("u01","different question","An unrelated answer"));
  reject(state,()->state.reply("u02",pending,"Cross-scene answer"));
  state.reply("u01",pending,"It is next to the bank.");check(state.chat("u01").length()==2,"successful retry adds one assistant message");check(state.pendingMessage("u01").isEmpty(),"completed reply hides retry");
  reject(state,()->state.reply("u01",pending,"Duplicate response"));
  state.message("u01","user","Yes");state.reply("u01","Yes","Which entrance?");state.message("u01","user","Yes");state.reply("u01","Yes","All right.");check(state.chat("u01").length()==6,"intentional repeated user words are not deduplicated");
  state.message("u01","user","First pending");state.message("u01","user","New pending");reject(state,()->state.reply("u01","First pending","Late answer"));
  check(state.pendingMessage("u01").equals("New pending"),"late reply cannot consume a newer question");
  state.clearChat("u01");reject(state,()->state.reply("u01","New pending","After clear"));check(state.chat("u01").length()==0,"cleared conversation stays empty");
  JourneyTest.FailingMemory failedMemory=new JourneyTest.FailingMemory();JourneyState failed=new JourneyState(failedMemory);failed.message("u01","user","Keep this question");failedMemory.fail=true;
  reject(failed,()->failed.reply("u01","Keep this question","Unsaved answer"));check(failed.pendingMessage("u01").equals("Keep this question"),"failed reply save remains retryable");
  failedMemory.fail=false;failed.reply("u01","Keep this question","Saved retry");check(new JourneyState(failedMemory).chat("u01").length()==2,"retry persists exactly one complete pair after storage recovers");
  System.out.println("Conversation retry checks: "+checks);
 }
}
