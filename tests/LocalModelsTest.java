package org.openenglish.reader;
import android.content.Context;import android.net.Uri;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.CancellationException;
/** Production installer with host filesystem/provider shims and simulated HTTP; not Android execution. */
public class LocalModelsTest {
 static int checks,mode;static byte[] fixture;static long requestedOffset;static boolean disconnected;
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static class Connection extends HttpURLConnection {
  Connection(URL url){super(url);}public void connect(){}public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}
  public int getResponseCode(){String range=getRequestProperty("Range");requestedOffset=range==null?0:Long.parseLong(range.substring(6,range.length()-1));return mode==3?302:mode==0?206:200;}
  public String getHeaderField(String name){if(name.equals("Location"))return "http://unsafe.example/model";if(name.equals("Content-Range"))return "bytes "+requestedOffset+"-511/512";return null;}
  public InputStream getInputStream(){return new ByteArrayInputStream(mode==2?new byte[512]:mode==0?Arrays.copyOfRange(fixture,(int)requestedOffset,fixture.length):fixture);}
 }
 static String sha(byte[] data)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("local-models-");Context c=new Context(root.toFile(),new File(args[0]));fixture=new byte[512];new Random(42).nextBytes(fixture);
  URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new Connection(url);}}:null);
  LocalModels.Spec m=new LocalModels.Spec("fixture","fixture","fixture.bin","https://fixture.invalid/model",sha(fixture),fixture.length);File target=LocalModels.file(c,m);target.getParentFile().mkdirs();File part=new File(target+".part");LocalModels.Progress progress=(s,n,t)->{};
  try {
   LocalModels.vad(c,new CloudClient.Job());check(LocalModels.ready(c,LocalModels.VAD),"real bundled Silero extraction and SHA-256 verification");
   LocalModels.whisper(c,new CloudClient.Job());check(LocalModels.ready(c,LocalModels.WHISPER),"real bundled Whisper extraction and SHA-256 verification");
   LocalModels.multilingual(c,new CloudClient.Job());check(LocalModels.ready(c,LocalModels.MULTI),"real multilingual Whisper extraction and SHA verification");
   Files.write(part.toPath(),Arrays.copyOf(fixture,256));mode=0;LocalModels.download(c,m,new CloudClient.Job(),progress);check(requestedOffset==256&&Arrays.equals(Files.readAllBytes(target.toPath()),fixture),"206 resume installs complete verified model");check(!part.exists(),"successful install consumes partial file");
   target.delete();Files.write(part.toPath(),Arrays.copyOf(fixture,128));mode=1;LocalModels.download(c,m,new CloudClient.Job(),progress);check(Arrays.equals(Files.readAllBytes(target.toPath()),fixture),"200 response safely restarts ignored range");
   Path imported=root.resolve("import.bin");Files.write(imported,fixture);Files.write(part.toPath(),new byte[]{4,5,6});LocalModels.importModel(c,m,Uri.parse(imported.toString()),new CloudClient.Job(),progress);check(part.length()==3&&target.length()==512,"successful import preserves download partial");
   Files.write(imported,new byte[512]);boolean failed=false;try{LocalModels.importModel(c,m,Uri.parse(imported.toString()),new CloudClient.Job(),progress);}catch(IOException e){failed=true;}check(failed&&Arrays.equals(Files.readAllBytes(target.toPath()),fixture),"bad import hash retains installed model");check(!new File(part+".import").exists()&&part.length()==3,"bad import removes staging but keeps download");
   Files.write(imported,new byte[513]);failed=false;try{LocalModels.importModel(c,m,Uri.parse(imported.toString()),new CloudClient.Job(),progress);}catch(IOException e){failed=true;}check(failed&&target.length()==512,"oversized import cannot replace installed model");
   target.delete();part.delete();mode=2;failed=false;try{LocalModels.download(c,m,new CloudClient.Job(),progress);}catch(IOException e){failed=true;}check(failed&&!target.exists()&&!part.exists(),"bad download hash rejected and partial removed");
   mode=3;failed=false;try{LocalModels.download(c,m,new CloudClient.Job(),progress);}catch(IOException e){failed=true;}check(failed&&!target.exists(),"HTTPS downgrade redirect rejected");
   mode=1;CloudClient.Job job=new CloudClient.Job();boolean cancelled=false;try{LocalModels.download(c,m,job,(s,n,t)->{if(s.equals("下载模型"))job.cancel();});}catch(CancellationException e){cancelled=true;}check(cancelled&&part.length()==512&&!target.exists()&&disconnected,"download cancellation retains resumable bytes");
   LocalModels.download(c,m,new CloudClient.Job(),progress);check(target.length()==512&&!part.exists(),"fully downloaded cancelled part can verify and install");
   System.out.println("LOCAL_MODEL_CHECKS: "+checks);
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
 }
}
