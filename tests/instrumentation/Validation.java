package org.openenglish.validation;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.*;
import org.openenglish.reader.*;

public class Validation extends Instrumentation {
  int checks = 0;
  boolean recordingCheck = false, persistCheck = false;

  void check(boolean b, String name) {
    checks++;
    if (!b) throw new AssertionError(name);
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    recordingCheck = b != null && "true".equals(b.getString("recording"));
    persistCheck = b != null && "true".equals(b.getString("persist"));
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    LearningStore s = null;
    Context c = getTargetContext();
    try {
      if (recordingCheck) {
        java.io.File f = new java.io.File(c.getFilesDir(), "recordings/sentence_0.m4a");
        check(f.exists() && f.length() > 200, "AAC recording saved");
        android.media.MediaMetadataRetriever m = new android.media.MediaMetadataRetriever();
        m.setDataSource(f.getAbsolutePath());
        long duration =
            Long.parseLong(
                m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
        m.release();
        check(duration > 0, "recording duration");
        result.putString(
            "result",
            "PASS: saved recording " + f.length() + " bytes, duration " + duration + " ms");
        finish(-1, result);
        return;
      }
      if (persistCheck) {
        s = new LearningStore(c);
        JSONArray w = s.items("word"), n = s.items("note");
        boolean foundWord = false, foundNote = false;
        for (int i = 0; i < w.length(); i++)
          foundWord |= "morning".equals(w.getJSONObject(i).optString("en"));
        for (int i = 0; i < n.length(); i++)
          foundNote |= "The cat is sleeping.".equals(n.getJSONObject(i).optString("en"));
        check(foundWord, "word persisted");
        check(foundNote, "shared note persisted");
        check(s.total() == 1, "unique learning progress persisted");
        result.putString(
            "result",
            "PASS: word, shared note and learning progress survived app and emulator restart");
        finish(-1, result);
        return;
      }
      c.deleteDatabase("validation.db");
      s = new LearningStore(c, "validation.db");
      long id = s.add("word", "coffee", "咖啡");
      check(id > 0, "insert word");
      check(s.add("word", "coffee", "重复") < 0, "duplicate word ignored");
      check(s.due() == 1, "new word due");
      s.review(id, true);
      check(s.due() == 0, "review schedule persisted");
      check(s.items("word").getJSONObject(0).getInt("interval") == 1, "first interval");
      s.add("note", "hello", "笔记");
      s.add("user", "my cat is white", "");
      s.add("assistant", "Tell me about your cat", "");
      check(s.conversation().length() == 2, "conversation memory");
      s.complete(1);
      s.complete(1);
      check(s.total() == 1 && s.today() == 2, "unique lessons versus practice count");
      JSONObject backup = s.backup();
      s.add("word", "ticket", "车票");
      s.restore(backup);
      check(s.items("word").length() == 1 && s.items("note").length() == 1, "backup restore");
      check(s.conversation().length() == 2, "chat restore");
      JSONObject invalid = new JSONObject(backup.toString());
      invalid.getJSONArray("items").getJSONObject(0).put("kind", "invalid");
      try {
        s.restore(invalid);
        throw new AssertionError("invalid import accepted");
      } catch (JSONException expected) {
        check(
            s.items("word").length() == 1 && s.conversation().length() == 2,
            "invalid import transaction rollback");
      }
      KeyVault.save(c, "test-key-not-real");
      check(KeyVault.read(c).equals("test-key-not-real"), "keystore encryption round trip");
      check(
          !c.getSharedPreferences("connection", 0).getString("token", "").contains("test-key"),
          "no plaintext key");
      KeyVault.save(c, "");
      check(KeyVault.read(c).isEmpty(), "clear key");
      check(
          AiClient.endpoint("https://example.org/v1/")
              .equals("https://example.org/v1/chat/completions"),
          "base path");
      check(
          AiClient.endpoint("https://example.org/v1/chat/completions")
              .endsWith("/chat/completions"),
          "full endpoint");
      try {
        AiClient.endpoint("http://example.org/v1");
        throw new AssertionError("http accepted");
      } catch (java.io.IOException expected) {
        check(true, "https enforced");
      }
      result.putString(
          "result", "PASS: " + checks + " Android storage, backup, key and endpoint checks");
      finish(-1, result);
    } catch (Throwable t) {
      result.putString("error", t.toString());
      finish(0, result);
    } finally {
      if (s != null) s.close();
      c.deleteDatabase("validation.db");
    }
  }
}
