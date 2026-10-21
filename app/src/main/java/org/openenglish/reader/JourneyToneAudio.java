package org.openenglish.reader;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** 将 App 私有的 AAC 跟读录音解码为单声道 PCM；仅供离线音高轮廓反馈。 */
public final class JourneyToneAudio {
  public static final class Samples {
    public final short[] monoPcm;
    public final int sampleRate;
    Samples(short[] pcm, int rate) {
      monoPcm = pcm; sampleRate = rate;
    }
  }

  private JourneyToneAudio() {}

  public static Samples decode(File file) throws IOException {
    if (file == null || !file.isFile() || file.length() < 256)
      throw new IOException("找不到有效录音，请重新录制单个粤语音节");
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec decoder = null;
    boolean started = false;
    try {
      extractor.setDataSource(file.getAbsolutePath());
      MediaFormat format = findAudio(extractor);
      String mime = format.getString(MediaFormat.KEY_MIME);
      decoder = MediaCodec.createDecoderByType(mime);
      decoder.configure(format, null, null, 0);
      decoder.start();
      started = true;
      return drain(decoder, extractor, format);
    } catch (IllegalStateException e) {
      throw new IOException("手机音频解码器无法处理这段录音", e);
    } finally {
      if (decoder != null) {
        try { if (started) decoder.stop(); }
        catch (IllegalStateException ignored) {}
        decoder.release();
      }
      extractor.release();
    }
  }

  private static MediaFormat findAudio(MediaExtractor ex) throws IOException {
    for (int i = 0; i < ex.getTrackCount(); i++) {
      MediaFormat f = ex.getTrackFormat(i);
      String mime = f.getString(MediaFormat.KEY_MIME);
      if (mime != null && mime.startsWith("audio/")) {
        ex.selectTrack(i);
        return f;
      }
    }
    throw new IOException("录音文件不包含可解码音频");
  }

  private static Samples drain(MediaCodec codec, MediaExtractor ex,
                               MediaFormat original) throws IOException {
    boolean inputDone = false, outputDone = false;
    int rate = original.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    int channels = original.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    ByteArrayOutputStream mono = new ByteArrayOutputStream();
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    // 最大约 30 秒，避免误处理大文件或永久等待损坏的解码器。
    int quietCycles = 0;
    while (!outputDone && quietCycles < 1000) {
      boolean moved = false;
      if (!inputDone) {
        int input = codec.dequeueInputBuffer(10000);
        if (input >= 0) {
          ByteBuffer buffer = codec.getInputBuffer(input);
          if (buffer == null) throw new IOException("音频解码输入缓存为空");
          buffer.clear();
          int length = ex.readSampleData(buffer, 0);
          if (length < 0) {
            codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            inputDone = true;
          } else {
            codec.queueInputBuffer(input, 0, length, ex.getSampleTime(), 0);
            ex.advance();
          }
          moved = true;
        }
      }
      int index = codec.dequeueOutputBuffer(info, 10000);
      if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat f = codec.getOutputFormat();
        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        // API 23 decoder defaults to signed 16-bit PCM when this key is absent.
        if (f.containsKey("pcm-encoding")
            && f.getInteger("pcm-encoding") != AudioFormat.ENCODING_PCM_16BIT)
          throw new IOException("音频解码器输出了不支持的 PCM 格式");
        moved = true;
      } else if (index >= 0) {
        try {
          if (info.size > 0) {
            ByteBuffer frame = codec.getOutputBuffer(index);
            if (frame == null) throw new IOException("音频解码输出缓存为空");
            appendMono(frame, info.offset, info.size, channels, rate, mono);
          }
          outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        } finally {
          codec.releaseOutputBuffer(index, false);
        }
        moved = true;
      }
      quietCycles = moved ? 0 : quietCycles + 1;
    }
    if (!outputDone) throw new IOException("音频解码超时");
    byte[] bytes = mono.toByteArray();
    if (bytes.length < 2 || rate < 8000 || rate > 96000)
      throw new IOException("有效人声数据不足，无法做声调走势分析");
    short[] values = new short[bytes.length / 2];
    for (int i = 0; i < values.length; i++) {
      int low = bytes[i * 2] & 255;
      int high = bytes[i * 2 + 1];
      values[i] = (short) ((high << 8) | low);
    }
    return new Samples(values, rate);
  }

  private static void appendMono(ByteBuffer buf, int offset, int size,
                                 int channels, int rate, ByteArrayOutputStream mono)
      throws IOException {
    if (channels < 1 || channels > 8) throw new IOException("不支持的音频通道数量");
    int frameBytes = channels * 2;
    if (size % frameBytes != 0) throw new IOException("PCM 样本边界异常");
    // Maximum 30 sec of decoded mono recording at the actual sample rate.
    int maxBytes = rate * 30 * 2;
    if (mono.size() + size / channels > maxBytes)
      throw new IOException("声调样本超过 30 秒限制");
    for (int i = offset; i < offset + size; i += frameBytes) {
      mono.write(buf.get(i) & 255);
      mono.write(buf.get(i + 1) & 255);
    }
  }
}
