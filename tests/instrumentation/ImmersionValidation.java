package org.openenglish.reader.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises actual MediaCodec playback, disk recovery and export in the test-signed UI copy. */
public final class ImmersionValidation extends Instrumentation {
  private Activity activity;
  private final List<String> checks = new ArrayList<>();
  private interface Work { Object run() throws Exception; }
  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
  private Object main(Work work) throws Exception {
    AtomicReference<Object> value = new AtomicReference<>();
    AtomicReference<Exception> error = new AtomicReference<>();
    runOnMainSync(() -> { try { value.set(work.run()); } catch (Exception e) { error.set(e); } });
    if (error.get() != null) throw error.get();
    return value.get();
  }
  private Object field(String name) throws Exception {
    Field f = activity.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(activity);
  }
  private Object call(String name) throws Exception {
    Method m = activity.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(activity);
  }
  private void check(boolean okay, String name) {
    if (!okay) throw new AssertionError(name); checks.add(name);
  }
  private File fixture(String name) throws Exception {
    File file = new File(getTargetContext().getFilesDir(), "validation-" + name);
    try (InputStream in = getContext().getAssets().open(name); OutputStream out = new FileOutputStream(file)) {
      byte[] data = new byte[8192]; int n; while ((n=in.read(data))!=-1) out.write(data,0,n);
    }
    return file;
  }
  private void deliver(int request, int result, File file) throws Exception {
    main(() -> {
      Method m = activity.getClass().getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);
      m.setAccessible(true); m.invoke(activity,request,result,file==null?null:new Intent().setData(Uri.fromFile(file))); return null;
    });
  }
  private void open() {
    Intent i = new Intent(Intent.ACTION_MAIN).setClassName("org.openenglish.reader", "org.openenglish.reader.JourneyImmersionActivity")
        .putExtra("quickLanguage","en").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity = startActivitySync(i); waitForIdleSync();
  }
  private void back() {
    if (!getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
      throw new AssertionError("System Back unavailable in test automation");
    SystemClock.sleep(700);
    waitForIdleSync();
  }
  private Object audioPlayer() throws Exception {
    return main(() -> {
      Object audio = field("practice");
      Field f = audio.getClass().getDeclaredField("player");
      f.setAccessible(true);return f.get(audio);
    });
  }
  private int count() throws Exception {
    return (Integer)main(() -> {
      SharedPreferences p=(SharedPreferences)field("prefs");
      return p.getInt("quickRecords_"+field("language")+"_"+field("quickIndex"),0);
    });
  }
  private int position() throws Exception { return (Integer)main(() -> ((VideoView)field("player")).getCurrentPosition()); }
  private void waitVideo(int minimum) throws Exception {
    long until=SystemClock.elapsedRealtime()+12000;
    while (SystemClock.elapsedRealtime()<until) {
      if ((Boolean)main(() -> (Boolean)field("videoReady")) && position()>=minimum) return;
      SystemClock.sleep(150);
    }
    throw new AssertionError("Video did not prepare/seek: "+position());
  }
  private byte[] bytes(File file) throws Exception {
    try (InputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
      byte[] data=new byte[8192]; int n; while((n=in.read(data))!=-1)out.write(data,0,n); return out.toByteArray();
    }
  }
  @Override public void onStart() {
    Bundle result=new Bundle();
    try {
      File stereo = fixture("right-channel.m4a");
      Class<?> decoder = getTargetContext().getClassLoader().loadClass("org.openenglish.reader.JourneyToneAudio");
      Object samples = decoder.getMethod("decode",File.class).invoke(null,stereo);
      short[] pcm = (short[]) samples.getClass().getField("monoPcm").get(samples);
      int rate = samples.getClass().getField("sampleRate").getInt(samples);
      Class<?> contour = getTargetContext().getClassLoader().loadClass("org.openenglish.reader.JourneyToneContour");
      Object measured = contour.getMethod("analyze",short[].class,int.class).invoke(null,pcm,rate);
      check(measured.getClass().getField("valid").getBoolean(measured),
          "AAC stereo reference uses audible right channel when left is silent");
      double hz = measured.getClass().getField("medianHz").getDouble(measured);
      check(Math.abs(hz - 170) < 3,"decoded AAC reference retains pitch precision");
      open();
      main(() -> { if (((View)field("advancedArea")).getVisibility()!=View.VISIBLE)call("toggleAdvanced");return null; });
      deliver(62,Activity.RESULT_OK,fixture("source.srt"));
      deliver(63,Activity.RESULT_OK,fixture("translation.srt"));
      check((Integer)main(() -> ((List<?>)field("source")).size())==2,"Android SRT parser accepts ASS tags");
      deliver(62,Activity.RESULT_OK,fixture("source.vtt"));
      check((Integer)main(() -> ((List<?>)field("source")).size())==2,"Android WebVTT parser accepts ASS tags");
      check((Integer)main(() -> ((List<?>)field("source")).size())==2,"source imported: " + main(() -> ((TextView)field("status")).getText().toString()));
      check((Integer)main(() -> ((List<?>)field("translated")).size())==2,"translation imported");
      File invalid=new File(getTargetContext().getFilesDir(),"invalid.srt");
      try(FileOutputStream out=new FileOutputStream(invalid)){out.write("not subtitles".getBytes("UTF-8"));}
      deliver(62,Activity.RESULT_OK,invalid);
      check((Integer)main(() -> ((List<?>)field("source")).size())==2,"invalid import preserves previous subtitles");
      deliver(61,Activity.RESULT_OK,fixture("video.mp4"));
      waitVideo(0);
      main(() -> { ((VideoView)field("player")).start();return null; });
      waitVideo(500);
      check((Boolean)main(() -> ((VideoView)field("player")).isPlaying()),"real MP4 playback advances");
      check((Boolean)main(() -> ((TextView)field("sourceLabel")).getText().toString().equals("Test original line.")),"Android subtitle markup stripped");
      check((Boolean)main(() -> ((TextView)field("translationLabel")).getText().toString().contains("测试译文")),"subtitles follow actual video");
      main(() -> {
        ((VideoView)field("player")).pause();
        Method seek=activity.getClass().getDeclaredMethod("seekVideo",int.class);
        seek.setAccessible(true);seek.invoke(activity,3500);return null;
      });
      waitVideo(3000);
      check(position() <= 4000,"paused exact seek avoids previous keyframe");
      main(() -> { ((VideoView)field("player")).pause(); call("saveVideoPosition");activity.finish();return null; });
      waitForIdleSync();SystemClock.sleep(500);open();
      check((Integer)main(() -> ((List<?>)field("source")).size())==2,"source restored after Activity destruction");
      check((Integer)main(() -> ((List<?>)field("translated")).size())==2,"translation restored after Activity destruction");
      waitVideo(3000);
      check(!(Boolean)main(() -> ((VideoView)field("player")).isPlaying()),"recovered video waits for user playback");
      int before=count();
      main(() -> {call("toggleQuickPractice");return null;});SystemClock.sleep(1700);
      main(() -> {
        Method m=activity.getClass().getDeclaredMethod("startGuide",String.class,String.class,boolean.class);
        m.setAccessible(true);m.invoke(activity,"Hello","en",false);return null;
      });
      check(count()==before+1,"demonstration interruption records successful take exactly once");
      main(() -> call("finishPractice"));
      check(count()==before+1,"repeated stop does not double count");
      main(() -> {call("toggleQuickPractice");return null;});SystemClock.sleep(1700);
      main(() -> {activity.startActivity(new Intent().setClassName("org.openenglish.reader","org.openenglish.reader.JourneyActivity"));return null;});
      waitForIdleSync();SystemClock.sleep(500);
      check(count()==before+2,"background stop records successful take");
      back();SystemClock.sleep(300);
      File recorded=(File)main(() -> field("practice").getClass().getMethod("recordingFile").invoke(field("practice")));
      byte[] original=bytes(recorded);
      deliver(67,Activity.RESULT_OK,stereo);
      main(() -> call("playToneReference"));
      check(audioPlayer()!=null && (Boolean)main(() -> {
        Object audio=field("practice");Field f=audio.getClass().getDeclaredField("player");
        f.setAccessible(true);return ((android.media.MediaPlayer)f.get(audio)).isPlaying();
      }),
          "imported reference audio can be played in learning page");
      SystemClock.sleep(1400);waitForIdleSync();
      check(audioPlayer()==null,"reference completion releases shared player");
      main(() -> call("playToneReference"));
      Object referencePlayer=audioPlayer();
      main(() -> call("playPractice"));
      check(audioPlayer()!=null && audioPlayer()!=referencePlayer && Arrays.equals(original,bytes(recorded)),
          "A/B playback switches player without replacing learner recording");
      main(() -> call("stopTonePlayback"));
      check(audioPlayer()==null,"reference comparison stop releases playback");
      main(() -> call("exportPractice"));waitForIdleSync();SystemClock.sleep(500);
      File exported=new File(getTargetContext().getFilesDir(),"saved-recording.m4a");
      deliver(68,Activity.RESULT_OK,exported);
      check(original.length>256 && Arrays.equals(original,bytes(exported)),"exported M4A exactly matches recording");
      back();
      main(() -> call("exportPractice"));waitForIdleSync();SystemClock.sleep(500);deliver(68,Activity.RESULT_CANCELED,null);
      check(Arrays.equals(original,bytes(exported)),"canceled export leaves previous file unchanged");
      back();
      main(() -> {
        SharedPreferences p=(SharedPreferences)field("prefs");
        p.edit().putStringSet("saved-phrases",new HashSet<>(Arrays.asList("keep bookmark"))).commit();
        call("clearMedia");return null;
      });
      check((Integer)main(() -> ((List<?>)field("source")).size())==0,"clear current media removes subtitles");
      check((Boolean)main(() -> ((SharedPreferences)field("prefs")).getStringSet("saved-phrases",Collections.emptySet()).contains("keep bookmark")),"clearing media preserves bookmarks");
      main(() -> {activity.finish();return null;});waitForIdleSync();open();
      check((Integer)main(() -> ((List<?>)field("source")).size())==0 && main(() -> field("videoUri"))==null,"cleared selection stays cleared after restart");
      check(count()==before+2,"export and restart preserve practice count");
      result.putString("stream","\nIMMERSION_VALIDATION_PASS "+checks.size()+"\n"+checks+"\n");
      finish(Activity.RESULT_OK,result);
    } catch(Throwable error) {
      StringWriter out=new StringWriter();error.printStackTrace(new PrintWriter(out));
      result.putString("stream","\nIMMERSION_VALIDATION_FAIL\n"+out);finish(Activity.RESULT_CANCELED,result);
    }
  }
}
