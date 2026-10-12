package org.openenglish.reader;
/** Epoch changes precede UI posts, so late recognition/tokens/tts never resurrect an interrupted turn. */
public final class VoiceTurns {
 private long epoch;
 public synchronized long next(){return ++epoch;}
 public synchronized boolean current(long turn){return epoch==turn;}
 public synchronized long current(){return epoch;}
 /** The epoch check and persistent write form one boundary with barge-in. */
 public synchronized boolean commit(long turn,Runnable action){if(epoch!=turn)return false;action.run();return true;}
}
