package org.openenglish.reader;
import java.util.*;

/** Shares stable-prefix and complete-word rules across English and multilingual voice chat. */
public final class StreamingSpeechChunks {
 private int consumed;
 private String prefix="";
 public void clear(){consumed=0;prefix="";}
 public List<String> accept(String text,boolean done){
  List<String> out=new ArrayList<>();
  if(text==null||text.indexOf('\ufffd')>=0||!text.startsWith(prefix))return out;
  int limit=text.length();
  // A cumulative native token callback can end halfway through a UTF-16 pair.
  if(limit>0&&Character.isHighSurrogate(text.charAt(limit-1)))limit--;
  int from=consumed;boolean cjk=false;
  for(int i=from;i<limit;i++){
   char c=text.charAt(i);cjk|=isCjk(c);
   boolean end=c=='!'||c=='?'||c=='\n'||c=='。'||c=='！'||c=='？';
   if(c=='.')end=periodEnds(text,i,limit,done);
   boolean boundary=end&&(i+1==limit||cjk||isCjk(text.charAt(i+1))||Character.isWhitespace(text.charAt(i+1))||c=='\n'||c>127||closing(text.charAt(i+1)));
   if(c=='\n'&&numberMarker(text.substring(from,i).trim()))boundary=false;
   int to=i+1;
   if(boundary){
    while(to<limit&&closing(text.charAt(to)))to++;
    emit(out,text,from,to);from=to;consumed=to;i=to-1;cjk=false;
   }else if(cjk&&((c=='，'||c=='、'||c=='；')&&i-from+1>=10||i-from+1>=28&&!Character.isHighSurrogate(c))){
    emit(out,text,from,to);from=to;consumed=to;cjk=false;
   }else if(!cjk&&Character.isWhitespace(c)&&(i-from>=72||consumed==0&&i-from>=38)){
    emit(out,text,from,i);from=to;consumed=to;
   }
  }
  if(done){emit(out,text,from,limit);consumed=limit;}
  prefix=text.substring(0,consumed);return out;
 }
 private static boolean periodEnds(String s,int at,int limit,boolean done){
  if(at+1<limit&&s.charAt(at+1)=='.')return false;
  if(at>0&&Character.isDigit(s.charAt(at-1))){
   if(at+1<limit&&Character.isDigit(s.charAt(at+1)))return false;
   if(at+1==limit&&!done)return false;
  }
  int line=s.lastIndexOf('\n',Math.max(0,at-1))+1;
  if((at+1==limit||Character.isWhitespace(s.charAt(at+1)))&&numberMarker(s.substring(line,at+1).trim()))return false;
  int start=at;while(start>0&&(Character.isLetter(s.charAt(start-1))||s.charAt(start-1)=='.'))start--;
  String token=s.substring(start,at+1).toLowerCase(Locale.ROOT);
  if(token.equals("mr.")||token.equals("mrs.")||token.equals("ms.")||token.equals("dr.")||token.equals("prof.")||token.equals("sr.")||token.equals("jr.")||token.equals("st.")||token.equals("e.g.")||token.equals("i.e.")||token.equals("a.m.")||token.equals("p.m."))return false;
  if(token.length()==2&&Character.isLetter(token.charAt(0)))return false;
  if(token.matches("(?:[a-z]\\.){2,}"))return false;
  return true;
 }
 private static boolean numberMarker(String s){return s.matches("(?:[>#*\\-]\\s*)*\\d{1,3}\\.");}
 private static boolean closing(char c){return c=='"'||c=='\''||c=='”'||c=='’'||c=='」'||c=='』'||c==')'||c==']'||c=='*'||c=='_'||c=='`';}
 private static boolean isCjk(char c){return c>=0x3040&&c<=0x30ff||c>=0x3400&&c<=0x9fff||c>=0xac00&&c<=0xd7af;}
 private static void emit(List<String> out,String s,int a,int b){
  String chunk=s.substring(a,b).trim();boolean content=false;
  for(int i=0;i<chunk.length();i++){char c=chunk.charAt(i);if(Character.isLetterOrDigit(c)||Character.isSurrogate(c)){content=true;break;}}
  if(content)out.add(chunk);
 }
}
