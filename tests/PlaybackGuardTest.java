import org.openenglish.reader.*;
public class PlaybackGuardTest {
 static int checks;static void check(boolean v,String name){if(!v)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 public static void main(String[] args){
  PlaybackGuard g=new PlaybackGuard();check(g.allows(false,true,false,0),"speaker input is available before a reply starts");g.begin(7);
  check(!g.allows(false,true,false,100),"AEC enabled flag does not let speaker echo cancel the reply");check(!g.allows(false,false,false,200),"speaker stays protected without AEC");check(g.allows(true,false,false,200),"headphone duplex remains available during playback");check(g.allows(false,true,true,200),"explicit speaker duplex remains available with AEC");check(!g.allows(false,false,true,200),"speaker opt-in without AEC still protects playback");
  check(!g.allows(false,true,false,10000),"protection survives a long gap between streamed chunks");g.begin(7);g.finish(6,11000);check(!g.allows(false,true,false,11000),"stale completion cannot release the current reply");g.finish(7,12000);
  check(!g.allows(false,true,false,12649),"speaker reverberation tail is protected");check(g.allows(false,true,false,12650),"automatic input resumes after full reply and tail");g.begin(8);g.clear(13000);check(!g.allows(false,true,false,13300),"manual interruption clears reply but protects remaining speaker tail");check(g.allows(false,true,false,13650),"input resumes after interruption tail");g.begin(9);g.finish(8,15000);check(!g.allows(false,true,false,16000),"old callback cannot release new turn");
  VoiceEvents events=new VoiceEvents();for(int n=0;n<40;n++)events.add(n,"event"+n);String report=events.report();check(report.split("\n").length==32,"diagnostics history is bounded");check(report.startsWith("8 ms")&&report.endsWith("event39"),"diagnostics retain recent control events");
  System.out.println("PLAYBACK_GUARD_CHECKS: "+checks);
 }
}
