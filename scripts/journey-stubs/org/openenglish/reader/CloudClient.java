package org.openenglish.reader;
import android.content.Context;import org.json.*;import java.io.File;
/** Compile-only binary contracts. Never included in the APK. */
public final class CloudClient {
 public interface Delta{void text(String complete);}
 public static class Job{public Job(){} public void cancel(){}public void check(){}}
 public CloudClient(){}
 public CloudClient(Context c){}
 public static String endpoint(String base,String path)throws Exception{return null;}
 public String chat(String base,String model,String key,String system,JSONArray history,Job job,Delta delta)throws Exception{return null;}
 public JSONObject transcribe(String base,String model,String key,File wav,boolean timestamps,Job job)throws Exception{return null;}
 public JSONObject assess(String region,String key,File wav,String reference,Job job)throws Exception{return null;}
}
