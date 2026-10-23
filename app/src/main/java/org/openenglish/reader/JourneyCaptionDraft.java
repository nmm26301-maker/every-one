package org.openenglish.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Validates model timestamps before replacing a bounded video interval. */
public final class JourneyCaptionDraft {
  public static final class Segment {
    public final double start, end;
    public final String text;
    public Segment(double start, double end, String text) {
      this.start = start; this.end = end; this.text = text;
    }
  }
  public final int startMs, endMs;
  public final List<JourneySubtitles.Cue> cues;

  public JourneyCaptionDraft(List<Segment> segments, int start, int duration) throws IOException {
    if (start < 0 || duration < 100 || duration > 120000 || (long) start + duration > Integer.MAX_VALUE)
      throw new IOException("视频片段必须为 0.1–120 秒");
    if (segments == null || segments.isEmpty() || segments.size() > 1000)
      throw new IOException("服务没有返回有效分段时间轴");
    startMs = start; endMs = start + duration;
    List<JourneySubtitles.Cue> valid = new ArrayList<>();
    int previous = start;
    for (Segment segment : segments) {
      if (segment == null || !Double.isFinite(segment.start) || !Double.isFinite(segment.end)
          || segment.start < 0 || segment.start * 1000 >= duration || segment.end <= segment.start
          || segment.end * 1000 > duration + 50)
        throw new IOException("服务返回了无效或超出片段的时间轴，原字幕未修改");
      int from = (int) Math.round(segment.start * 1000) + start;
      int to = (int) Math.min(endMs, Math.round(segment.end * 1000) + start);
      if (from < previous || to <= from) throw new IOException("服务分段重叠或顺序异常，原字幕未修改");
      String text = segment.text == null ? "" : segment.text.replace('\r',' ').replace('\n',' ').trim();
      if (text.isEmpty() || text.length() > 4000) throw new IOException("服务返回了空白或过长的字幕");
      valid.add(new JourneySubtitles.Cue(from, to, text)); previous = to;
    }
    cues = java.util.Collections.unmodifiableList(valid);
  }

  public List<JourneySubtitles.Cue> merge(List<JourneySubtitles.Cue> existing) {
    List<JourneySubtitles.Cue> merged = new ArrayList<>();
    for (JourneySubtitles.Cue cue : existing) {
      if (cue.endMs <= startMs || cue.startMs >= endMs) merged.add(cue);
      else {
        if (cue.startMs < startMs) merged.add(new JourneySubtitles.Cue(cue.startMs,startMs,cue.text));
        if (cue.endMs > endMs) merged.add(new JourneySubtitles.Cue(endMs,cue.endMs,cue.text));
      }
    }
    merged.addAll(cues);
    merged.sort(Comparator.comparingInt(c -> c.startMs));
    return merged;
  }

  public static String srt(List<JourneySubtitles.Cue> cues) {
    StringBuilder text = new StringBuilder();
    for (int i=0;i<cues.size();i++) {
      JourneySubtitles.Cue cue=cues.get(i);
      text.append(i+1).append('\n').append(time(cue.startMs)).append(" --> ").append(time(cue.endMs))
          .append('\n').append(cue.text.replace('\r',' ').replace('\n',' ')).append("\n\n");
    }
    return text.toString();
  }

  public static List<JourneySubtitles.Cue> shift(List<JourneySubtitles.Cue> cues,int offset) {
    List<JourneySubtitles.Cue> shifted = new ArrayList<>();
    for (JourneySubtitles.Cue cue : cues) {
      int start = (int)Math.max(0,Math.min(Integer.MAX_VALUE,cue.startMs+(long)offset));
      int end = (int)Math.max(0,Math.min(Integer.MAX_VALUE,cue.endMs+(long)offset));
      if (end>start) shifted.add(new JourneySubtitles.Cue(start,end,cue.text));
    }
    return shifted;
  }

  public static String time(int ms) {
    return String.format(Locale.ROOT,"%02d:%02d:%02d,%03d",ms/3600000,ms/60000%60,ms/1000%60,ms%1000);
  }
}
