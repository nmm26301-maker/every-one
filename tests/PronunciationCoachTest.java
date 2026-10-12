import org.openenglish.reader.*;
import java.io.*;
import java.util.*;
public class PronunciationCoachTest {
 static int checks;
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
 static float[] tone(double first,double last,double seconds,double gain){int n=(int)(seconds*16000);float[] p=new float[n+6400];double phase=0;for(int i=0;i<n;i++){phase+=2*Math.PI*(first+(last-first)*i/(double)n)/16000;double envelope=Math.min(1,i/320.0)*Math.min(1,(n-i)/320.0);p[i+3200]=(float)(gain*envelope*(Math.sin(phase)+.25*Math.sin(2*phase)));}return p;}
 public static void main(String[] args)throws Exception{
  check(ContourScore.score(new double[]{0,1,3,6},new double[]{4,5,7,10})==100,"relative voice registers");
  check(ContourScore.score(new double[]{-6,-3,0,3,6},new double[]{0,0,0,0,0})<50,"flat is not rising");
  check(ContourScore.score(new double[]{Double.NaN,1,2,3},new double[]{1,2,3})==100,"invalid frames filtered");
  check(ContourScore.score(new double[]{},new double[]{1})==0,"no track no score");
  check(ContourScore.resample(new double[]{2},64).length==64,"single sample");
  PronunciationCoach.Track flat=PronunciationCoach.extract(tone(160,160,.7,.2));
  check(flat.usable(),"clear signal usable");check(Math.abs(flat.medianHz-160)<3,"known fundamental");check(PronunciationCoach.spread(flat.pitch)<1,"stable fundamental");
  PronunciationCoach.Track quiet=PronunciationCoach.extract(tone(160,160,.7,.0001));check(!quiet.usable(),"quiet no award");
  check(!PronunciationCoach.extract(new float[0]).usable(),"empty");check(!PronunciationCoach.extract(new float[1023]).usable(),"short window bounded");
  Random rand=new Random(42);float[] noise=new float[16000];for(int i=0;i<noise.length;i++)noise[i]=(rand.nextFloat()-.5f)*.3f;
  check(!PronunciationCoach.extract(noise).usable(),"noise not voiced speech");
  float[] saturated=new float[16000];for(int i=0;i<saturated.length;i++)saturated[i]=i%100<50?1:-1;check(!PronunciationCoach.extract(saturated).usable(),"clipping rejected");
  PronunciationCoach.Track rise=PronunciationCoach.extract(tone(120,220,.8,.2)),higher=PronunciationCoach.extract(tone(180,330,.8,.2));
  check(rise.usable()&&higher.usable(),"rising signals extracted");
  check(PronunciationCoach.compare(rise,higher,"pitch",0,0).shape>85,"same rise across speakers");
  check(PronunciationCoach.compare(rise,flat,"pitch",0,0).shape<65,"flat versus rising feedback");
  check(!PronunciationCoach.compare(flat,quiet,"pitch",0,0).valid,"weak take not successful");
  check(PronunciationCoach.compare(flat,flat,"level",0,0).shape==-1,"uncalibrated level tone not graded");
  check(PronunciationCoach.compare(flat,flat,"level",0,0).detail.contains("校准"),"requires middle calibration");
  PronunciationCoach.Track high=PronunciationCoach.extract(tone(220,220,.7,.2));
  check(PronunciationCoach.compare(flat,high,"level",160,160).detail.contains("偏高"),"relative high correction");
  PronunciationCoach.Track low=PronunciationCoach.extract(tone(110,110,.7,.2));
  check(PronunciationCoach.compare(flat,low,"level",160,160).detail.contains("偏低"),"relative low correction");
  check(PronunciationCoach.compare(flat,high,"level",220,220).detail.contains("接近"),"no absolute speaker pitch bias");
  check(PronunciationCoach.compare(flat,flat,"level_high",160,160).detail.contains("偏低"),"high tone needs relative lift");
  check(PronunciationCoach.compare(flat,flat,"level_low",160,160).detail.contains("偏高"),"low tone needs relative lowering");
  check(PronunciationCoach.compare(flat,PronunciationCoach.extract(tone(160,160,1.8,.2)),"rhythm",0,0).detail.contains("比示范长"),"duration feedback");
  check(PronunciationCoach.compare(null,flat,"rhythm",0,0).shape==-1,"missing reference does not invent comparison");
  File dir=new File("build/language-tests");dir.mkdirs();File wave=new File(dir,"roundtrip.wav");float[] original=tone(160,160,.7,.2);LessonAudio.write(wave,original);float[] read=LocalWav.read(wave);check(read.length==original.length,"wav length roundtrip");double error=0;for(int i=0;i<read.length;i++)error=Math.max(error,Math.abs(read[i]-original[i]));check(error<.00004,"wav signal roundtrip");
  File assets=new File("app/src/main/assets/languages");String[] files=assets.list((d,n)->n.endsWith(".wav"));Arrays.sort(files);check(files.length==204,"204 offline reference clips");
  for(String f:files){long before=System.nanoTime();PronunciationCoach.Track t=PronunciationCoach.extract(LocalWav.read(new File(assets,f)));System.out.printf(Locale.US,"Reference %s: %.3fs voiced=%d median=%.1fHz usable=%s analysis=%dms%n",f,t.duration,t.voiced,t.medianHz,t.usable(),(System.nanoTime()-before)/1000000);if(!f.startsWith("dojo_"))check(t.usable(),"usable original reference "+f);else check(Double.isFinite(t.duration),"bounded analysis for dojo reference "+f);}
  System.out.println("Pronunciation checks passed: "+checks);
 }
}
