import org.openenglish.reader.*;
import org.json.*;
import java.nio.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Executes production Java inference against the real exported model on host ORT 1.22.0. */
public final class JourneyPhoneNativeTest {
 public static void main(String[] args)throws Exception{
  File model=new File(args[0]);byte[] bytes=Files.readAllBytes(Paths.get(args[1]));FloatBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();float[] pcm=new float[b.remaining()];b.get(pcm);
  JSONObject rules=new JSONObject(new String(Files.readAllBytes(Paths.get("app/src/main/assets/journey/phone-rules.json")),"UTF-8"));JourneyPhones core=new JourneyPhones(rules);
  long start=System.nanoTime();JourneyPhones.Recognition r=core.decode(JourneyPhoneEngine.infer(model,pcm,new JourneyPhoneEngine.Control()));double seconds=(System.nanoTime()-start)/1e9;
  JSONObject fixtures=new JSONObject(new String(Files.readAllBytes(Paths.get("tests/phone-upstream-fixtures.json")),"UTF-8"));JSONArray cases=fixtures.getJSONArray("cases"),expected=cases.getJSONObject(cases.length()-1).getJSONArray("phones");
  if(!new JSONArray(r.phones).toString().equals(expected.toString()))throw new AssertionError("Java inference differs from Python real-audio output");
  for(float[] invalid:new float[][]{new float[8000],new float[7999],new float[192001],new float[]{Float.NaN}}){try{JourneyPhoneEngine.normalize(invalid);throw new AssertionError("invalid audio accepted");}catch(IllegalArgumentException e){}}
  JourneyPhoneEngine.Control cancelled=new JourneyPhoneEngine.Control();cancelled.cancel();try{JourneyPhoneEngine.infer(model,pcm,cancelled);throw new AssertionError("cancelled job ran");}catch(java.util.concurrent.CancellationException e){}
  JourneyPhoneEngine.Control active=new JourneyPhoneEngine.Control();AtomicReference<Throwable> error=new AtomicReference<>();Thread thread=new Thread(()->{try{JourneyPhoneEngine.infer(model,Arrays.copyOf(pcm,192000),active);}catch(Throwable e){error.set(e);}});thread.start();Thread.sleep(200);active.cancel();thread.join(30000);if(thread.isAlive()||error.get()==null)throw new AssertionError("cancel did not stop job");
  float[][] retry=JourneyPhoneEngine.infer(model,Arrays.copyOf(pcm,8000),new JourneyPhoneEngine.Control());if(retry.length<1)throw new AssertionError("retry failed");
  float[][] longest=JourneyPhoneEngine.infer(model,Arrays.copyOf(pcm,192000),new JourneyPhoneEngine.Control());if(longest.length!=599)throw new AssertionError("12 second frame count");core.decode(longest);
  JSONObject manifest=new JSONObject(new String(Files.readAllBytes(Paths.get("app/src/main/assets/journey/phone-model.json")),"UTF-8"));
  JSONObject report=new JSONObject().put("model_sha256",manifest.getString("sha256")).put("maximum_12_second_input",true).put("java_ort","1.22.0 host Linux").put("real_human_audio_seconds",pcm.length/16000.0).put("load_plus_inference_seconds",seconds).put("matches_python_phone_sequence",true).put("normalized_phones",new JSONArray(r.phones)).put("cancel_and_retry",true).put("invalid_inputs_rejected",4).put("physical_android_audio_tested",false);
  Files.write(Paths.get("qa/PHONE-JAVA-VERIFICATION.json"),report.toString(2).getBytes("UTF-8"));System.out.println(report.toString());
 }
}
