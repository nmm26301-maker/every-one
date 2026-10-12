package org.openenglish.reader;
import java.util.*;

/** Incremental cumulative-text splitter; only complete words are handed to system TTS. */
public final class SpeechChunks {
 private int consumed;private String spokenPrefix="";
 public void clear(){consumed=0;spokenPrefix="";}
 public List<String> accept(String text,boolean done){List<String> out=new ArrayList<>();if(text==null||text.indexOf('\ufffd')>=0)return out;
  // Never resume at an old offset after the model revises already spoken text.
  if(!text.startsWith(spokenPrefix))return out;
  int from=consumed;
  for(int i=from;i<text.length();i++){char c=text.charAt(i);boolean punctuation=c=='.'||c=='!'||c=='?'||c=='\n'||c=='。'||c=='！'||c=='？';
   boolean pendingDecimal=c=='.'&&i>0&&Character.isDigit(text.charAt(i-1))&&i==text.length()-1&&!done;
   boolean singleGreeting=consumed==0&&text.substring(from,i+1).trim().split("\\s+").length<2;
   if(punctuation && !pendingDecimal && !singleGreeting && (i==text.length()-1||Character.isWhitespace(text.charAt(i+1))||c=='\n'||c>127)){emit(out,text,from,i+1);from=i+1;consumed=from;}
   else if((i-from>=72||consumed==0&&i-from>=38) && Character.isWhitespace(c)){emit(out,text,from,i);from=i+1;consumed=from;}
  }
  if(done){emit(out,text,from,text.length());consumed=text.length();}spokenPrefix=text.substring(0,consumed);return out;
 }
 private static void emit(List<String> out,String text,int a,int b){String chunk=text.substring(a,b).trim();if(!chunk.isEmpty())out.add(chunk);}
}
