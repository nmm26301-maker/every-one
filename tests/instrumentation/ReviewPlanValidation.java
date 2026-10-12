package org.openenglish.validation;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import org.json.*;
import org.openenglish.reader.*;

/** Disposable database/media fixture; compiled separately, never run against user data. */
public class ReviewPlanValidation extends Instrumentation {
  int checks;
  void check(boolean yes,String name){if(!yes)throw new AssertionError(name);checks++;}
  JSONObject page(StudioStore db,String filter)throws Exception{return db.reviewPage(0,30,filter,new CloudClient.Job());}
  public void onCreate(Bundle b){super.onCreate(b);start();}
  public void onStart(){Bundle result=new Bundle();PortableValidation.Isolated c=null;StudioStore db=null;
    try{
      c=new PortableValidation.Isolated(getTargetContext());db=new StudioStore(c);
      File media=new File(c.getFilesDir(),"fixture.m4a");
      try(InputStream in=c.getAssets().open("studio-demo.m4a");OutputStream out=new FileOutputStream(media)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
      long material=db.addMaterial("audio","Review fixture",Uri.fromFile(media).toString(),"");
      db.updateMaterial(material,StudioStore.values("cues","[{\"start\":0,\"end\":2000,\"text\":\"Take your time\"}]"));
      long wrong=db.practice(material,0,"Take your time","Take time","","");
      check(page(db,"到期复习").getInt("total")==1,"wrong is due");
      check(db.deferReview(wrong,true),"defer current writing");
      check(page(db,"到期复习").getInt("total")==0 && page(db,"待巩固").getInt("total")==1,"deferred wrong remains pending");
      long correct=db.practice(material,0,"Take your time","take YOUR time!","","");
      check(page(db,"待巩固").getInt("total")==0,"correct exits pending");
      JSONObject plan=db.query("SELECT * FROM review_plan").getJSONObject(0);long firstDue=plan.getLong("due");
      check(plan.getLong("attempt")==correct && plan.getInt("interval")==1,"first correct schedule committed with practice");
      check(!db.deferReview(wrong,false),"stale card cannot change newest writing");
      db.practice(material,0,"Take your time","Take your time","","");
      check(db.query("SELECT * FROM review_plan").getJSONObject(0).getLong("due")==firstDue,"early writing does not change due");
      db.getWritableDatabase().execSQL("UPDATE review_plan SET due=?",new Object[]{System.currentTimeMillis()-1});
      db.practice(material,0,"Take your time","Take your time","","");
      check(db.query("SELECT * FROM review_plan").getJSONObject(0).getInt("interval")==3,"due correct advances interval");
      long latest=db.practice(material,0,"Take your time","Take time","","");
      check(db.query("SELECT * FROM review_plan").getJSONObject(0).getInt("interval")==0 && page(db,"到期复习").getInt("total")==1,"new mistake resets interval");
      File take=new File(c.getFilesDir(),"studio-recordings/fixture.m4a");PortableValidation.write(take,PortableValidation.read(media));
      db.practice(material,0,"Take your time","Take your time","studio-recordings/fixture.m4a","");
      check(db.query("SELECT * FROM review_plan").getJSONObject(0).getLong("attempt")==latest,"ASR recording cannot clear writing queue");
      db.deferReview(latest,true);JSONObject backup=db.backup();long savedDue=backup.getJSONArray("review_plan").getJSONObject(0).getLong("due");
      db.restore(backup);check(db.query("SELECT * FROM review_plan").getJSONObject(0).getLong("due")==savedDue,"JSON preserves schedule");
      JSONObject invalid=new JSONObject(backup.toString());invalid.getJSONArray("review_plan").getJSONObject(0).put("attempt",999999);
      boolean rejected=false;try{db.restore(invalid);}catch(JSONException e){rejected=true;}
      check(rejected && db.query("SELECT * FROM review_plan").getJSONObject(0).getLong("due")==savedDue,"invalid schedule restore rolls back");
      JSONObject negative=new JSONObject(backup.toString());negative.getJSONArray("review_plan").getJSONObject(0).put("interval",3);
      rejected=false;try{db.restore(negative);}catch(JSONException e){rejected=true;}
      check(rejected && db.practiceCount()==backup.getJSONArray("practice").length(),"wrong writing cannot restore positive interval");
      File archive=new File(c.getCacheDir(),"review-portable.zip");PortableBackup.export(c,Uri.fromFile(archive),new CloudClient.Job(),(n,f,b)->{});
      db.deferReview(latest,false);PortableBackup.restore(c,Uri.fromFile(archive),new CloudClient.Job(),(n,f,b)->{});
      check(db.query("SELECT * FROM review_plan").getJSONObject(0).getLong("due")==savedDue,"portable restore preserves planned due");
      JSONObject old=new JSONObject(backup.toString());old.remove("review_plan");db.restore(old);
      check(db.query("SELECT * FROM review_plan").length()==0 && page(db,"到期复习").getInt("total")==1,"older schema 2 without plan remains compatible");
      db.restore(backup);db.updateMaterial(material,StudioStore.values("cues","[{\"start\":0,\"end\":2000,\"text\":\"Changed original\"}]"));
      check(page(db,"全部计划").getInt("total")==0,"edited subtitle skipped");
      db.restore(backup);db.removePractice(latest);check(db.query("SELECT * FROM review_plan").length()==0,"deleting planned writing clears its mapping");
      db.restore(backup);db.removeMaterial(material);check(page(db,"全部计划").getInt("total")==0 && db.query("SELECT * FROM review_plan").length()==0,"material deletion clears schedule");
      CloudClient.Job cancelled=new CloudClient.Job();cancelled.cancel();rejected=false;
      try{db.reviewPage(0,30,"到期复习",cancelled);}catch(java.util.concurrent.CancellationException e){rejected=true;}
      check(rejected,"cancelled query stops");
      result.putString("stream","PASS: "+checks+" isolated Android review-plan integration checks\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");finish(0,result);}
    finally{if(db!=null)db.close();if(c!=null)c.clean();}
  }
}
