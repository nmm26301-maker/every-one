import org.openenglish.reader.JourneyCore;
import java.io.*;
import java.util.concurrent.*;

/** Exercise the same engine boundary used by UI actions and background jobs. */
public final class JourneyEngineFailureTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  int[] calls={0};check(JourneyCore.engineCall(()->{calls[0]++;return "reply";}).equals("reply")&&calls[0]==1,"successful task runs once");
  check(JourneyCore.engineCall(()->null)==null,"null return remains caller's responsibility");
  for(LinkageError error:new LinkageError[]{new UnsatisfiedLinkError("secret-key"),new NoSuchMethodError("private-url"),new NoClassDefFoundError("private-path"),new ExceptionInInitializerError("raw-body")}){
   boolean converted=false;try{JourneyCore.engineCall(()->{throw error;});}catch(IOException e){converted=true;check(e.getCause()==error,"original cause remains available for safe diagnostic");check(e.getMessage().contains("引擎无法加载")&&!e.getMessage().contains(error.getMessage()),"UI error is actionable without leaking binary message");}
   check(converted,"linkage error does not escape the task as an uncaught Error");
  }
  for(Exception error:new Exception[]{new IOException("network"),new CancellationException("cancel"),new IllegalStateException("state")}){
   Exception observed=null;try{JourneyCore.engineCall(()->{throw error;});}catch(Exception e){observed=e;}check(observed==error,"ordinary errors and cancellation preserve identity");
  }
  OutOfMemoryError fatal=new OutOfMemoryError("test only, no memory pressure");Error observed=null;try{JourneyCore.engineCall(()->{throw fatal;});}catch(Error e){observed=e;}check(observed==fatal,"VM resource failures are not disguised as load errors");
  Exception inner=new IOException("PRIVATE_KEY_123"),outer=new IOException("https://private.example/api?token=PRIVATE_TOKEN",inner);
  inner.setStackTrace(new StackTraceElement[]{new StackTraceElement("Engine","load","/private/audio.wav",37)});
  String report=JourneyCore.diagnostic(outer);check(report.contains("java.io.IOException")&&report.contains("Engine.load:37"),"safe type and code location retained");
  check(!report.contains("PRIVATE_")&&!report.contains("https://")&&!report.contains("audio.wav"),"error messages and file paths excluded");
  inner.initCause(outer);check(JourneyCore.diagnostic(outer).length()<3000,"cause cycle terminates");
  StackTraceElement[] many=new StackTraceElement[100];for(int i=0;i<many.length;i++)many[i]=new StackTraceElement("Class"+i,"method","secret",i);outer.setStackTrace(many);
  check(!JourneyCore.diagnostic(outer).contains("Class6.method"),"frame count bounded");
  check(JourneyCore.diagnostic(null).isEmpty(),"absent diagnostic supported");
  System.out.println("Engine failure checks: "+checks);
 }
}
