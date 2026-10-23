package org.openenglish.reader;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;

public final class JourneyMp4TimelineTest {
  private static byte[] join(byte[]...parts)throws Exception {
    ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] part:parts)out.write(part);return out.toByteArray();
  }
  private static byte[] box(String name,byte[] body)throws Exception {
    return join(ByteBuffer.allocate(4).putInt(body.length+8).array(),name.getBytes("US-ASCII"),body);
  }
  private static byte[] movie(int entries)throws Exception {
    byte[] mvhd=ByteBuffer.allocate(20).putInt(12,1000).array();
    byte[] mdhd=ByteBuffer.allocate(20).putInt(12,16000).array();
    byte[] hdlr=ByteBuffer.allocate(12).putInt(8,0x736f756e).array();
    byte[] edits=ByteBuffer.allocate(32).putInt(4,entries).putInt(8,1936).putInt(12,-1)
        .putInt(16,0x10000).putInt(20,2064).putInt(24,0).putInt(28,0x10000).array();
    byte[] media=box("mdia",join(box("mdhd",mdhd),box("hdlr",hdlr)));
    byte[] track=box("trak",join(box("edts",box("elst",edits)),media));
    return join(box("ftyp",new byte[4]),box("moov",join(box("mvhd",mvhd),track)));
  }
  public static void main(String[] args)throws Exception {
    Path path=Files.createTempFile("movie-timing-",".mp4");
    try {
      Files.write(path,movie(2));
      try(FileChannel c=FileChannel.open(path)) {
        long offset=JourneyMp4Timeline.audioOffset(c,0,c.size());
        if(offset!=1936000)throw new AssertionError("empty edit movie offset");
        if(JourneyMp4Timeline.correction(offset,0)!=1936000)throw new AssertionError("older extractor correction");
        if(JourneyMp4Timeline.correction(offset,offset)!=0)throw new AssertionError("do not double-correct newer extractor");
        try{JourneyMp4Timeline.correction(offset,500000);throw new AssertionError("ambiguous timeline");}
        catch(IOException expected){}
      }
      Files.write(path,movie(3));
      try(FileChannel c=FileChannel.open(path)) {
        try{JourneyMp4Timeline.audioOffset(c,0,c.size());throw new AssertionError("complex edits");}
        catch(IOException expected){}
      }
      Files.write(path,new byte[8]);
      try(FileChannel c=FileChannel.open(path)) {
        if(JourneyMp4Timeline.audioOffset(c,0,c.size())!=0)throw new AssertionError("other containers");
      }
    }finally{Files.deleteIfExists(path);}
    System.out.println("JourneyMp4Timeline: OK");
  }
}
