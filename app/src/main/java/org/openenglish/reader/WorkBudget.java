package org.openenglish.reader;

/** Monotonic limits for a single operation. Only increasing visible output extends idle time. */
public final class WorkBudget {
 public static final long WARM_MS=120000, ASR_MS=90000, CHAT_MS=120000, CHAT_IDLE_MS=75000;
 private final long started,total,idle;private long progressed;private int visible;private boolean complete;
 public WorkBudget(long now,long totalMs,long idleMs){if(totalMs<=0||idleMs<0)throw new IllegalArgumentException("invalid wait budget");started=progressed=now;total=totalMs;idle=idleMs;}
 public synchronized void progress(long now,int length){if(length>visible){visible=length;progressed=Math.max(progressed,now);}}
 public synchronized boolean expired(long now){return !complete&&(now-started>=total||idle>0&&now-progressed>=idle);}
 public synchronized void complete(){complete=true;}
 public synchronized boolean completed(){return complete;}
 public long seconds(long now){return Math.max(0,now-started)/1000;}
}
