import org.openenglish.reader.TrainingCore;
import java.util.*;

public class TrainingCoreTest {
  static int checks;
  static void check(boolean yes, String name) { if (!yes) throw new AssertionError(name); checks++; }
  static long moment(String date, String zone) throws Exception {
    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    f.setTimeZone(TimeZone.getTimeZone(zone)); return f.parse(date).getTime();
  }
  public static void main(String[] args) throws Exception {
    check(TrainingCore.feedback("Take your time!", "take YOUR time.").contains("单词一致"), "case and punctuation");
    check(TrainingCore.feedback("Take your time", "Take time").contains("漏写第 2 词：“your”"), "missing word");
    check(TrainingCore.feedback("Take your time", "Take my time").contains("应写 “your”，你写了 “my”"), "replacement");
    check(TrainingCore.feedback("Take your time", "Please take your time").contains("多写：“please”"), "extra word");
    check(TrainingCore.feedback("a a b", "a b").contains("漏写"), "repeated word alignment");
    check(TrainingCore.feedback("Don't hurry", "don’t hurry").contains("单词一致"), "curly apostrophe");
    check(TrainingCore.feedback("hello", "").contains("漏写"), "empty input");
    check(TrainingCore.feedback("", "hello").contains("没有可比较"), "empty reference");
    StringBuilder longText = new StringBuilder(); for (int i=0;i<201;i++) longText.append("word ");
    check(TrainingCore.feedback(longText.toString(), "word").contains("分句"), "bounded alignment");
    check(TrainingCore.searchPattern(" 100%_! ").equals("%100!%!_!!%"), "literal LIKE escaping");
    check(TrainingCore.searchPattern(null).equals("%%"), "empty query");
    check(TrainingCore.searchPattern("a\\b").equals("%a\\b%"), "literal backslash");
    check(TrainingCore.csvCell("hello,\"world\"\n").equals("\"hello,\"\"world\"\"\n\""), "CSV quote comma and newline");
    check(TrainingCore.csvCell(" =1+1").startsWith("\"'"), "CSV formula with whitespace");
    for (String s : new String[] {"+SUM(1)","-2+3","@CMD", "\t=1"}) check(TrainingCore.csvCell(s).startsWith("\"'"), "CSV formula " + s);
    check(TrainingCore.csvCell(null).equals("\"\""), "CSV null");
    List<TrainingCore.DayWindow> sh = TrainingCore.recentDays(moment("2026-10-09 03:00","Asia/Shanghai"),TimeZone.getTimeZone("Asia/Shanghai"),7);
    check(sh.size()==7, "seven windows");
    check(sh.get(6).start==moment("2026-10-09 00:00","Asia/Shanghai"), "local midnight");
    check(sh.get(6).end==moment("2026-10-10 00:00","Asia/Shanghai"), "half-open next midnight");
    check(sh.get(0).start==moment("2026-10-03 00:00","Asia/Shanghai"), "oldest first");
    for(int i=0;i<6;i++) check(sh.get(i).end==sh.get(i+1).start, "no date gap");
    TrainingCore.DayWindow spring=TrainingCore.recentDays(moment("2026-03-08 12:00","America/New_York"),TimeZone.getTimeZone("America/New_York"),1).get(0);
    check(spring.end-spring.start==23L*3600000, "DST spring 23 hours");
    TrainingCore.DayWindow autumn=TrainingCore.recentDays(moment("2026-11-01 12:00","America/New_York"),TimeZone.getTimeZone("America/New_York"),1).get(0);
    check(autumn.end-autumn.start==25L*3600000, "DST autumn 25 hours");
    TrainingCore.DayWindow year=TrainingCore.recentDays(moment("2027-01-01 01:00","Asia/Shanghai"),TimeZone.getTimeZone("Asia/Shanghai"),2).get(0);
    check(year.start==moment("2026-12-31 00:00","Asia/Shanghai"), "year boundary");
    boolean rejected=false;try{TrainingCore.recentDays(0,TimeZone.getDefault(),0);}catch(IllegalArgumentException e){rejected=true;}
    check(rejected,"invalid window count");
    System.out.println("PASS: " + checks + " training feedback, CSV, search and local-date checks");
  }
}
