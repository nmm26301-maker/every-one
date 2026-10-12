package org.openenglish.reader;

import android.media.*;
import java.util.*;

/** A short manual take. All hardware setup/read/release run on its own worker.
 * Non-blocking read lets cancellation finish without stopping a recorder from the UI. */
public final class LessonRecorder {
 public interface Listener {void ready();void meter(double rms,int millis);void done(float[] pcm);void error(String reason);}
 private volatile boolean stop,cancel;private volatile Thread thread;
 public synchronized boolean start(Listener listener){
  if(thread!=null)return false;stop=false;cancel=false;thread=new Thread(()->capture(listener),"language-take");thread.start();return true;
 }
 public void finish(){stop=true;}public void cancel(){cancel=true;stop=true;}
 public boolean busy(){return thread!=null;}
 private void capture(Listener l){
  AudioRecord record=null;String error=null;float[] result=null;
  try{
   int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(min<=0)throw new IllegalStateException("手机暂不支持这次录音格式");
   record=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,8192));
   if(record.getState()!=AudioRecord.STATE_INITIALIZED){record.release();record=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,8192));}
   if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("麦克风未能启动");
   if(stop){if(cancel)return;throw new IllegalStateException("请等开始提示后，再完整读一遍");}record.startRecording();if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("麦克风被其他应用占用");l.ready();
   short[] buffer=new short[640];float[] pcm=new float[PronunciationCoach.MAX_SAMPLES];int used=0,lastMeter=0;long lastRead=System.currentTimeMillis();
   while(!stop&&used<pcm.length){
    int n=record.read(buffer,0,Math.min(buffer.length,pcm.length-used),AudioRecord.READ_NON_BLOCKING);
    if(n<0)throw new IllegalStateException("录音中断，请关闭占用麦克风的应用后重试");
    if(n==0){if(System.currentTimeMillis()-lastRead>2500)throw new IllegalStateException("麦克风没有返回声音，请重新开始");Thread.sleep(8);continue;}
    lastRead=System.currentTimeMillis();double sum=0;for(int i=0;i<n;i++){float x=buffer[i]/32768f;pcm[used++]=x;sum+=(double)x*x;}
    if(used-lastMeter>=1600){l.meter(Math.sqrt(sum/n),used*1000/16000);lastMeter=used;}
   }
   if(!cancel){if(used<1600)throw new IllegalStateException("这次太短了，等提示开始后，完整读一遍");result=Arrays.copyOf(pcm,used);}
  }catch(SecurityException e){error="请允许麦克风权限，再开始跟读";}catch(Exception e){error=e.getMessage()==null?"录音没有完成，请再试一次":e.getMessage();}
  finally{if(record!=null){try{record.stop();}catch(Exception ignored){}record.release();}thread=null;}
  if(!cancel){if(error!=null)l.error(error);else if(result!=null)l.done(result);}
 }
}
