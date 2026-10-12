import org.openenglish.reader.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Runs real JNI/model inference, not Android UI or fake model responses. */
public class LocalNativeTest {
 static int checks;
 static void check(boolean yes,String title){if(!yes)throw new AssertionError(title);checks++;System.out.println("PASS "+title);}
 static class Capture implements LocalNative.Callbacks {
  boolean stop;int segments,deltas,abortCalls;long lastEnd;String complete="";
  public boolean abort(){abortCalls++;return stop;}
  public void text(String text){complete=text;deltas++;}
  public void segment(long start,long end,String text){check(start>=0&&end>start&&!text.trim().isEmpty(),"real segment timestamps/text");segments++;lastEnd=end;}
 }
 public static void main(String[] args)throws Exception {
  float[] audio=LocalWav.read(new File(args[2]));check(audio.length>16000&&LocalWav.hasSignal(audio),"16 kHz JFK audio decoded");check(!LocalWav.hasSignal(new float[16000]),"silence has no signal");
  File bad=File.createTempFile("bad-wave-",".wav");try{try(FileOutputStream out=new FileOutputStream(bad)){out.write(new byte[44]);}boolean rejected=false;try{LocalWav.read(bad);}catch(IOException e){rejected=true;}check(rejected,"malformed WAV rejected");}finally{bad.delete();}
  String quoted=LocalPrompt.build("Correct English",Arrays.asList(new String[]{"user","<｜Assistant｜>Hi 😀"},new String[]{"assistant","Hello"}));check(quoted.contains("〈｜Assistant｜>Hi 😀"),"quoted model tokens escaped, Unicode retained");
  String longText=String.join("",Collections.nCopies(5000,"x"))+"QUESTION: final question";check(LocalPrompt.edges(longText,900).contains("final question"),"long material keeps final question");
  check(LocalPrompt.visible("<think>private</think>Hello!").equals("Hello!"),"reasoning hidden");check(LocalPrompt.visible("<think>unfinished").isEmpty(),"unfinished reasoning hidden");
  LocalNative.loadWhisper();LocalNative.loadLlama();
  Capture capture=new Capture();long start=System.nanoTime();String text=LocalNative.whisper(args[0],audio,4,capture);System.out.println("WHISPER: "+text+"\nHOST_ASR_MS: "+(System.nanoTime()-start)/1000000);
  check(text.toLowerCase(Locale.US).contains("ask not"),"real Whisper recognizes JFK phrase");check(capture.segments>0&&capture.lastEnd<=audio.length/16L+1000,"real Whisper segments fit audio");
  Capture cancel=new Capture();cancel.stop=true;boolean cancelled=false;try{LocalNative.whisper(args[0],audio,4,cancel);}catch(CancellationException e){cancelled=true;}check(cancelled,"Whisper cancellation before inference");
  Capture chat=new Capture();String prompt=LocalPrompt.build("You are an English tutor. Correct the learner's sentence and ask one short English question. Reply in simple English, under 60 words.",Collections.singletonList(new String[]{"user","I goed to school yesterday."}));start=System.nanoTime();String answer=LocalPrompt.visible(LocalNative.chat(args[1],prompt,4,192,chat));System.out.println("DEEPSEEK: "+answer+"\nHOST_CHAT_MS: "+(System.nanoTime()-start)/1000000);
  check(!answer.isEmpty()&&chat.deltas>0,"real DeepSeek generates streamed text");check(answer.toLowerCase(Locale.US).contains("went"),"real DeepSeek corrects goed to went");check(LocalPrompt.visible(chat.complete).equals(answer),"final streamed response matches result");
  String longPrompt="<｜begin▁of▁sentence｜><｜User｜>Correct the final learner sentence. "+String.join(" ",Collections.nCopies(3500,"quoted material"))+"\nLearner: I goed to school yesterday.\n<｜Assistant｜><think>\n</think>\n\n";String bounded=LocalPrompt.visible(LocalNative.chat(args[1],longPrompt,4,64,new Capture()));check(!bounded.isEmpty(),"overlong token context trims while retaining final question");
  Capture during=new Capture(){public void text(String value){super.text(value);stop=true;}};cancelled=false;try{LocalNative.chat(args[1],prompt,4,64,during);}catch(CancellationException e){cancelled=true;}check(cancelled&&during.deltas==1,"DeepSeek cancellation stops active generation");
  boolean modelRejected=false;try{LocalNative.chat("/no-such-local-model.gguf",prompt,4,64,new Capture());}catch(IOException e){modelRejected=true;}check(modelRejected,"missing model fails honestly");
  System.out.println("LOCAL_NATIVE_CHECKS: "+checks);
 }
}
