package org.openenglish.reader;
import java.util.*;

/** Bounded onset/end gate over 32 ms PCM frames; capture uses streaming Silero probabilities. */
final class VoiceGateOld {
 public static final int FRAME=512,RATE=16000;
 public interface Listener {void start();void end(float[] pcm,boolean clipped);}
 private final Listener listener;private final int quietFrames;private final ArrayDeque<short[]> pre=new ArrayDeque<>();
 private final short[] buffer=new short[RATE*18];private int used,onset,voiced,quiet;private boolean active,clipped;private double noise=.002;
 public VoiceGateOld(int silenceMs,Listener listener){this.listener=listener;quietFrames=Math.max(6,(silenceMs+31)/32);}
 public boolean speaking(){return active;}
 public static double rms(short[] frame){double sum=0;for(short s:frame){double value=s/32768.0;sum+=value*value;}return Math.sqrt(sum/frame.length);}
 private void append(short[] frame){int count=Math.min(frame.length,buffer.length-used);if(count>0){System.arraycopy(frame,0,buffer,used,count);used+=count;}if(count<frame.length)clipped=true;}
 public void frame(short[] frame){double energy=rms(frame);frame(frame,energy>Math.max(.006,noise*3.2));}
 public void frame(short[] frame,boolean speech){if(frame.length!=FRAME)throw new IllegalArgumentException("32 ms frame required");double energy=rms(frame);
  if(!active){if(!speech)noise=noise*.98+Math.min(energy,.02)*.02;pre.addLast(frame.clone());while(pre.size()>8)pre.removeFirst();onset=speech?onset+1:0;
   if(onset>=4){active=true;used=0;voiced=onset;quiet=0;clipped=false;for(short[] old:pre)append(old);pre.clear();listener.start();}return;}
  append(frame);if(speech){voiced++;quiet=0;}else quiet++;
  if(used>=buffer.length){clipped=true;finish();return;}
  if(quiet>=quietFrames){
   // Four consecutive neural speech frames already established onset. Keep short Yes/No answers.
   // Leave 160 ms of trailing audio instead of passing all endpoint silence to Whisper.
   finish();
  }
 }
 public void finish(){if(!active)return;
  int kept=clipped?used:Math.max(FRAME,used-Math.max(0,quiet-5)*FRAME);
  float[] pcm=new float[kept];for(int i=0;i<kept;i++)pcm[i]=buffer[i]/32768f;
  boolean limited=clipped;reset();listener.end(pcm,limited);
 }
 public void reset(){active=false;used=onset=voiced=quiet=0;clipped=false;pre.clear();}
}
