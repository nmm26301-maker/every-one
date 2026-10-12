package org.openenglish.reader;
import java.io.*;
/** Bounded PCM16/16kHz/mono reader for Whisper, shared with real JNI host tests. */
public final class LocalWav {
 private LocalWav() {}
 private static long u32(RandomAccessFile in)throws IOException{return (long)Integer.reverseBytes(in.readInt())&0xffffffffL;}
 public static float[] read(File file)throws IOException {
  if(file.length()<44 || file.length()>180L*32000+65536)throw new IOException("WAV size is invalid");
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){
   if(in.readInt()!=0x52494646)throw new IOException("Not RIFF WAV");u32(in);if(in.readInt()!=0x57415645)throw new IOException("Not WAV");boolean valid=false;
   while(in.getFilePointer()+8<=in.length()){
    int tag=in.readInt();long size=u32(in),start=in.getFilePointer();if(size>in.length()-start)throw new IOException("Truncated WAV");
    if(tag==0x666d7420){if(size<16)throw new IOException("WAV format missing");int format=Short.reverseBytes(in.readShort())&65535,channels=Short.reverseBytes(in.readShort())&65535;long rate=u32(in);u32(in);int align=Short.reverseBytes(in.readShort())&65535,bits=Short.reverseBytes(in.readShort())&65535;
     if(format!=1 || channels!=1 || rate!=16000 || align!=2 || bits!=16)throw new IOException("Whisper needs PCM16, mono, 16 kHz");valid=true;
    }else if(tag==0x64617461){if(!valid || size<2 || size%2!=0 || size>180L*32000)throw new IOException("Invalid PCM data");float[] pcm=new float[(int)size/2];for(int i=0;i<pcm.length;i++)pcm[i]=(float)Short.reverseBytes(in.readShort())/32768f;return pcm;}
    long end=start+size+(size&1);if(end>in.length())throw new IOException("Missing WAV padding");in.seek(end);
   }throw new IOException("WAV data missing");
  }
 }
 public static boolean hasSignal(float[] pcm){double sum=0;for(float x:pcm)sum+=(double)x*x;return pcm.length>0 && sum/pcm.length>0.000001;}
}
