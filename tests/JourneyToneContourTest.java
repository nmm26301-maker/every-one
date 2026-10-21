package org.openenglish.reader;

public final class JourneyToneContourTest {
  private static void yes(boolean b, String label) {
    if (!b) throw new AssertionError(label);
  }
  private static short[] generated(int rate, double startHz, double endHz) {
    short[] pcm = new short[rate];
    double phase = 0;
    for (int i = 0; i < pcm.length; i++) {
      double t = i / (double) pcm.length;
      double freq = startHz + (endHz - startHz) * t;
      phase += 2 * Math.PI * freq / rate;
      double fade = Math.min(1.0, Math.min(t, 1 - t) * 15);
      pcm[i] = (short) Math.round(Math.sin(phase) * 14000 * fade);
    }
    return pcm;
  }
  public static void main(String[] args) {
    int rate = 16000;
    JourneyToneContour.Result level = JourneyToneContour.analyze(generated(rate, 170, 170), rate);
    JourneyToneContour.Result up = JourneyToneContour.analyze(generated(rate, 135, 205), rate);
    JourneyToneContour.Result down = JourneyToneContour.analyze(generated(rate, 205, 135), rate);
    yes(level.valid && level.trend.equals("近似平稳"), "flat syllable");
    yes(up.valid && up.trend.equals("上扬"), "rising syllable");
    yes(down.valid && down.trend.equals("下降"), "falling syllable");
    yes(JourneyToneContour.expectedTrend(5).equals("上扬"), "tone 5");
    yes(JourneyToneContour.expectedTrend(6).equals("近似平稳"), "tone 6");
    yes(up.feedback(2).contains("无法判断声母"), "not a full pronunciation score");
    yes(!JourneyToneContour.analyze(new short[rate], rate).valid, "silence invalid");
    yes(!JourneyToneContour.analyze(new short[12], rate).valid, "short invalid");
    System.out.println("JourneyToneContour: OK");
  }
}
