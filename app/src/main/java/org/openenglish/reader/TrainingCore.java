package org.openenglish.reader;

import java.util.*;

/** Offline text feedback and local calendar boundaries; never grades pronunciation. */
public final class TrainingCore {
  private TrainingCore() {}

  public static final class DayWindow {
    public final long start, end;
    public DayWindow(long s, long e) { start = s; end = e; }
  }

  public static List<DayWindow> recentDays(long now, TimeZone zone, int count) {
    if (count < 1 || count > 31) throw new IllegalArgumentException("day count");
    Calendar day = Calendar.getInstance(zone);
    day.setTimeInMillis(now);
    day.set(Calendar.HOUR_OF_DAY, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MILLISECOND, 0);
    day.add(Calendar.DAY_OF_MONTH, 1 - count);
    List<DayWindow> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      long start = day.getTimeInMillis();
      day.add(Calendar.DAY_OF_MONTH, 1);
      result.add(new DayWindow(start, day.getTimeInMillis()));
    }
    return result;
  }

  public static String searchPattern(String query) {
    String q = query == null ? "" : query.trim();
    if (q.length() > 200) q = q.substring(0, 200);
    return "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  public static String csvCell(String value) {
    String s = value == null ? "" : value;
    String trimmed = s.trim();
    if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  /** A negative score means the comparison is too large or has no English reference. */
  public static int reviewMatch(String reference, String written) {
    String[] a = LearningCore.tokens(reference), b = LearningCore.tokens(written);
    if (a.length == 0) return -1;
    if (Arrays.equals(a, b)) return 100;
    if ((long)a.length * b.length > 40000) return -1;
    return Math.min(99, LearningCore.textMatch(reference, written));
  }

  public static int resolveCue(List<LearningCore.Cue> cues, int anchor, String reference) {
    if (anchor >= 0 && anchor < cues.size() && cues.get(anchor).text.equals(reference)) return anchor;
    int found = -1;
    for (int i = 0; i < cues.size(); i++) if (cues.get(i).text.equals(reference)) {
      if (found >= 0) return -1;
      found = i;
    }
    return found;
  }

  public static String feedback(String expected, String written) {
    String[] a = LearningCore.tokens(expected), b = LearningCore.tokens(written);
    if (a.length == 0) return "原句没有可比较的英文单词。";
    if (a.length > 200 || b.length > 200) return "句子较长，请分句练习或人工核对；本次不逐词展开差异。";
    int[][] d = new int[a.length + 1][b.length + 1];
    for (int i = 0; i <= a.length; i++) d[i][0] = i;
    for (int j = 0; j <= b.length; j++) d[0][j] = j;
    for (int i = 1; i <= a.length; i++)
      for (int j = 1; j <= b.length; j++)
        d[i][j] = Math.min(Math.min(d[i-1][j] + 1, d[i][j-1] + 1),
            d[i-1][j-1] + (a[i-1].equals(b[j-1]) ? 0 : 1));
    List<String> edits = new ArrayList<>();
    int i = a.length, j = b.length;
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && a[i-1].equals(b[j-1]) && d[i][j] == d[i-1][j-1]) { i--; j--; }
      else if (i > 0 && j > 0 && d[i][j] == d[i-1][j-1] + 1) {
        edits.add("第 " + i + " 词应写 “" + a[i-1] + "”，你写了 “" + b[j-1] + "”"); i--; j--;
      } else if (i > 0 && d[i][j] == d[i-1][j] + 1) {
        edits.add("漏写第 " + i + " 词：“" + a[i-1] + "”"); i--;
      } else {
        edits.add("多写：“" + b[j-1] + "”"); j--;
      }
    }
    if (edits.isEmpty()) return "单词一致，已忽略大小写与标点。";
    Collections.reverse(edits);
    StringBuilder out = new StringBuilder();
    for (int n = 0; n < Math.min(30, edits.size()); n++) {
      if (n > 0) out.append('\n');
      out.append(edits.get(n));
    }
    if (edits.size() > 30) out.append("\n还有 ").append(edits.size() - 30).append(" 处差异，请分句练习。");
    return out.toString();
  }
}
