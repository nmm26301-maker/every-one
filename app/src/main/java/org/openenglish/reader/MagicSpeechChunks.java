package org.openenglish.reader;
import java.util.*;
/** CJK streaming: hold tiny fragments, emit completed phrases, flush the final short reply. */
public final class MagicSpeechChunks {
 private int consumed;private String prefix="";public void clear(){consumed=0;prefix="";}
 public List<String> accept(String text,boolean done){List<String> out=new ArrayList<>();if(text==null||text.indexOf('\ufffd')>=0||!text.startsWith(prefix))return out;int from=consumed;boolean cjk=false;for(int i=from;i<text.length();i++){char c=text.charAt(i);if(c>0x2fff)cjk=true;int length=i-from+1;boolean end=c=='。'||c=='！'||c=='？'||c=='\n'||c=='.'||c=='!'||c=='?';boolean pause=c=='，'||c=='、'||c=='；';boolean decimal=c=='.'&&i>0&&Character.isDigit(text.charAt(i-1));boolean phrase=cjk?length>=3:text.substring(from,i+1).trim().split("\\s+").length>=2;if((end&&!decimal&&phrase)||(pause&&cjk&&length>=10)||(cjk&&length>=28&&!Character.isHighSurrogate(c))){emit(out,text,from,i+1);from=i+1;consumed=from;}}if(done){emit(out,text,from,text.length());consumed=text.length();}prefix=text.substring(0,consumed);return out;}
 private static void emit(List<String> out,String text,int a,int b){String s=text.substring(a,b).trim();if(!s.isEmpty())out.add(s);}
}
