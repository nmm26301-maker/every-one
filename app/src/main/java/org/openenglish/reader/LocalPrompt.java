package org.openenglish.reader;
import java.util.*;
/** Small on-phone context, never sends model control tokens from quoted material verbatim. */
public final class LocalPrompt {
 private LocalPrompt() {}
 public static String safe(String text,int limit){String s=text==null?"":text;s=s.replace("<｜","〈｜").replace("<|","〈|");if(s.length()>limit){int end=limit;if(end>0 && Character.isHighSurrogate(s.charAt(end-1)))end--;s=s.substring(0,end)+"\n[内容已截短]";}return s;}
 public static String edges(String text,int limit){String s=safe(text,Integer.MAX_VALUE);if(s.length()<=limit)return s;int half=limit/2;return safe(s,half)+"\n[…]\n"+s.substring(s.length()-half+(Character.isLowSurrogate(s.charAt(s.length()-half))?1:0));}
 public static String build(String instruction,List<String[]> history){
  StringBuilder prompt=new StringBuilder("<｜begin▁of▁sentence｜>");int from=Math.max(0,history.size()-4);
  for(int i=from;i<history.size();i++){String[] message=history.get(i);boolean assistant="assistant".equals(message[0]);
   if(assistant){prompt.append("<｜Assistant｜>").append(edges(visible(message[1]),180)).append("<｜end▁of▁sentence｜>");}
   else {prompt.append("<｜User｜>");if(i==history.size()-1)prompt.append(safe(instruction,600)).append("\nInstruction applies to the following input. Input:\n");prompt.append(edges(message[1],i==history.size()-1?900:180));}
  }
  if(history.isEmpty() || "assistant".equals(history.get(history.size()-1)[0]))prompt.append("<｜User｜>").append(safe(instruction,600)).append("\nContinue the conversation briefly.");
  return prompt.append("<｜Assistant｜><think>\n</think>\n\n").toString();
 }
 public static String visible(String raw){
  String s=raw==null?"":raw;int start;
  while((start=s.indexOf("<think>"))>=0){int end=s.indexOf("</think>",start+7);s=end<0?s.substring(0,start):s.substring(0,start)+s.substring(end+8);}
  return s.replace("<｜end▁of▁sentence｜>","").replace("<｜Assistant｜>","").trim();
 }
}
