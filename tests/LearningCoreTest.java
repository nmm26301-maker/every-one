import org.openenglish.reader.LearningCore;
import java.util.*;
public class LearningCoreTest {
 static int checks=0; static void check(boolean x,String s){checks++;if(!x)throw new AssertionError(s);}
 public static void main(String[] args){
  String s="\uFEFF2\r\n00:01:02,345 --> 00:01:04,900\r\nSecond\r\n第二句\r\n\r\n1\r\n00:00:02,000 --> 00:00:03,000\r\n<i>Hello</i>";
  List<LearningCore.Cue> c=LearningCore.parseSubtitles(s);check(c.size()==2,"SRT count");check(c.get(0).start==2000&&c.get(1).start==62345,"sorted milliseconds");check(c.get(0).text.equals("Hello"),"strip formatting");check(c.get(1).text.contains("第二句"),"multiline unicode");
  c=LearningCore.parseSubtitles("WEBVTT\n\n00:00.000 --> 00:04.000 align:start\nHello!\n\n00:04.000 --> 00:08.000\nWorld.");check(c.size()==2&&c.get(1).end==8000,"VTT short timestamps");
  try{LearningCore.parseSubtitles("Not a caption");throw new AssertionError("invalid subtitle accepted");}catch(IllegalArgumentException e){checks++;}
  check(LearningCore.textMatch("Hello, world!","hello world")==100,"punctuation case ignored");check(LearningCore.textMatch("I like coffee","I like tea")==67,"one incorrect word");check(LearningCore.textMatch("I like coffee","")==0,"empty recognized");check(LearningCore.textMatch("I'm learning.","I’m learning")==100,"curly apostrophe");check(LearningCore.textMatch("cat","cat cat cat")==33,"extra words penalized");
  check(LearningCore.nextInterval(0,true)==1,"first review");check(LearningCore.nextInterval(4,true)==8,"repeat review");check(LearningCore.nextInterval(64,true)==90,"cap review");check(LearningCore.nextInterval(8,false)==0,"forget reset");check(LearningCore.nextDue(1000,0)==601000,"retry 10 min");check(LearningCore.nextDue(1000,1)==86401000,"daily review");
  System.out.println("PASS: "+checks+" learning algorithm checks");
 }
}
