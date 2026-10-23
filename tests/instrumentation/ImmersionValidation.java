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
  private void selectLine(int index) throws Exception {
    main(() -> {
      Method m=activity.getClass().getDeclaredMethod("showCue",int.class);
      m.setAccessible(true);m.invoke(activity,index);return null;
    });
  }
  private void consentLine(boolean yes) throws Exception {
    main(() -> {
      android.app.AlertDialog dialog=(android.app.AlertDialog)field("lineConfirm");
      dialog.getButton(yes?android.content.DialogInterface.BUTTON_POSITIVE:
          android.content.DialogInterface.BUTTON_NEGATIVE).performClick();return null;
    });
  }
  private String analysisText() throws Exception {
    return (String)main(() -> ((TextView)field("lineAnalysis")).getText().toString());
  }
  private void waitAnalysis(String text) throws Exception {
    long until=SystemClock.elapsedRealtime()+6000;
    while(SystemClock.elapsedRealtime()<until) {
      if(main(() -> field("lineJob"))==null && analysisText().contains(text))return;
      SystemClock.sleep(50);
    }
    throw new AssertionError("Analysis state: "+analysisText());
  }
  private static final class ReplyConnection extends java.net.HttpURLConnection {
    final ByteArrayOutputStream sent=new ByteArrayOutputStream();
    final int status;final String answer;
    final java.util.concurrent.CountDownLatch gate;
    ReplyConnection(java.net.URL url,int status,String answer,java.util.concurrent.CountDownLatch gate) {
      super(url);this.status=status;this.answer=answer;this.gate=gate;
    }
    public void connect() {}
    public boolean usingProxy() {return false;}
    public void disconnect() {}
    public OutputStream getOutputStream() {return sent;}
    public int getResponseCode() throws IOException {
      if(gate!=null)try{gate.await(6,java.util.concurrent.TimeUnit.SECONDS);}
      catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("interrupted");}
      return status;
    }
    public String getContentType() {return "application/json";}
    public InputStream getInputStream() {
      String json=answer.startsWith("{")?answer:"{\"choices\":[{\"message\":{\"content\":\""+answer+"\"}}]}";
      return new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }
  private void captionConsent(boolean yes) throws Exception {
    main(()->{
      android.app.AlertDialog dialog=(android.app.AlertDialog)field("captionDialog");
      dialog.getButton(yes?android.content.DialogInterface.BUTTON_POSITIVE:
          android.content.DialogInterface.BUTTON_NEGATIVE).performClick();return null;
    });
  }
  private String captionText() throws Exception {
    return (String)main(()->((TextView)field("captionStatus")).getText().toString());
  }
  private void waitCaption(String text) throws Exception {
    long until=SystemClock.elapsedRealtime()+12000;
    while(SystemClock.elapsedRealtime()<until) {
      if(main(()->field("captionJob"))==null && captionText().contains(text))return;
      SystemClock.sleep(50);
    }
    throw new AssertionError("Caption state: "+captionText());
  }
  private void captionChecks(File video) throws Exception {
    ClassLoader loader=getTargetContext().getClassLoader();
    Class<?> jobType=loader.loadClass("org.openenglish.reader.CloudClient$Job");
    Class<?> clipType=loader.loadClass("org.openenglish.reader.JourneyVideoClip");
    Method extract=clipType.getMethod("extract",Context.class,Uri.class,int.class,int.class,File.class,jobType);
    File clip=(File)extract.invoke(null,getTargetContext(),Uri.fromFile(video),3000,1000,
        getTargetContext().getCacheDir(),jobType.getConstructor().newInstance());
    byte[] wav=bytes(clip);clip.delete();
    java.nio.ByteBuffer header=java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    int rate=header.getInt(24),channels=header.getShort(22),count=header.getInt(40)/2;
    check(new String(wav,0,4,"UTF-8").equals("RIFF") && channels==1 && Math.abs(count-rate)<10,
        "real MP4 audio becomes a bounded one-second mono PCM WAV");
    int crossings=0;short previous=0;
    for(int i=44;i+1<wav.length;i+=2) {short sample=header.getShort(i);if(previous<0 && sample>=0)crossings++;previous=sample;}
    check(crossings>425 && crossings<455,"video audio seek trims to 440Hz region after two-second 220Hz lead-in");
    File delayedClip=(File)extract.invoke(null,getTargetContext(),Uri.fromFile(fixture("delayed-audio.mp4")),
        1000,2000,getTargetContext().getCacheDir(),jobType.getConstructor().newInstance());
    byte[] delayedWav=bytes(delayedClip);delayedClip.delete();
    java.nio.ByteBuffer delayedHeader=java.nio.ByteBuffer.wrap(delayedWav).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    int delayedRate=delayedHeader.getInt(24);long quiet=0,loud=0;
    for(int i=0;i<delayedRate/2;i++)quiet+=Math.abs((int)delayedHeader.getShort(44+i*2));
    for(int i=delayedRate+delayedRate/4;i<delayedRate*7/4;i++)loud+=Math.abs((int)delayedHeader.getShort(44+i*2));
    android.media.MediaExtractor timing=new android.media.MediaExtractor();
    timing.setDataSource(fixture("delayed-audio.mp4").getAbsolutePath());
    long firstPts=-1,seekPts=-1;
    for(int i=0;i<timing.getTrackCount();i++)if(timing.getTrackFormat(i).getString("mime").startsWith("audio/")) {
      timing.selectTrack(i);firstPts=timing.getSampleTime();timing.seekTo(1000000,android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      seekPts=timing.getSampleTime();break;
    }
    timing.release();
    check(delayedHeader.getInt(40)/2==delayedRate*2 && quiet<delayedRate*5L && loud>delayedRate*500L,
        "delayed video audio retains leading silence so generated timestamps stay aligned: rate="+delayedRate
            +" samples="+delayedHeader.getInt(40)/2+" quiet="+quiet+" loud="+loud+" firstPts="+firstPts+" seekPts="+seekPts);
    Object canceled=jobType.getConstructor().newInstance();jobType.getMethod("cancel").invoke(canceled);
    try {extract.invoke(null,getTargetContext(),Uri.fromFile(video),0,1000,getTargetContext().getCacheDir(),canceled);
      throw new AssertionError("Canceled extraction started");}
    catch(InvocationTargetException e){check(e.getCause() instanceof java.util.concurrent.CancellationException,
        "canceled extraction does not create an upload");}
    try {extract.invoke(null,getTargetContext(),Uri.fromFile(fixture("no-audio.mp4")),0,1000,
        getTargetContext().getCacheDir(),jobType.getConstructor().newInstance());throw new AssertionError("No audio accepted");}
    catch(InvocationTargetException e){check(e.getCause() instanceof IOException,"video without audio fails before upload");}
    java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
    AtomicReference<String> response=new AtomicReference<>("{\"segments\":[{\"start\":0.1,\"end\":0.5,\"text\":\"Draft caption\"}]}");
    AtomicReference<ReplyConnection> last=new AtomicReference<>();
    AtomicReference<java.util.concurrent.CountDownLatch> gate=new AtomicReference<>();
    Class<?> factory=loader.loadClass("org.openenglish.reader.CloudClient$ConnectionFactory");
    Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{factory},(p,m,args)->{
      requests.incrementAndGet();ReplyConnection c=new ReplyConnection((java.net.URL)args[0],200,response.get(),gate.get());
      last.set(c);return c;
    });
    Object service=loader.loadClass("org.openenglish.reader.JourneyCaptionService").getConstructor(factory).newInstance(proxy);
    String oldLanguage=(String)main(()->field("language"));
    main(()->{
      Field f=activity.getClass().getDeclaredField("captionService");f.setAccessible(true);f.set(activity,service);
      f=activity.getClass().getDeclaredField("captionLengthMs");f.setAccessible(true);f.setInt(activity,1000);
      f=activity.getClass().getDeclaredField("language");f.setAccessible(true);f.set(activity,"yue");
      activity.getSharedPreferences("speech",0).edit().clear().putString("base","https://example.invalid/v1")
          .putString("model","fixture-asr").commit();return null;
    });
    int oldCount=(Integer)main(()->((List<?>)field("source")).size());
    main(()->call("confirmCaptionClip"));
    check(requests.get()==0,"video audio is not uploaded before range and service consent");
    captionConsent(true);waitCaption("草稿");
    check((Integer)main(()->((List<?>)field("source")).size())==oldCount,
        "generated caption draft does not modify subtitles before preview approval");
    String sent=last.get().sent.toString("UTF-8");
    check(sent.contains("verbose_json") && sent.contains("timestamp_granularities[]") && sent.contains("RIFF")
        && !sent.contains("name=\"language\"") && last.get().sent.size()<64000,
        "caption protocol uploads only bounded WAV and does not force English for Cantonese");
    captionConsent(false);
    main(()->call("confirmCaptionClip"));captionConsent(true);waitCaption("草稿");captionConsent(true);
    check((Integer)main(()->((List<?>)field("source")).size())>oldCount
        && (Integer)main(()->((List<?>)field("translated")).size())==2,
        "approved draft replaces selected interval while retaining outside captions and translation");
    File cache=new File(getTargetContext().getFilesDir(),"immersion-source.txt");byte[] applied=bytes(cache);
    check(new String(applied,"UTF-8").contains("Draft caption"),"approved generated subtitles persist atomically");
    java.util.concurrent.CountDownLatch delayed=new java.util.concurrent.CountDownLatch(1);gate.set(delayed);
    main(()->call("confirmCaptionClip"));captionConsent(true);
    long until=SystemClock.elapsedRealtime()+8000;
    while(requests.get()<3 && SystemClock.elapsedRealtime()<until)SystemClock.sleep(30);
    main(()->call("stopCaptionWork"));delayed.countDown();SystemClock.sleep(300);waitForIdleSync();
    check(main(()->field("captionJob"))==null && main(()->field("captionDialog"))==null
        && Arrays.equals(applied,bytes(cache)),"canceling subtitle generation rejects late results and preserves source cache");
    gate.set(null);response.set("{\"text\":\"No timestamp response\"}");
    main(()->call("confirmCaptionClip"));captionConsent(true);waitCaption("未完成");
    check(Arrays.equals(applied,bytes(cache)),"timestamp-free ASR response cannot replace existing subtitles");
    main(()->call("exportSubtitles"));waitForIdleSync();SystemClock.sleep(500);
    File exported=new File(getTargetContext().getFilesDir(),"captions-export.srt");deliver(69,Activity.RESULT_OK,exported);
    check(Arrays.equals(applied,bytes(exported)),"exported SRT matches applied caption snapshot byte for byte");back();
    main(()->call("exportSubtitles"));waitForIdleSync();SystemClock.sleep(500);deliver(69,Activity.RESULT_CANCELED,null);
    check(Arrays.equals(applied,bytes(exported)),"canceling SRT export leaves previous file unchanged");back();
    main(()->{Field f=activity.getClass().getDeclaredField("language");f.setAccessible(true);f.set(activity,oldLanguage);return null;});
    File[] leftovers=getTargetContext().getCacheDir().listFiles((dir,name)->name.startsWith("everyone-caption-") && name.endsWith(".wav"));
    check(leftovers!=null && leftovers.length==0,"success, error and canceled generation remove temporary audio clips");
  }
  private void lineChecks() throws Exception {
    java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicInteger statusCode=new java.util.concurrent.atomic.AtomicInteger(200);
    AtomicReference<java.util.concurrent.CountDownLatch> gate=new AtomicReference<>();
    AtomicReference<ReplyConnection> last=new AtomicReference<>();
    ClassLoader loader=getTargetContext().getClassLoader();
    Class<?> client=loader.loadClass("org.openenglish.reader.CloudClient");
    Class<?> factory=loader.loadClass("org.openenglish.reader.CloudClient$ConnectionFactory");
    Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{factory},(p,m,args)->{
      int n=calls.incrementAndGet();
      ReplyConnection connection=new ReplyConnection((java.net.URL)args[0],statusCode.get(),
          n==1?"测试解析：自然译文和分块练习。":"LATE_STALE_REPLY",gate.get());
      last.set(connection);return connection;
    });
    Object instance=client.getConstructor(factory).newInstance(proxy);
    main(()->{
      Field f=activity.getClass().getDeclaredField("lineClient");f.setAccessible(true);f.set(activity,instance);
      activity.getSharedPreferences("connection",0).edit().clear().putString("base","https://example.invalid/v1")
          .putString("model","fixture-model").commit();return null;
    });
    selectLine(0);main(()->call("confirmLineAnalysis"));
    check(calls.get()==0 && (Boolean)main(()->((android.app.AlertDialog)field("lineConfirm")).isShowing()),
        "AI explanation requires explicit consent before text leaves page");
    consentLine(true);waitAnalysis("本页已缓存");
    check(analysisText().contains("自然译文"),"AI result appears in selected subtitle page");
    org.json.JSONObject sent=new org.json.JSONObject(last.get().sent.toString("UTF-8"));
    org.json.JSONArray messages=sent.getJSONArray("messages");
    check(sent.getString("model").equals("fixture-model") && messages.length()==2
        && messages.getJSONObject(1).getString("content").contains("Test original line.")
        && messages.getJSONObject(1).getString("content").contains("后文："),
        "existing cloud protocol sends bounded selected line and subtitle context only");
    main(()->call("confirmLineAnalysis"));
    check(calls.get()==1,"cached explanation does not issue duplicate requests");
    selectLine(1);main(()->call("confirmLineAnalysis"));consentLine(false);
    check(calls.get()==1,"canceling text consent does not call AI service");
    java.util.concurrent.CountDownLatch delayed=new java.util.concurrent.CountDownLatch(1);gate.set(delayed);
    main(()->call("confirmLineAnalysis"));consentLine(true);
    long until=SystemClock.elapsedRealtime()+3000;
    while(calls.get()<2 && SystemClock.elapsedRealtime()<until)SystemClock.sleep(20);
    selectLine(0);delayed.countDown();SystemClock.sleep(250);waitForIdleSync();
    check(main(()->field("lineJob"))==null && analysisText().contains("自然译文")
        && !analysisText().contains("LATE_STALE_REPLY"),"switching subtitle cancels request and rejects late reply");
    gate.set(null);statusCode.set(429);selectLine(1);main(()->call("confirmLineAnalysis"));consentLine(true);
    waitAnalysis("未完成");
    check(!analysisText().contains("本页已缓存"),"AI service errors remain retryable and are not cached");
    main(()->call("confirmLineAnalysis"));consentLine(false);
    java.util.concurrent.CountDownLatch background=new java.util.concurrent.CountDownLatch(1);gate.set(background);
    statusCode.set(200);main(()->call("confirmLineAnalysis"));consentLine(true);
    main(()->{activity.startActivity(new Intent().setClassName("org.openenglish.reader","org.openenglish.reader.JourneyActivity"));return null;});
    waitForIdleSync();SystemClock.sleep(300);background.countDown();
    check(main(()->field("lineJob"))==null,"leaving learning page cancels AI explanation");
    back();SystemClock.sleep(250);
    check(!analysisText().contains("LATE_STALE_REPLY"),"returning from background does not show canceled AI reply");
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
      lineChecks();
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
      captionChecks(fixture("video.mp4"));
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
