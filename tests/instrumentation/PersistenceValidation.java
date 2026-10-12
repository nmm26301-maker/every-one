package org.openenglish.validation;

import android.app.Instrumentation;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import java.io.File;
import org.json.*;
import org.openenglish.reader.StudioStore;

/** Read-only validation of the UI fixture after a signed update and process restart. */
public class PersistenceValidation extends Instrumentation {
  int checks;

  void check(boolean yes, String label) {
    if (!yes) throw new AssertionError(label);
    checks++;
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  public void onStart() {
    Bundle out = new Bundle();
    StudioStore s = new StudioStore(getTargetContext());
    try {
      check(s.materials().length() == 3, "demo, article and imported article persisted");
      long article = 0;
      for (int i = 0; i < s.materials().length(); i++) {
        JSONObject m = s.materials().getJSONObject(i);
        if (m.getString("title").equals("My first story")
            && m.getString("body").contains("Small habits")) article = m.getLong("id");
      }
      check(article > 0, "actual imported article body");
      check(s.practiceCount() >= 2, "recording and dictation history");
      check(
          s.items("word").length() >= 2 && s.annotations(article, 0).length() >= 1,
          "old vocabulary and article-selected word persisted");
      check(s.sessions().length() == 3, "legacy, coffee and travel topics");
      boolean draft = false;
      for (int i = 0; i < s.sessions().length(); i++) {
        JSONObject t = s.sessions().getJSONObject(i);
        if (t.getString("title").equals("咖啡店点单")) draft = t.getString("draft").contains("latte");
      }
      check(draft, "unsent per-topic draft");
      JSONArray records =
          s.query("SELECT recording FROM practice WHERE recording<>'' ORDER BY id DESC");
      check(records.length() > 0, "recording metadata");
      String path = records.getJSONObject(0).getString("recording");
      check(path.startsWith("studio-recordings/"), "private recording path");
      File f = new File(getTargetContext().getFilesDir(), path);
      check(f.isFile() && f.length() > 1000, "actual recorded file survives restart");
      MediaMetadataRetriever reader = new MediaMetadataRetriever();
      long duration;
      try {
        reader.setDataSource(f.getAbsolutePath());
        duration =
            Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
      } finally {
        reader.release();
      }
      check(duration > 500 && duration <= 31000, "valid saved AAC duration");
      out.putString(
          "result",
          "PASS: "
              + checks
              + " UI persistence checks; recording="
              + f.length()
              + " bytes / "
              + duration
              + " ms; materials=3, topics=3");
      finish(-1, out);
    } catch (Throwable e) {
      out.putString("error", e.toString());
      finish(0, out);
    } finally {
      s.close();
    }
  }
}
