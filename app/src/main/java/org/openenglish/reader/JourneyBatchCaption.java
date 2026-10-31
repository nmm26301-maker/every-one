package org.openenglish.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Planning and merging helpers for whole-video caption generation.
 * Network and media extraction stay in the Activity; this class keeps the
 * boundaries deterministic and easy to validate on the host.
 */
public final class JourneyBatchCaption {
  public static final int MIN_CHUNK_MS = 30_000;
  public static final int MAX_CHUNK_MS = 120_000;
  public static final int MAX_VIDEO_MS = 12 * 60 * 60 * 1000;
  private JourneyBatchCaption() {}

  public static final class Chunk {
    public final int startMs;
    public final int endMs;
    Chunk(int start, int end) { startMs = start; endMs = end; }
    public int durationMs() { return endMs - startMs; }
  }

  /** Split a video into bounded, contiguous extraction requests. */
  public static List<Chunk> plan(int durationMs, int chunkMs) throws IOException {
    if (durationMs < 1000 || durationMs > MAX_VIDEO_MS)
      throw new IOException("视频时长必须在 1 秒至 12 小时之间");
    if (chunkMs < MIN_CHUNK_MS || chunkMs > MAX_CHUNK_MS)
      throw new IOException("批量字幕片段应为 30–120 秒");
    List<Chunk> chunks = new ArrayList<>();
    for (int start = 0; start < durationMs;) {
      int end = (int) Math.min((long) durationMs, (long) start + chunkMs);
      chunks.add(new Chunk(start, end));
      if (end <= start) throw new IOException("无法划分视频时间轴");
      start = end;
    }
    return Collections.unmodifiableList(chunks);
  }

  /**
   * Merge generated cues into an existing subtitle track. Generated cues win
   * only inside their requested ranges; cues outside remain untouched.
   */
  public static List<JourneySubtitles.Cue> merge(
      List<JourneySubtitles.Cue> existing, List<JourneySubtitles.Cue> generated) {
    List<JourneySubtitles.Cue> result = new ArrayList<>();
    if (existing != null) result.addAll(existing);
    if (generated == null || generated.isEmpty()) return sorted(result);
    for (JourneySubtitles.Cue fresh : generated) {
      List<JourneySubtitles.Cue> next = new ArrayList<>();
      for (JourneySubtitles.Cue old : result) {
        if (old.endMs <= fresh.startMs || old.startMs >= fresh.endMs) {
          next.add(old);
        } else {
          if (old.startMs < fresh.startMs)
            next.add(new JourneySubtitles.Cue(old.startMs, fresh.startMs, old.text));
          if (old.endMs > fresh.endMs)
            next.add(new JourneySubtitles.Cue(fresh.endMs, old.endMs, old.text));
        }
      }
      next.add(fresh);
      result = next;
    }
    return sorted(result);
  }

  /** Remove exact duplicates and trim accidental overlap at chunk boundaries. */
  public static List<JourneySubtitles.Cue> normalize(List<JourneySubtitles.Cue> cues)
      throws IOException {
    if (cues == null) return Collections.emptyList();
    for (JourneySubtitles.Cue cue : cues) {
      if (cue == null) throw new IOException("生成字幕包含空分段");
    }
    List<JourneySubtitles.Cue> sorted = sorted(cues);
    List<JourneySubtitles.Cue> out = new ArrayList<>();
    for (JourneySubtitles.Cue cue : sorted) {
      if (cue == null || cue.startMs < 0 || cue.endMs <= cue.startMs
          || cue.text == null || cue.text.trim().isEmpty())
        throw new IOException("生成字幕包含无效分段");
      if (!out.isEmpty()) {
        JourneySubtitles.Cue previous = out.get(out.size() - 1);
        if (cue.startMs < previous.endMs) {
          if (cue.text.equals(previous.text)) continue;
          int trimmedStart = previous.endMs;
          if (trimmedStart >= cue.endMs) continue;
          cue = new JourneySubtitles.Cue(trimmedStart, cue.endMs, cue.text);
        }
      }
      out.add(cue);
    }
    return Collections.unmodifiableList(out);
  }

  private static List<JourneySubtitles.Cue> sorted(List<JourneySubtitles.Cue> cues) {
    List<JourneySubtitles.Cue> copy = new ArrayList<>();
    if (cues != null) copy.addAll(cues);
    copy.removeAll(Collections.singleton(null));
    copy.sort(Comparator.comparingInt(c -> c.startMs));
    return copy;
  }
}
