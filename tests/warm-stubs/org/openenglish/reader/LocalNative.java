package org.openenglish.reader;
import java.util.*;
public class LocalNative {
 public interface Callbacks {boolean abort();void segment(long a,long b,String s);void text(String s);}
 public static final List<String> calls=new ArrayList<>();public static Runnable duringWhisper=()->{};public static int decode;public static String recognized="fixture",lastLanguage="";
 public static void loadWhisper(){calls.add("loadWhisper");}public static void loadLlama(){calls.add("loadLlama");}
 public static void warmWhisper(String p,Callbacks cb){calls.add("warmWhisper:"+p);duringWhisper.run();cb.abort();}public static void warmLlama(String p,Callbacks cb){calls.add("warmLlama:"+p);cb.abort();}
 public static String whisperLanguage(String p,float[] x,int t,String l,Callbacks cb){decode++;lastLanguage=l;return recognized;}public static String whisper(String p,float[] x,int t,Callbacks cb){decode++;return "fixture";}public static String chat(String p,String text,int t,int n,Callbacks cb){decode++;return "fixture";}public static String chatFast(String p,String text,int t,int n,Callbacks cb){decode++;return "fixture";}public static void releaseWhisper(){}public static void releaseLlama(){}
}
