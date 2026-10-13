package org.openenglish.reader;
import java.util.*;
import java.lang.management.ManagementFactory;
public final class VoiceGateBufferTest {
 static int checks;
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static class Log implements VoiceGate.Listener,VoiceGateOld.Listener {
  int starts;List<float[]> clips=new ArrayList<>();List<Boolean> limited=new ArrayList<>();
  public void start(){starts++;}public void end(float[] pcm,boolean cap){clips.add(pcm);limited.add(cap);}
 }
 static void same(Log a,Log b,String name){
  check(a.starts==b.starts&&a.clips.size()==b.clips.size()&&a.limited.equals(b.limited),name+" boundaries");
  for(int n=0;n<a.clips.size();n++)if(!Arrays.equals(a.clips.get(n),b.clips.get(n)))throw new AssertionError(name+" PCM "+n);
  check(true,name+" sample-for-sample audio");
 }
 public static void main(String[] args)throws Exception {
  Log current=new Log(),old=new Log();VoiceGate a=new VoiceGate(360,current);VoiceGateOld b=new VoiceGateOld(360,old);Random r=new Random(20261013);short[] frame=new short[512];
  for(int i=0;i<12000;i++){
   boolean voice=i%120>=17&&i%120<50;
   for(int n=0;n<frame.length;n++)frame[n]=(short)(r.nextInt(4001)-2000);
   a.frame(frame,voice);b.frame(frame,voice);
   if(i%379==0){a.reset();b.reset();}
   if(i%233==0){a.finish();b.finish();}
  }
  a.finish();b.finish();same(current,old,"reused input across repeated reset and manual finish");
  current=new Log();old=new Log();a=new VoiceGate(600,current);b=new VoiceGateOld(600,old);
  for(int i=0;i<700;i++){Arrays.fill(frame,(short)i);a.frame(frame,true);b.frame(frame,true);}
  for(int i=0;i<20;i++){Arrays.fill(frame,(short)0);a.frame(frame,false);b.frame(frame,false);}
  same(current,old,"capacity and balanced silence");
  float[] delivered=current.clips.get(0).clone();for(int i=0;i<1000;i++){Arrays.fill(frame,(short)i);a.frame(frame,false);}check(Arrays.equals(delivered,current.clips.get(0)),"ring reuse cannot mutate delivered recordings");
  a.reset();for(int i=0;i<4;i++){Arrays.fill(frame,(short)1234);a.frame(frame,true);}a.finish();float[] fresh=current.clips.get(current.clips.size()-1);check(fresh.length==4*512&&fresh[0]==1234/32768f,"reset discards every stale pre-roll frame");
  VoiceGate quiet=new VoiceGate(360,new Log());Arrays.fill(frame,(short)0);for(int i=0;i<10000;i++)quiet.frame(frame,false);
  java.lang.management.ThreadMXBean base=ManagementFactory.getThreadMXBean();
  if(base instanceof com.sun.management.ThreadMXBean){
   com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;
   if(bean.isThreadAllocatedMemorySupported()){
    bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
    long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)quiet.frame(frame,false);long now=bean.getThreadAllocatedBytes(thread)-before;
    VoiceGateOld legacy=new VoiceGateOld(360,new Log());before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)legacy.frame(frame,false);long previous=bean.getThreadAllocatedBytes(thread)-before;
    System.out.println("HOST_10000_QUIET_FRAMES_ALLOCATED_BYTES: old="+previous+" new="+now);
    check(previous>=10000000&&now<65536,"quiet pre-roll avoids per-frame heap arrays");
   }
  }
  System.out.println("VOICE_BUFFER_CHECKS: "+checks);
 }
}
