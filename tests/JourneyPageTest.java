import org.openenglish.reader.JourneyState;
import org.json.*;

/** Test the actual descriptor used by Activity instance-state restoration. */
public final class JourneyPageTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void home(String text){JourneyState.Page p=JourneyState.Page.decode(text);check(p.screen.equals("home")&&p.unit.isEmpty()&&!p.review,"invalid instance state safely falls back to home");}
 public static void main(String[] args)throws Exception{
  for(String screen:new String[]{"home","route","placement","unit","listen","practice","review","lab","progress","conversation","settings","drill"}){
   JourneyState.Page p=JourneyState.Page.decode(JourneyState.Page.of(screen,"u48",2,true,"θ think").encode());
   check(p.screen.equals(screen)&&p.unit.equals("u48")&&p.phrase==2,"screen, selected scene and last sentence round trip");
   check(p.review==screen.equals("practice"),"only practice restores review mode");
   check(p.word.equals(screen.equals("drill")?"θ think":""),"only word drills retain their target");
  }
  for(int i=0;i<3;i++){JourneyState.Page p=JourneyState.Page.decode(JourneyState.Page.of("listen","u01",i,false,"").encode());check(p.phrase==i,"each listening question restores its own index");}
  for(String screen:new String[]{"unit","listen","practice","conversation"})check(JourneyState.Page.of(screen,"",0,false,"").screen.equals("home"),"lesson-dependent page needs a real lesson");
  for(String screen:new String[]{"home","route","placement","review","lab","progress","settings"})check(JourneyState.Page.of(screen,"",0,false,"").screen.equals(screen),"global pages work before selecting a lesson");
  home(null);home("");home("not json");home("{}");home(new String(new char[1025]));
  String valid=JourneyState.Page.of("conversation","u01",0,false,"").encode();
  for(String key:new String[]{"schema","phrase","review","screen","unit","word"}){
   JSONObject bad=new JSONObject(valid);bad.put(key,JSONObject.NULL);home(bad.toString());
  }
  for(Object value:new Object[]{"0",true,0.5,-1,3}){JSONObject bad=new JSONObject(valid).put("phrase",value);home(bad.toString());}
  for(String unit:new String[]{"u00","u49","u1","../u01"})home(JourneyState.Page.of("conversation",unit,0,false,"").encode());
  home(JourneyState.Page.of("unknown","u01",0,false,"").encode());home(JourneyState.Page.of("drill","",0,false," ").encode());
  JourneyState state=new JourneyState(new JourneyTest.Memory());state.draft("u01","My next question");state.message("u01","user","Pending question");String before=state.export();
  JourneyState.Page restored=JourneyState.Page.decode(valid);check(state.export().equals(before),"restoring navigation cannot append messages or change review progress");
  check(state.draft(restored.unit).equals("My next question")&&state.pendingMessage(restored.unit).equals("Pending question"),"restored scene resolves saved draft and pending question");
  check(valid.length()<1024&&!valid.contains("My next question")&&!valid.contains("Pending question"),"descriptor stays small and excludes conversation text");
  System.out.println("Page restoration checks: "+checks);
 }
}
