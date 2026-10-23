package org.openenglish.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单音节粤语的本机基频轮廓实验，不是声母、韵母、六调的客观正确率评分。
 * 明确拒绝过长、无声及多个分离发声段，避免给用户误导性的调型结论。
 */
public final class JourneyToneContour {
  private static final double LOG2 = Math.log(2.0);
  private static final int FRAME_MS = 45;
  private static final int HOP_MS = 20;

  private JourneyToneContour() {}

  /** pitchHz 每点间隔 20ms，0 表示该帧无法可靠估计基频。 */
  public static final class Result {
    public final boolean valid;
    public final int voicedFrames;
    public final double medianHz;
    public final double semitoneChange;
    public final String trend;
    public final String reason;
    public final double[] pitchHz;

    Result(boolean valid, int frames, double median, double change,
           String trend, String reason, double[] pitches) {
      this.valid = valid;
      voicedFrames = frames;
      medianHz = median;
      semitoneChange = change;
      this.trend = trend;
      this.reason = reason;
      pitchHz = pitches.clone();
    }

    public String feedback(int tone) {
      if (!valid) return reason + "\n本练习只适合安静环境中的单个粤语音节。";
      String change = String.format(java.util.Locale.US, "%+.1f", semitoneChange);
      String expected = expectedTrend(tone);
      String start = "实际音高走势：" + trend + "（首尾相差 " + change
          + " 半音），目标声调的常见走势：" + expected + "。";
      String advice = expected.equals(trend)
          ? "走势大致接近，仍需比较起始高低和真人语音。"
          : "走势不同，可先听原声并重新练一次。";
      return start + "\n" + advice
          + "\n注意：曲线仅表示本次测得的基频，不能判断声母、韵母、绝对调域，"
          + "且平调 1、3、6 等不能凭走势单独区分。";
    }
  }

  public static String expectedTrend(int tone) {
    if (tone == 2 || tone == 5) return "上扬";
    if (tone == 4) return "下降";
    return "近似平稳";
  }

  /** 分析单个 0.25–3 秒内的 16-bit PCM；约 20ms 取一个 F0 样本。 */
  public static Result analyze(short[] samples, int sampleRate) {
    if (samples == null || sampleRate < 8000 || sampleRate > 96000)
      return invalid("采样率不受支持", new double[0]);
    if (samples.length > sampleRate * 3L)
      return invalid("录音超过 3 秒。请只读一个音节，录 0.5–1 秒即可", new double[0]);
    int clipped = 0;
    for (short value : samples) if (Math.abs((int) value) >= 32276) clipped++;
    if (clipped > samples.length * .08)
      return invalid("录音爆音较多，请离麦克风稍远一些再录", new double[0]);
    // 与项目现有 YIN 纠音路径保持一致；高采样率先做区间平均，限制计算量。
    if (sampleRate > 16000) {
      samples = downsample(samples, sampleRate);
      sampleRate = 16000;
    }
    int frame = sampleRate * FRAME_MS / 1000;
    int hop = sampleRate * HOP_MS / 1000;
    if (samples.length < sampleRate / 4 || hop <= 0)
      return invalid("录音少于 0.25 秒，无法分析", new double[0]);
    int maxLag = sampleRate / 75;
    int minLag = sampleRate / 480;
    if (maxLag >= frame || minLag <= 1) return invalid("录音不适合基频检测", new double[0]);
    List<Double> track = new ArrayList<>();
    for (int start = 0; start + frame <= samples.length; start += hop) {
      double hz = estimate(samples, start, frame, sampleRate, minLag, maxLag);
      track.add(hz > 0 ? hz : 0.0);
    }
    double[] pitches = new double[track.size()];
    for (int i = 0; i < pitches.length; i++) pitches[i] = track.get(i);
    if (separateSyllables(pitches))
      return invalid("检测到多个分离的发声段。请只读一个音节", pitches);
    List<Double> voiced = new ArrayList<>();
    for (double hz : pitches) if (hz > 0) voiced.add(hz);
    if (voiced.size() < 12)
      return invalid("有效有声部分不足，无法稳定测量音高", pitches);
    List<Double> smooth = new ArrayList<>();
    // 局部中值仅对连续有效帧进行平滑，不跨越静音来拼接两个音节。
    for (int i = 0; i < pitches.length; i++) {
      if (pitches[i] <= 0) continue;
      List<Double> local = new ArrayList<>();
      for (int j = Math.max(0, i - 1); j <= Math.min(pitches.length - 1, i + 1); j++)
        if (pitches[j] > 0) local.add(pitches[j]);
      smooth.add(median(local));
    }
    int portion = Math.max(3, smooth.size() / 3);
    double first = median(smooth.subList(0, portion));
    double last = median(smooth.subList(smooth.size() - portion, smooth.size()));
    double delta = 12.0 * Math.log(last / first) / LOG2;
    String trend = delta > 1.3 ? "上扬" : delta < -1.3 ? "下降" : "近似平稳";
    return new Result(true, smooth.size(), median(smooth), delta, trend, "", pitches);
  }

  /** 允许自然的短暂无声帧；两组至少 100ms 有声间隔 >=160ms 才拒绝。 */
  private static boolean separateSyllables(double[] pitch) {
    int firstGroup = 0, secondGroup = 0, gap = 0;
    boolean split = false;
    for (double hz : pitch) {
      if (hz > 0) {
        if (!split && gap >= 8 && firstGroup >= 5) split = true;
        if (split) secondGroup++;
        else firstGroup++;
        gap = 0;
      } else if (firstGroup >= 5) {
        gap++;
      }
    }
    return secondGroup >= 5;
  }

  private static Result invalid(String reason, double[] track) {
    return new Result(false, 0, 0, 0, "无法判断", reason, track);
  }

  private static double median(List<Double> values) {
    if (values.isEmpty()) return 0;
    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    return sorted.get(sorted.size() / 2);
  }

  /** YIN 近似归一化差分：仅保留低差分率的浊音帧。 */
  private static double estimate(short[] audio, int start, int window,
                                 int rate, int minLag, int maxLag) {
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
    for (int lag = 1; lag <= maxLag; lag++) {
      cumulative += diff[lag];
      diff[lag] = cumulative <= 0 ? 1 : diff[lag] * lag / cumulative;
    }
    // 首次越过阈值后寻找同一谷底，不能把阈值交点直接当作周期。
    int best = -1;
    for (int lag = minLag; lag < maxLag; lag++) {
      if (diff[lag] >= .18) continue;
      while (lag + 1 <= maxLag && diff[lag + 1] < diff[lag]) lag++;
      best = lag;
      break;
    }
    if (best < 0) return -1;
    double period = best;
    if (best > 1 && best < maxLag) {
      double left = diff[best - 1], middle = diff[best], right = diff[best + 1];
      double denominator = left - 2 * middle + right;
      if (Math.abs(denominator) > 1e-12)
        period += Math.max(-.5, Math.min(.5, .5 * (left - right) / denominator));
    }
    double hz = rate / period;
    // 周期插值在检测边界允许 1% 的离散采样误差。
    return hz >= 75 * .99 && hz <= 480 * 1.01 ? hz : -1;
  }

  /** 以输入采样区间的加权平均降至 16kHz，保留录音时长和实际采样率。 */
  private static short[] downsample(short[] input, int rate) {
    short[] output = new short[(int) (input.length * 16000L / rate)];
    double scale = rate / 16000.0;
    for (int i = 0; i < output.length; i++) {
      double start = i * scale, end = Math.min(input.length, (i + 1) * scale);
      double sum = 0;
      for (int j = (int) start; j < end; j++) {
        double weight = Math.min(j + 1, end) - Math.max(j, start);
        sum += input[j] * weight;
      }
      output[i] = (short) Math.round(sum / (end - start));
    }
    return output;
  }
}
