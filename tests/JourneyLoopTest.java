import org.openenglish.reader.*;
import org.json.*;

public final class JourneyLoopTest {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 interface Action{void run()throws Exception;}
 static void reject(Action action)throws Exception{boolean failed=false;try{action.run();}catch(Exception e){failed=true;}check(failed,"invalid transition rejected");}
 public static void main(String[] args)throws Exception{
  check(JourneyCore.assessmentMode(null).equals("manual"),"no model required by default");
  check(JourneyCore.assessmentMode("bad").equals("manual"),"unknown mode safe fallback");
  for(String mode:new String[]{"manual","recognition","azure","openpronounce"})check(JourneyCore.assessmentMode(mode).equals(mode),"explicit service retained");
  for(int u=1;u<=48;u++)for(int n=0;n<3;n++){
   String key=JourneyCore.sampleKey(String.format("u%02d",u),n);
   check(JourneyCore.samplePack(key).equals("level"+((u-1)/8)+".zip"),"144 valid sample mappings");
  }
  reject(()->JourneyCore.sampleKey("../u01",0));reject(()->JourneyCore.sampleKey("u49",0));reject(()->JourneyCore.sampleKey("u01",3));reject(()->JourneyCore.samplePack("u01_0/../x"));
  for(int step=0;step<4;step++)for(int mask=0;mask<16;mask++){
   boolean p=(mask&1)!=0,v=(mask&2)!=0,r=(mask&4)!=0,h=(mask&8)!=0;
   boolean ready=JourneyCore.loopReady(step,p,v,r,h);
   check(ready==((step%2==0)?p&&v:r&&h),"cannot bypass listening/text or recording/replay gates");
  }
  check(!JourneyCore.loopReady(4,true,true,true,true),"finished screen cannot advance again");
  JourneyTest.Memory memory=new JourneyTest.Memory();JourneyState s=new JourneyState(memory);
  final JourneyState initial=s;reject(()->initial.loopRecall("u01",0,true,100));reject(()->initial.loopAdvance("u01",0,2));
  for(int n=0;n<3;n++){
   check(s.loopStage("u01",n)==0,"new sentence starts intensive listening");
   s.loopAdvance("u01",n,0);check(s.loopStage("u01",n)==1,"intensive to shadowing");
   final int phrase=n;final JourneyState current=s;reject(()->current.loopAdvance("u01",phrase,1));
   s.practice("u01",n,"仅录音回听，未评测",false);s.loopAdvance("u01",n,1);
   s=new JourneyState(memory);check(s.loopStage("u01",n)==2,"resume blind stage after restart");
   s.loopAdvance("u01",n,2);check((s.unit("u01").optInt("heard")&(1<<n))!=0,"blind self-check records heard evidence");
   s.practice("u01",n,"复述录音，未评测",false);s.loopRecall("u01",n,true,System.currentTimeMillis());
   check(s.loopStage("u01",n)==4,"retell schedules review");
   check(!s.card("u01_"+n).optBoolean("assessed"),"manual loop never fabricates phone scores");
   check(s.card("u01_"+n).optInt("stage")==1,"first successful self recall due tomorrow");
  }
  check(s.complete("u01"),"whole unit completion needs all hearing/speaking/recall evidence");
  JourneyState restored=new JourneyState(new JourneyTest.Memory());restored.restore(s.export());check(restored.loopStage("u01",2)==4,"backup retains completed loop");
  s.loopRestart("u01",0);check(s.loopStage("u01",0)==0,"repeat difficult sentence from intensive");
  s.loopAdvance("u01",0,0);s.loopAdvance("u01",0,1);s.loopAdvance("u01",0,2);long now=System.currentTimeMillis();s.loopRecall("u01",0,false,now);
  check(s.loopStage("u01",0)==0&&!s.complete("u01"),"failed retell reopens learning");check(s.card("u01_0").optLong("due")==now+600000,"failed recall due in ten minutes");
  JSONObject invalid=new JSONObject(s.export());invalid.getJSONObject("units").getJSONObject("u01").put("loop0",5);String before=s.export();
  final JourneyState target=s;reject(()->target.restore(invalid.toString()));check(s.export().equals(before),"invalid loop backup preserves current journal");
  JourneyTest.FailingMemory failing=new JourneyTest.FailingMemory();JourneyState stable=new JourneyState(failing);JourneyTest.rejectedSave(failing,stable,()->stable.loopAdvance("u01",0,0));
  JourneyState.Page page=JourneyState.Page.decode(JourneyState.Page.of("loop","u01",2,false,"").encode());check(page.screen.equals("loop")&&page.phrase==2,"rotation restores loop sentence, not transient proof flags");
  System.out.println("Guided loop checks: "+checks);
 }
}
