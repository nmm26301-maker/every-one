package org.openenglish.reader;
public final class LessonRecorder{public interface Listener{void ready();void meter(double rms,int millis);void done(float[] pcm);void error(String reason);}public boolean start(Listener l){return false;}public boolean busy(){return false;}public void cancel(){}public void finish(){}}
