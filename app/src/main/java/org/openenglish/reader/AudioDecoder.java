package org.openenglish.reader;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;

/** Real decoded audio segment, downmixed and resampled to PCM16 mono WAV. */
public final class AudioDecoder {
  public static File clip(Context c, Uri uri, long startMs, long endMs, CloudClient.Job job)
      throws Exception {
    if (startMs < 0 || endMs <= startMs || endMs - startMs > 180000)
      throw new IOException("一次最多提取 3 分钟音频");
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec decoder = null;
    File file = null;
    try {
      extractor.setDataSource(c, uri, null);
      MediaFormat format = null;
      int track = -1;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat f = extractor.getTrackFormat(i);
        if (f.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
          format = f;
          track = i;
          break;
        }
      }
      if (track < 0) throw new IOException("文件没有可解码的音轨");
      extractor.selectTrack(track);
      // AAC/MP3 decoders can suppress their first frame after seeking. Decode
      // a small preroll, then crop by output presentation timestamps below.
      extractor.seekTo(Math.max(0, startMs - 500) * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      decoder.configure(format, null, null, 0);
      decoder.start();
      int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
          channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
          encoding = AudioFormat.ENCODING_PCM_16BIT;
      ByteArrayOutputStream mono = new ByteArrayOutputStream();
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      boolean inputDone = false, outputDone = false;
      int idle = 0;
      while (!outputDone) {
        job.check();
        boolean progress = false;
        if (!inputDone) {
          int idx = decoder.dequeueInputBuffer(10000);
          if (idx >= 0) {
            ByteBuffer buf = decoder.getInputBuffer(idx);
            long pts = extractor.getSampleTime();
            // -1 means EOF; other negative values can be valid encoder priming.
            if (pts == -1 || pts >= (endMs + 500) * 1000) {
              decoder.queueInputBuffer(
                  idx, 0, 0, Math.max(0, pts), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
            } else {
              int n = extractor.readSampleData(buf, 0);
              decoder.queueInputBuffer(
                  idx, 0, Math.max(0, n), pts, n < 0 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
              if (n < 0) inputDone = true;
              else extractor.advance();
            }
            progress = true;
          }
        }
        int idx = decoder.dequeueOutputBuffer(info, 10000);
        if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat f = decoder.getOutputFormat();
          rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          encoding =
              f.containsKey(MediaFormat.KEY_PCM_ENCODING)
                  ? f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                  : AudioFormat.ENCODING_PCM_16BIT;
          progress = true;
        } else if (idx >= 0) {
          ByteBuffer buf = decoder.getOutputBuffer(idx);
          buf.order(ByteOrder.LITTLE_ENDIAN);
          buf.position(info.offset);
          buf.limit(info.offset + info.size);
          int width =
              encoding == AudioFormat.ENCODING_PCM_FLOAT
                  ? 4
                  : encoding == AudioFormat.ENCODING_PCM_16BIT
                      ? 2
                      : encoding == AudioFormat.ENCODING_PCM_8BIT ? 1 : 0;
          if (width == 0 || channels < 1 || rate < 8000 || rate > 192000)
            throw new IOException("此音频 PCM 格式暂不支持");
          int frames = info.size / (width * channels);
          for (int frame = 0; frame < frames; frame++) {
            double sum = 0;
            for (int channel = 0; channel < channels; channel++) {
              sum +=
                  width == 4
                      ? buf.getFloat() * 32767
                      : width == 2 ? buf.getShort() : ((buf.get() & 255) - 128) * 256;
            }
            long pts = info.presentationTimeUs + (long) frame * 1000000 / rate;
            if (pts >= startMs * 1000 && pts < endMs * 1000) {
              int sample = (int) Math.max(-32768, Math.min(32767, Math.round(sum / channels)));
              mono.write(sample & 255);
              mono.write((sample >> 8) & 255);
            }
          }
          if (mono.size() > 75000000) throw new IOException("解码片段过大");
          outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          decoder.releaseOutputBuffer(idx, false);
          progress = true;
        }
        idle = progress ? 0 : idle + 1;
        if (idle > 1500) throw new IOException("音频解码超时");
      }
      job.check();
      byte[] pcm = mono.toByteArray();
      int frames = pcm.length / 2;
      if (frames == 0) throw new IOException("所选范围没有音频");
      int count = (int) ((long) frames * 16000 / rate);
      byte[] resampled = new byte[count * 2];
      for (int i = 0; i < count; i++) {
        if ((i & 65535) == 0) job.check();
        double position = (double) i * rate / 16000;
        int a = (int) position, b = Math.min(a + 1, frames - 1);
        int x = (short) ((pcm[a * 2] & 255) | (pcm[a * 2 + 1] << 8)),
            y = (short) ((pcm[b * 2] & 255) | (pcm[b * 2 + 1] << 8));
        int sample = (int) Math.round(x + (y - x) * (position - a));
        resampled[i * 2] = (byte) sample;
        resampled[i * 2 + 1] = (byte) (sample >> 8);
      }
      file = File.createTempFile("speech-", ".wav", c.getCacheDir());
      try (OutputStream out = new FileOutputStream(file)) {
        out.write(header(resampled.length));
        out.write(resampled);
      }
      job.check();
      return file;
    } catch (Exception e) {
      if (file != null) file.delete();
      throw e;
    } finally {
      if (decoder != null) {
        try {
          decoder.stop();
        } catch (Exception ignored) {
        }
        decoder.release();
      }
      extractor.release();
    }
  }

  public static byte[] header(int length) throws Exception {
    ByteBuffer b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    b.put("RIFF".getBytes("US-ASCII"))
        .putInt(length + 36)
        .put("WAVEfmt ".getBytes("US-ASCII"))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(16000)
        .putInt(32000)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes("US-ASCII"))
        .putInt(length);
    return b.array();
  }
}
