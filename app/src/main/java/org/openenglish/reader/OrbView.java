package org.openenglish.reader;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class OrbView extends View {
  private final Paint p = new Paint(3);
  public boolean active = false;
  private final long born = System.currentTimeMillis();

  public OrbView(Context c) {
    super(c);
    setContentDescription("口语老师动态球，点击选择练习或开口");
  }

  @Override
  protected void onDraw(Canvas c) {
    float x = getWidth() / 2f, y = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) * .34f;
    double t = (System.currentTimeMillis() - born) / 1100.0;
    float pulse = (float) Math.sin(t) * (active ? .06f : .025f);
    p.setShader(null);
    p.setColor(0xffe1efeb);
    c.drawCircle(x, y, r * 1.3f, p);
    p.setColor(0xffc9e4db);
    c.drawCircle(x, y, r * (1.12f + pulse), p);
    p.setShader(
        new RadialGradient(
            x - r * .35f,
            y - r * .45f,
            r * 1.7f,
            new int[] {0xffc1fff1, 0xff49bcab, 0xff126d64},
            null,
            Shader.TileMode.CLAMP));
    c.drawCircle(x, y, r * (1 + pulse), p);
    p.setShader(null);
    p.setColor(0xbbffffff);
    float wobble = (float) Math.sin(t) * r * .018f;
    c.drawOval(x - r * .44f, y - r * .19f + wobble, x - r * .29f, y + r * .04f + wobble, p);
    c.drawOval(x + r * .29f, y - r * .19f + wobble, x + r * .44f, y + r * .04f + wobble, p);
    p.setColor(0x88ffffff);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(r * .045f);
    c.drawArc(x - r * .12f, y + r * .06f, x + r * .12f, y + r * .26f, 10, 160, false, p);
    p.setStyle(Paint.Style.FILL);
    if (isAttachedToWindow()) postInvalidateDelayed(active ? 30 : 80);
  }
}
