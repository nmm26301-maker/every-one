package org.openenglish.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * 粤语单音节录音 F0 曲线（时间-相对半音）可视化。
 * 空白区表示无可靠 F0，不把未测音段连接成虚假的音高曲线。
 */
public final class JourneyTonePlotView extends View {
  private static final int PURPLE = 0xff5948cf;
  private static final int TEAL = 0xff148b83;
  private static final int GRID = 0xffdce0e8;
  private static final int TEXT = 0xff5f6e83;
  private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
  private double[] pitches = new double[0];
  private double[] referencePitches = new double[0];
  private double referenceHz;
  private double baseHz;
  private boolean valid;

  public JourneyTonePlotView(Context context) {
    super(context);
    setContentDescription("录音音高曲线。请完成单音节录音后点击分析。");
  }

  public void show(JourneyToneContour.Result result) {
    pitches = result == null ? new double[0] : result.pitchHz.clone();
    referencePitches = new double[0];
    referenceHz = 0;
    valid = result != null && result.valid;
    baseHz = valid ? result.medianHz : 0;
    String accessibility = !valid
        ? "暂时没有可用音高走势，请阅读分析反馈"
        : "音高趋势：" + result.trend + "，首尾相差约 "
            + String.format(java.util.Locale.US, "%+.1f", result.semitoneChange)
            + " 半音；图中空白段表示无法估计基频";
    setContentDescription(accessibility);
    invalidate();
  }

  /** 对照：绿线是真实参考音，紫线是本次跟读，分别按中位音高归一化。 */
  public void showComparison(JourneyToneCompare.Result comparison) {
    if (comparison == null || !comparison.comparable) {
      show(null);
      return;
    }
    pitches = comparison.attempt.pitchHz.clone();
    baseHz = comparison.attempt.medianHz;
    referencePitches = comparison.reference.pitchHz.clone();
    referenceHz = comparison.reference.medianHz;
    valid = true;
    setContentDescription("参考音和跟读的相对音高走势对比，误差约 "
        + String.format(java.util.Locale.US, "%.1f", comparison.rmsSemitones)
        + " 半音。绿色是参考，紫色是自己。不能作为粤语发音正确率。");
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float scale = getResources().getDisplayMetrics().density;
    float left = 44 * scale;
    float right = getWidth() - 14 * scale;
    float top = 12 * scale;
    float bottom = getHeight() - 28 * scale;
    if (right <= left || bottom <= top) return;
    canvas.drawColor(Color.WHITE);
    pen.setStrokeWidth(1 * scale);
    pen.setColor(GRID);
    for (int semi = -6; semi <= 6; semi += 3) {
      float y = yPosition(semi, top, bottom);
      canvas.drawLine(left, y, right, y, pen);
      pen.setTextSize(11 * scale);
      pen.setColor(TEXT);
      canvas.drawText((semi > 0 ? "+" : "") + semi, 6 * scale, y + 4 * scale, pen);
      pen.setColor(GRID);
    }
    pen.setTextSize(11 * scale);
    pen.setColor(TEXT);
    canvas.drawText("相对半音", 4 * scale, 10 * scale, pen);
    canvas.drawText("开始", left, getHeight() - 6 * scale, pen);
    canvas.drawText("结束", right - 26 * scale, getHeight() - 6 * scale, pen);
    if (!valid || pitches.length < 2 || baseHz <= 0) {
      pen.setTextSize(13 * scale);
      canvas.drawText("录制单音节后显示音高轨迹", left + 8 * scale,
          (top + bottom) / 2, pen);
      return;
    }
    if (referencePitches.length > 1 && referenceHz > 0) {
      pen.setTextSize(11 * scale);
      pen.setColor(TEAL);
      canvas.drawText("绿：参考", left + 6 * scale, top + 13 * scale, pen);
      pen.setColor(PURPLE);
      canvas.drawText("紫：跟读", right - 65 * scale, top + 13 * scale, pen);
      drawTrack(canvas, referencePitches, referenceHz, TEAL, left, right, top, bottom, scale);
    }
    drawTrack(canvas, pitches, baseHz, PURPLE, left, right, top, bottom, scale);
  }

  private void drawTrack(Canvas canvas, double[] track, double median, int color,
                         float left, float right, float top, float bottom, float scale) {
    pen.setColor(color);
    pen.setStrokeWidth(2.5f * scale);
    float px = 0, py = 0;
    boolean previous = false;
    for (int i = 0; i < track.length; i++) {
      if (track[i] <= 0) { previous = false; continue; }
      double semi = 12 * Math.log(track[i] / median) / Math.log(2.0);
      float x = left + (right - left) * i / (track.length - 1);
      float y = yPosition(Math.max(-9, Math.min(9, semi)), top, bottom);
      if (previous) canvas.drawLine(px, py, x, y, pen);
      else canvas.drawCircle(x, y, 1.8f * scale, pen);
      px = x;
      py = y;
      previous = true;
    }
  }

  private float yPosition(double semi, float top, float bottom) {
    return (float) ((top + bottom) / 2 - semi * (bottom - top) / 18.0);
  }
}
