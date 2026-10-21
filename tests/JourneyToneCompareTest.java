package org.openenglish.reader;

/** 对照相对音高趋势，测试不同说话者调域及不匹配调型。 */
public final class JourneyToneCompareTest {
  private static void yes(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static short[] sine(int sampleRate, int millis, double start, double end) {
    short[] samples = new short[sampleRate * millis / 1000];
    double angle = 0;
    for (int i = 0; i < samples.length; i++) {
      double progress = (double) i / samples.length;
      angle += 2 * Math.PI * (start + (end - start) * progress) / sampleRate;
      double fade = Math.min(1, Math.min(progress, 1 - progress) * 14);
      samples[i] = (short) (Math.sin(angle) * 14000 * fade);
    }
    return samples;
  }

  public static void main(String[] args) {
    int rate = 16000;
    JourneyToneCompare.Result close = JourneyToneCompare.compare(
        sine(rate, 850, 125, 205), rate, sine(rate, 650, 155, 252), rate);
    yes(close.comparable, "reference comparison works");
    yes(close.reference.trend.equals("上扬") && close.attempt.trend.equals("上扬"),
        "both voiced rising");
    yes(close.rmsSemitones < 2, "normalized pitches compare across voices");
    JourneyToneCompare.Result opposite = JourneyToneCompare.compare(
        sine(rate, 850, 125, 205), rate, sine(rate, 850, 205, 125), rate);
    yes(opposite.comparable && opposite.rmsSemitones > close.rmsSemitones + 1.0,
        "wrong pitch shape detected");
    yes(opposite.feedback.contains("不检测声母、韵母"), "no false grading");
    JourneyToneCompare.Result invalid = JourneyToneCompare.compare(
        new short[rate], rate, sine(rate, 700, 180, 180), rate);
    yes(!invalid.comparable && invalid.feedback.contains("参考录音无效"),
        "silent reference rejected");
    JourneyToneCompare.Result overlong = JourneyToneCompare.compare(
        sine(rate, 4000, 140, 200), rate, sine(rate, 700, 140, 200), rate);
    yes(!overlong.comparable && overlong.feedback.contains("超过 3 秒"),
        "overlong reference rejected");
    System.out.println("JourneyToneCompare: OK");
  }
}
