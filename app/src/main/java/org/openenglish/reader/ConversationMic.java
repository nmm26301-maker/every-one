package org.openenglish.reader;

import android.media.*;
import android.media.audiofx.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;

/** One capture session owns every hardware/native resource; setup and teardown never run on UI. */
public final class ConversationMic {
 public interface Permit {boolean allowed(boolean echoCancellation);}
 public interface Listener extends VoiceGate.Listener {
  void ready(boolean echoCancellation,int source);void meter(int value,double rms,float probability,boolean guarded,boolean silenced);void notice(String message);void error(String message);void released();
 }
 private final AtomicBoolean stopping=new AtomicBoolean();
 private final ConcurrentLinkedQueue<Integer> commands=new ConcurrentLinkedQueue<>();
 private final Listener listener;private final Permit permit;private final String model;private final int silence;private final int source;private final float threshold;
 private volatile AudioRecord recorder;
 public ConversationMic(String model,int silence,int source,float threshold,Permit permit,Listener listener){this.model=model;this.silence=silence;this.source=source;this.threshold=threshold;this.permit=permit;this.listener=listener;}
 public void beginManual(){commands.offer(1);}
 public void finishManual(){commands.offer(2);}
 public void cancelManual(){commands.offer(3);}
 public void start(){new Thread(this::capture,"english-duplex-mic").start();}
 public void reset(){commands.offer(3);}
 public void finishAutomatic(){commands.offer(4);}
 public void close(){if(!stopping.compareAndSet(false,true))return;
  new Thread(()->{AudioRecord r=recorder;if(r!=null)try{r.stop();}catch(Exception ignored){}},"english-stop-mic").start();
 }
 private void capture(){
  AudioRecord record=null;AcousticEchoCanceler echo=null;NoiseSuppressor noise=null;long vad=0;
  try{
   android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
   int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(min<=0)throw new IllegalStateException("手机不支持 16 kHz 录音");
   record=new AudioRecord(source,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,VoiceGate.FRAME*8));
   if(record.getState()!=AudioRecord.STATE_INITIALIZED&&source!=MediaRecorder.AudioSource.MIC){record.release();record=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,VoiceGate.FRAME*8));}
   recorder=record;
   if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("麦克风初始化失败");
   if(stopping.get())return;
   boolean aec=false;try{if(AcousticEchoCanceler.isAvailable()){echo=AcousticEchoCanceler.create(record.getAudioSessionId());if(echo!=null){echo.setEnabled(true);aec=echo.getEnabled();}}}catch(Exception ignored){}
   try{if(record.getAudioSource()==MediaRecorder.AudioSource.VOICE_COMMUNICATION&&NoiseSuppressor.isAvailable()){noise=NoiseSuppressor.create(record.getAudioSessionId());if(noise!=null)noise.setEnabled(true);}}catch(Exception ignored){}
   vad=LocalNative.vadOpen(model);if(stopping.get())return;
   record.startRecording();if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("无法启动麦克风，请检查其他应用是否占用");
   listener.ready(aec,record.getAudioSource());VoiceGate gate=new VoiceGate(silence,listener);ManualCapture manual=new ManualCapture();
   short[] frame=new short[VoiceGate.FRAME];float[] samples=new float[VoiceGate.FRAME];int filled=0;long meterAt=0;boolean guarded=false;
   while(!stopping.get()){
    int n=record.read(frame,filled,frame.length-filled,AudioRecord.READ_BLOCKING);
    if(n<0){if(stopping.get())break;throw new IllegalStateException("录音中断（"+n+"），请重新开始");}
    if(n==0)continue;filled+=n;if(filled<frame.length)continue;filled=0;
    Integer command;while((command=commands.poll())!=null){
     if(command==4){if(manual.active()){ManualCapture.Result result=manual.finish();if(result!=null)listener.end(result.pcm,result.clipped);}else if(gate.speaking())gate.finish();else listener.notice("还没有录到这句话，请先说话或按住录音");LocalNative.vadReset(vad);continue;}
     gate.reset();LocalNative.vadReset(vad);
     if(command==1){manual.begin();listener.start();}
     else if(command==2){ManualCapture.Result result=manual.finish();if(result!=null)listener.end(result.pcm,result.clipped);else listener.notice("录音太短，请按住按钮说完一句再松开");}
     else manual.cancel();
    }
    double volume=VoiceGate.rms(frame);boolean allowed=permit.allowed(aec);float probability=Float.NaN;
    if(manual.active()){manual.append(frame);}
    else if(!allowed){if(!guarded){gate.reset();LocalNative.vadReset(vad);}guarded=true;}
    else {
     if(guarded){guarded=false;gate.reset();LocalNative.vadReset(vad);}
     for(int i=0;i<frame.length;i++)samples[i]=frame[i]/32768f;
     probability=LocalNative.vadProbability(vad,samples);boolean before=gate.speaking();
     if(permit.allowed(aec))gate.frame(frame,probability>=threshold&&volume>.0005);
     else {gate.reset();LocalNative.vadReset(vad);guarded=true;}
     if(before&&!gate.speaking())LocalNative.vadReset(vad);
    }
    long now=SystemClock.elapsedRealtime();if(now-meterAt>=150){meterAt=now;boolean silenced=false;
     if(Build.VERSION.SDK_INT>=29)try{AudioRecordingConfiguration config=record.getActiveRecordingConfiguration();silenced=config!=null&&config.isClientSilenced();}catch(RuntimeException ignored){}
     listener.meter((int)Math.min(1000,volume*10000),volume,probability,!allowed&&!manual.active(),silenced);
    }
   }
  }catch(Exception e){if(!stopping.get())listener.error(e.getMessage()==null?"麦克风无法使用":e.getMessage());}
  catch(OutOfMemoryError|UnsatisfiedLinkError e){if(!stopping.get())listener.error("本地语音初始化失败，请检查模型或可用内存");}
  finally{
   stopping.set(true);recorder=null;
   if(vad!=0)try{LocalNative.vadClose(vad);}catch(Exception ignored){}
   if(echo!=null)try{echo.release();}catch(Exception ignored){}if(noise!=null)try{noise.release();}catch(Exception ignored){}
   if(record!=null){try{record.stop();}catch(Exception ignored){}try{record.release();}catch(Exception ignored){}}
   listener.released();
  }
 }
}
