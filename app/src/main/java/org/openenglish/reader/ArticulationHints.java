package org.openenglish.reader;
import java.text.Normalizer;
import java.util.Locale;
/** Authored practice actions. Not machine-detected errors or a substitute for a teacher. */
public final class ArticulationHints {
 private ArticulationHints(){}
 public static String forUnit(String lang,String label,String fallback){
  String base=Normalizer.normalize(label,Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[1-6]$","").toLowerCase(Locale.ROOT);
  if(lang.equals("ja")){
   if(label.contains("・"))return "保持同一个元音，连着读两拍；手指轻点两下，中间不换气。";
   if(base.equals("t"))return "舌尖先准备 t，留出无新元音的一拍，再接 ta；轻点三拍 ki・っ・ta。";
   if(base.equals("n"))return "ん也占一拍。跟着示范自然收在鼻音，不要吞掉这一拍。";
   if(base.equals("a"))return "嘴自然打开，舌头放松；短音一拍读完。";
   if(base.equals("i"))return "嘴角轻轻展开，舌位较高，不要把声音挤在喉咙里。";
   if(base.equals("u"))return "双唇稍收，避免用力突出嘴唇；短音轻轻读完。";
   if(base.equals("e"))return "嘴稍张，保持一个清楚的元音，不拖成 ei。";
   if(base.equals("o"))return "口腔圆一点，保持一个元音，不拖成 ou。";
   return "这一段留一拍，先按示范慢读，再放回整句。\n"+fallback;
  }
  String action="";
  if(base.startsWith("ng")||base.equals("m"))action="先轻哼鼻音，再接后面的元音；不用多加一个字。";
  else if(base.startsWith("n"))action="舌尖抵上齿龈，气流从鼻腔出来；先轻哼 n，再接元音。";
  else if(base.startsWith("l"))action="舌尖抵上齿龈，气流从舌两侧出来；轻读 l，再连上元音。";
  else if(lang.equals("zh")&&(base.startsWith("zh")||base.startsWith("ch")||base.startsWith("sh")))action="舌尖稍抬向上齿龈后方，不必用力卷舌。"+(base.startsWith("ch")?"轻送气，再接元音。":"保持自然，不要把舌头顶得过紧。");
  else if(base.startsWith("s")||base.startsWith("z")||base.startsWith("c"))action="舌尖靠近下齿后，气流在前方。"+((base.startsWith("c"))?"留意送气，不用大声喊。":"先慢读，保持清楚的气流。");
  else if(base.startsWith("b")||base.startsWith("p"))action="双唇先轻轻闭合，再放开。"+(base.startsWith("p")?"让气流轻轻吹动纸巾。":"不用刻意吹出很强的气流。");
  else if(base.startsWith("d")||base.startsWith("t"))action="舌尖轻触上齿龈，再放开接元音。"+(base.startsWith("t")?"有轻轻的送气。":"不要多加一拍。");
  else if(base.startsWith("g")||base.startsWith("k"))action="舌后部轻抬后放开，接上元音；别把起音挤在喉咙里。";
  if(lang.equals("yue")&&base.matches(".*[ptk]$"))action+="\n入声结尾在口腔收住，不额外爆破出一个元音，也不要多加一拍。";
  String tone="";
  if(lang.equals("yue")&&label.matches(".*[1-6]$")){
   int number=label.charAt(label.length()-1)-'1';tone=new String[]{"高平调：比舒服的中音稍高，保持平稳，不用喊。","高升调：从中低处轻轻向上走。","中平调：用舒服的中音，稳稳读完。","低降调：低处起音，轻轻往下走，别压嗓子。","低升调：从低处轻轻升到中音。","低平调：比中音低一点，仍保持清楚，不压嗓子。"}[number];
  }else if(lang.equals("zh")){
   String accent=Normalizer.normalize(label,Normalizer.Form.NFD);tone=accent.contains("\u0304")?"一声：高处平稳读完，不需要用力喊。":accent.contains("\u0301")?"二声：从较低处自然向上走。":accent.contains("\u030c")?"三声：这段先对照示范，低处转折；放回词句时也留意变调。":accent.contains("\u0300")?"四声：起点较高，干脆向下落，不拖成上扬。":"轻声：放回词句，轻一点、短一点；先听示范。";
  }
  if(action.isEmpty()&&tone.isEmpty())return fallback;
  return (tone.isEmpty()?"":tone+"\n")+action+"\n先对照听这一段，再放回整句；这些是练习动作，不表示已经检测到此处读错。";
 }
}
