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
    yes(up.feedback(2).contains("不能判断声母"), "not a full pronunciation score");
    yes(!JourneyToneContour.analyze(new short[rate], rate).valid, "silence invalid");
    yes(!JourneyToneContour.analyze(new short[12], rate).valid, "short invalid");
    yes(!JourneyToneContour.analyze(generated(rate, 170, 170), 7000).valid, "bad sample rate");
    short[] extended = new short[rate * 4];
    yes(!JourneyToneContour.analyze(extended, rate).valid, "long file invalid");
    yes(JourneyToneContour.analyze(extended, rate).reason.contains("超过 3 秒"),
        "long file guidance");
    short[] separated = new short[rate * 2];
    short[] sample = generated(rate, 170, 170);
    System.arraycopy(sample, 0, separated, 0, rate / 2);
    System.arraycopy(sample, 0, separated, rate, rate / 2);
    JourneyToneContour.Result two = JourneyToneContour.analyze(separated, rate);
    yes(!two.valid && two.reason.contains("多个分离"), "two syllables rejected");
    yes(up.pitchHz.length > 20 && up.pitchHz.length < 60, "fixed-time Hz curve");
    yes(up.medianHz > 100 && up.medianHz < 260, "Hz curve scale");
    yes(up.feedback(2).contains("平调 1、3、6"), "flat tone ambiguity is explicit");
    for (int sampleRate : new int[]{8000, 16000, 44100, 48000, 96000}) {
      for (double hz : new double[]{75, 80, 125, 220, 420, 480}) {
        JourneyToneContour.Result measured = JourneyToneContour.analyze(
            generated(sampleRate, hz, hz), sampleRate);
        double error = 12 * Math.abs(Math.log(measured.medianHz / hz) / Math.log(2));
        yes(measured.valid && error < .25, "pitch precision at " + sampleRate + "/" + hz
            + ": " + measured.medianHz + " Hz, error " + error + " semitones");
      }
    }
    short[] harmonic = generated(rate, 145, 145);
    for (int i = 0; i < harmonic.length; i++)
      harmonic[i] = (short) (harmonic[i] + 3000 * Math.sin(2 * Math.PI * 290 * i / rate) + 4000);
    JourneyToneContour.Result voiced = JourneyToneContour.analyze(harmonic, rate);
    yes(voiced.valid && Math.abs(voiced.medianHz - 145) < 2, "harmonics and DC offset retain fundamental");
    short[] clipped = generated(rate, 170, 170);
    for (int i = 0; i < clipped.length; i++)
      clipped[i] = (short) Math.max(-32768, Math.min(32767, clipped[i] * 5));
    JourneyToneContour.Result overloaded = JourneyToneContour.analyze(clipped, rate);
    yes(!overloaded.valid && overloaded.reason.contains("爆音"), "clipped recording needs retake");
    java.util.Random random = new java.util.Random(7);
    short[] noise = new short[rate];
    for (int i = 0; i < noise.length; i++) noise[i] = (short) (random.nextInt(20000) - 10000);
    yes(!JourneyToneContour.analyze(noise, rate).valid, "noise cannot become pronunciation advice");
    System.out.println("JourneyToneContour: OK");
  }
}
