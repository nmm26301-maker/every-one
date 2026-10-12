package org.openenglish.reader;
/** Java adaptation of Jyut Dojo src/lib/srs.ts. Copyright 2026 cheeryaudio, MIT. */
public final class IslandSrs {
 public static final long DAY=86400000L;
 public static final class Card {public final long due;public final int interval,reps,lapses;public final double ease;public Card(long due,int interval,double ease,int reps,int lapses){this.due=due;this.interval=interval;this.ease=ease;this.reps=reps;this.lapses=lapses;}public static Card fresh(long now){return new Card(now,0,2.5,0,0);}}
 private IslandSrs(){}
 public static Card schedule(Card c,int grade,long now){if(grade<0||grade>3)throw new IllegalArgumentException("grade");double e=c.ease;int n=c.reps,l=c.lapses,d=c.interval;switch(grade){case 0:e-=.2;l++;n=0;d=1;break;case 1:e-=.15;d=n==0?1:Math.max(d+1,(int)Math.round(d*1.2));n++;break;case 2:d=n==0?1:n==1?6:(int)Math.round(d*e);n++;break;case 3:e+=.15;d=n==0?4:Math.max(d+1,(int)Math.round(d*e*1.3));n++;break;}e=Math.max(1.3,Math.round(e*100)/100.0);d=Math.max(1,Math.min(36500,d));return new Card(now+d*DAY,d,e,n,l);}
}
