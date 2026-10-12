package org.openenglish.reader;

import java.util.*;

/** Local calendar scheduling. Only exact normalized writing counts as correct. */
public final class ReviewPlanner {
  private ReviewPlanner() {}
  public static final long MAX_DUE = 253402300799000L;
  public static final class Plan {
    public final int interval;
    public final long due;
    public Plan(int interval, long due) { this.interval = interval; this.due = due; }
  }
  public static boolean validInterval(int days) {
    return days == 0 || days == 1 || days == 3 || days == 7 || days == 14 || days == 30;
  }
  public static int nextInterval(int days) {
    if (!validInterval(days)) throw new IllegalArgumentException("复习间隔");
    if (days == 0) return 1;
    if (days == 1) return 3;
    if (days == 3) return 7;
    if (days == 7) return 14;
    return 30;
  }
  public static long morningAfter(long now, int days, TimeZone zone) {
    if (now < 0 || now > MAX_DUE || days < 1 || days > 30 || zone == null)
      throw new IllegalArgumentException("复习日期");
    Calendar day = Calendar.getInstance(zone);
    day.setTimeInMillis(now);
    day.add(Calendar.DAY_OF_MONTH, days);
    day.set(Calendar.HOUR_OF_DAY, 9); day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
    long result = day.getTimeInMillis();
    if (result <= now || result > MAX_DUE) throw new IllegalArgumentException("复习日期超出范围");
    return result;
  }
  public static Plan afterWriting(long now, boolean correct, Plan previous, TimeZone zone) {
    if (now < 0 || now > MAX_DUE) throw new IllegalArgumentException("训练时间");
    if (previous != null && (!validInterval(previous.interval) || previous.due < 0 || previous.due > MAX_DUE))
      throw new IllegalArgumentException("复习计划");
    if (!correct) return new Plan(0, now);
    // An early correct attempt does not repeatedly advance an existing positive interval.
    if (previous != null && previous.interval > 0 && previous.due > now) return previous;
    int interval = nextInterval(previous == null ? 0 : previous.interval);
    return new Plan(interval, morningAfter(now, interval, zone));
  }
  public static boolean include(String filter, int score, boolean hasPlan, long due, long now) {
    if (!Arrays.asList("到期复习", "待巩固", "全部计划").contains(filter))
      throw new IllegalArgumentException("复习分类");
    boolean pending = score != 100;
    if (!pending && !hasPlan) return false;
    if (filter.equals("待巩固")) return pending;
    return filter.equals("全部计划") || due <= now;
  }
}
