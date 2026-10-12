import org.openenglish.reader.*;import java.util.*;
public class ConversationBoundaryTest {
 static int checks;static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static short[] pcm(int amplitude){short[] f=new short[512];Arrays.fill(f,(short)amplitude);return f;}
 public static void main(String[] args){
  PlaybackGuard guard=new PlaybackGuard();guard.clear(1000);guard.captureStarted();check(guard.allows(false,false,false,1000),"confirmed voice onset does not discard recording under a new echo tail");int[] calls={0,0};boolean[] busy={false};VoiceGate gate=new VoiceGate(360,new VoiceGate.Listener(){public void start(){calls[0]++;}public void end(float[] p,boolean clipped){calls[1]++;busy[0]=true;check(p.length>1600,"complete spoken PCM retained");}});
  for(int i=0;i<14;i++){check(guard.allows(false,false,false,1000+i*32),"automatic onset leaves echo guard unchanged");gate.frame(pcm(2000),true);}for(int i=0;i<12;i++)gate.frame(pcm(0),false);
  check(calls[0]==1&&calls[1]==1&&busy[0],"one voice onset reaches recognition exactly once");
  for(int i=0;i<30;i++)if(!busy[0]&&guard.allows(false,false,false,3000))gate.frame(pcm(3000),true);
  check(calls[0]==1,"ambient speech cannot create a turn during recognition or response");guard.begin(1);check(!guard.allows(false,false,false,3000),"reply playback blocks speaker echo");guard.finish(1,4000);busy[0]=false;check(!guard.allows(false,false,false,4500)&&guard.allows(false,false,false,4650),"reply tail expires and microphone becomes eligible again");
  for(int i=0;i<14;i++)gate.frame(pcm(2000),true);for(int i=0;i<12;i++)gate.frame(pcm(0),false);check(calls[0]==2&&calls[1]==2,"second utterance accepted after first completed reply");
  final int[] forced={0};VoiceGate held=new VoiceGate(600,new VoiceGate.Listener(){public void start(){}public void end(float[] p,boolean clipped){forced[0]++;}});for(int i=0;i<14;i++)held.frame(pcm(2000),true);held.finish();held.finish();check(forced[0]==1&&!held.speaking(),"finish sentence submits once and allows new capture");held.finish();check(forced[0]==1,"finish without active recording does not resubmit");
  System.out.println("CONVERSATION_BOUNDARY_CHECKS: "+checks);
 }
}
