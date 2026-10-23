package org.openenglish.reader;

import java.util.List;

/** 不依赖 Android SDK 的 SRT/WebVTT 主机回归检查。 */
public final class JourneySubtitlesTest {
  private static void check(boolean pass, String label) {
    if (!pass) throw new AssertionError(label);
  }

  public static void main(String[] args) {
    String srt = "\ufeff1\r\n00:00:00,100 --> 00:00:01,200\r\nこんにちは\r\n\r\n"
        + "2\r\n00:00:02,000 --> 00:00:03,200\r\nそうです。\r\n";
    List<JourneySubtitles.Cue> a = JourneySubtitles.parse(srt);
    check(a.size() == 2, "SRT 数量");
    check(a.get(0).startMs == 100 && a.get(0).endMs == 1200, "SRT 时间");
    check(JourneySubtitles.at(a, 500) == 0, "检索第一句");
    check(JourneySubtitles.at(a, 1500) == -1, "时间间隔无字幕");
    check(JourneySubtitles.at(a, 2400) == 1, "检索第二句");

    String vtt = "WEBVTT\n\nline-1\n00:00:01.000 --> 00:00:02.500 align:start\n"
        + "<b>じゃん</b> &amp; そう\n\n00:03.000 --> 00:04.000\nまたね";
    List<JourneySubtitles.Cue> b = JourneySubtitles.parse(vtt);
    check(b.size() == 2, "VTT 数量");
    check(b.get(0).text.equals("じゃん & そう"), "标签与实体");
    check(b.get(1).startMs == 3000, "短 VTT 时间");
    check(JourneySubtitles.at(b, 4000) == -1, "结束边界");
    check(JourneySubtitles.parse("WEBVTT\n\nNOTE demo").isEmpty(), "无效内容");
    check(JourneySubtitles.parse("00:00:08,000 --> 00:00:01,000\nBad").isEmpty(), "逆序时间");
    System.out.println("JourneySubtitles: OK");
  }
}
