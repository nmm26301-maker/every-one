import org.openenglish.reader.*;import android.content.*;import android.os.Build;import org.json.*;import java.util.*;import java.nio.file.*;import java.io.*;import java.util.concurrent.*;
public final class IslandCompletionTest {
 static int checks;static void check(boolean v,String s){if(!v)throw new AssertionError(s);checks++;System.out.println("PASS "+s);}
 public static void main(String[] args)throws Exception {
  check(TranscriptCheck.compare("您好，我要一杯咖啡。","您好 我要一杯咖啡!").same,"punctuation and spaces do not become pronunciation errors");
  check(TranscriptCheck.compare("ＡＢＣ１２３","abc123").same,"full width letters and digits normalize");
  check(!TranscriptCheck.suitable("試")&&TranscriptCheck.suitable("こんにちは！"),"single syllables excluded but full Japanese phrase allowed");
  TranscriptCheck.Result r=TranscriptCheck.compare("我想喝咖啡","我想喝茶");check(r.missing.equals("咖啡")&&r.extra.equals("茶"),"substitution preserves expected and recognized spans");
  r=TranscriptCheck.compare("一二三四五","一三五");check(r.missing.equals("二四")&&r.extra.isEmpty(),"deletions retained in spoken order");
  r=TranscriptCheck.compare("一三五","一二三四五");check(r.extra.equals("二四")&&r.missing.isEmpty(),"extra text retained in spoken order");
  r=TranscriptCheck.compare("日本語","ニホンゴ");check(!r.same&&r.message("ja").contains("写法"),"Japanese alternative orthography not claimed as wrong pronunciation");
  check(TranscriptCheck.compare("凍奶茶","冻奶茶").message("yue").contains("不代表你读错"),"Cantonese mismatch has ASR warning");
  check(TranscriptCheck.compare("我想要咖啡","我想要咖啡").message("zh").contains("不代表发音完全正确"),"matching transcript not interpreted as phoneme accuracy");
  check(TranscriptCheck.compare("你好呀","！？").empty,"non lexical hallucination produces no matching text");
  String rare="\uD840\uDC00";check(TranscriptCheck.compare(rare+"你好",rare+"您好").missing.equals("你"),"alignment keeps supplementary Unicode intact");
  char[] longChars=new char[4000];Arrays.fill(longChars,'字');String huge=new String(longChars);check(TranscriptCheck.normalize(huge).length()==160,"alignment memory is bounded for long model output");
  check(IslandScene.parse("日常聊天")==null&&IslandScene.parse("magic:")==null&&IslandScene.parse("magic:xx:聊天")==null,"invalid scene metadata is rejected before navigation");
  check(IslandScene.parse("magic:yue:茶餐厅:少甜").scene.equals("茶餐厅:少甜"),"colon inside scene survives parsing");
  check(IslandScene.parse("magic:ja:こんにちは").language.equals("ja")&&IslandScene.parse("magic:zh:你好").language.equals("zh"),"language derives from actual topic metadata");
  check(IslandScene.parse("magic:yue:  ")==null&&IslandScene.parse("magic:yue:"+huge)==null,"empty and oversized scenes rejected");
  File root=Files.createTempDirectory("completion44-").toFile();Context c=new Context(root,root);IslandMemory y=new IslandMemory(c,"yue"),ja=new IslandMemory(c,"ja");y.note("慢一点");y.complete("我想點奶茶","少甜定正常甜呀？","茶餐厅");y.complete("車站喺邊","一直行","问路");y.complete("少甜","要冰嗎？","茶餐厅");ja.complete("こーひー","ほっとですか？","喫茶店");
  check(y.scenes().equals(Arrays.asList("茶餐厅","问路")),"scene browser deduplicates in newest first order");
  check(y.search("少甜",null).length()==2,"search covers learner and teacher utterances");
  check(y.search("一直",null).length()==1,"teacher-only words are searchable");
  check(y.search("茶餐厅",null).length()==2,"scene title searchable");
  check(y.search("", "问路").length()==1&&y.search("少甜","问路").length()==0,"exact scene filter isolates other conversations");
  check(y.search("こーひー",null).length()==0,"browser keeps languages isolated");
  check(y.search(" ",null).length()==3&&y.search("不存在",null).length()==0,"empty query lists completed pairs and absent query yields empty state");
  check(y.search("",null).getJSONObject(0).getString("user").equals("少甜"),"result order is newest first");
  check(new IslandMemory(new Context(root,root),"yue").search("奶茶",null).length()==1,"search reopens existing durable memory");
  y.clearScene("茶餐厅");check(y.search("少甜",null).length()==0&&y.scenes().equals(Arrays.asList("问路"))&&y.note().equals("慢一点"),"deletion removes searchable scene memory but preserves preferences");
  for(int i=0;i<305;i++)y.complete("HELLO "+i,"回复","聊天");check(y.search("hello",null).length()==300&&y.search("hello",null).getJSONObject(0).getString("user").equals("HELLO 304"),"case insensitive search retains bounded newest 300 pairs");
  Build.SUPPORTED_ABIS=new String[]{"arm64-v8a"};LocalModels.installed=false;float[] pcm=new float[16000];Arrays.fill(pcm,.02f);LocalNative.recognized="我想飲凍奶茶";
  check(LocalAI.islandTranscribe(c,pcm,"yue",new CloudClient.Job()).equals("我想飲凍奶茶")&&LocalNative.lastLanguage.equals("zh"),"production Cantonese check uses multilingual Whisper without installed teacher");
  LocalNative.recognized="こんにちは";LocalAI.islandTranscribe(c,pcm,"ja",new CloudClient.Job());check(LocalNative.lastLanguage.equals("ja"),"Japanese check routes language explicitly");
  LocalAI.islandTranscribe(c,pcm,"zh",new CloudClient.Job());check(LocalNative.lastLanguage.equals("zh"),"Mandarin check routes multilingual Chinese model");
  boolean failed=false;try{LocalAI.islandTranscribe(c,new float[16000],"zh",new CloudClient.Job());}catch(IOException e){failed=true;}check(failed,"silent recording is not scored as matching content");
  int calls=LocalNative.decode;CloudClient.Job job=new CloudClient.Job();job.cancel();failed=false;try{LocalAI.islandTranscribe(c,pcm,"ja",job);}catch(CancellationException e){failed=true;}check(failed&&LocalNative.decode==calls,"cancelled check cannot decode retained audio");
  LocalNative.recognized="  ";failed=false;try{LocalAI.islandTranscribe(c,pcm,"zh",new CloudClient.Job());}catch(IOException e){failed=true;}check(failed,"empty recognition becomes retryable failure");
  System.out.println("COMPLETION_CHECKS: "+checks);
 }
}
