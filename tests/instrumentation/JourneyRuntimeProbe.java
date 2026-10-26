package org.openenglish.validation;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;
import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicInteger;

/** Disposable emulator only: exercise real Activity listeners and inject a controlled linker failure. */
public final class JourneyRuntimeProbe extends Instrumentation {
 private Activity activity;private int checks;private boolean diagnostics;
 private void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private interface Main {void run()throws Exception;}
 private void main(Main action){final Throwable[] failure={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
 private View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
 private View find(String text){return find(activity.getWindow().getDecorView(),text);}
 private void click(String text){main(()->{View v=find(text);check(v instanceof Button,"button present: "+text);check(v.performClick(),"listener accepted: "+text);});}
 private boolean busy()throws Exception{Field f=activity.getClass().getDeclaredField("busy");f.setAccessible(true);return f.getBoolean(activity);}
 private void idle()throws Exception{for(int i=0;i<300;i++){final boolean[] b={true};main(()->b[0]=busy());if(!b[0])return;Thread.sleep(100);}throw new AssertionError("task did not finish");}
 private String diagnostic(){return getTargetContext().getSharedPreferences("journey_diagnostics",0).getString("last","");}
 private void job(boolean fail,AtomicInteger replies)throws Exception{
  ClassLoader loader=activity.getClassLoader();Class<?> work=loader.loadClass("org.openenglish.reader.JourneyActivity$Work"),result=loader.loadClass("org.openenglish.reader.JourneyActivity$Result");
  Object w=Proxy.newProxyInstance(loader,new Class<?>[]{work},(p,m,a)->{if(fail)throw new UnsatisfiedLinkError("PROBE_PRIVATE_MESSAGE");return "recovered";});
  Object r=Proxy.newProxyInstance(loader,new Class<?>[]{result},(p,m,a)->{replies.incrementAndGet();return null;});
  Method run=activity.getClass().getDeclaredMethod("runJob",work,result,String.class);run.setAccessible(true);main(()->run.invoke(activity,w,r,"Controlled probe"));idle();
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);diagnostics=args!=null&&"true".equals(args.getString("diagnostics"));start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  check(Build.SUPPORTED_ABIS[0].equals("x86_64"),"disposable x86 emulator only");
  activity=startActivitySync(new Intent().setClassName("org.openenglish.reader","org.openenglish.reader.JourneyActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  main(()->check(find("每天开口")!=null,"home renders"));click("场景交流 · AI 对话");
  main(()->{check(find("把英语用起来")!=null,"conversation renders");check(find("发送并继续对话")!=null,"input action renders");});
  click("帮我开场");idle();main(()->{check(find("没有完成：")!=null,"unsupported local engine reports failure");check(find("重试上一条")!=null,"pending question remains retryable");});
  if(diagnostics){
   check(diagnostic().contains("LocalAI"),"ordinary engine failure diagnosed");AtomicInteger replies=new AtomicInteger();
   String before=getTargetContext().getSharedPreferences("journey_progress_v1",0).getString("data","");job(true,replies);
   main(()->{check(find("引擎无法加载")!=null,"linker error displayed");check(!busy(),"busy cleared after linker error");check((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)==0,"wake flag cleared");});
   check(replies.get()==0,"failure never delivers a reply");check(diagnostic().contains("UnsatisfiedLinkError")&&!diagnostic().contains("PROBE_PRIVATE_MESSAGE"),"typed diagnostic excludes engine message");
   check(before.equals(getTargetContext().getSharedPreferences("journey_progress_v1",0).getString("data","")),"failure preserves pending conversation");
   job(false,replies);check(replies.get()==1,"new task succeeds after linker failure");
   click("首页");click("设置、模型与备份");main(()->check(find("查看最近错误")!=null,"diagnostic controls render"));click("复制诊断");
   main(()->{ClipboardManager c=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);String text=c.getPrimaryClip().getItemAt(0).getText().toString();check(text.contains("Everyone "+activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName)&&text.contains("UnsatisfiedLinkError")&&!text.contains("PROBE_PRIVATE_MESSAGE"),"explicit copy is bounded diagnostic");});
   click("清除诊断");check(diagnostic().isEmpty(),"diagnostic cleared");
  }
  out.putString("result","PASS");out.putInt("checks",checks);out.putBoolean("native_arm_tested",false);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("result","FAIL");out.putString("failure",e.toString());out.putInt("checks",checks);finish(Activity.RESULT_CANCELED,out);}}
}
