package org.openenglish.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 本地 SRT/WebVTT 字幕解析器；不下载视频，不访问网络。 */
public final class JourneySubtitles {
  private static final Pattern TIME = Pattern.compile("(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})[.,](\\d{1,3})");
  private static final int MAX_CUES = 30000;

  /** 一个字幕区间（毫秒），不可变。 */
  public static final class Cue {
    public final int startMs, endMs;
    public final String text;
    Cue(int start, int end, String caption) {
      startMs = start;
      endMs = end;
      text = caption;
    }
  }

  private JourneySubtitles() {}

  /** 解析 SRT 和 WebVTT，可容忍 BOM、换行差异、序号和时间轴位置参数。 */
  public static List<Cue> parse(String source) {
    if (source == null || source.trim().isEmpty()) return Collections.emptyList();
    String normalized = source.replace("\ufeff", "").replace("\r\n", "\n").replace('\r', '\n');
    List<Cue> result = new ArrayList<>();
    for (String block : normalized.split("\n[ \t]*\n")) {
      String[] lines = block.split("\n");
      int timeline = -1;
      for (int i = 0; i < lines.length; i++) {
        if (lines[i].contains("-->")) { timeline = i; break; }
      }
      if (timeline < 0) continue;
      String[] pieces = lines[timeline].split("-->", 2);
      if (pieces.length != 2) continue;
      int start = time(pieces[0]), end = time(pieces[1]);
      if (start < 0 || end <= start) continue;
      StringBuilder caption = new StringBuilder();
      for (int i = timeline + 1; i < lines.length; i++) {
        String line = clean(lines[i]);
        if (line.isEmpty()) continue;
        if (caption.length() > 0) caption.append("\n");
        caption.append(line);
      }
      if (caption.length() == 0) continue;
      result.add(new Cue(start, end, caption.toString()));
      if (result.size() == MAX_CUES) break;
    }
    result.sort(Comparator.comparingInt(c -> c.startMs));
    return result;
  }

  private static int time(String raw) {
    Matcher match = TIME.matcher(raw);
    if (!match.find()) return -1;
    try {
      long hours = match.group(1) == null ? 0 : Long.parseLong(match.group(1));
      long minutes = Long.parseLong(match.group(2)), seconds = Long.parseLong(match.group(3));
      String fraction = match.group(4);
      if (minutes > 59 || seconds > 59) return -1;
      long ms = (hours * 3600 + minutes * 60 + seconds) * 1000;
      ms += Long.parseLong((fraction + "00").substring(0, 3));
      return ms > Integer.MAX_VALUE ? -1 : (int) ms;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String clean(String line) {
    return line.replaceAll("<[^>]*>", "")
        .replaceAll("\\{\\\\[^}]+\\}", "")
        .replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&nbsp;", " ").trim();
  }

  /** 二分查找当前显示的字幕，间隔期返回 -1。 */
  public static int at(List<Cue> cues, int positionMs) {
    int lo = 0, hi = cues.size() - 1, candidate = -1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      if (cues.get(mid).startMs <= positionMs) {
        candidate = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return candidate >= 0 && positionMs < cues.get(candidate).endMs ? candidate : -1;
  }

  /** 翻译字幕通常不是逐项对齐，因此只按时间轴选取。 */
  public static String translatedAt(List<Cue> cues, int positionMs) {
    int active = at(cues, positionMs);
    return active < 0 ? "" : cues.get(active).text;
  }
}
