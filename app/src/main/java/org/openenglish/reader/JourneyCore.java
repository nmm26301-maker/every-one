package org.openenglish.reader;

import java.util.*;

/** Platform independent learning rules. Recognition agreement is never a pronunciation score. */
public final class JourneyCore {
 public static final long DAY=86400000L;
 public static final int[] INTERVALS={0,1,3,7,14,30};
 public static int nextStage(int old, boolean recalled, boolean due) {
  if(!recalled)return 0;
  return due?Math.min(5,Math.max(0,old)+1):Math.max(0,Math.min(5,old));
 }
 public static long nextDue(long now,int stage){return now+(stage==0?10*60000L:INTERVALS[Math.min(5,Math.max(0,stage))]*DAY);}
 public static String[] words(String text){
  String s=text.toLowerCase(Locale.US).replace('’','\'').replaceAll("[^a-z0-9']+"," ").trim();
  return s.isEmpty()?new String[0]:s.split(" +");
 }
 public static String difference(String expected,String heard){
  String[] a=words(expected),b=words(heard);
  if(a.length>100||b.length>150)return "内容较长，请分句练习。";
  if(b.length==0)return "没有识别到有效英文。请检查麦克风、环境和模型后重试。";
  int[][] d=new int[a.length+1][b.length+1];
  for(int i=0;i<=a.length;i++)d[i][0]=i;
  for(int j=0;j<=b.length;j++)d[0][j]=j;
  for(int i=1;i<=a.length;i++)for(int j=1;j<=b.length;j++)d[i][j]=Math.min(d[i-1][j]+1,Math.min(d[i][j-1]+1,d[i-1][j-1]+(a[i-1].equals(b[j-1])?0:1)));
  if(d[a.length][b.length]==0)return "识别文本与目标一致。它不代表音素发音正确；请回听，或启用音素评测。";
  List<String> out=new ArrayList<>();int i=a.length,j=b.length;
  while(i>0||j>0){
   if(i>0&&j>0&&d[i][j]==d[i-1][j-1]+(a[i-1].equals(b[j-1])?0:1)){
    if(!a[i-1].equals(b[j-1]))out.add("目标 "+a[i-1]+" → 识别为 "+b[j-1]);i--;j--;
   }else if(i>0&&d[i][j]==d[i-1][j]+1){out.add("未识别到："+a[--i]);}
   else out.add("额外识别："+b[--j]);
  }
  Collections.reverse(out);StringBuilder s=new StringBuilder("识别对照（可能包含识别错误，不是发音诊断）\n");
  for(String line:out)s.append("• ").append(line).append('\n');return s.toString();
 }
 public static String phoneTip(String phone){
  String p=phone.toLowerCase(Locale.US).replaceAll("[012ˈˌ]","");
  switch(p){
   case "th":case "θ":return "舌尖轻放上下齿间，持续送气，不振动声带。think / sink；不要用 s 代替。";
   case "dh":case "ð":return "舌尖轻触齿间，气流通过时声带振动。then / den；不要直接用 d 代替。";
   case "r":case "ɹ":return "舌尖不碰上齿龈，舌身略后收，双唇轻圆。right / light。";
   case "l":return "舌尖接触上齿龈，气流从舌两侧通过。light / right。";
   case "v":return "上齿轻触下唇，送气并振动声带；不要双唇闭合。very / berry。";
   case "w":return "双唇收圆再迅速展开，不用牙齿咬下唇。west / vest。";
   case "ih":case "ɪ":return "舌位较高但放松，元音短而松。ship / sheep；不是单纯把 ee 缩短。";
   case "iy":case "i":case "iː":return "舌身向前抬高，双唇自然展开。sheep / ship。";
   case "ae":case "æ":return "下巴打开，舌位靠前且低。bad / bed；不要读成 e。";
   case "eh":case "ɛ":return "舌位在口腔前部中间，开口比 æ 小。bed / bad。";
   case "uw":case "u":case "uː":return "双唇收圆，舌后部抬高。food / foot。";
   case "uh":case "ʊ":return "双唇略圆，舌头比 u 更放松。foot / food。";
   case "ah":case "ʌ":return "下巴自然打开，舌头放在口腔中央。cup / cap。";
   case "ax":case "ə":return "非重读音节里放松口腔，短促轻读；不要每个音节都用力。about。";
   case "er":case "ɝ":case "ɚ":return "舌身略后收、两侧抬起，不要额外加一个元音。bird / word。";
   case "ng":case "ŋ":return "舌后部接近软腭，声音从鼻腔通过，词尾不要额外加 g。sing。";
   case "sh":case "ʃ":return "双唇略圆，舌头不碰上腭，持续送气。she / see。";
   case "zh":case "ʒ":return "保持类似 sh 的口形，同时振动声带。vision。";
   case "ch":case "tʃ":return "先短暂阻气再释放为 sh，连成一个音。cheap / sheep。";
   case "jh":case "dʒ":return "先阻气再释放，声带振动。jeep / cheap。";
   case "p":case "t":case "k":return "先形成阻碍再释放；词尾保持收尾动作，不要多加一个‘呃’。词尾可不爆破。";
   case "b":case "d":case "g":return "保持清楚的闭塞和收尾；浊音不需要夸张爆破，词尾不要添加元音。";
   case "s":case "z":return "舌尖靠近齿龈，保持细气流；s 不振动声带，z 振动。sip / zip。";
   default:return "先听单词示范，再慢速分音节模仿，最后放回整句自然说。评测受口音、噪声与设备影响。";
  }
 }
 private JourneyCore(){}
}
