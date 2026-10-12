import org.openenglish.reader.PortableZip;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.*;

public class PortableZipTest {
  static int checks;
  static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);checks++;}
  static void fails(byte[] zip,String name)throws Exception{
    File dir=Files.createTempDirectory("portable-bad-").toFile();boolean failed=false;
    try{PortableZip.read(new ByteArrayInputStream(zip),dir,()->{},(n,f,b)->{});}catch(Exception e){failed=true;}
    finally{PortableZip.deleteTree(dir);}
    check(failed,name);
  }
  static byte[] raw(Map<String,byte[]> files)throws Exception{
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    try(ZipOutputStream zip=new ZipOutputStream(out)){
      for(Map.Entry<String,byte[]> e:files.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
    }return out.toByteArray();
  }
  static byte[] valid()throws Exception{
    Map<String,PortableZip.Source> files=new LinkedHashMap<>();
    files.put("learning.json",()->new ByteArrayInputStream("{\"text\":\"英语\\nword\"}".getBytes("UTF-8")));
    files.put("settings.json",()->new ByteArrayInputStream("{}".getBytes("UTF-8")));
    files.put("media/material-12.bin",()->new ByteArrayInputStream(new byte[]{0,1,-1,2,0}));
    files.put("studio-recordings/take-1.m4a",()->new ByteArrayInputStream(new byte[]{3,4,5}));
    files.put("recordings/sentence_59.m4a",()->new ByteArrayInputStream(new byte[]{6,7}));
    ByteArrayOutputStream out=new ByteArrayOutputStream();PortableZip.write(out,files,()->{},(n,f,b)->{});return out.toByteArray();
  }
  static Map<String,byte[]> entries(byte[] data)throws Exception{
    Map<String,byte[]> result=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;byte[] b=new byte[4096];while((e=zip.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(b))!=-1)out.write(b,0,n);result.put(e.getName(),out.toByteArray());}}return result;
  }
  public static void main(String[] args)throws Exception{
    byte[] data=valid();File dir=Files.createTempDirectory("portable-good-").toFile();
    Map<String,PortableZip.Receipt> receipt=PortableZip.read(new ByteArrayInputStream(data),dir,()->{},(n,f,b)->{});
    check(receipt.size()==6,"all files including manifest");
    check(Arrays.equals(Files.readAllBytes(new File(dir,"media/material-12.bin").toPath()),new byte[]{0,1,-1,2,0}),"binary media preserved");
    check(receipt.get("studio-recordings/take-1.m4a").size==3,"studio recording size");
    check(new File(dir,"recordings/sentence_59.m4a").isFile(),"legacy recording retained");
    check(new String(Files.readAllBytes(new File(dir,"learning.json").toPath()),"UTF-8").contains("英语"),"UTF-8 JSON preserved");
    boolean rejected=false;try{PortableZip.read(new ByteArrayInputStream(data),dir,()->{},(n,f,b)->{});}catch(IOException e){rejected=true;}check(rejected,"nonempty staging directory rejected");PortableZip.deleteTree(dir);
    Map<String,byte[]> base=entries(data);
    Map<String,byte[]> changed=new LinkedHashMap<>(base);changed.put("media/material-12.bin",new byte[]{8,1,-1,2,0});fails(raw(changed),"changed media checksum");
    changed=new LinkedHashMap<>(base);changed.remove("studio-recordings/take-1.m4a");fails(raw(changed),"missing manifested recording");
    changed=new LinkedHashMap<>(base);changed.put("media/material-13.bin",new byte[]{1});fails(raw(changed),"unlisted extra media");
    changed=new LinkedHashMap<>(base);changed.remove("manifest.tsv");fails(raw(changed),"missing manifest");
    changed=new LinkedHashMap<>(base);changed.put("manifest.tsv","OTHER-1\n".getBytes("UTF-8"));fails(raw(changed),"unknown format");
    for(String path:new String[]{"../outside","/absolute","media/../../outside","studio-recordings/../bad.m4a","media\\material-12.bin","secret.xml","media/material-0.bin"}){
      changed=new LinkedHashMap<>(base);changed.put(path,new byte[]{1});fails(raw(changed),"invalid path "+path);
    }
    changed=new LinkedHashMap<>(base);String manifest=new String(base.get("manifest.tsv"),"UTF-8");changed.put("manifest.tsv",(manifest+manifest.split("\n")[1]+"\n").getBytes("UTF-8"));fails(raw(changed),"duplicate manifest line");
    changed=new LinkedHashMap<>(base);changed.put("manifest.tsv",manifest.replace("EVERYONE-PORTABLE-1","EVERYONE-PORTABLE-2").getBytes("UTF-8"));fails(raw(changed),"future version rejected");
    byte[] duplicate=data.clone();byte[] a="settings.json".getBytes("US-ASCII"),b="learning.json".getBytes("US-ASCII");
    for(int i=0;i<=duplicate.length-a.length;i++){boolean match=true;for(int j=0;j<a.length;j++)if(duplicate[i+j]!=a[j])match=false;if(match)System.arraycopy(b,0,duplicate,i,b.length);}
    fails(duplicate,"duplicate ZIP entry");
    changed=new LinkedHashMap<>(base);changed.put("settings.json",new byte[65537]);fails(raw(changed),"settings expansion bounded");
    File cancelled=Files.createTempDirectory("portable-cancel-").toFile();boolean cancel=false;
    try{PortableZip.read(new ByteArrayInputStream(data),cancelled,()->{throw new CancellationException();},(n,f,nbytes)->{});}catch(CancellationException e){cancel=true;}finally{PortableZip.deleteTree(cancelled);}check(cancel,"restore cancellation");
    Map<String,PortableZip.Source> sources=new LinkedHashMap<>();sources.put("learning.json",()->new ByteArrayInputStream(new byte[]{1}));sources.put("settings.json",()->new ByteArrayInputStream(new byte[]{2}));
    cancel=false;try{PortableZip.write(new ByteArrayOutputStream(),sources,()->{throw new CancellationException();},(n,f,bytes)->{});}catch(CancellationException e){cancel=true;}check(cancel,"export cancellation");
    sources.put("media/material-1.bin",()->{throw new FileNotFoundException();});boolean missing=false;try{PortableZip.write(new ByteArrayOutputStream(),sources,()->{},(n,f,bytes)->{});}catch(FileNotFoundException e){missing=true;}check(missing,"unreadable referenced source fails export");
    Map<String,PortableZip.Source> huge=new LinkedHashMap<>();huge.put("learning.json",()->new ByteArrayInputStream(new byte[0]));huge.put("settings.json",()->new ByteArrayInputStream(new byte[0]));for(int i=1;i<=10000;i++)huge.put("media/material-"+i+".bin",()->new ByteArrayInputStream(new byte[0]));
    boolean count=false;try{PortableZip.write(new ByteArrayOutputStream(),huge,()->{},(n,f,bytes)->{});}catch(IOException e){count=true;}check(count,"entry-count bound");
    check(PortableZip.allowed("studio-recordings/r-abc-1.m4a"),"restored take naming accepted");
    check(!PortableZip.allowed("recordings/sentence_100000.m4a"),"legacy index bound");
    System.out.println("PASS: "+checks+" actual ZIP round-trip, integrity, bounds and cancellation checks");
  }
}
