package org.openenglish.reader;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.io.*;
import java.util.concurrent.*;

/** Cancellable local reader audio, offline system voice with bundled eSpeak fallback. */
public final class ReadingAudio {
  public interface Status { void update(String message); }
  private final Context context;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final Status status;
  private final AudioManager manager;
  private final AudioManager.OnAudioFocusChangeListener focus=change->{if(change<0)ui.post(this::stop);};
  private TextToSpeech tts;
  private boolean offline,active,closed;
  private int generation;
  private MediaPlayer player;
  private File wav;
  private CloudClient.Job job;
  private Runnable watchdog,done;
  private String text="";
  private float speed=1f;
  public ReadingAudio(Context c,Status listener){
    context=c.getApplicationContext();status=listener;manager=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
    tts=new TextToSpeech(context,result->{
      if(closed){if(tts!=null)tts.shutdown();return;}
      if(result==TextToSpeech.SUCCESS){offline=OfflineVoice.select(tts);tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
        public void onStart(String id){ui.post(()->{if(current(id))status.update("正在朗读 · 系统离线英语");});}
        public void onDone(String id){ui.post(()->{if(current(id))finish(generation);});}
        public void onError(String id){ui.post(()->{if(current(id)){offline=false;fallback(generation);}});}
      });}
      if(!active)status.update(offline?"离线英语语音已就绪":"内置离线英语朗读可用");
    });
  }
  private boolean current(String id){return !closed&&active&&id.equals("reader-audio-"+generation);}
  public void play(String s,float rate,Runnable completion){
    stop();if(closed)return;text=s;speed=rate;done=completion;active=true;final int token=generation;
    if(manager.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stop();status.update("暂时无法取得音频输出，请稍后再试");return;}
    status.update("正在准备朗读…");watchdog=()->{if(token==generation&&active){stop();status.update("朗读等待过久，已停止；可以重新播放");}};ui.postDelayed(watchdog,90000);
    if(offline){tts.setSpeechRate(speed);if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"reader-audio-"+token)==TextToSpeech.SUCCESS)return;offline=false;}
    fallback(token);
  }
  private void fallback(int token){
    if(closed||!active||token!=generation)return;
    if(job!=null)return;
    final CloudClient.Job request=new CloudClient.Job();job=request;final String phrase=text;
    worker.execute(()->{File file=null;
      try{
        float[] pcm=IslandSpeech.synthesize(context,"en-us",phrase,request);request.check();
        if(pcm==null||pcm.length==0)throw new IOException("Empty speech");
        file=File.createTempFile("reader-audio-",".wav",context.getCacheDir());LessonAudio.writeSpeech(file,pcm);request.check();final File ready=file;
        ui.post(()->{
          if(closed||!active||token!=generation||request.cancelled()){ready.delete();return;}
          wav=ready;
          try{
            player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(ready.getPath());
            player.setOnPreparedListener(p->{if(token==generation&&active){try{p.setPlaybackParams(new PlaybackParams().setSpeed(speed));p.start();status.update("正在朗读 · 内置离线英语");}catch(Exception e){failed(token);}}});
            player.setOnCompletionListener(p->finish(token));player.setOnErrorListener((p,a,b)->{failed(token);return true;});player.prepareAsync();
          }catch(Exception e){failed(token);}
        });
      }catch(Exception e){if(file!=null)file.delete();ui.post(()->{if(!request.cancelled())failed(token);});}
    });
  }
  private void failed(int token){if(token!=generation||closed)return;stop();status.update("朗读未能完成，请重试本段");}
  private void finish(int token){if(token!=generation||closed||!active)return;Runnable completion=done;stop();status.update("朗读结束 · 可以跟读或回听");if(completion!=null)completion.run();}
  public void stop(){
    boolean wasActive=active;generation++;active=false;done=null;
    if(watchdog!=null){ui.removeCallbacks(watchdog);watchdog=null;}
    if(tts!=null)tts.stop();if(job!=null){job.cancel();job=null;}
    if(player!=null){player.release();player=null;}if(wav!=null){wav.delete();wav=null;}
    manager.abandonAudioFocus(focus);if(wasActive&&!closed)status.update("朗读已停止");
  }
  public void close(){closed=true;stop();if(tts!=null)tts.shutdown();worker.shutdownNow();}
}
