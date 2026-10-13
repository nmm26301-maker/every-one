package android.media;
import java.util.concurrent.atomic.AtomicInteger;
public class AudioRecord {
 public static final int STATE_INITIALIZED=1,RECORDSTATE_RECORDING=3,READ_BLOCKING=0,READ_NON_BLOCKING=1;
 public static volatile boolean readError,noData;
 public static volatile int maxRead=127,delayMs=2;
 public static volatile int lastMode;public static final AtomicInteger attempts=new AtomicInteger(),created=new AtomicInteger();
 public static volatile boolean failSource,silenced;public static final AtomicInteger reads=new AtomicInteger(),released=new AtomicInteger();
 private volatile boolean recording;private final int source;
 public AudioRecord(int s,int rate,int channels,int bits,int buffer){source=s;created.incrementAndGet();}
 public static int getMinBufferSize(int rate,int channels,int bits){return 4096;}
 public int getState(){return failSource&&source!=1?0:1;}
 public int getAudioSessionId(){return 9;}
 public int getAudioSource(){return source;}
 public void startRecording(){recording=true;}
 public int getRecordingState(){return recording?3:1;}
 public int read(short[] p,int offset,int length,int mode){lastMode=mode;attempts.incrementAndGet();if(noData)return 0;try{Thread.sleep(delayMs);}catch(InterruptedException e){Thread.currentThread().interrupt();}if(!recording||readError)return -3;int n=Math.min(maxRead,length);for(int i=0;i<n;i++)p[offset+i]=(short)(silenced?0:((i%2==0)?2000:-2000));reads.incrementAndGet();return n;}
 public AudioRecordingConfiguration getActiveRecordingConfiguration(){return new AudioRecordingConfiguration();}
 public void stop(){recording=false;}public void release(){released.incrementAndGet();recording=false;}
}
