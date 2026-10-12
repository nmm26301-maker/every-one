import org.openenglish.reader.*;
import android.media.AudioRecord;
import java.util.concurrent.*;
public class LessonRecorderTest {
 static int checks;static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 static class Result implements LessonRecorder.Listener {
  CountDownLatch ready=new CountDownLatch(1),finished=new CountDownLatch(1);volatile float[] pcm;volatile String error;volatile int meters;volatile long callbackThread;
  public void ready(){callbackThread=Thread.currentThread().getId();ready.countDown();}
  public void meter(double rms,int millis){meters++;}
  public void done(float[] p){pcm=p;finished.countDown();}
  public void error(String s){error=s;finished.countDown();}
 }
 static void reset(){AudioRecord.reads.set(0);AudioRecord.released.set(0);AudioRecord.failSource=false;AudioRecord.silenced=false;AudioRecord.readError=false;}
 static void awaitReads(int n)throws Exception{long until=System.currentTimeMillis()+2000;while(AudioRecord.reads.get()<n&&System.currentTimeMillis()<until)Thread.sleep(2);if(AudioRecord.reads.get()<n)throw new AssertionError("capture reads timeout");}
 static void awaitFree(LessonRecorder r)throws Exception{long until=System.currentTimeMillis()+4000;while(r.busy()&&System.currentTimeMillis()<until)Thread.sleep(2);check(!r.busy(),"recorder releases ownership");}
 public static void main(String[] args)throws Exception{
  reset();LessonRecorder r=new LessonRecorder();Result result=new Result();check(r.start(result),"starts");check(!r.start(new Result()),"double start rejected");check(result.ready.await(2,TimeUnit.SECONDS),"ready callback");awaitReads(20);r.finish();check(result.finished.await(2,TimeUnit.SECONDS),"manual finish");check(result.error==null&&result.pcm.length>=1600,"partial reads captured");check(result.meters>0,"volume and time meter");check(result.callbackThread!=Thread.currentThread().getId(),"hardware work off caller thread");check(AudioRecord.released.get()==1,"resource released before result");awaitFree(r);
  reset();result=new Result();r.start(result);result.ready.await(2,TimeUnit.SECONDS);r.cancel();awaitFree(r);Thread.sleep(15);check(result.finished.getCount()==1,"cancel drops partial audio");check(AudioRecord.released.get()==1,"cancel releases hardware");
  reset();AudioRecord.failSource=true;result=new Result();r.start(result);check(result.ready.await(2,TimeUnit.SECONDS),"MIC source fallback");awaitReads(20);r.finish();result.finished.await(2,TimeUnit.SECONDS);check(result.pcm!=null&&AudioRecord.released.get()==2,"fallback frees both records");awaitFree(r);
  reset();AudioRecord.readError=true;result=new Result();r.start(result);check(result.finished.await(2,TimeUnit.SECONDS),"read error ends take");check(result.error!=null&&result.pcm==null,"read error not a successful take");check(AudioRecord.released.get()==1,"error cleanup");awaitFree(r);
  reset();result=new Result();r.start(result);check(result.finished.await(7,TimeUnit.SECONDS),"12 second automatic cap");check(result.pcm!=null&&result.pcm.length==PronunciationCoach.MAX_SAMPLES,"bounded take size");check(!r.busy(),"ready for another take");
  System.out.println("Lesson capture checks passed: "+checks);
 }
}
