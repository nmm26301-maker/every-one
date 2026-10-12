import org.openenglish.reader.*;import java.io.*;import java.util.concurrent.*;
/** Executes real rebuilt speech JNI, not a device TTS or AudioRecord test. */
public final class EnglishSpeechNativeTest {
 static int checks;static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;System.out.println("PASS "+s);}
 static class Callback implements LocalNative.Callbacks {boolean cancelled;public boolean abort(){return cancelled;}public void text(String s){}public void segment(long a,long b,String s){}}
 public static void main(String[] args)throws Exception {System.loadLibrary("english_speech");String[] codes={"en-us","en","yue","ja","cmn"},phrases={"Hello! Would you like a coffee?","Yes, I would like a coffee.","你好呀，你想飲咩？","こんにちは。こーひーはいかがですか？","你好，你想喝什么？"};for(int i=0;i<codes.length;i++){float[] pcm=IslandSpeech.render(args[0],codes[i],phrases[i],new Callback());check(pcm!=null&&pcm.length>1600&&LocalWav.hasSignal(pcm),"actual JNI produces audible PCM signal "+codes[i]);LessonAudio.writeSpeech(new File(args[1],"speech-"+codes[i]+".wav"),pcm);}
  Callback stop=new Callback();stop.cancelled=true;boolean cancelled=false;try{IslandSpeech.render(args[0],"en-us","Hello",stop);}catch(CancellationException e){cancelled=true;}check(cancelled,"English fallback honours pre-synthesis cancellation");
  boolean rejected=false;try{IslandSpeech.render(args[0],"xx","Hello",new Callback());}catch(IOException e){rejected=true;}check(rejected,"unsupported voices still rejected");
  float[] pcm=IslandSpeech.render(args[0],"en-us","We can continue talking now.",new Callback());check(LocalWav.hasSignal(pcm),"engine remains usable after cancellation and rejected language");
  System.out.println("SPEECH_NATIVE_CHECKS: "+checks);
 }
}
