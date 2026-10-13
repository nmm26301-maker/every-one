import org.openenglish.reader.*;
import java.util.*;
public final class SpeechStreamingTest {
 static int checks;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static String joined(List<String> chunks){return String.join(" ",chunks).replaceAll("\\s+"," ").trim();}
 public static void main(String[] args){
  StreamingSpeechChunks s=new StreamingSpeechChunks();
  check(s.accept("Hello!",false).equals(Arrays.asList("Hello!")),"short greeting speaks before completion");
  check(s.accept("Hello! How are you?",false).equals(Arrays.asList("How are you?")),"next sentence follows without repeating greeting");
  check(s.accept("Hello! How are you?",true).isEmpty(),"final callback does not repeat spoken sentences");
  s.clear();check(s.accept("It costs 3.",false).isEmpty(),"pending decimal waits for next token");
  check(s.accept("It costs 3.50 dollars.",false).equals(Arrays.asList("It costs 3.50 dollars.")),"decimal is spoken as a single number");
  s.clear();check(s.accept("There are 2.",true).equals(Arrays.asList("There are 2.")),"final integer sentence flushes");
  for(String title:Arrays.asList("Dr.","Mr.","Ms.","Prof.")){
   s.clear();check(s.accept("Ask "+title,false).isEmpty(),"streamed title stays with name: "+title);
   check(s.accept("Ask "+title+" Smith.",false).equals(Arrays.asList("Ask "+title+" Smith.")),"complete name sentence stays together: "+title);
  }
  s.clear();check(s.accept("Use e.g. a short reply.",false).equals(Arrays.asList("Use e.g. a short reply.")),"example abbreviation does not split a phrase");
  s.clear();check(s.accept("Meet J. Smith in the U.S. today.",false).equals(Arrays.asList("Meet J. Smith in the U.S. today.")),"initials and country abbreviation stay in sentence");
  s.clear();check(s.accept("Visit example.com today.",false).equals(Arrays.asList("Visit example.com today.")),"domain period does not split a sentence");
  s.clear();check(s.accept("She said \"Hello!\" Then left.",true).equals(Arrays.asList("She said \"Hello!\"","Then left.")),"closing quote remains with sentence");
  s.clear();check(s.accept("Wait... Please continue.",true).equals(Arrays.asList("Wait...","Please continue.")),"ellipsis does not create empty speech fragments");
  s.clear();check(s.accept("### ... !!!",true).isEmpty(),"punctuation-only content is not sent to speech engine");
  s.clear();check(s.accept("Hello \ud83d",false).isEmpty(),"incomplete surrogate is not handed to TTS");
  check(s.accept("Hello 😀!",true).equals(Arrays.asList("Hello 😀!")),"completed emoji is preserved");
  s.clear();check(s.accept("bad\ufffd",false).isEmpty(),"incomplete native text is ignored");
  s.clear();s.accept("Hello there.",false);check(s.accept("Revised first sentence. Other words",true).isEmpty(),"model revision cannot replay an unrelated suffix");
  check(s.accept("Hello there. Stable ending",true).equals(Arrays.asList("Stable ending")),"stable prefix resumes after revision");
  String english="This is a long English reply with enough complete words to begin speaking before the rest of this reply is generated";
  MagicSpeechChunks magic=new MagicSpeechChunks();List<String> early=magic.accept(english,false);
  check(!early.isEmpty()&&early.get(0).length()>=38,"multilingual chat starts long English before final punctuation");
  List<String> all=new ArrayList<>(early);all.addAll(magic.accept(english,true));check(joined(all).equals(english),"long English preserves every word exactly once");
  check(magic.accept(english,true).isEmpty(),"multilingual completion remains idempotent");
  String cjk="我们可以先从今天的天气聊起然后慢慢练习更多日常表达不要担心说得慢只要每天坚持就会进步";
  magic.clear();early=magic.accept(cjk,false);check(!early.isEmpty()&&early.get(0).length()==28,"long Chinese begins before punctuation arrives");
  all=new ArrayList<>(early);all.addAll(magic.accept(cjk,true));check(String.join("",all).equals(cjk),"Chinese streaming neither loses nor repeats characters");
  magic.clear();check(magic.accept("好的。下一句？",false).equals(Arrays.asList("好的。","下一句？")),"short Chinese sentences speak promptly");
  s.clear();List<String> tokens=new ArrayList<>();for(int i=1;i<=english.length();i++)tokens.addAll(s.accept(english.substring(0,i),false));tokens.addAll(s.accept(english,true));check(joined(tokens).equals(english),"character-at-a-time cumulative stream preserves all words");
  s.clear();String word="abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";check(s.accept(word,false).isEmpty(),"long English word is never cut in half");check(s.accept(word,true).equals(Arrays.asList(word)),"long final word flushes intact");
  s.clear();check(s.accept("你好!我们一起练习。",false).equals(Arrays.asList("你好!","我们一起练习。")),"ASCII punctuation ends a Chinese phrase without spaces");
  s.clear();check(s.accept("Hello!你好。",false).equals(Arrays.asList("Hello!","你好。")),"English can hand off immediately to Chinese text");
  s.clear();check(s.accept("**Hello!** How are you?",false).equals(Arrays.asList("**Hello!**","How are you?")),"bold formatting does not delay a finished greeting");
  s.clear();check(s.accept("`Hello!` Next sentence.",false).equals(Arrays.asList("`Hello!`","Next sentence.")),"inline formatting stays with completed phrase");
  s.clear();check(s.accept("1. ",false).isEmpty(),"streamed numbered marker waits for spoken content");
  check(s.accept("1. Say hello.",false).equals(Arrays.asList("1. Say hello.")),"number stays with its instruction");
  s.clear();check(s.accept("1.\n",false).isEmpty(),"line break alone does not speak a numbered marker");
  check(s.accept("1.\nSay hello.",false).equals(Arrays.asList("1.\nSay hello.")),"split-line instruction retains its number once");
  s.clear();check(s.accept("It is 2. Next item.",false).equals(Arrays.asList("It is 2.","Next item.")),"integer sentence still ends normally");
  String mixedWord="abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
  s.clear();check(s.accept("中文 "+mixedWord,false).isEmpty(),"Chinese prefix cannot force an unfinished English word into TTS");
  check(s.accept("中文 "+mixedWord,true).equals(Arrays.asList("中文 "+mixedWord)),"final mixed-language long word flushes intact");
  for(String mixed:Arrays.asList("中文 "+mixedWord+" next word.","An introduction 中文 "+mixedWord+" then continue.","日本語 "+mixedWord+" 続けましょう。","한국어 😀 "+mixedWord+" finish!")){
   magic.clear();List<String> pieces=new ArrayList<>();
   for(int i=1;i<=mixed.length();i++)pieces.addAll(magic.accept(mixed.substring(0,i),false));
   pieces.addAll(magic.accept(mixed,true));
   check(pieces.stream().anyMatch(p->p.contains(mixedWord)),"mixed cumulative tokens keep the whole Latin word: "+mixed.substring(0,4));
   check(String.join("",pieces).replaceAll("\\s+","").equals(mixed.replaceAll("\\s+","")),"mixed cumulative tokens preserve all text exactly once: "+mixed.substring(0,4));
  }
  check(magic.accept("한국어 😀 "+mixedWord+" finish!",true).isEmpty(),"mixed completion remains idempotent");
  magic.clear();check(magic.accept(cjk,false).get(0).length()==28,"mixed-word protection preserves prompt CJK chunking");
  System.out.println("SPEECH_STREAM_CHECKS: "+checks);
 }
}
