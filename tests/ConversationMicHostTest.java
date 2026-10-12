import org.openenglish.reader.*;
import android.media.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Production capture loop with fake Android input/JNI; verifies control flow, not phone acoustics. */
public class ConversationMicHostTest {
 static int checks;static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static void until(java.util.function.BooleanSupplier condition)throws Exception {long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition.getAsBoolean()){if(System.nanoTime()>limit)throw new AssertionError("capture fixture timeout");Thread.sleep(2);}}
 static class Listen implements ConversationMic.Listener {
  CountDownLatch ready=new CountDownLatch(1),released=new CountDownLatch(1);AtomicInteger starts=new AtomicInteger(),ends=new AtomicInteger(),notices=new AtomicInteger(),meters=new AtomicInteger();volatile int source;volatile String error;volatile float[] pcm;volatile boolean guarded,silenced;
  public void ready(boolean aec,int source){this.source=source;ready.countDown();}public void start(){starts.incrementAndGet();}public void end(float[] pcm,boolean clipped){this.pcm=pcm;ends.incrementAndGet();}
  public void meter(int v,double rms,float probability,boolean guarded,boolean silenced){this.guarded=guarded;this.silenced=silenced;meters.incrementAndGet();}public void notice(String s){notices.incrementAndGet();}public void error(String s){error=s;}public void released(){released.countDown();}
 }
 public static void main(String[] args)throws Exception {
  Listen l=new Listen();AtomicBoolean allowed=new AtomicBoolean(false);AudioRecord.failSource=true;
  ConversationMic mic=new ConversationMic("fixture",360,6,.4f,aec->allowed.get(),l);mic.start();check(l.ready.await(3,TimeUnit.SECONDS)&&l.source==1,"uninitialized recognition input falls back to ordinary microphone");
  until(()->l.meters.get()>0);check(l.guarded&&l.starts.get()==0,"playback guard keeps automatic detection inactive while reporting input");
  mic.beginManual();until(()->l.starts.get()==1);int read=AudioRecord.reads.get();final int first=read;until(()->AudioRecord.reads.get()>first+50);mic.finishManual();until(()->l.ends.get()==1);
  check(l.pcm.length>=1600&&Math.abs(l.pcm[0])>.05,"manual capture submits real PCM despite low VAD probability and playback guard");
  int ends=l.ends.get();mic.beginManual();until(()->l.starts.get()==2);mic.cancelManual();read=AudioRecord.reads.get();final int at=read;until(()->AudioRecord.reads.get()>at+20);check(l.ends.get()==ends,"cancelled touch does not send a partial utterance");
  mic.reset();mic.beginManual();until(()->l.starts.get()==3);final int before=AudioRecord.reads.get();until(()->AudioRecord.reads.get()>before+50);mic.finishManual();until(()->l.ends.get()==2);check(l.ends.get()==2,"ordered reset does not discard a later manual press");
  mic.beginManual();mic.finishManual();until(()->l.notices.get()>0);check(l.ends.get()==2,"press and release without enough frames gives an explicit short-recording notice");
  allowed.set(true);LocalNative.probability=.45f;until(()->l.starts.get()>=5);LocalNative.probability=.05f;until(()->l.ends.get()==3);check(l.pcm.length>=512,"automatic speech resumes after manual control and accepts moderate neural confidence");
  AudioRecord.silenced=true;until(()->l.silenced);check(l.silenced,"system-silenced microphone state reaches UI diagnostics");
  mic.close();check(l.released.await(3,TimeUnit.SECONDS)&&l.error==null,"stopping releases capture without reporting a false read error");check(LocalNative.closed.get()==1&&AudioRecord.released.get()==2,"native VAD and failed/active input resources are released once each");
  AudioRecord.failSource=false;AudioRecord.silenced=false;LocalNative.probability=.45f;LocalNative.entered=new CountDownLatch(1);LocalNative.resume=new CountDownLatch(1);allowed.set(true);Listen racing=new Listen();ConversationMic next=new ConversationMic("fixture",360,6,.4f,aec->allowed.get(),racing);next.start();check(LocalNative.entered.await(3,TimeUnit.SECONDS),"capture fixture enters detection before output begins");allowed.set(false);LocalNative.resume.countDown();until(()->racing.meters.get()>0);check(racing.starts.get()==0,"playback starting during VAD invalidates the in-flight detection");next.close();if(!racing.released.await(3,TimeUnit.SECONDS))throw new AssertionError("race fixture release");LocalNative.entered=null;LocalNative.resume=null;
  ManualCapture manual=new ManualCapture();manual.begin();short[] frame=new short[512];for(int i=0;i<700;i++)manual.append(frame);ManualCapture.Result result=manual.finish();check(result.clipped&&result.pcm.length==16000*18,"held microphone buffer is bounded to eighteen seconds");check(manual.finish()==null,"duplicate release cannot send a duplicate reply");manual.begin();manual.append(frame);manual.cancel();check(manual.finish()==null,"lifecycle cancellation clears held audio");
  System.out.println("MIC_CAPTURE_HOST_CHECKS: "+checks);
 }
}
