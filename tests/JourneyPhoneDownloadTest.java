package org.openenglish.reader;
import android.content.Context;import org.json.*;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;

/** Real downloader code with deterministic HTTPS/provider fixtures; no live service. */
public final class JourneyPhoneDownloadTest {
 static final Map<String,Fake> routes=new ConcurrentHashMap<>();static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void await(CountDownLatch latch)throws Exception{check(latch.await(3,TimeUnit.SECONDS),"cleanup is not blocked by another resource");}
 static final class Fake extends HttpURLConnection {
  int code=200,requests;String location;InputStream input;boolean throwDisconnect;
  final CountDownLatch disconnected=new CountDownLatch(1);
  Fake(String path,InputStream in)throws Exception{super(new URL("https://fixture.test/"+path));input=in;routes.put(path,this);}
  public int getResponseCode(){requests++;return code;}
  public String getHeaderField(String name){return "Location".equals(name)?location:null;}
  public InputStream getInputStream(){return input;}
  public void disconnect(){disconnected.countDown();if(throwDisconnect)throw new IllegalStateException("disconnect failed");}
  public boolean usingProxy(){return false;}public void connect(){}
 }
 static String hash(byte[] data)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u)throws IOException{Fake f=routes.get(u.getPath().substring(1));if(f==null)throw new IOException("unregistered fixture");return f;}}:null);
  Path root=Files.createTempDirectory("journey-http-");
  try{run(root);}finally{try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
  System.out.println("Phone download/recovery checks: "+checks);
 }
 static void run(Path root)throws Exception{
  Path files=Files.createDirectory(root.resolve("files")),assets=Files.createDirectory(root.resolve("assets"));Files.createDirectory(assets.resolve("journey"));
  Context ctx=new Context(files.toFile(),assets.toFile());byte[] valid="downloaded fixture model".getBytes("UTF-8"),old="previous model".getBytes("UTF-8");File target=JourneyPhoneEngine.modelFile(ctx);
  JSONObject meta=new JSONObject().put("bytes",valid.length).put("sha256",hash(valid)).put("url","https://fixture.test/model");
  Files.write(assets.resolve("journey/phone-model.json"),meta.toString().getBytes("UTF-8"));Files.write(target.toPath(),old);
  Fake untouched=new Fake("model",new ByteArrayInputStream(valid));JourneyPhoneEngine.Control cancelledBeforeStart=new JourneyPhoneEngine.Control();cancelledBeforeStart.cancel();boolean earlyStop=false;
  try{JourneyPhoneEngine.download(ctx,cancelledBeforeStart,(n,t)->{});}catch(CancellationException expected){earlyStop=true;}
  check(earlyStop&&untouched.requests==0,"pre-cancelled download does not open a response");check(Arrays.equals(Files.readAllBytes(target.toPath()),old),"pre-cancel leaves installed model unchanged");
  Fake connection=new Fake("model",new ByteArrayInputStream(valid));connection.throwDisconnect=true;
  JourneyPhoneEngine.download(ctx,new JourneyPhoneEngine.Control(),(n,t)->{});
  await(connection.disconnected);check(Arrays.equals(Files.readAllBytes(target.toPath()),valid),"disconnect failure cannot reject verified installation");
  JourneyPhoneEngine.importModel(ctx,new ByteArrayInputStream(valid),new JourneyPhoneEngine.Control(),(n,t)->{});
  check(JourneyPhoneEngine.installed(ctx),"cleanup failure releases task lock for retry");
  for(int code:new int[]{403,404,500}){
   Fake failed=new Fake("model",new ByteArrayInputStream(valid));failed.code=code;boolean rejected=false;
   try{JourneyPhoneEngine.download(ctx,new JourneyPhoneEngine.Control(),(n,t)->{});}catch(IOException expected){rejected=true;}
   check(rejected,"HTTP error reported");await(failed.disconnected);check(Arrays.equals(Files.readAllBytes(target.toPath()),valid),"HTTP failure preserves installed model");
  }
  Fake redirect=new Fake("model",null);redirect.code=302;redirect.location="/final";Fake finish=new Fake("final",new ByteArrayInputStream(valid));
  JourneyPhoneEngine.download(ctx,new JourneyPhoneEngine.Control(),(n,t)->{});await(redirect.disconnected);await(finish.disconnected);check(JourneyPhoneEngine.installed(ctx),"HTTPS redirect installs verified bytes");
  Fake downgrade=new Fake("model",null);downgrade.code=302;downgrade.location="http://fixture.test/plain";boolean rejected=false;
  try{JourneyPhoneEngine.download(ctx,new JourneyPhoneEngine.Control(),(n,t)->{});}catch(IOException expected){rejected=true;}
  check(rejected,"HTTPS downgrade rejected");
  Fake corrupt=new Fake("model",new ByteArrayInputStream(new byte[valid.length]));rejected=false;
  try{JourneyPhoneEngine.download(ctx,new JourneyPhoneEngine.Control(),(n,t)->{});}catch(IOException expected){rejected=true;}
  check(rejected&&Arrays.equals(Files.readAllBytes(target.toPath()),valid),"bad response checksum preserves model");check(!new File(target+".part").exists(),"failed response removes partial");
  CountDownLatch closed=new CountDownLatch(1),disconnectEntered=new CountDownLatch(1),releaseDisconnect=new CountDownLatch(1);
  HttpURLConnection blocked=new HttpURLConnection(new URL("https://fixture.test/blocked")){
   public void connect(){}public boolean usingProxy(){return false;}
   public void disconnect(){disconnectEntered.countDown();try{releaseDisconnect.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  };
  JourneyPhoneEngine.Control control=new JourneyPhoneEngine.Control();control.attach(blocked);control.attach(new ByteArrayInputStream(valid){public void close(){closed.countDown();}});
  try{control.cancel();await(disconnectEntered);await(closed);}finally{releaseDisconnect.countDown();}
  // Cancellation must be checked before another read, even when provider close itself cannot unblock reads.
  byte[] large=new byte[1200000];meta.put("bytes",large.length).put("sha256",hash(large));Files.write(assets.resolve("journey/phone-model.json"),meta.toString().getBytes("UTF-8"));
  AtomicInteger reads=new AtomicInteger();JourneyPhoneEngine.Control cancelled=new JourneyPhoneEngine.Control();
  InputStream guarded=new ByteArrayInputStream(large){public synchronized int read(byte[] b,int off,int len){int n=reads.incrementAndGet();if(n>9)throw new AssertionError("read continued after cancellation");return super.read(b,off,len);}};
  new Fake("model",guarded);boolean stopped=false;
  try{JourneyPhoneEngine.download(ctx,cancelled,(n,t)->cancelled.cancel());}catch(CancellationException expected){stopped=true;}
  check(stopped&&reads.get()==9,"no extra provider read after cancellation");check(!new File(target+".part").exists(),"cancelled download removes partial");
 }
}
