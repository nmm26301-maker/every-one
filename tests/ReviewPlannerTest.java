import org.openenglish.reader.*;
import java.util.*;
public class ReviewPlannerTest {
  static int count;
  static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;}
  static long at(String zone,int y,int m,int d,int h){Calendar c=Calendar.getInstance(TimeZone.getTimeZone(zone));c.clear();c.set(y,m-1,d,h,0);return c.getTimeInMillis();}
  static void invalid(Runnable task,String name){boolean rejected=false;try{task.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,name);}
  public static void main(String[] args){
    TimeZone zone=TimeZone.getTimeZone("Asia/Shanghai");long now=at("Asia/Shanghai",2026,10,9,17);
    int[] previous={0,1,3,7,14,30},next={1,3,7,14,30,30};
    for(int i=0;i<previous.length;i++)check(ReviewPlanner.nextInterval(previous[i])==next[i],"interval "+previous[i]);
    ReviewPlanner.Plan first=ReviewPlanner.afterWriting(now,true,null,zone);
    check(first.interval==1 && first.due==at("Asia/Shanghai",2026,10,10,9),"first correct next local morning");
    ReviewPlanner.Plan repeated=ReviewPlanner.afterWriting(now+60000,true,first,zone);
    check(repeated.interval==1 && repeated.due==first.due,"early correct keeps schedule");
    ReviewPlanner.Plan due=ReviewPlanner.afterWriting(first.due,true,first,zone);
    check(due.interval==3 && due.due==at("Asia/Shanghai",2026,10,13,9),"due correct advances at boundary");
    ReviewPlanner.Plan wrong=ReviewPlanner.afterWriting(now,false,new ReviewPlanner.Plan(30,now+1000),zone);
    check(wrong.interval==0 && wrong.due==now,"wrong resets now despite future plan");
    ReviewPlanner.Plan corrected=ReviewPlanner.afterWriting(now,true,new ReviewPlanner.Plan(0,now+1000),zone);
    check(corrected.interval==1 && corrected.due==first.due,"deferred wrong corrected starts one day");
    check(ReviewPlanner.morningAfter(at("Asia/Shanghai",2026,12,31,23),1,zone)==at("Asia/Shanghai",2027,1,1,9),"year rollover");
    check(ReviewPlanner.morningAfter(at("Asia/Shanghai",2028,2,28,22),1,zone)==at("Asia/Shanghai",2028,2,29,9),"leap date");
    TimeZone ny=TimeZone.getTimeZone("America/New_York");
    check(ReviewPlanner.morningAfter(at("America/New_York",2026,3,7,17),1,ny)==at("America/New_York",2026,3,8,9),"spring DST local morning");
    check(ReviewPlanner.morningAfter(at("America/New_York",2026,10,31,17),1,ny)==at("America/New_York",2026,11,1,9),"fall DST local morning");
    check(ReviewPlanner.include("到期复习",99,false,0,now),"old wrong due without plan");
    check(!ReviewPlanner.include("到期复习",100,false,0,now),"old correct without plan excluded");
    check(ReviewPlanner.include("到期复习",100,true,now,now),"scheduled correct due inclusive");
    check(!ReviewPlanner.include("到期复习",99,true,now+1,now),"deferred wrong not due");
    check(ReviewPlanner.include("待巩固",99,true,now+1,now),"deferred wrong remains pending");
    check(!ReviewPlanner.include("待巩固",100,true,now,now),"correct excluded from pending");
    check(ReviewPlanner.include("全部计划",100,true,now+1,now),"future correct visible in all");
    check(ReviewPlanner.include("到期复习",-1,false,0,now),"ungraded manual comparison pending");
    invalid(()->ReviewPlanner.nextInterval(2),"unknown interval rejected");
    invalid(()->ReviewPlanner.afterWriting(-1,true,null,zone),"negative clock rejected");
    invalid(()->ReviewPlanner.afterWriting(now,true,new ReviewPlanner.Plan(2,0),zone),"invalid previous interval rejected");
    invalid(()->ReviewPlanner.afterWriting(now,true,new ReviewPlanner.Plan(1,-1),zone),"negative due rejected");
    invalid(()->ReviewPlanner.morningAfter(now,0,zone),"zero day rejected");
    invalid(()->ReviewPlanner.morningAfter(now,31,zone),"unbounded day rejected");
    invalid(()->ReviewPlanner.morningAfter(now,1,null),"missing zone rejected");
    invalid(()->ReviewPlanner.morningAfter(ReviewPlanner.MAX_DUE,1,zone),"calendar overflow rejected");
    invalid(()->ReviewPlanner.include("unknown",99,true,0,now),"filter validated");
    System.out.println("PASS: "+count+" review interval, early practice, filter and local-calendar checks");
  }
}
