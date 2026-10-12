package org.openenglish.reader;

import java.util.*;
import java.util.regex.*;

public final class LearningCore {
  public static final Comparator<Cue> CUE_ORDER = (a, b) -> Long.compare(a.start, b.start);

  public static final class Cue {
    public final long start, end;
    public final String text;

    public Cue(long s, long e, String t) {
      start = s;
      end = e;
      text = t;
    }
  }

  public static List<Cue> parseSubtitles(String input) {
    if (input.length() > 2000000) throw new IllegalArgumentException("字幕文件过大");
    String normalized = input.replace("\uFEFF", "").replace("\r", "").trim();
    List<Cue> out = new ArrayList<>();
    Pattern time = Pattern.compile("(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})[,.](\\d{3})");
    for (String block : normalized.split("\\n\\s*\\n")) {
      String[] lines = block.split("\n");
      int at = -1;
      for (int i = 0; i < lines.length; i++)
        if (lines[i].contains("-->")) {
          at = i;
          break;
        }
      if (at < 0) continue;
      Matcher m = time.matcher(lines[at]);
      long[] t = new long[2];
      int n = 0;
      while (m.find() && n < 2)
        t[n++] =
            (m.group(1) == null ? 0 : Long.parseLong(m.group(1)) * 3600000)
                + Long.parseLong(m.group(2)) * 60000
                + Long.parseLong(m.group(3)) * 1000
                + Long.parseLong(m.group(4));
      StringBuilder b = new StringBuilder();
      for (int j = at + 1; j < lines.length; j++) {
        if (b.length() > 0) b.append('\n');
        b.append(lines[j].replaceAll("<[^>]*>", ""));
      }
      if (n == 2 && t[1] > t[0] && b.length() > 0) out.add(new Cue(t[0], t[1], b.toString()));
    }
    Collections.sort(out, CUE_ORDER);
    if (out.isEmpty()) throw new IllegalArgumentException("未找到有效的 SRT / VTT 时间轴");
    return out;
  }

  public static String[] tokens(String s) {
    String n =
        s.toLowerCase(Locale.US)
            .replace('’', '\'')
            .replaceAll("[^a-z0-9' ]", " ")
            .trim()
            .replaceAll("\\s+", " ");
    return n.isEmpty() ? new String[0] : n.split(" ");
  }

  public static int textMatch(String expected, String recognized) {
    String[] a = tokens(expected), b = tokens(recognized);
    if (a.length == 0) return 0;
    int[] previous = new int[b.length + 1], current = new int[b.length + 1];
    for (int j = 0; j <= b.length; j++) previous[j] = j;
    for (int i = 1; i <= a.length; i++) {
      current[0] = i;
      for (int j = 1; j <= b.length; j++)
        current[j] =
            Math.min(
                Math.min(previous[j] + 1, current[j - 1] + 1),
                previous[j - 1] + (a[i - 1].equals(b[j - 1]) ? 0 : 1));
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return Math.max(
        0,
        (int)
            Math.round(100.0 * (1.0 - (double) previous[b.length] / Math.max(a.length, b.length))));
  }

  public static int nextInterval(int previous, boolean remembered) {
    return remembered ? (previous <= 0 ? 1 : Math.min(90, previous * 2)) : 0;
  }

  public static long nextDue(long now, int interval) {
    return now + (interval == 0 ? 600000L : interval * 86400000L);
  }
}
