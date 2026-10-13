package org.openenglish.reader;
import java.util.*;
/** CJK streaming: hold tiny fragments, emit completed phrases, flush the final short reply. */
public final class MagicSpeechChunks {
 private final StreamingSpeechChunks stream=new StreamingSpeechChunks();
 public void clear(){stream.clear();}
 public List<String> accept(String text,boolean done){return stream.accept(text,done);}
}
