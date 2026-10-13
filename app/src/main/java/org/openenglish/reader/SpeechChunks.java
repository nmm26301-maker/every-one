package org.openenglish.reader;
import java.util.*;

/** Incremental cumulative-text splitter; only complete words are handed to system TTS. */
public final class SpeechChunks {
 private final StreamingSpeechChunks stream=new StreamingSpeechChunks();
 public void clear(){stream.clear();}
 public List<String> accept(String text,boolean done){return stream.accept(text,done);}
}
