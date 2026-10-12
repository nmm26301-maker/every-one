package org.openenglish.reader;
import android.content.Context;import java.io.*;import java.util.*;
public class LocalModels {
 public static final class Spec {public final String name;Spec(String s){name=s;}}
 public static final Spec DEEPSEEK=new Spec("deepseek"),QWEN=new Spec("qwen");public static Spec selected=QWEN;public static boolean installed=true;public static final List<String> calls=new ArrayList<>();
 public static Spec islandModel(Context c){return selected;}public static boolean ready(Context c,Spec s){return installed;}public static File file(Context c,Spec s){return new File(c.getFilesDir(),s.name);}
 public static File vad(Context c,CloudClient.Job j){j.check();calls.add("vad");return new File(c.getFilesDir(),"vad");}public static File multilingual(Context c,CloudClient.Job j){j.check();calls.add("multi");return new File(c.getFilesDir(),"multi");}public static File whisper(Context c,CloudClient.Job j){j.check();calls.add("english");return new File(c.getFilesDir(),"english");}
}
