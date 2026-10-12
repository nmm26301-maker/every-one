package org.openenglish.reader;
import java.util.Arrays;

/** TIFA flat grouped Viterbi decoder, MIT adaptation of openvpi/TIFA modules/decoding.py.
 * Scores locate expected phones; they are not pronunciation correctness probabilities. */
public final class TifaDecoder {
 private TifaDecoder(){}
 public static int[][] decode(float[][] sim,int[] groups){
  int tMax=sim.length,n=groups.length;
  if(tMax==0||n==0||n>128||tMax>1200)throw new IllegalArgumentException("alignment bounds");
  for(float[] row:sim){if(row.length!=n)throw new IllegalArgumentException("alignment shape");for(float v:row)if(!Float.isFinite(v))throw new IllegalArgumentException("nonfinite alignment");}
  boolean[] allowed=new boolean[n+1];Arrays.fill(allowed,true);for(int i=1;i<n;i++)allowed[i]=groups[i-1]!=groups[i];
  float[] gap=new float[n+1],token=new float[n];Arrays.fill(gap,Float.NEGATIVE_INFINITY);Arrays.fill(token,Float.NEGATIVE_INFINITY);
  byte[][] gb=new byte[tMax+1][n+1],tb=new byte[tMax+1][n];gap[0]=0;
  for(int i=0;i<n;i++){gap[i+1]=gap[i]-.5f;gb[0][i+1]=2;}
  for(int t=1;t<=tMax;t++){
   float[] nt=new float[n],ng=new float[n+1];Arrays.fill(ng,Float.NEGATIVE_INFINITY);
   for(int i=0;i<n;i++){float best=token[i];if(gap[i]>best){best=gap[i];tb[t][i]=1;}nt[i]=best+sim[t-1][i];}
   for(int i=0;i<=n;i++)if(allowed[i])ng[i]=gap[i];
   for(int i=0;i<n;i++){if(nt[i]>ng[i+1]){ng[i+1]=nt[i];gb[t][i+1]=1;}float skipped=ng[i]-.5f;if(skipped>ng[i+1]){ng[i+1]=skipped;gb[t][i+1]=2;}}
   gap=ng;token=nt;
  }
  int[][] spans=new int[n][2];for(int[] s:spans)Arrays.fill(s,-1);int t=tMax,i=n;boolean in=false;
  while(t>0||i>0||in){if(in){if(spans[i][1]<0)spans[i][1]=t;spans[i][0]=t-1;byte entered=tb[t][i];t--;if(entered==1)in=false;}
   else {byte source=gb[t][i];if(source==0)t--;else if(source==1){i--;in=true;}else{i--;spans[i][0]=spans[i][1]=t;}}}
  int lo=0;while(lo<n){if(spans[lo][0]!=spans[lo][1]){lo++;continue;}int hi=lo;while(hi+1<n&&spans[hi+1][0]==spans[hi+1][1])hi++;
   int left=lo>0?spans[lo-1][1]:0,right=hi+1<n?spans[hi+1][0]:tMax,g=lo;while(g<=hi+1&&!allowed[g])g++;
   for(int j=lo;j<=hi;j++)spans[j][0]=spans[j][1]=j<g?left:right;lo=hi+1;
  }
  return spans;
 }
 public static int[][] grouped(int[][] phones,int[] groups,int count){
  int[][] out=new int[count][2];for(int[] s:out){s[0]=Integer.MAX_VALUE;s[1]=-1;}
  for(int i=0;i<phones.length;i++){int g=groups[i];if(g<0||g>=count)throw new IllegalArgumentException("group bounds");if(phones[i][1]>phones[i][0]){out[g][0]=Math.min(out[g][0],phones[i][0]);out[g][1]=Math.max(out[g][1],phones[i][1]);}}
  for(int[] s:out)if(s[1]<0)s[0]=s[1]=0;return out;
 }
 public static float[] crop(float[] pcm,int startFrame,int endFrame,int padding){
  int start=Math.max(0,startFrame*160-padding),end=Math.min(pcm.length,endFrame*160+padding);
  if(end<=start)return new float[0];return Arrays.copyOfRange(pcm,start,end);
 }
}
