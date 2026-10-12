import org.openenglish.reader.*;
import org.json.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public class TifaIntegrationTest {
 static int checks;static void ok(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static JSONObject json(File f)throws Exception{return new JSONObject(new String(Files.readAllBytes(f.toPath()),"UTF-8"));}
 static int[] ints(JSONArray a)throws Exception{int[] b=new int[a.length()];for(int i=0;i<b.length;i++)b[i]=a.getInt(i);return b;}
 static int[][] spans(JSONArray a)throws Exception{int[][] b=new int[a.length()][];for(int i=0;i<b.length;i++)b[i]=ints(a.getJSONArray(i));return b;}
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);JSONArray fixtures=new JSONArray(new String(Files.readAllBytes(new File(root,"tests/tifa-decoder-fixtures.json").toPath()),"UTF-8"));
  for(int k=0;k<fixtures.length();k++){JSONObject f=fixtures.getJSONObject(k);JSONArray rows=f.getJSONArray("sim");float[][] sim=new float[rows.length()][];for(int i=0;i<sim.length;i++){JSONArray row=rows.getJSONArray(i);sim[i]=new float[row.length()];for(int j=0;j<row.length();j++)sim[i][j]=(float)row.getDouble(j);}ok(Arrays.deepEquals(TifaDecoder.decode(sim,ints(f.getJSONArray("groups"))),spans(f.getJSONArray("spans"))),"canonical decoder "+k);}
  File assets=new File(root,"app/src/main/assets/languages");JSONObject templates=json(new File(assets,"segments.json")),references=json(new File(assets,"reference-segments.json"));JSONArray lessons=new JSONArray(new String(Files.readAllBytes(new File(assets,"lessons.json").toPath()),"UTF-8"));
  int maxDiff=0;float[] seed=null;
  try(LocalTifa engine=new LocalTifa(new File(assets,"tifa/spectrogram.onnx"),new File(assets,"tifa/model.onnx"),new LocalTifa.Control())){
   for(int k=0;k<lessons.length();k++){
    JSONObject l=lessons.getJSONObject(k),t=templates.getJSONObject(l.getString("id"));int[] ids=ints(t.getJSONArray("tokens")),groups=ints(t.getJSONArray("groups"));long[] tokens=new long[ids.length];for(int i=0;i<ids.length;i++)tokens[i]=ids[i];
    float[] pcm=LocalWav.read(new File(assets,l.getString("audio")));seed=pcm;int[][] actual=engine.align(pcm,tokens,groups,new LocalTifa.Control()),expected=spans(references.getJSONObject(l.getString("id")).getJSONArray("spans"));
    int diff=0;for(int i=0;i<actual.length;i++)for(int j=0;j<2;j++)diff=Math.max(diff,Math.abs(actual[i][j]-expected[i][j]));maxDiff=Math.max(maxDiff,diff);ok(diff<=2,"Java/Python inference "+l.getString("id")+" drift "+diff);
    JSONArray ls=t.getJSONArray("labels");String[] labels=new String[ls.length()];for(int i=0;i<labels.length;i++)labels[i]=ls.getString(i);
    List<SegmentCoach.Unit> units=SegmentCoach.compare(l.getString("lang"),labels,groups,expected,actual,pcm,pcm);ok(!units.isEmpty(),"units "+k);
    for(SegmentCoach.Unit u:units)if(u.available){ok(u.start>=0&&u.end*160<=pcm.length+160,"bounded unit");ok(TifaDecoder.crop(pcm,u.start,u.end,800).length>0,"replay clip");}
    if(l.getString("id").equals("ja_obaasan"))ok(units.size()==4&&units.get(1).label.equals("ba・a"),"long vowel combined");
    if(l.getString("id").equals("ja_kitta"))ok(units.size()==3&&units.get(1).label.equals("t"),"sokuon unit");
    if(l.getString("id").equals("dojo_w_wan2"))ok(!units.get(0).available,"missed reference phone refuses focused replay");
    if(k%40==0)System.out.println("real ONNX courses "+k);
   }
   final float[] longAudio=new float[192000];for(int i=0;i<longAudio.length;i++)longAudio[i]=seed[i%seed.length];final LocalTifa.Control c=new LocalTifa.Control();ExecutorService pool=Executors.newSingleThreadExecutor();
   Future<?> pending=pool.submit(()->{try{engine.align(longAudio,new long[]{206,191,206,191},new int[]{0,0,1,1},c);throw new AssertionError("cancel must not publish");}catch(CancellationException|ai.onnxruntime.OrtException expected){}});
   Thread.sleep(40);c.cancel();pending.get(30,TimeUnit.SECONDS);ok(c.cancelled(),"active inference cancelled");pool.shutdownNow();
   ok(engine.align(seed,new long[]{206,191},new int[]{0,0},new LocalTifa.Control()).length==2,"fresh run after cancellation");
  }
  ok(ArticulationHints.forUnit("zh","nián","").contains("鼻腔"),"n action");ok(ArticulationHints.forUnit("zh","lián","").contains("两侧"),"l action");ok(ArticulationHints.forUnit("ja","ba・a","").contains("两拍"),"long vowel action");
  boolean rejected=false;try{LocalTifa.upsample(new float[10]);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"short rejected");
  PronunciationCoach.Track tr=PronunciationCoach.extract(seed);ok(PronunciationCoach.compare(tr,tr,"rhythm",0,0).shape==-1,"no pitch score for articulation/mora lessons");
  System.out.println("PASS: "+checks+" checks; 200 canonical decoders; all 204 real Java ONNX courses; max frame drift "+maxDiff+"; segment replay bounds, long vowel, cancellation/recovery, actions.");
 }
}
