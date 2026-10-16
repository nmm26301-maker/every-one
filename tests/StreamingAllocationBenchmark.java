import org.openenglish.reader.*;
import java.lang.management.ManagementFactory;
import java.util.function.BiFunction;
import java.util.*;

/** Optional host allocation measurement; identical prebuilt cumulative callbacks for both versions. */
public final class StreamingAllocationBenchmark {
 private static volatile int sink;
 private static String[] updates;
 private static long allocated(BiFunction<String,Boolean,List<String>> accept) {
  com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
  long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id);
  int count=0;
  for(String update:updates)count+=accept.apply(update,false).size();
  count+=accept.apply(updates[updates.length-1],true).size();sink=count;
  return bean.getThreadAllocatedBytes(id)-before;
 }
 public static void main(String[] args){
  StringBuilder reply=new StringBuilder();
  for(int n=0;n<160;n++)reply.append("Sentence ").append(n).append(" keeps every spoken word. ");
  updates=new String[reply.length()];for(int i=0;i<updates.length;i++)updates[i]=reply.substring(0,i+1);
  for(int i=0;i<4;i++){allocated(new PreviousSpeechChunks()::accept);allocated(new StreamingSpeechChunks()::accept);}
  long previous=allocated(new PreviousSpeechChunks()::accept),current=allocated(new StreamingSpeechChunks()::accept);
  System.out.println("callbacks="+updates.length+" previous_allocated_bytes="+previous+" current_allocated_bytes="+current+" chunks="+sink);
 }
}
