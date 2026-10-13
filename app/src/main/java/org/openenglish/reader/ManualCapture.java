package org.openenglish.reader;

/** Explicit microphone capture bypasses automatic speech detection; owned by the capture thread. */
public final class ManualCapture {
 private final short[] buffer=new short[VoiceGate.RATE*18];
 private int used;private boolean active,clipped;
 public void begin(){used=0;clipped=false;active=true;}
 public boolean active(){return active;}
 public boolean limitReached(){return active&&used==buffer.length;}
 public void append(short[] frame){if(!active)return;int n=Math.min(frame.length,buffer.length-used);System.arraycopy(frame,0,buffer,used,n);used+=n;if(used==buffer.length)clipped=true;}
 public static final class Result {public final float[] pcm;public final boolean clipped;Result(float[] pcm,boolean clipped){this.pcm=pcm;this.clipped=clipped;}}
 public Result finish(){if(!active)return null;active=false;if(used<1600){used=0;return null;}float[] pcm=new float[used];for(int i=0;i<used;i++)pcm[i]=buffer[i]/32768f;Result result=new Result(pcm,clipped);used=0;return result;}
 public void cancel(){active=false;used=0;clipped=false;}
}
