package org.openenglish.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 对齐参考音节与用户跟读的 F0 曲线。属于客观波形对照，不是声学发音正确率。
 * 纵向分别减去各自的中位 F0，避免声线不同造成虚假的判错。
 */
public final class JourneyToneCompare {
  private static final int POINTS = 24;
  private JourneyToneCompare() {}

  public static final class Result {
    public final boolean comparable;
    public final String feedback;
    public final double rmsSemitones;
    public final JourneyToneContour.Result reference;
    public final JourneyToneContour.Result attempt;
    Result(boolean ok, String info, double rms,
           JourneyToneContour.Result ref, JourneyToneContour.Result user) {
      comparable = ok;
      feedback = info;
      rmsSemitones = rms;
      reference = ref;
      attempt = user;
    }
  }

  public static Result compare(short[] reference, int referenceRate,
                               short[] attempt, int attemptRate) {
    JourneyToneContour.Result r = JourneyToneContour.analyze(reference, referenceRate);
    JourneyToneContour.Result a = JourneyToneContour.analyze(attempt, attemptRate);
    if (!r.valid || !a.valid) {
      String reason = !r.valid ? "参考录音无效：" + r.reason : "本次跟读无效：" + a.reason;
      return new Result(false, reason + "。请保证两段都是清晰的同一单音节录音。",
          0, r, a);
    }
    double[] rTrack = resample(r.pitchHz, r.medianHz);
    double[] aTrack = resample(a.pitchHz, a.medianHz);
    if (rTrack.length != POINTS || aTrack.length != POINTS)
      return new Result(false, "可供对齐的有声片段不足，请重新录制。", 0, r, a);
    double square = 0;
    for (int i = 0; i < POINTS; i++) {
      double diff = rTrack[i] - aTrack[i];
      square += diff * diff;
    }
    double rms = Math.sqrt(square / POINTS);
    String comparison = r.trend.equals(a.trend)
        ? "总体走势一致，但不代表发音正确。"
        : "总体走势不同，建议听参考发音并重录。";
    String details = String.format(Locale.US,
        "同音节相对音高轮廓差异约 %.1f 半音（均方根，仅用于这两段音频对比）。", rms);
    String next = rms < 1.5
        ? "曲线形状较接近。"
        : rms < 3.0 ? "曲线存在可见差异，可以再放慢并模仿参考音的起伏。"
                    : "曲线差异较大，先检查是否读了同一个音节、手机周围是否有噪音。";
    String disclaimer = "\n两段音高都以各自的中位值归一化，因此不比较绝对音高；"
        + "模型不检测声母、韵母、嗓音品质或所有六个粤语声调，结果不能用作准确率评分。";
    return new Result(true,
        "参考：" + r.trend + "；你的跟读：" + a.trend + "。\n"
            + comparison + " " + details + next + disclaimer, rms, r, a);
  }

  /** 把有效有声帧按音节相对时间插值到 24 个点，单位为相对半音。 */
  private static double[] resample(double[] pitches, double medianHz) {
    if (pitches == null || medianHz <= 0) return new double[0];
    List<Double> clean = new ArrayList<>();
    for (double f0 : pitches) {
      if (f0 > 0 && Double.isFinite(f0)) {
        clean.add(12 * Math.log(f0 / medianHz) / Math.log(2));
      }
    }
    if (clean.size() < 12) return new double[0];
    double[] values = new double[POINTS];
    for (int i = 0; i < POINTS; i++) {
      double at = (double) i * (clean.size() - 1) / (POINTS - 1);
      int low = (int) at;
      int high = Math.min(clean.size() - 1, low + 1);
      double fraction = at - low;
      values[i] = clean.get(low) * (1 - fraction) + clean.get(high) * fraction;
    }
    return values;
  }
}
