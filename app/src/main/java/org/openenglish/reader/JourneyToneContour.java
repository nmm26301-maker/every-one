package org.openenglish.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单音节粤语练习的离线基频轮廓实验。
 * 只检测音高随时间的趋势，不判断声母、韵母、绝对音高或完整声调正确率。
 */
public final class JourneyToneContour {
  private static final double LOG2 = Math.log(2.0);
  private JourneyToneContour() {}

  /** 基频轮廓分析结果。voicedFrames 不满足要求时有效值为 false。 */
  public static final class Result {
    public final boolean valid;
    public final int voicedFrames;
    public final double medianHz;
    public final double semitoneChange;
    public final String trend;
    Result(boolean ok, int frames, double median, double change, String name) {
      valid = ok; voicedFrames = frames; medianHz = median;
      semitoneChange = change; trend = name;
    }
    public String feedback(int tone) {
      String expected = expectedTrend(tone);
      if (!valid) return "音频里没有足够稳定的有声片段，无法可靠估计音高。请在安静环境只读一个音节，并避免拖长。";
      String change = String.format(java.util.Locale.US, "%+.1f", semitoneChange);
      String info = "测得单音节音高走势：" + trend + "（前后约 " + change + " 半音），目标：" + expected + "。";
      boolean match = expected.equals(trend);
      if (!match) info += "建议先模仿目标调型，再重新录一遍。";
      else info += "走势大致接近目标，可继续和真人示范比较高低起点。";
      return info + "\n注意：仅测得音高走势，无法判断声母、韵母、绝对调域，也不代表发音已正确。";
    }
  }

  public static String expectedTrend(int tone) {
    if (tone == 2 || tone == 5) return "上扬";
    if (tone == 4) return "下降";
    return "近似平稳";
  }

  /** 从 16-bit mono PCM 中推断 F0，预处理与判定均在本机 CPU 内完成。 */
  public static Result analyze(short[] samples, int sampleRate) {
    if (samples == null || sampleRate < 8000 || sampleRate > 96000) return invalid();
    int length = sampleRate * 45 / 1000;
    int stride = sampleRate * 20 / 1000;
    if (samples.length < length * 3 || stride <= 0) return invalid();
    int maxLag = sampleRate / 75;
    int minLag = sampleRate / 480;
    if (maxLag >= length || minLag <= 1) return invalid();
    List<Double> pitches = new ArrayList<>();
    for (int start = 0; start + length <= samples.length; start += stride) {
      double pitch = estimate(samples, start, length, sampleRate, minLag, maxLag);
      if (pitch > 0) pitches.add(pitch);
    }
    if (pitches.size() < 7) return invalid();
    // 降低单帧跳倍频的影响，逐个取邻域中值。
    List<Double> smooth = new ArrayList<>();
    for (int i = 0; i < pitches.size(); i++) {
      List<Double> small = new ArrayList<>();
      for (int j = Math.max(0, i - 1); j <= Math.min(i + 1, pitches.size() - 1); j++)
        small.add(pitches.get(j));
      Collections.sort(small);
      smooth.add(small.get(small.size() / 2));
    }
    int portion = Math.max(2, smooth.size() / 3);
    double first = median(smooth.subList(0, portion));
    double last = median(smooth.subList(smooth.size() - portion, smooth.size()));
    double delta = 12.0 * Math.log(last / first) / LOG2;
    String trend = delta > 1.3 ? "上扬" : delta < -1.3 ? "下降" : "近似平稳";
    return new Result(true, smooth.size(), median(smooth), delta, trend);
  }

  private static Result invalid() {
    return new Result(false, 0, 0, 0, "无法判断");
  }

  private static double median(List<Double> vals) {
    List<Double> sorted = new ArrayList<>(vals);
    Collections.sort(sorted);
    return sorted.get(sorted.size() / 2);
  }

  /**
   * YIN-style normalized difference method. A low normalized difference
   * near one period identifies voiced frames; otherwise discard noisy frames.
   */
  private static double estimate(short[] audio, int start, int window, int rate,
                                 int minLag, int maxLag) {
    double sum = 0;
    for (int i = 0; i < window; i++) sum += audio[start + i];
    double dc = sum / window;
    double energy = 0;
    for (int i = 0; i < window; i++) {
      double x = (audio[start + i] - dc) / 32768.0;
      energy += x * x;
    }
    if (Math.sqrt(energy / window) < 0.018) return -1;
    double[] diff = new double[maxLag + 1];
    int width = window - maxLag;
    for (int lag = 1; lag <= maxLag; lag++) {
      double squared = 0;
      for (int i = 0; i < width; i++) {
        double d = (audio[start + i] - dc) - (audio[start + i + lag] - dc);
        squared += d * d;
      }
      diff[lag] = squared;
    }
    double cumulative = 0;
    int best = -1;
    double bestScore = 0.18;
    for (int lag = 1; lag <= maxLag; lag++) {
      cumulative += diff[lag];
      if (lag < minLag || cumulative <= 0) continue;
      double normalized = diff[lag] * lag / cumulative;
      if (normalized < bestScore) {
        best = lag;
        bestScore = normalized;
        if (normalized < 0.09) break;
      }
    }
    if (best < 0) return -1;
    double f0 = (double) rate / best;
    return f0 >= 75 && f0 <= 480 ? f0 : -1;
  }
}
