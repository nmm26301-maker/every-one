import org.openenglish.reader.CloudClient;
import java.util.concurrent.CancellationException;
public class CloudJobTest {
  static int count;
  static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;}
  static boolean fails(CloudClient.Job j){try{j.check();return false;}catch(CancellationException e){return true;}}
  public static void main(String[] args){
    CloudClient.Job ordinary=new CloudClient.Job();ordinary.check();check(!ordinary.cancelled(),"new job active");ordinary.cancel();check(fails(ordinary),"ordinary cancellation");
    boolean blocked=false;try{ordinary.beginCommit();}catch(CancellationException e){blocked=true;}check(blocked,"cancel before commit blocks commit");
    CloudClient.Job committed=new CloudClient.Job();committed.beginCommit();committed.cancel();check(!committed.cancelled(),"commit rejects late cancel");check(!fails(committed),"commit result remains successful");
    Thread.currentThread().interrupt();check(!fails(committed),"critical local commit survives worker interruption");Thread.interrupted();
    CloudClient.Job interrupt=new CloudClient.Job();Thread.currentThread().interrupt();check(fails(interrupt),"ordinary interruption still cancels");Thread.interrupted();
    CloudClient.Job before=new CloudClient.Job();Thread.currentThread().interrupt();blocked=false;try{before.beginCommit();}catch(CancellationException e){blocked=true;}finally{Thread.interrupted();}check(blocked,"interruption before commit blocks commit");
    System.out.println("PASS: "+count+" cancellation/commit boundary checks (host Java)");
  }
}
