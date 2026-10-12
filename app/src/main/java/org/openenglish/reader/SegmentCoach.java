package org.openenglish.reader;
import java.util.*;

/** One actionable acoustic observation per aligned syllable/mora, no phoneme accuracy grade. */
public final class SegmentCoach {
 private SegmentCoach(){}
 public static final class Unit {
  public final String label;public final int refStart,refEnd,start,end;public final boolean available;
  public final double relativeDuration;public final String observation;public final PronunciationCoach.Track reference,take;
  Unit(String l,int rs,int re,int s,int e,boolean ok,double ratio,String o,PronunciationCoach.Track r,PronunciationCoach.Track t){label=l;refStart=rs;refEnd=re;start=s;end=e;available=ok;relativeDuration=ratio;observation=o;reference=r;take=t;}
 }
 public static List<Unit> compare(String lang,String[] labels,int[] groups,int[][] referenceSpans,int[][] takeSpans,float[] ref,float[] pcm){
  if(referenceSpans.length!=groups.length||takeSpans.length!=groups.length||labels.length<1)throw new IllegalArgumentException("segment shape");
  int[][] r=TifaDecoder.grouped(referenceSpans,groups,labels.length),t=TifaDecoder.grouped(takeSpans,groups,labels.length);
  boolean[] good=new boolean[labels.length];Arrays.fill(good,true);for(int i=0;i<groups.length;i++)if(referenceSpans[i][1]<=referenceSpans[i][0]||takeSpans[i][1]<=takeSpans[i][0])good[groups[i]]=false;
  int[] starts=new int[labels.length],ends=new int[labels.length];for(int i=0;i<labels.length;i++){starts[i]=i;ends[i]=i;}
  // A long vowel has no audible internal boundary: replay and compare the entire two-mora span.
  if(lang.equals("ja"))for(int i=1;i<labels.length;i++)if(labels[i].matches("[aiueo]")&&labels[i-1].endsWith(labels[i])){starts[i]=i-1;ends[i-1]=i;}
  double rd=0,td=0;for(int i=0;i<labels.length;i++){rd+=r[i][1]-r[i][0];td+=t[i][1]-t[i][0];}
  List<Unit> out=new ArrayList<>();for(int i=0;i<labels.length;i++){
   if(starts[i]!=i)continue;int last=ends[i],rs=r[i][0],re=r[last][1],s=t[i][0],e=t[last][1];boolean ok=good[i];for(int j=i+1;j<=last;j++)ok&=good[j];
   String label=labels[i];for(int j=i+1;j<=last;j++)label+="・"+labels[j];
   float[] ra=TifaDecoder.crop(ref,rs,re,0),ta=TifaDecoder.crop(pcm,s,e,0);PronunciationCoach.Track rt=PronunciationCoach.extract(ra),tt=PronunciationCoach.extract(ta);
   double ratio=(re>rs&&rd>0&&td>0)?((e-s)/td)/((re-rs)/rd):0;
   String observation=!ok?"这一段没有切稳，请完整读本关后再试；可以先对照整段录音。":rhythm(labels.length,ratio,(e-s)*10,(re-rs)*10);
   if(ok&&!lang.equals("ja")&&rt.usable()&&tt.usable()){
    double[] a=ContourScore.resample(ContourScore.normalize(rt.pitch),4),b=ContourScore.resample(ContourScore.normalize(tt.pitch),4);double max=0;int at=0;
    for(int j=0;j<4;j++)if(Math.abs(b[j]-a[j])>Math.abs(max)){max=b[j]-a[j];at=j;}
    if(Math.abs(max)>2.5)observation+="\n音高走势："+new String[]{"开头","前半段","后半段","结尾"}[at]+(max>0?"相对示范偏上，轻轻放低这一段。":"相对示范偏下，轻轻抬高这一段。");
    else observation+="\n这段音高走势接近示范；再听听声调的高低位置。";
   }
   if(lang.equals("ja")&&label.contains("・"))observation+="\n长音连成一个元音，占两拍；不用在中间停下来。";
   if(lang.equals("ja")&&label.equals("t"))observation+="\n促音留一拍准备 t，不要额外加一个元音。";
   out.add(new Unit(label,rs,re,s,e,ok,ratio,observation,rt,tt));
  }return out;
 }
 private static String rhythm(int count,double ratio,int ms,int refMs){
  String heard="片段时长：你 "+ms+" ms · 示范 "+refMs+" ms。";
  if(count==1)return heard+"\n只看这一段无法判断长短音，先用示范 / 我对照听。";
  return heard+"\n"+(ratio<.65?"相对整句，这一段比示范短。慢一点，把这一拍留出来。":ratio>1.55?"相对整句，这一段比示范长。听示范的拍子，轻轻收短。":"这一段在整句中占的时间接近示范。")+"（节奏辅助）";
 }
 public static String focused(String lang,PronunciationCoach.Track ref,PronunciationCoach.Track take,String tip){
  if(!take.usable())return "这次可用音高不够，先对照听；短促辅音也可能没有稳定音高。\n"+tip;
  String s="先听示范，再听自己，只改一个动作。";
  if(ref!=null&&ref.usable()){
   double ratio=take.duration/ref.duration;s="你 "+Math.round(take.duration*1000)+" ms · 示范 "+Math.round(ref.duration*1000)+" ms。\n"+(ratio<.65?"这次片段较短，完整保留这一拍。":ratio>1.55?"这次片段较长，听听是不是多拖了一拍。":"片段时长接近示范。");
  }return s+"\n\n这次只练："+tip+"\n时长辅助不能判断声母、韵母或元音是否正确。";
 }
}
