package org.openenglish.reader;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import java.io.File;
import java.io.IOException;

/**
 * 只在应用私有缓存录制跟读语音。录音临时保存，退出学习页即删除。
 * 无后台麦克风、无上传、无网络功能。
 */
public final class JourneyPracticeAudio {
  private MediaRecorder recorder;
  private MediaPlayer player;
  private File recording;
  private boolean capturing;

  /** 开始录音前须由 Activity 检查 RECORD_AUDIO 权限。 */
  public void start(File cacheDir) throws IOException {
    if (capturing) return;
    stopPlayback();
    deleteRecording();
    recording = File.createTempFile("everyone-practice-", ".m4a", cacheDir);
    MediaRecorder next = new MediaRecorder();
    try {
      next.setAudioSource(MediaRecorder.AudioSource.MIC);
      next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      next.setAudioEncodingBitRate(64000);
      next.setAudioSamplingRate(16000);
      next.setOutputFile(recording.getAbsolutePath());
      next.prepare();
      next.start();
      recorder = next;
      capturing = true;
    } catch (Exception e) {
      next.release();
      deleteRecording();
      throw new IOException("无法开启麦克风，请检查权限和其他占用麦克风的应用", e);
    }
  }

  /** 停止当前录音，过短的录音可能无法生成有效音频。 */
  public boolean stop() {
    if (!capturing) return recording != null && recording.isFile()
        && recording.length() > 256;
    capturing = false;
    MediaRecorder current = recorder;
    recorder = null;
    boolean ok = false;
    try {
      current.stop();
      ok = recording != null && recording.isFile() && recording.length() > 256;
    } catch (RuntimeException ignored) {
      // 系统对极短录音可能抛出 stop 失败；由界面通知用户重试。
    } finally {
      current.release();
    }
    if (!ok) deleteRecording();
    return ok;
  }

  /** 回放用户自己的录音；当文件丢失时返回 false。 */
  public boolean play() throws IOException {
    if (capturing) stop();
    if (recording == null || !recording.isFile()) return false;
    stopPlayback();
    MediaPlayer next = new MediaPlayer();
    try {
      next.setDataSource(recording.getAbsolutePath());
      next.prepare();
      next.setOnCompletionListener(mp -> stopPlayback());
      next.start();
      player = next;
      return true;
    } catch (Exception e) {
      next.release();
      throw new IOException("录音回放失败，可能是文件不完整", e);
    }
  }

  /** 仅返回已停止录制的私有录音，供本机音高分析使用。 */
  public File recordingFile() {
    return !capturing && recording != null && recording.isFile() ? recording : null;
  }

  public boolean isRecording() {
    return capturing;
  }

  public void stopPlayback() {
    MediaPlayer current = player;
    player = null;
    if (current != null) {
      try { current.stop(); } catch (IllegalStateException ignored) { }
      current.release();
    }
  }

  private void deleteRecording() {
    if (recording != null && recording.exists()) recording.delete();
    recording = null;
  }

  /**
   * 快捷练习切换句子时清理上一句录音，避免把旧录音误认作新句练习。
   * 仅删除本 Activity 创建的私有临时文件，不影响课程记录。
   */
  public void discard() {
    if (capturing) stop();
    stopPlayback();
    deleteRecording();
  }

  /** 页面退出/切后台停止麦克风，释放资源；页面销毁时删除音频。 */
  public void close() {
    discard();
  }
}
