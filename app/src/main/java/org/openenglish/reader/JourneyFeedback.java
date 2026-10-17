package org.openenglish.reader;
import org.json.*;
import java.util.*;

/** Only reports scores actually returned by the audio assessment provider. */
public final class JourneyFeedback {
 public final String text;public final boolean usable;public final double accuracy;
 private JourneyFeedback(String t,boolean u,double a){text=t;usable=u;accuracy=a;}
 private static double score(JSONObject o,String name){if(o==null||!o.has(name))return Double.NaN;double n=o.optDouble(name,Double.NaN);return n>=0&&n<=100?n:Double.NaN;}
 private static String label(JSONObject o,String key,String label){double n=score(o,key);return Double.isNaN(n)?"":label+" "+Math.round(n)+" / 100\n";}
 public static JourneyFeedback parse(JSONObject root){
  JSONArray best=root.optJSONArray("NBest");JSONObject b=best==null?null:best.optJSONObject(0);
  if(!"Success".equals(root.optString("RecognitionStatus"))||b==null)return new JourneyFeedback("这次没有可用的音素评测结果，请重录。",false,Double.NaN);
  JSONObject pa=b.optJSONObject("PronunciationAssessment");double accuracy=score(pa,"AccuracyScore");
  StringBuilder s=new StringBuilder("音频评测 · Azure Speech · 美式英语参考\n");
  s.append(label(pa,"AccuracyScore","准确度")).append(label(pa,"FluencyScore","流利度")).append(label(pa,"CompletenessScore","完整度"));
  JSONArray words=b.optJSONArray("Words");List<String> priorities=new ArrayList<>();int reported=0;
  if(words!=null)for(int i=0;i<words.length();i++){
   JSONObject w=words.optJSONObject(i);if(w==null)continue;String word=w.optString("Word");JSONObject wp=w.optJSONObject("PronunciationAssessment");
   String error=wp==null?"":wp.optString("ErrorType");double a=score(wp,"AccuracyScore");
   if("Omission".equals(error)){priorities.add("漏读："+word+"。先单独读，再连回整句。");continue;}
   if("Insertion".equals(error)){priorities.add("多读："+word+"。核对目标文本。");continue;}
   JSONArray phones=w.optJSONArray("Phonemes");
   if(phones!=null)for(int j=0;j<phones.length();j++){
    JSONObject p=phones.optJSONObject(j);if(p==null)continue;double n=score(p.optJSONObject("PronunciationAssessment"),"AccuracyScore");
    if(!Double.isNaN(n)){reported++;if(n<75)priorities.add(word+" · /"+p.optString("Phoneme")+"/ · "+Math.round(n)+"\n"+JourneyCore.phoneTip(p.optString("Phoneme")));}
   }
   if((phones==null||phones.length()==0)&&!Double.isNaN(a)&&a<75)priorities.add(word+" · 单词准确度 "+Math.round(a)+"。先听再慢读；服务没有返回该词的音素分数。");
  }
  if(Double.isNaN(accuracy)&&reported==0)return new JourneyFeedback("服务未返回可用的发音分数。这次不计作音素评测，请重试。",false,Double.NaN);
  if(priorities.isEmpty())s.append("\n本次未发现低于练习提示线的已评分音素。继续练自然表达，不必追求消除口音。\n");
  else {s.append("\n先练以下 "+Math.min(3,priorities.size())+" 项（75 是练习提示线，不是能力等级）：\n");for(int i=0;i<Math.min(3,priorities.size());i++)s.append("\n").append(i+1).append(". ").append(priorities.get(i)).append('\n');}
  s.append("\n顺序：听示范 → 单词慢读三次 → 整句重录 → 回听。分数仅作练习参考，不是 CEFR 等级或母语口音认证。");
  if(reported==0)s.append("\n本次无音素级分数，仅显示服务返回的单词/句子信息。");
  return new JourneyFeedback(s.toString(),true,accuracy);
 }
}
