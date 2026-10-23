package org.openenglish.reader;
import android.content.Context;import android.os.Build;import org.json.*;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;

/** Production engine tests with host storage/ABI fixtures; no acoustic inference. */
public final class JourneyPhoneInstallTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static void await(CountDownLatch latch)throws Exception{check(latch.await(3,TimeUnit.SECONDS),"cleanup/worker completes");}
 private static class Blocked extends InputStream {
  final CountDownLatch entered=new CountDownLatch(1),closed=new CountDownLatch(1);
  public int read()throws IOException{entered.countDown();try{closed.await();}catch(InterruptedException e){throw new IOException(e);}throw new IOException("closed");}
  public int read(byte[] b,int off,int len)throws IOException{return read();}
  public void close(){closed.countDown();}
 }
 private static final class Connection extends HttpURLConnection {
  final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);final AtomicInteger calls=new AtomicInteger();
  Connection()throws Exception{super(new URL("https://example.test/model"));}
  public void disconnect(){calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  public boolean usingProxy(){return false;}public void connect(){}
 }
 private static String sha(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
 private static byte[] bytes(File f)throws Exception{return Files.readAllBytes(f.toPath());}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("journey-install-");
  try{run(root);}finally{try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
  System.out.println("Phone installation/cancellation checks: "+checks);
 }
 private static void run(Path root)throws Exception{
  Path files=Files.createDirectory(root.resolve("files")),assets=Files.createDirectory(root.resolve("assets"));Files.createDirectory(assets.resolve("journey"));
  byte[] valid="verified model fixture".getBytes("UTF-8"),old="previous installed model".getBytes("UTF-8");
  JSONObject meta=new JSONObject().put("bytes",valid.length).put("sha256",sha(valid));
  Files.write(assets.resolve("journey/phone-model.json"),meta.toString().getBytes("UTF-8"));Context ctx=new Context(files.toFile(),assets.toFile());File target=JourneyPhoneEngine.modelFile(ctx);Files.write(target.toPath(),old);
  JourneyPhoneEngine.Progress progress=(n,t)->{};
  for(byte[] invalid:new byte[][]{new byte[valid.length],Arrays.copyOf(valid,valid.length-1),Arrays.copyOf(valid,valid.length+1)}){
   boolean rejected=false;try{JourneyPhoneEngine.importModel(ctx,new ByteArrayInputStream(invalid),new JourneyPhoneEngine.Control(),progress);}catch(IOException e){rejected=true;}
   check(rejected&&Arrays.equals(bytes(target),old),"bad hash/short/large input preserves old model");check(!new File(target+".part").exists(),"failed partial removed");
  }
  JourneyPhoneEngine.Control preCancelled=new JourneyPhoneEngine.Control();preCancelled.cancel();AtomicBoolean closed=new AtomicBoolean();
  try{JourneyPhoneEngine.importModel(ctx,new ByteArrayInputStream(valid){public void close(){closed.set(true);}},preCancelled,progress);throw new AssertionError("cancel accepted");}catch(CancellationException expected){}
  check(closed.get(),"pre-cancel closes provider stream");
  Build.VERSION.SDK_INT=23;closed.set(false);
  try{JourneyPhoneEngine.importModel(ctx,new ByteArrayInputStream(valid){public void close(){closed.set(true);}},new JourneyPhoneEngine.Control(),progress);throw new AssertionError("unsupported accepted");}catch(IOException expected){}finally{Build.VERSION.SDK_INT=35;}
  check(closed.get(),"unsupported device closes provider stream");
  Blocked input=new Blocked();JourneyPhoneEngine.Control active=new JourneyPhoneEngine.Control();CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();
  Thread worker=new Thread(()->{try{JourneyPhoneEngine.importModel(ctx,input,active,progress);}catch(Throwable e){error.set(e);}finally{done.countDown();}});worker.setDaemon(true);worker.start();await(input.entered);
  closed.set(false);boolean busy=false;try{JourneyPhoneEngine.importModel(ctx,new ByteArrayInputStream(valid){public void close(){closed.set(true);}},new JourneyPhoneEngine.Control(),progress);}catch(IOException e){busy=true;}
  check(busy&&closed.get(),"concurrent import rejected and stream closed");
  active.cancel();await(done);check(error.get()!=null&&Arrays.equals(bytes(target),old),"blocked import cancelled without replacing old model");check(!new File(target+".part").exists(),"cancelled partial removed");
  boolean callbackFailed=false;try{JourneyPhoneEngine.importModel(ctx,new ByteArrayInputStream(valid),new JourneyPhoneEngine.Control(),(n,t)->{throw new IllegalStateException("progress failed");});}catch(IllegalStateException e){callbackFailed=true;}
  check(callbackFailed&&Arrays.equals(bytes(target),old),"callback failure preserves installed model");check(!new File(target+".part").exists(),"callback failure removes partial");
  JourneyPhoneEngine.importModel(ctx,new ByteArrayInputStream(valid),new JourneyPhoneEngine.Control(),progress);check(Arrays.equals(bytes(target),valid),"retry installs verified model");check(JourneyPhoneEngine.installed(ctx),"installed after successful retry");
  JourneyPhoneEngine.Control control=new JourneyPhoneEngine.Control();Connection connection=new Connection();control.attach(connection);
  CountDownLatch returned=new CountDownLatch(1);Thread caller=new Thread(()->{control.cancel();returned.countDown();});caller.start();
  try{await(connection.entered);await(returned);boolean cancelled=false;try{control.check();}catch(CancellationException e){cancelled=true;}check(cancelled,"cancel flag visible while disconnect blocked");control.cancel();check(connection.calls.get()==1,"repeat cancellation does not disconnect twice");}finally{connection.release.countDown();caller.join(3000);}
  JourneyPhoneEngine.Control late=new JourneyPhoneEngine.Control();late.cancel();Blocked lateInput=new Blocked();try{late.attach(lateInput);throw new AssertionError("late attachment accepted");}catch(CancellationException expected){}await(lateInput.closed);
  Connection lateConnection=new Connection();lateConnection.release.countDown();try{late.attach(lateConnection);throw new AssertionError("late connection accepted");}catch(CancellationException expected){}await(lateConnection.entered);
  JourneyPhoneEngine.Control interrupted=new JourneyPhoneEngine.Control();Blocked interruptedInput=new Blocked();Connection interruptedConnection=new Connection();interruptedConnection.release.countDown();
  Thread.currentThread().interrupt();try{
   try{interrupted.attach(interruptedInput);throw new AssertionError("interrupted stream accepted");}catch(CancellationException expected){}
   try{interrupted.attach(interruptedConnection);throw new AssertionError("interrupted connection accepted");}catch(CancellationException expected){}
  }finally{Thread.interrupted();}
  await(interruptedInput.closed);await(interruptedConnection.entered);
 }
}
