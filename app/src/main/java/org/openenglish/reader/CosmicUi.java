package org.openenglish.reader;
import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;
/** Code-drawn stars and luminous controls: no downloads and no continuous animation loop. */
public final class CosmicUi {
 public static final int INK=0xfff5eeff,MUTED=0xffbab4d3,ACCENT=0xff9ceedd,BG=0xff10152c,CARD=0xff222442;
 private CosmicUi(){}public static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
 public static GradientDrawable panel(Context c,int a,int b,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),0xff4d4771);return d;}
 public static void button(TextView v,boolean primary){Context c=v.getContext();v.setAllCaps(false);v.setTextColor(primary?0xff181d36:INK);v.setTextSize(15);v.setGravity(Gravity.CENTER);v.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));v.setMinHeight(dp(c,52));v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x449ceedd),panel(c,primary?0xffa2e8dc:0xff292747,primary?0xffc9b0fd:0xff202b47,17),null));}
 public static final class Sky extends View {private final Paint p=new Paint(3);public Sky(Context c){super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff181c3b,0xff2f2450,0xff101a31},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);for(int i=0;i<70;i++){float x=w*((i*73%997)/997f),y=h*((i*127%991)/991f);p.setColor(i%3==0?0x99a8e8ed:0x668fa4d6);c.drawCircle(x,y,dp(getContext(),i%7==0?2:1),p);}p.setStyle(Paint.Style.STROKE);p.setColor(0x224ccdd4);p.setStrokeWidth(dp(getContext(),1));c.drawCircle(w*.85f,h*.15f,w*.33f,p);p.setStyle(Paint.Style.FILL);}}
}
