package org.openenglish.reader;
import ai.onnxruntime.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Offline expected-phone alignment; TIFA 1.0 ST weights CC BY-NC-SA 4.0.
 * Android entry point must gate SDK >=24. Independent of Whisper and chat. */
public final class LocalTifa implements AutoCloseable {
 public static final class Control {
  private volatile boolean cancelled,completed;private final long deadline=System.nanoTime()+120000000000L;
  private OrtSession.RunOptions running;
  public synchronized void cancel(){cancelled=true;if(running!=null)try{running.setTerminate(true);}catch(OrtException ignored){}}
  public boolean cancelled(){return cancelled;}
  public boolean completed(){return completed;}
  public void complete(){check();completed=true;}
  public void check(){if(cancelled||Thread.currentThread().isInterrupted())throw new CancellationException("已取消逐段纠音");if(!completed&&System.nanoTime()>deadline)throw new CancellationException("逐段分析等待超时");}
  synchronized void attach(OrtSession.RunOptions r){check();running=r;}
  synchronized void detach(OrtSession.RunOptions r){if(running==r)running=null;}
 }
 private final OrtEnvironment env;private OrtSession spectrogram,model;
 public LocalTifa(File spec,File net,Control c)throws OrtException{
  c.check();env=OrtEnvironment.getEnvironment();
  try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()){
   opts.setIntraOpNumThreads(2);opts.setInterOpNumThreads(1);
   spectrogram=env.createSession(spec.getAbsolutePath(),opts);c.check();model=env.createSession(net.getAbsolutePath(),opts);c.check();
  }catch(RuntimeException|OrtException e){close();throw e;}
 }
 public int[][] align(float[] pcm,long[] tokens,int[] groups,Control c)throws OrtException{
  if(tokens.length<1||tokens.length>128||groups.length!=tokens.length)throw new IllegalArgumentException("模板不可用");
  float[] up=upsample(pcm);c.check();
  try(OnnxTensor audio=OnnxTensor.createTensor(env,new float[][]{up});OnnxTensor duration=OnnxTensor.createTensor(env,new float[]{pcm.length/16000f});OrtSession.RunOptions run=new OrtSession.RunOptions()){
   Map<String,OnnxTensor> inputs=new HashMap<>();inputs.put("waveform",audio);inputs.put("duration",duration);
   c.attach(run);try(OrtSession.Result spec=spectrogram.run(inputs,new HashSet<>(Arrays.asList("spectrogram","maskT")),run)){
    c.check();boolean[] mask=new boolean[tokens.length];Arrays.fill(mask,true);
    try(OnnxTensor ids=OnnxTensor.createTensor(env,new long[][]{tokens});OnnxTensor maskN=OnnxTensor.createTensor(env,new boolean[][]{mask})){
     inputs.clear();inputs.put("spectrogram",(OnnxTensor)spec.get("spectrogram").get());inputs.put("maskT",(OnnxTensor)spec.get("maskT").get());inputs.put("tokens",ids);inputs.put("maskN",maskN);
     try(OrtSession.Result result=model.run(inputs,Collections.singleton("similarities"),run)){
      c.check();float[][] similarities=((float[][][])result.get("similarities").get().getValue())[0];return TifaDecoder.decode(similarities,groups);
     }
    }
   }finally{c.detach(run);}
  }
 }
 public static float[] upsample(float[] pcm){
  if(pcm.length<1600||pcm.length>192000)throw new IllegalArgumentException("请录 0.1 至 12 秒完整练习");
  float[] out=new float[pcm.length*3];for(int i=0;i<pcm.length;i++){float a=pcm[i],b=pcm[Math.min(i+1,pcm.length-1)];if(!Float.isFinite(a)||Math.abs(a)>1.01f)throw new IllegalArgumentException("录音数据不可用");out[i*3]=a;out[i*3+1]=(a*2+b)/3;out[i*3+2]=(a+b*2)/3;}return out;
 }
 @Override public void close(){if(model!=null){try{model.close();}catch(OrtException ignored){}model=null;}if(spectrogram!=null){try{spectrogram.close();}catch(OrtException ignored){}spectrogram=null;}}
}
