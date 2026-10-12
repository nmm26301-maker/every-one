package org.openenglish.reader;

import java.util.*;

/** Bounded offline acoustic coaching. Original time-domain YIN implementation.
 * Input must be PCM mono 16kHz. Does not identify words, consonants or vowels. */
public final class PronunciationCoach {
 public static final int RATE=16000,MAX_SAMPLES=RATE*12;
 private PronunciationCoach() {}
 public static final class Track {
  public final double[] pitch,time;public final double duration,rms,clipped;
  public final int frames,voiced;public final double medianHz;
  Track(double[] p,double[] t,double d,double r,double c,int f){pitch=p;time=t;duration=d;rms=r;clipped=c;frames=f;voiced=p.length;medianHz=p.length==0?0:Math.pow(2,ContourScore.median(p)/12);}
  public boolean usable(){return voiced>=8&&duration>=.16&&rms>=.003&&clipped<.08&&voiced>=frames*.12;}
 }
 public static Track extract(float[] pcm) {
  int len=Math.min(pcm.length,MAX_SAMPLES),win=1024,hop=160,frames=len<win?0:(len-win)/hop+1;
  double[] pitches=new double[frames],times=new double[frames];int n=0,first=-1,last=-1,clipped=0;double sum=0,maxEnergy=0;
  double[] energy=new double[frames];for(int i=0;i<len;i++){sum+=(double)pcm[i]*pcm[i];if(Math.abs(pcm[i])>=.985)clipped++;}
  for(int f=0;f<frames;f++){double e=0;for(int j=0;j<win;j++)e+=(double)pcm[f*hop+j]*pcm[f*hop+j];energy[f]=Math.sqrt(e/win);maxEnergy=Math.max(maxEnergy,energy[f]);}
  double gate=Math.max(.003,maxEnergy*.07);
  for(int f=0;f<frames;f++){
   if(energy[f]<gate)continue;if(first<0)first=f;last=f;
   double hz=yin(pcm,f*hop,win);if(hz<=0)continue;
   pitches[n]=12*Math.log(hz)/Math.log(2);times[n]=(f*hop+win/2.0)/RATE;n++;
  }
  double duration=first<0?0:((last-first)*hop+win)/(double)RATE;
  return new Track(Arrays.copyOf(pitches,n),Arrays.copyOf(times,n),duration,len==0?0:Math.sqrt(sum/len),len==0?0:(double)clipped/len,first<0?frames:last-first+1);
 }
 static double yin(float[] x,int start,int size) {
  int max=RATE/60,min=RATE/500;double[] d=new double[max+1];
  // Fixed integration length keeps the difference function unbiased across lags.
  int span=size/2;for(int tau=1;tau<=max;tau++){double s=0;for(int j=0;j<span;j++){double v=x[start+j]-x[start+j+tau];s+=v*v;}d[tau]=s;}
  double running=0;for(int tau=1;tau<=max;tau++){running+=d[tau];d[tau]=running==0?1:d[tau]*tau/running;}
  int at=-1;for(int tau=min;tau<max;tau++)if(d[tau]<.25){while(tau+1<max&&d[tau+1]<d[tau])tau++;at=tau;break;}
  if(at<0)return 0;double a=d[at-1],b=d[at],c=d[Math.min(max,at+1)],den=a-2*b+c;
  double period=at+(Math.abs(den)<1e-12?0:.5*(a-c)/den),hz=RATE/period;
  return hz>=60&&hz<=500?hz:0;
 }
 public static final class Feedback {
  public final boolean valid;public final int shape;public final String title,detail;public final double ratio;
  Feedback(boolean v,int s,String t,String d,double r){valid=v;shape=s;title=t;detail=d;ratio=r;}
 }
 public static Feedback compare(Track reference,Track take,String focus,double baselineHz,double referenceMiddleHz) {
  if(!take.usable()){
   String why=take.clipped>=.08?"声音有些爆音，离麦克风稍远一点，再自然读一遍。":take.rms<.003?"声音太轻或录到了静音。靠近麦克风，在安静处再读一次。":"这次可用的音高太少。请完整读一遍，避免耳语；不用用力。";
   return new Feedback(false,-1,"小精灵还没听清",why,0);
  }
  if(reference==null||!reference.usable())return new Feedback(true,-1,"已经留下你的声音","示范曲线暂时不可用。可以对照回放，按下面的口型提示再练一次。",0);
  int shape=ContourScore.score(reference.pitch,take.pitch);double ratio=take.duration/reference.duration;
  String rhythm=ratio<.65?"总时长比示范短，听听有没有漏读或省掉长音。":ratio>1.55?"总时长比示范长，试着把拍子读得更连贯。":"总时长接近示范。";
  if(focus.equals("rhythm"))return new Feedback(true,-1,"先听一听这次的节奏",rhythm+"\n这一关练口型、舌位或拍子，音高走势不作为发音准确率。请用逐段对照听，把本关的动作放回词句。",ratio);
  String title=shape>=80?"音高走势挺接近":"再调一小步就好";
  String detail;
  if(focus.startsWith("level")){
   double range=spread(take.pitch);
   if(range>3){detail="这次平调有明显起伏。像走平路一样，先保持稳稳的一条线。";title="先把平调读稳";}
   else detail="这次音高比较平稳。";
   if(baselineHz>=60&&baselineHz<=500&&referenceMiddleHz>=60){
    // Relative practice positions, not a claim of universal speaker tone ranges.
    double target=focus.equals("level_high")?4:focus.equals("level_low")?-3:0;
    double heard=12*Math.log(take.medianHz/baselineHz)/Math.log(2),delta=heard-target;
    detail+=delta>2.5?"相对你校准的中音，这次偏高；轻轻降低一点试试。":delta< -2.5?"相对你校准的中音，这次偏低；轻轻抬高一点试试。":"相对中音的位置接近这段示范。";
    detail+="中音位置判断是粗略辅助，不必模仿示范者的绝对音高。";
   }else detail+="一、三、六声都可能很平；先校准自己的中音，才能辅助比较高低位置。";
   shape=-1;
  }else{
   double[] r=ContourScore.resample(ContourScore.normalize(reference.pitch),4),t=ContourScore.resample(ContourScore.normalize(take.pitch),4);int at=0;double diff=0;
   for(int i=0;i<4;i++)if(Math.abs(t[i]-r[i])>Math.abs(diff)){diff=t[i]-r[i];at=i;}
   detail=shape>=80?"这次升降路线接近示范，继续轻松读。":new String[]{"开头","前半段","后半段","结尾"}[at]+(diff>0?"音高偏上。对照示范，轻轻放低这一段。":"音高偏下。对照示范，轻轻抬高这一段。");
  }
  return new Feedback(true,shape,title,detail+"\n"+rhythm+"\n仅分析音高走势与总时长；不判断字词或声母韵母是否正确。",ratio);
 }
 public static double spread(double[] a){if(a.length==0)return 0;double[] b=a.clone();Arrays.sort(b);return b[(int)((b.length-1)*.9)]-b[(int)((b.length-1)*.1)];}
}
