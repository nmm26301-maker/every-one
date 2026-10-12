package org.openenglish.reader;

import java.util.*;

/** Java adaptation of Jyut Dojo src/lib/pitchScore.ts (MIT).
 * Copyright (c) 2026 cheeryaudio. Full notice: assets/licenses/Jyut-Dojo-MIT.txt.
 * Compares pitch shape only. It is NOT a phoneme/pronunciation grade. */
public final class ContourScore {
 private ContourScore() {}
 public static double median(double[] input) {
  if(input.length==0)return 0;double[] a=input.clone();Arrays.sort(a);int m=a.length/2;
  return a.length%2==0?(a[m-1]+a[m])/2:a[m];
 }
 public static double[] normalize(double[] input) {
  double[] clean=new double[input.length];int n=0;
  for(double x:input)if(!Double.isNaN(x)&&!Double.isInfinite(x))clean[n++]=x;
  clean=Arrays.copyOf(clean,n);double m=median(clean);for(int i=0;i<n;i++)clean[i]-=m;return clean;
 }
 public static double[] resample(double[] a,int n) {
  if(a.length==0||n<1)return new double[0];double[] out=new double[n];
  if(n==1){out[0]=a[0];return out;}
  for(int i=0;i<n;i++){double p=(double)i/(n-1)*(a.length-1);int lo=(int)p,hi=Math.min(lo+1,a.length-1);out[i]=a[lo]*(1-(p-lo))+a[hi]*(p-lo);}return out;
 }
 public static int score(double[] a,double[] b) {
  a=resample(normalize(a),64);b=resample(normalize(b),64);if(a.length==0||b.length==0)return 0;
  double[] prev=new double[65];Arrays.fill(prev,Double.POSITIVE_INFINITY);prev[0]=0;
  for(int i=1;i<=64;i++){double[] cur=new double[65];Arrays.fill(cur,Double.POSITIVE_INFINITY);for(int j=1;j<=64;j++)cur[j]=Math.abs(a[i-1]-b[j-1])+Math.min(prev[j],Math.min(cur[j-1],prev[j-1]));prev=cur;}
  return Math.max(0,Math.min(100,(int)Math.round(100-20*prev[64]/64)));
 }
}
