package org.openenglish.reader;
import java.text.Normalizer;
import java.util.*;
/** Text alignment only. ASR agreement cannot establish phoneme or tone accuracy. */
public final class TranscriptCheck {
 private TranscriptCheck(){}
 public static String normalize(String text){
  String s=Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  StringBuilder out=new StringBuilder();int count=0;
  for(int i=0;i<s.length()&&count<160;){int cp=s.codePointAt(i);i+=Character.charCount(cp);if(Character.isLetterOrDigit(cp)){out.appendCodePoint(cp);count++;}}
  return out.toString();
 }
 private static int[] points(String s){int[] p=new int[s.codePointCount(0,s.length())];int n=0;for(int i=0;i<s.length();){int cp=s.codePointAt(i);p[n++]=cp;i+=Character.charCount(cp);}return p;}
 public static boolean suitable(String expected){return points(normalize(expected)).length>=4;}
 public static final class Result {
  public final boolean same,empty;public final String missing,extra;
  Result(boolean s,boolean e,String m,String x){same=s;empty=e;missing=m;extra=x;}
  public String message(String lang){
   String warning="文字核对不是发音评分；识别和字形差异可能造成不匹配。";
   if("yue".equals(lang))warning="粤语可能被转成普通话字样；不匹配不代表你读错。";
   if("ja".equals(lang))warning="假名与汉字写法可能不同；不匹配不代表你读错。";
   if(empty)return "没有得到可核对的文字。请听录音，安静一点再试。\n"+warning;
   if(same)return "识别字样与课程一致 ✦\n再听听自己的声调、节奏和口型；字样一致不代表发音完全正确。\n"+warning;
   StringBuilder b=new StringBuilder("有些字样没有对上，先对照听一遍。\n");
   if(!missing.isEmpty())b.append("课程中未匹配：").append(missing).append('\n');
   if(!extra.isEmpty())b.append("识别中不同的字样：").append(extra).append('\n');
   return b.append(warning).toString();
  }
 }
 public static Result compare(String expected,String heard){
  int[] a=points(normalize(expected)),b=points(normalize(heard));int[][] d=new int[a.length+1][b.length+1];
  for(int i=0;i<=a.length;i++)d[i][0]=i;for(int j=0;j<=b.length;j++)d[0][j]=j;
  for(int i=1;i<=a.length;i++)for(int j=1;j<=b.length;j++)d[i][j]=Math.min(d[i-1][j]+1,Math.min(d[i][j-1]+1,d[i-1][j-1]+(a[i-1]==b[j-1]?0:1)));
  List<Integer> missing=new ArrayList<>(),extra=new ArrayList<>();int i=a.length,j=b.length;
  while(i>0||j>0){if(i>0&&j>0&&d[i][j]==d[i-1][j-1]+(a[i-1]==b[j-1]?0:1)){if(a[i-1]!=b[j-1]){missing.add(a[i-1]);extra.add(b[j-1]);}i--;j--;}
   else if(i>0&&d[i][j]==d[i-1][j]+1){missing.add(a[--i]);}else extra.add(b[--j]);}
  Collections.reverse(missing);Collections.reverse(extra);StringBuilder m=new StringBuilder(),x=new StringBuilder();for(int cp:missing)m.appendCodePoint(cp);for(int cp:extra)x.appendCodePoint(cp);
  return new Result(d[a.length][b.length]==0,b.length==0,m.toString(),x.toString());
 }
}
