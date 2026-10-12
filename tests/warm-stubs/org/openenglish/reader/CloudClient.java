package org.openenglish.reader;
import java.util.concurrent.CancellationException;
public class CloudClient {public interface Delta {void text(String s);}public static class Job {private volatile boolean stopped;public void cancel(){stopped=true;}public void check(){if(stopped)throw new CancellationException();}public boolean cancelled(){return stopped;}}}
