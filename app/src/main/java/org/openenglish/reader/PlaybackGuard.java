package org.openenglish.reader;

/** Hold speaker protection across an entire streamed reply, including gaps between TTS chunks. */
public final class PlaybackGuard {
 private boolean active;private long turn=-1,tail;
 public synchronized void begin(long turn){this.turn=turn;active=true;}
 public synchronized void finish(long turn,long now){if(this.turn!=turn)return;active=false;tail=Math.max(tail,now+650);}
 public synchronized void clear(long now){active=false;turn=-1;tail=Math.max(tail,now+650);}
 public synchronized boolean allows(boolean headphones,boolean aec,boolean speakerBargeIn,long now){return headphones||(speakerBargeIn&&aec)||(!active&&now>=tail);}
}
