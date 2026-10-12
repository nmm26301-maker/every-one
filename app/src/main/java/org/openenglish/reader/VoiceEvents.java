package org.openenglish.reader;
import java.util.ArrayDeque;
/** Bounded diagnostics record control events without recording conversation text. */
public final class VoiceEvents {
 private final ArrayDeque<String> entries=new ArrayDeque<>();
 public synchronized void add(long time,String event){entries.addLast(time+" ms · "+event);while(entries.size()>32)entries.removeFirst();}
 public synchronized String report(){StringBuilder out=new StringBuilder();for(String entry:entries){if(out.length()>0)out.append("\n");out.append(entry);}return out.toString();}
}
