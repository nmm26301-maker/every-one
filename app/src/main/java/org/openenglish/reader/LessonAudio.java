package org.openenglish.reader;
import java.io.*;
/** Plain PCM WAV output for local replay. No codec or server is needed. */
public final class LessonAudio {
 private LessonAudio(){}
 public static void write(File target,float[] pcm)throws IOException{
  writeLimit(target,pcm,PronunciationCoach.MAX_SAMPLES);
 }
 public static void writeSpeech(File target,float[] pcm)throws IOException{writeLimit(target,pcm,16000*90);}
 private static void writeLimit(File target,float[] pcm,int max)throws IOException{
  if(pcm.length>max)throw new IOException("录音太长");
  File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建录音目录");
  File temp=new File(parent,target.getName()+".part");
  try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))){
   out.writeBytes("RIFF");out.writeInt(Integer.reverseBytes(36+pcm.length*2));out.writeBytes("WAVEfmt ");out.writeInt(Integer.reverseBytes(16));out.writeShort(Short.reverseBytes((short)1));out.writeShort(Short.reverseBytes((short)1));out.writeInt(Integer.reverseBytes(16000));out.writeInt(Integer.reverseBytes(32000));out.writeShort(Short.reverseBytes((short)2));out.writeShort(Short.reverseBytes((short)16));out.writeBytes("data");out.writeInt(Integer.reverseBytes(pcm.length*2));
   for(float x:pcm){if(Float.isNaN(x)||Float.isInfinite(x))x=0;int sample=Math.round(Math.max(-1,Math.min(.999969f,x))*32768);out.writeShort(Short.reverseBytes((short)sample));}
  }catch(IOException e){temp.delete();throw e;}
  if(!temp.renameTo(target)){temp.delete();throw new IOException("录音无法保存");}
 }
}
