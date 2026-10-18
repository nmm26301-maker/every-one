package org.openenglish.reader;

import java.util.*;
import org.json.*;

/** Android adaptation of OpenPronounce phones.py (MIT), commit 74bc17ea406e6f057f15eea0e70951e1931af868.
 * Copyright (c) 2025 Jean-François Lépine. See assets/journey/OPENPRONOUNCE-LICENSE.txt.
 * Reuses CTC decoding, canonical phones, accepted variants, posterior plausibility and weighted edits.
 * Change: bounded Java DP alignment (tie choices may differ from python-Levenshtein); no total score.
 */
public final class JourneyPhones {
 private final JSONObject rules;private final String[] vocab;private final Map<String,List<Integer>> tokenIds=new HashMap<>();
 public JourneyPhones(JSONObject config)throws JSONException{
  rules=config;vocab=strings(config.getJSONArray("vocab"));
  for(int i=0;i<vocab.length;i++)if(!vocab[i].startsWith("<")){String p=normalize(vocab[i]);if(!p.isEmpty()){if(!tokenIds.containsKey(p))tokenIds.put(p,new ArrayList<>());tokenIds.get(p).add(i);}}
 }
 public String normalize(String phone){String p=phone.replace("ː","").replaceAll("[0-9]","");p=rules.optJSONObject("aliases").optString(p,p);return rules.optJSONObject("map").optString(p,p);}
 public static String[] strings(JSONArray a){String[] s=new String[a.length()];for(int i=0;i<s.length;i++)s[i]=a.optString(i);return s;}
 public static final class Recognition {
  public final List<String> phones=new ArrayList<>();public final List<int[]> spans=new ArrayList<>();public final List<Double> confidences=new ArrayList<>();
  final float[][] probabilities;Recognition(float[][] p){probabilities=p;}
 }
 public Recognition decode(float[][] logits){
  if(logits.length==0||logits.length>650)throw new IllegalArgumentException("音素模型返回了无效长度");
  float[][] prob=new float[logits.length][vocab.length];int[] ids=new int[logits.length];
  for(int i=0;i<logits.length;i++){
   if(logits[i].length!=vocab.length)throw new IllegalArgumentException("音素模型词表不匹配");
   double sum=0;float max=-Float.MAX_VALUE;for(int j=0;j<vocab.length;j++){float v=logits[i][j];if(!Float.isFinite(v))throw new IllegalArgumentException("音素输出无效，请重新录音");if(v>max){max=v;ids[i]=j;}}
   for(int j=0;j<vocab.length;j++){prob[i][j]=(float)Math.exp(logits[i][j]-max);sum+=prob[i][j];}for(int j=0;j<vocab.length;j++)prob[i][j]/=sum;
  }
  Recognition r=new Recognition(prob);int start=0;
  for(int end=1;end<=ids.length;end++){
   if(end<ids.length&&ids[end]==ids[start])continue;String raw=vocab[ids[start]];
   if(ids[start]!=0&&!raw.startsWith("<")){
    String p=normalize(raw);double confidence=0;for(int k=start;k<end;k++)confidence=Math.max(confidence,prob[k][ids[start]]);
    if(!p.isEmpty()){int last=r.phones.size()-1;if(last>=0&&(r.phones.get(last).equals(p)||(r.phones.get(last).equals("ɚ")&&p.equals("ɹ")))){r.spans.get(last)[1]=end;r.confidences.set(last,Math.max(confidence,r.confidences.get(last)));}
     else{r.phones.add(p);r.spans.add(new int[]{start,end});r.confidences.add(confidence);}}
   }start=end;
  }return r;
 }
 /** Minimum edit alignment. Insertions attach to the preceding expected phone, as upstream. */
 static List<List<Integer>> align(List<String> a,List<String> b){
  int[][] d=distanceMatrix(a,b);List<List<Integer>> out=new ArrayList<>();for(String p:a)out.add(new ArrayList<>());
  int i=a.size(),j=b.size();while(i>0||j>0){
   if(i>0&&j>0&&d[i][j]==d[i-1][j-1]+(a.get(i-1).equals(b.get(j-1))?0:1)){out.get(--i).add(--j);}
   else if(i>0&&d[i][j]==d[i-1][j]+1)i--;
   else{j--;if(!a.isEmpty())out.get(Math.max(0,i-1)).add(j);}
  }for(List<Integer> row:out)Collections.sort(row);return out;
 }
 private static int[][] distanceMatrix(List<String>a,List<String>b){if(a.size()>650||b.size()>650)throw new IllegalArgumentException("请分短句练习");int[][] d=new int[a.size()+1][b.size()+1];for(int i=0;i<=a.size();i++)d[i][0]=i;for(int j=0;j<=b.size();j++)d[0][j]=j;for(int i=1;i<=a.size();i++)for(int j=1;j<=b.size();j++)d[i][j]=Math.min(d[i-1][j]+1,Math.min(d[i][j-1]+1,d[i-1][j-1]+(a.get(i-1).equals(b.get(j-1))?0:1)));return d;}
 private static int distance(List<String>a,List<String>b){return distanceMatrix(a,b)[a.size()][b.size()];}
 private double cost(String expected,String heard){JSONArray near=rules.optJSONArray("near");for(int i=0;i<near.length();i++){List<String> group=Arrays.asList(strings(near.optJSONArray(i)));if(group.contains(expected)&&group.contains(heard))return .5;}return 1;}
 private double plausible(Recognition r,String p,int start,int end){List<Integer> ids=tokenIds.get(p);if(ids==null)return 0;double best=0;for(int f=Math.max(0,start);f<Math.min(end,r.probabilities.length);f++)for(int id:ids)best=Math.max(best,r.probabilities[f][id]);return best;}
 private static String join(List<String> a){StringBuilder s=new StringBuilder();for(String p:a)s.append(p);return s.toString();}
 public JSONObject compare(Recognition r,JSONObject reference)throws JSONException{
  JSONArray words=reference.getJSONArray("words"),groups=reference.getJSONArray("groups"),alternates=reference.getJSONArray("alternates");
  List<String> expected=new ArrayList<>();for(int i=0;i<groups.length();i++)expected.addAll(Arrays.asList(strings(groups.getJSONArray(i))));
  if(expected.isEmpty()||r.phones.isEmpty())throw new IllegalArgumentException("没有识别到足够清楚的音素，请靠近麦克风重录");
  List<List<Integer>> global=align(expected,r.phones);JSONArray reports=new JSONArray();int offset=0;
  for(int w=0;w<words.length();w++){
   List<String> group=Arrays.asList(strings(groups.getJSONArray(w)));TreeSet<Integer> matchedSet=new TreeSet<>();for(int k=offset;k<offset+group.size();k++)matchedSet.addAll(global.get(k));
   List<Integer> matched=new ArrayList<>(matchedSet);List<String> actual=new ArrayList<>();for(int j:matched)actual.add(r.phones.get(j));
   List<String> candidate=group;int dist=distance(group,actual);JSONArray variants=alternates.getJSONArray(w);for(int v=0;v<variants.length();v++){List<String> c=Arrays.asList(strings(variants.getJSONArray(v)));int dd=distance(c,actual);if(dd<dist){candidate=c;dist=dd;}}
   int begin=0,end=r.probabilities.length;
   if(!matched.isEmpty()){begin=r.spans.get(matched.get(0))[0];end=r.spans.get(matched.get(matched.size()-1))[1];}
   else{for(int k=0;k<offset;k++)for(int j:global.get(k))begin=Math.max(begin,r.spans.get(j)[0]);for(int k=offset+group.size();k<expected.size();k++)for(int j:global.get(k))end=Math.min(end,r.spans.get(j)[1]);}
   offset+=group.size();JSONArray phones=new JSONArray();double edits=0;
   if(dist>0){List<List<Integer>> local=align(candidate,actual);for(int k=0;k<candidate.size();k++){
    String target=candidate.get(k);List<Integer> indices=local.get(k);List<String> heard=new ArrayList<>();for(int j:indices)heard.add(actual.get(j));double confidence=0;
    if(!(heard.size()==1&&heard.get(0).equals(target))){
     if(heard.contains(target))confidence=k==candidate.size()-1?.25:1;
     else{double c=0;int start=begin,finish=end;
      if(!heard.isEmpty()){start=Integer.MAX_VALUE;finish=0;for(int j:indices){c=Math.max(c,cost(target,actual.get(j)));int[] span=r.spans.get(matched.get(j));start=Math.min(start,span[0]);finish=Math.max(finish,span[1]);}}
      else{c=k==candidate.size()-1?.5:1;for(int q=0;q<k;q++)for(int j:local.get(q))start=Math.max(start,r.spans.get(matched.get(j))[0]);for(int q=k+1;q<candidate.size();q++)for(int j:local.get(q))finish=Math.min(finish,r.spans.get(matched.get(j))[1]);}
      confidence=c*(1-Math.min(1,plausible(r,target,start,finish)/.05));
     }
    }edits+=confidence;phones.put(new JSONObject().put("expected",target).put("heard",join(heard)).put("confidence",confidence));
   }}
   boolean flagged=dist>0&&(edits/group.size()>=.4||edits>=2);
   reports.put(new JSONObject().put("word",words.getString(w)).put("position",w).put("expected",join(group)).put("actual",join(actual)).put("phones",phones).put("flagged",flagged).put("confidence",Math.min(1,Math.max(edits/group.size(),edits/2))).put("start_ms",Math.max(0,begin*20-80)).put("end_ms",end*20+100));
  }
  double confidence=0;for(double c:r.confidences)confidence+=c;confidence/=r.confidences.size();double error=(double)distance(expected,r.phones)/expected.size();
  return new JSONObject().put("engine","OpenPronounce Android adaptation").put("words",reports).put("heard_phones",new JSONArray(r.phones)).put("recognition_confidence",confidence).put("usable",confidence>=.35&&error<=1.5).put("phone_edit_ratio",error);
 }
 public static String feedback(JSONObject result){
  if(!result.optBoolean("usable"))return "这次音素识别不够稳定，暂不判断错音。请在安静环境重录，并先回听确认录音清楚。";
  StringBuilder text=new StringBuilder("开源离线纠音 · 根据真实录音识别音素\n");JSONArray words=result.optJSONArray("words");int count=0;
  for(int i=0;i<words.length()&&count<3;i++){JSONObject w=words.optJSONObject(i);if(!w.optBoolean("flagged")&&words.length()!=1)continue;count++;text.append(words.length()==1?"\n单词音素对照（短词不作确定判断） ":"\n先练 ").append(w.optString("word")).append("：目标 /").append(w.optString("expected")).append("/，模型听到 /").append(w.optString("actual")).append("/\n");JSONArray phones=w.optJSONArray("phones");JSONObject best=null;for(int j=0;j<phones.length();j++){JSONObject p=phones.optJSONObject(j);if(best==null||p.optDouble("confidence")>best.optDouble("confidence"))best=p;}if(best!=null&&best.optDouble("confidence")>0)text.append(JourneyCore.phoneTip(best.optString("expected"))).append('\n');}
  if(count==0)text.append("未发现达到提示阈值的音素差异。请继续回听；这不代表每个音都已正确。\n");
  return text.append("\n以上是疑似差异，不是确定诊断；口音、连读和噪声都可能引起误报。先听示范，再听自己，然后重录同一句。离线引擎不评价重音、语调或流利度。").toString();
 }
}
