package org.openenglish.reader;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class CloudJobRecoveryTest {
 static int checks;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static class SlowConnection extends HttpURLConnection {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
  SlowConnection()throws Exception{super(new URL("https://example.invalid"));}
  public void connect(){}public boolean usingProxy(){return false;}
  public void disconnect(){calls.incrementAndGet();entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{done.countDown();}}
 }
 public static void main(String[] args)throws Exception {
  CloudClient.Job job=new CloudClient.Job();SlowConnection connection=new SlowConnection();job.attach(connection);
  long started=System.nanoTime();job.cancel();long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
  check(elapsed<500,"cancel returns before blocked connection disconnect finishes");check(job.cancelled(),"cancellation flag is visible immediately");
  boolean stopped=false;try{job.check();}catch(CancellationException e){stopped=true;}check(stopped,"worker sees cancellation during pending cleanup");
  check(connection.entered.await(1,TimeUnit.SECONDS),"connection cleanup starts on independent thread");
  for(int i=0;i<100;i++)job.cancel();check(connection.calls.get()==1,"repeated cancellation does not duplicate cleanup threads");
  stopped=false;try{job.beginCommit();}catch(CancellationException e){stopped=true;}check(stopped,"pending disconnect cannot permit cancelled local commit");
  connection.release.countDown();check(connection.done.await(1,TimeUnit.SECONDS),"slow cleanup eventually completes");
  CloudClient.Job cancelled=new CloudClient.Job();cancelled.cancel();SlowConnection late=new SlowConnection();stopped=false;
  try{cancelled.attach(late);}catch(CancellationException e){stopped=true;}
  check(stopped&&late.entered.await(1,TimeUnit.SECONDS),"connection returned after cancellation is rejected and released");late.release.countDown();late.done.await(1,TimeUnit.SECONDS);
  CloudClient.Job committing=new CloudClient.Job();SlowConnection committed=new SlowConnection();committing.attach(committed);committing.beginCommit();committing.cancel();check(!committing.cancelled()&&committed.calls.get()==0,"critical database commit retains prior cancellation semantics");
  Thread.currentThread().interrupt();committing.check();Thread.interrupted();check(!committing.cancelled(),"commit remains protected from worker interruption");
  CloudClient.Job interrupted=new CloudClient.Job();SlowConnection abandoned=new SlowConnection();Thread.currentThread().interrupt();stopped=false;
  try{interrupted.attach(abandoned);}catch(CancellationException e){stopped=true;}finally{Thread.interrupted();}
  check(stopped&&abandoned.entered.await(1,TimeUnit.SECONDS),"interrupted attach also releases the unattached connection");abandoned.release.countDown();abandoned.done.await(1,TimeUnit.SECONDS);
  for(final boolean commit : new boolean[]{false,true}){
   final Object turnLock=new Object();final CountDownLatch held=new CountDownLatch(1),checking=new CountDownLatch(1);final AtomicBoolean rejected=new AtomicBoolean();
   final CloudClient.Job guarded=new CloudClient.Job(){public void check(){super.check();checking.countDown();synchronized(turnLock){}}};final SlowConnection pending=new SlowConnection();pending.release.countDown();
   Thread ui=new Thread(()->{synchronized(turnLock){held.countDown();try{checking.await(1,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}guarded.cancel();}});
   Thread worker=new Thread(()->{try{held.await(1,TimeUnit.SECONDS);if(commit)guarded.beginCommit();else guarded.attach(pending);}catch(CancellationException e){rejected.set(true);}catch(InterruptedException e){throw new RuntimeException(e);}});
   ui.setDaemon(true);worker.setDaemon(true);ui.start();worker.start();ui.join(1500);worker.join(1500);
   check(!ui.isAlive()&&!worker.isAlive(),"turn guard and job cancellation cannot deadlock: "+(commit?"commit":"attach"));
   check(rejected.get()&&guarded.cancelled(),"concurrent cancellation wins before critical operation: "+(commit?"commit":"attach"));
  }
  System.out.println("CLOUD_JOB_RECOVERY_CHECKS: "+checks);
 }
}
