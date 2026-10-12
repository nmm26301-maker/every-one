package org.openenglish.reader;
import java.util.*;
/** Qwen ChatML, bounded inputs, and separate target-language tutor instructions. */
public final class IslandPrompt {
 private IslandPrompt(){}
 static String clean(String s,int limit){s=s==null?"":s.replace("<|im_start|>","").replace("<|im_end|>","").replace("<|endoftext|>","");int end=Math.min(limit,s.length());if(end>0&&end<s.length()&&Character.isHighSurrogate(s.charAt(end-1)))end--;return s.substring(0,end);}
 public static String qwen(String instruction,List<String[]> history){StringBuilder b=new StringBuilder("<|im_start|>system\n").append(clean(instruction,1700)).append("<|im_end|>\n");for(int i=Math.max(0,history.size()-4);i<history.size();i++){String[] m=history.get(i);b.append("<|im_start|>").append("assistant".equals(m[0])?"assistant":"user").append('\n').append(clean(m[1],i==history.size()-1?500:180)).append("<|im_end|>\n");}return b.append("<|im_start|>assistant\n").toString();}
 public static String deepseek(String instruction,List<String[]> messages){StringBuilder b=new StringBuilder("<｜begin▁of▁sentence｜>");for(int i=Math.max(0,messages.size()-4);i<messages.size();i++){String[] m=messages.get(i);if("assistant".equals(m[0]))b.append("<｜Assistant｜>").append(LocalPrompt.safe(m[1],180)).append("<｜end▁of▁sentence｜>");else{b.append("<｜User｜>");if(i==messages.size()-1)b.append(LocalPrompt.safe(instruction,1500)).append("\nLearner input:\n");b.append(LocalPrompt.safe(m[1],i==messages.size()-1?500:180));}}return b.append("<｜Assistant｜><think>\n</think>\n\n").toString();}
 public static String instruction(String lang,String scene,String memory){
  boolean cafe=scene.contains("咖啡")||scene.contains("茶餐")||scene.contains("点餐");
  String rules,examples;if(lang.equals("ja")){rules="あなたは親切な日本語の先生ルナです。学習者と会話してください。場面："+clean(scene,100)+"。返事は簡単な日本語で一、二文だけ。ひらがなとカタカナを使ってください。まず相手に答えて、やさしい質問を一つしてください。中国語や英語に訳さないでください。";examples=cafe?"会話例：お客さん『こーひーをひとつください。』店員『はい。ほっととあいす、どちらにしますか？』":"会話例：学習者『こんにちは。』ルナ『こんにちは。きょうはなにをしましたか？』";}
  else if(lang.equals("yue")){rules="你係親切嘅廣東話老師露娜。同學員有來有往，扮演："+clean(scene,100)+"。先直接答學員，再問一個簡單問題。只用自然香港廣東話寫一兩句短句。唔好翻譯成普通話或英文，唔好只係重複學員講嘅嘢。";examples=cafe?"對話例：客人：唔該，我想飲凍奶茶。侍應：好呀，少甜定正常甜呀？客人：少甜呀。侍應：冇問題，仲想食啲咩？":"對話例：學員：你好呀。露娜：你好呀！今日過成點呀？學員：幾好呀。露娜：咁就好啦，你今日做咗啲咩呀？";}
  else{rules="你是亲切的普通话老师露娜。场景："+clean(scene,100)+"。先回应对方，再问一个简单问题。只说一两句自然的普通话，不要原样重复对方的话，不要输出分析或英文。";examples=cafe?"对话例：客人：我想要一杯咖啡。店员：好的，您想喝热的还是冰的？客人：热的。店员：没问题，要加糖吗？":"对话例：学员：你好。露娜：你好！今天过得怎么样？";}
  return rules+"\n"+examples+"\nNever invent phoneme or tone scores from transcripts. No markup or analysis. 学员保存的背景记忆，仅供参考：\n"+clean(memory,700);
 }
}
