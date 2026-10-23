package org.openenglish.reader;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;
import java.nio.ByteBuffer;

/** Bounded MediaCodec extraction, following JourneyToneAudio, streamed into a temporary mono WAV. */
public final class JourneyVideoClip {
  private JourneyVideoClip() {}

  public static File extract(Context context,Uri uri,int startMs,int durationMs,File cache,
      CloudClient.Job job) throws Exception {
    if (uri==null || startMs<0 || durationMs<100 || durationMs>120000
        || (long)startMs+durationMs>Integer.MAX_VALUE) throw new IOException("视频片段无效");
    job.check();
    File target=File.createTempFile("everyone-caption-",".wav",cache);
    MediaExtractor extractor=new MediaExtractor(); MediaCodec codec=null;
    boolean started=false, complete=false;
    try {
      extractor.setDataSource(context,uri,null);
      MediaFormat format=null;
      for(int i=0;i<extractor.getTrackCount();i++) {
        MediaFormat candidate=extractor.getTrackFormat(i);
        String mime=candidate.getString(MediaFormat.KEY_MIME);
        if (mime!=null && mime.startsWith("audio/")) { format=candidate;extractor.selectTrack(i);break; }
      }
      if(format==null)throw new IOException("所选视频没有可解码音轨");
      extractor.seekTo(startMs*1000L,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format,null,null,0);codec.start();started=true;
      try(RandomAccessFile wav=new RandomAccessFile(target,"rw")) {
        wav.write(new byte[44]);
        decode(codec,extractor,format,startMs*1000L,(startMs+(long)durationMs)*1000,wav,job);
      }
      job.check();complete=true;return target;
    } finally {
      if(codec!=null) {
        try{if(started)codec.stop();}catch(RuntimeException ignored){}
        codec.release();
      }
      extractor.release();if(!complete)target.delete();
    }
  }

  private static void decode(MediaCodec codec,MediaExtractor extractor,MediaFormat original,
      long start,long end,RandomAccessFile wav,CloudClient.Job job) throws Exception {
    int rate=original.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    int channels=original.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    int writtenRate=0;
    boolean inputDone=false,outputDone=false;
    MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    long deadline=SystemClock.elapsedRealtime()+60000;
    while(!outputDone) {
      job.check();if(SystemClock.elapsedRealtime()>deadline)throw new IOException("视频音轨解码超时");
      if(!inputDone) {
        int index=codec.dequeueInputBuffer(10000);
        if(index>=0) {
          ByteBuffer input=codec.getInputBuffer(index);
          if(input==null)throw new IOException("音轨解码输入缓存为空");
          input.clear();long time=extractor.getSampleTime();
          int count=time<0 || time>=end?-1:extractor.readSampleData(input,0);
          if(count<0) {
            codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;
          } else {codec.queueInputBuffer(index,0,count,time,0);extractor.advance();}
        }
      }
      int index=codec.dequeueOutputBuffer(info,10000);
      if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat f=codec.getOutputFormat();
        rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if(f.containsKey("pcm-encoding") && f.getInteger("pcm-encoding")!=AudioFormat.ENCODING_PCM_16BIT)
          throw new IOException("不支持此解码器的 PCM 格式");
      } else if(index>=0) {
        try {
          if(rate<8000 || rate>96000 || channels<1 || channels>8)
            throw new IOException("音轨格式不受支持");
          if(writtenRate!=0 && writtenRate!=rate)throw new IOException("片段内采样率发生变化");
          if(info.size>0) {
            ByteBuffer buffer=codec.getOutputBuffer(index);
            if(buffer==null)throw new IOException("音轨解码输出缓存为空");
            int bytes=channels*2;
            if(info.size%bytes!=0)throw new IOException("PCM 样本边界异常");
            int frames=info.size/bytes;
            int from=(int)Math.max(0,Math.min(frames,Math.ceil((start-info.presentationTimeUs)*rate/1000000.0)));
            int to=(int)Math.max(0,Math.min(frames,Math.ceil((end-info.presentationTimeUs)*rate/1000000.0)));
            long intended=Math.max(0,Math.round((info.presentationTimeUs-start)*rate/1000000.0)+from);
            long present=(wav.length()-44)/2;
            if(to>from && intended>present) pad(wav,intended-present);
            else if(intended<present) from+=(int)Math.min(to-from,present-intended);
            if(to>from) {
              if(wav.length()+(to-from)*2>24000000)throw new IOException("音频片段超过 24MB");
              byte[] mono=new byte[(to-from)*2];
              for(int frame=from;frame<to;frame++) {
                int sum=0;
                for(int channel=0;channel<channels;channel++) {
                  int at=info.offset+frame*bytes+channel*2;
                  sum+=(short)((buffer.get(at)&255)|(buffer.get(at+1)<<8));
                }
                int sample=sum/channels,at=(frame-from)*2;
                mono[at]=(byte)sample;mono[at+1]=(byte)(sample>>8);
              }
              wav.write(mono);writtenRate=rate;
            }
          }
          outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0 || info.presentationTimeUs>=end;
        } finally {codec.releaseOutputBuffer(index,false);}
      }
    }
    int size=(int)wav.length()-44;
    if(writtenRate==0)throw new IOException("所选区间没有音频");
    long expected=Math.round((end-start)*writtenRate/1000000.0);
    if(size/2<expected)pad(wav,expected-size/2);
    else if(size/2>expected)wav.setLength(44+expected*2);
    size=(int)wav.length()-44;
    wav.seek(0);wav.writeBytes("RIFF");wav.writeInt(Integer.reverseBytes(size+36));wav.writeBytes("WAVEfmt ");
    wav.writeInt(Integer.reverseBytes(16));wav.writeShort(Short.reverseBytes((short)1));
    wav.writeShort(Short.reverseBytes((short)1));wav.writeInt(Integer.reverseBytes(writtenRate));
    wav.writeInt(Integer.reverseBytes(writtenRate*2));wav.writeShort(Short.reverseBytes((short)2));
    wav.writeShort(Short.reverseBytes((short)16));wav.writeBytes("data");wav.writeInt(Integer.reverseBytes(size));
  }

  private static void pad(RandomAccessFile wav,long samples) throws IOException {
    long bytes=samples*2;
    if(bytes<0 || wav.length()+bytes>24000000)throw new IOException("音轨时间轴超出片段大小限制");
    byte[] zeros=new byte[8192];
    while(bytes>0) {int count=(int)Math.min(bytes,zeros.length);wav.write(zeros,0,count);bytes-=count;}
  }
}
