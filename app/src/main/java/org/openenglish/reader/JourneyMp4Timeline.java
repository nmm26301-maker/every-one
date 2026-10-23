package org.openenglish.reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/** Reads only MP4 movie/track timing; compensates an empty edit ignored by older extractors.
 * Timing reference: AOSP android-10.0.0_r1 MPEG4Extractor.cpp (elst, ISO 14496-12 8.6.6).
 * It does not decode media or implement ASR. Unsupported multi-part edits fail closed.
 */
public final class JourneyMp4Timeline {
  private final FileChannel file;
  private int boxes;
  private JourneyMp4Timeline(FileChannel file) { this.file=file; }
  private static final class Box {
    long data,end;String type;
    Box(long data,long end,String type){this.data=data;this.end=end;this.type=type;}
  }
  private Box box(long at,long end) throws IOException {
    if(++boxes>10000 || end-at<8)throw new IOException("视频时间轴结构异常");
    ByteBuffer b=read(at,8);long size=b.getInt()&0xffffffffL;
    byte[] name=new byte[4];b.get(name);String type=new String(name,java.nio.charset.StandardCharsets.US_ASCII);
    long header=8;
    if(size==1){size=read(at+8,8).getLong();header=16;}
    else if(size==0)size=end-at;
    if(size<header || size>end-at)throw new IOException("视频时间轴区块不完整");
    return new Box(at+header,at+size,type);
  }
  private ByteBuffer read(long at,int count) throws IOException {
    ByteBuffer b=ByteBuffer.allocate(count);
    while(b.hasRemaining()){int n=file.read(b,at+b.position());if(n<=0)throw new IOException("无法读取视频时间轴");}
    b.flip();return b;
  }
  private long scale(Box box) throws IOException {
    int version=read(box.data,1).get()&255;
    if(version>1)throw new IOException("不支持此视频时间轴版本");
    long at=box.data+(version==1?20:12);
    if(at+4>box.end)throw new IOException("视频时间轴头不完整");
    return read(at,4).getInt()&0xffffffffL;
  }
  private Box find(Box parent,String type) throws IOException {
    for(long at=parent.data;at<parent.end;){Box b=box(at,parent.end);if(type.equals(b.type))return b;at=b.end;}
    return null;
  }
  private long edits(Box edits,long movieScale,long mediaScale) throws IOException {
    ByteBuffer head=read(edits.data,8);int version=head.get()&255;head.position(4);long count=head.getInt()&0xffffffffL;
    if(count<=1)return 0;
    if(count!=2 || version>1)throw new IOException("暂不支持多段编辑的音轨，请使用普通视频");
    int width=version==1?20:12;
    if(edits.data+8+width*2>edits.end)throw new IOException("音轨编辑时间轴不完整");
    ByteBuffer first=read(edits.data+8,width),second=read(edits.data+8+width,width);
    long duration=version==1?first.getLong():first.getInt()&0xffffffffL;
    long firstTime=version==1?first.getLong():first.getInt();
    if(version==1)second.getLong();else second.getInt();
    long mediaTime=version==1?second.getLong():second.getInt();
    if(firstTime!=-1 || mediaTime<0 || first.getInt()!=0x10000 || second.getInt()!=0x10000
        || duration<0 || movieScale<=0 || mediaScale<=0)
      throw new IOException("音轨编辑偏移无法可靠解析");
    double offset=duration*1000000.0/movieScale-mediaTime*1000000.0/mediaScale;
    if(!Double.isFinite(offset) || Math.abs(offset)>Integer.MAX_VALUE*1000.0)
      throw new IOException("音轨偏移超出支持范围");
    return Math.round(offset);
  }
  public static long audioOffset(FileChannel file,long base,long length) throws IOException {
    JourneyMp4Timeline reader=new JourneyMp4Timeline(file);
    if(length<8)return 0;
    ByteBuffer first=reader.read(base+4,4);byte[] type=new byte[4];first.get(type);
    String name=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
    if(!name.equals("ftyp") && !name.equals("moov") && !name.equals("free") && !name.equals("mdat"))return 0;
    Box movie=reader.find(new Box(base,base+length,"root"),"moov");if(movie==null)return 0;
    Box mvhd=reader.find(movie,"mvhd");if(mvhd==null)return 0;
    long movieScale=reader.scale(mvhd);
    for(long at=movie.data;at<movie.end;){
      Box track=reader.box(at,movie.end);at=track.end;if(!track.type.equals("trak"))continue;
      Box media=reader.find(track,"mdia");if(media==null)continue;
      Box handler=reader.find(media,"hdlr");if(handler==null || handler.end-handler.data<12)continue;
      if(reader.read(handler.data+8,4).getInt()!=0x736f756e)continue;
      Box edts=reader.find(track,"edts"),mdhd=reader.find(media,"mdhd");
      if(edts==null || mdhd==null)return 0;
      Box edit=reader.find(edts,"elst");return edit==null?0:reader.edits(edit,movieScale,reader.scale(mdhd));
    }
    return 0;
  }
  public static long correction(long offset,long firstSample) throws IOException {
    if(offset==0)return 0;
    if(Math.abs(firstSample-offset)<1000)return 0;
    if(Math.abs(firstSample)<1000)return offset;
    throw new IOException("手机返回的音轨起点与视频编辑偏移不一致，无法保证字幕同步");
  }
}
