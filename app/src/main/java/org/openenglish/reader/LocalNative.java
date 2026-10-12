package org.openenglish.reader;

/** No Android dependencies: the same JNI bridges are exercised by real host model tests. */
public final class LocalNative {
  private LocalNative() {}
  public interface Callbacks {
    boolean abort();
    void text(String complete);
    void segment(long startMs,long endMs,String text);
  }
  private static boolean whisperLoaded,llamaLoaded;
  public static synchronized void loadWhisper() { if(!whisperLoaded){System.loadLibrary("english_whisper");whisperLoaded=true;} }
  public static synchronized void loadLlama() { if(!llamaLoaded){System.loadLibrary("english_llama");llamaLoaded=true;} }
  public static native String whisper(String model,float[] samples,int threads,Callbacks callback) throws java.io.IOException;
  public static native String whisperLanguage(String model,float[] samples,int threads,String language,Callbacks callback) throws java.io.IOException;
  public static native long vadOpen(String model) throws java.io.IOException;
  public static native float vadProbability(long handle,float[] frame) throws java.io.IOException;
  public static native void vadReset(long handle) throws java.io.IOException;
  public static native void vadClose(long handle);
  public static native void warmWhisper(String model,Callbacks callback) throws java.io.IOException;
  public static native void warmLlama(String model,Callbacks callback) throws java.io.IOException;
  public static native void releaseWhisper();
  public static native void releaseLlama();
  public static native long whisperLoadCount();
  public static native long llamaLoadCount();
  public static native String chatFast(String model,String prompt,int threads,int maxTokens,Callbacks callback) throws java.io.IOException;
  public static native String chat(String model,String prompt,int threads,int maxTokens,Callbacks callback) throws java.io.IOException;
}
