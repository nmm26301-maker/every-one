package org.openenglish.validation;

import android.app.Instrumentation;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import org.json.*;
import org.openenglish.reader.*;

/** Isolated SQLite fixtures and mocked HTTP protocols; never contacts cloud services. */
public class StudioValidation extends Instrumentation {
  int checks;

  void check(boolean yes, String name) {
    if (!yes) throw new AssertionError(name);
    checks++;
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  public void onStart() {
    Context c = getTargetContext();
    Bundle out = new Bundle();
    StudioStore db = null;
    File demo = null, wav = null;
    try {
      c.deleteDatabase("studio-validation.db");
      db = new StudioStore(c, "studio-validation.db");
      long audio = db.addMaterial("audio", "my lesson", "file:///audio", ""),
          article = db.addMaterial("text", "essay", "", "Small habits matter.");
      List<LearningCore.Cue> cues =
          Arrays.asList(
              new LearningCore.Cue(0, 1000, "Hello."),
              new LearningCore.Cue(1100, 2000, "How are you?"));
      db.updateMaterial(
          audio, StudioStore.values("cues", StudioStore.cues(cues).toString(), "position", 700));
      check(
          db.materials().length() == 2 && db.material(audio).getInt("position") == 700,
          "separate material and position");
      check(
          StudioStore.cues(db.material(audio).getString("cues")).size() == 2,
          "subtitle persistence");
      long a = db.addSession("coffee", "cafe"), b = db.addSession("travel", "hotel");
      db.message(a, "user", "latte");
      db.message(a, "assistant", "hot or iced?");
      db.message(b, "user", "reservation");
      check(
          db.context(a).length() == 2 && !db.context(a).toString().contains("reservation"),
          "topic context isolation");
      db.draft(a, "unsent draft");
      check(db.session(a).getString("draft").equals("unsent draft"), "topic draft storage");
      for (int i = 0; i < 35; i++) db.message(a, "user", "message " + i);
      check(db.context(a).length() == 30, "bounded recent context");
      long p = db.practice(audio, 0, "Hello", "Helo", "studio-recordings/take-1.m4a", "");
      db.updatePractice(p, "Hello", "{\"score\":90}");
      check(
          db.practices(audio, 0).getJSONObject(0).getString("heard").equals("Hello"),
          "saved recognition updates own recording");
      db.annotate(article, 0, "word", "habits", "习惯");
      check(db.annotations(article, 0).length() == 1, "article anchored annotation");
      db.add("word", "coffee", "咖啡");
      JSONObject backup = db.backup();
      check(
          backup.getInt("schema") == 2 && !backup.toString().contains("token"),
          "expanded backup excludes secrets");
      db.removeMaterial(audio);
      check(
          db.practiceCount() == 0 && db.material(article) != null,
          "material delete removes related training only");
      db.restore(backup);
      check(
          db.materials().length() == 2
              && db.practiceCount() == 1
              && db.session(a).getString("draft").equals("unsent draft"),
          "expanded backup restore");
      JSONObject invalid = new JSONObject(backup.toString());
      invalid
          .getJSONArray("materials")
          .getJSONObject(0)
          .put("cues", "[{\"start\":10,\"end\":1,\"text\":\"bad\"}]");
      try {
        db.restore(invalid);
        throw new AssertionError("invalid cue accepted");
      } catch (JSONException expected) {
        check(
            db.materials().length() == 2 && db.practiceCount() == 1,
            "invalid backup rolls back all tables");
      }
      invalid = new JSONObject(backup.toString());
      invalid.getJSONArray("messages").getJSONObject(0).put("session", 9999);
      try {
        db.restore(invalid);
        throw new AssertionError("orphan accepted");
      } catch (JSONException expected) {
        check(db.context(a).length() == 30, "orphan backup rejected transactionally");
      }
      JSONObject old =
          new JSONObject()
              .put("schema", 1)
              .put("app", "org.openenglish.reader")
              .put(
                  "items",
                  new JSONArray()
                      .put(
                          new JSONObject()
                              .put("kind", "user")
                              .put("en", "My old chat")
                              .put("zh", "")
                              .put("created", 1)))
              .put("days", new JSONArray())
              .put("completed", new JSONArray());
      db.restore(old);
      check(
          db.sessions().length() == 1
              && db.context(db.sessions().getJSONObject(0).getLong("id"))
                  .toString()
                  .contains("My old chat"),
          "schema 1 chat becomes accessible topic");
      check(
          db.materials().length() == 0 && db.practiceCount() == 0,
          "old backup intentionally replaces studio data");
      db.close();
      db = null;
      c.deleteDatabase("studio-migrate.db");
      File legacy = c.getDatabasePath("studio-migrate.db");
      legacy.getParentFile().mkdirs();
      SQLiteDatabase v1 = SQLiteDatabase.openOrCreateDatabase(legacy, null);
      v1.execSQL(
          "CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,en TEXT NOT"
              + " NULL,zh TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL,due INTEGER NOT NULL"
              + " DEFAULT 0,interval INTEGER NOT NULL DEFAULT 0)");
      v1.execSQL("CREATE UNIQUE INDEX unique_vocab ON items(en) WHERE kind='word'");
      v1.execSQL("CREATE TABLE days(day TEXT PRIMARY KEY,count INTEGER NOT NULL DEFAULT 0)");
      v1.execSQL("CREATE TABLE completed(sentence INTEGER PRIMARY KEY)");
      v1.execSQL(
          "INSERT INTO items(kind,en,zh,created) VALUES('word','morning','早晨',1),('user','I like"
              + " coffee','',2),('assistant','Hot or iced?','',3)");
      v1.setVersion(1);
      v1.close();
      db = new StudioStore(c, "studio-migrate.db");
      check(
          db.items("word").length() == 1 && db.getReadableDatabase().getVersion() == 2,
          "real SQLite v1 to v2 upgrade");
      check(
          db.sessions().length() == 1
              && db.context(db.sessions().getJSONObject(0).getLong("id")).length() == 2,
          "legacy chat migrated without deletion");
      check(db.conversation().length() == 2, "legacy chat rows retained");
      KeyVault.save(c, "speech", "speech-test-secret");
      KeyVault.save(c, "assessment", "assessment-test-secret");
      check(
          KeyVault.read(c, "speech").equals("speech-test-secret")
              && KeyVault.read(c, "assessment").equals("assessment-test-secret"),
          "independent encrypted service keys");
      check(
          !c.getSharedPreferences("speech", 0).getString("token", "").contains("speech-test"),
          "no plaintext service key");
      KeyVault.save(c, "speech", "");
      KeyVault.save(c, "assessment", "");
      check(OfflineDictionary.count(c) == 20000, "20,000 offline dictionary entries");
      check(
          OfflineDictionary.lookup(c, "Coffee").getString("translation").contains("咖啡"),
          "case-normalized bilingual dictionary");
      check(
          OfflineDictionary.lookup(c, "habits").getString("word").equals("habit"),
          "inflected word lookup");
      check(OfflineDictionary.lookup(c, "zzzzmissingword") == null, "missing word not invented");
      demo = new File(c.getCacheDir(), "validation-demo.m4a");
      try (InputStream in = c.getAssets().open("studio-demo.m4a");
          OutputStream stream = new FileOutputStream(demo)) {
        byte[] bytes = new byte[65536];
        int n;
        while ((n = in.read(bytes)) != -1) stream.write(bytes, 0, n);
      }
      MediaMetadataRetriever meta = new MediaMetadataRetriever();
      meta.setDataSource(demo.getAbsolutePath());
      long length =
          Long.parseLong(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
      meta.release();
      check(length > 20000 && length < 70000, "real AAC demo with valid duration");
      wav = AudioDecoder.clip(c, Uri.fromFile(demo), 7000, 10000, new CloudClient.Job());
      byte[] bytes = read(wav);
      ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      check(
          new String(bytes, 0, 4, "US-ASCII").equals("RIFF")
              && header.getInt(24) == 16000
              && header.getShort(22) == 1,
          "decoder mono 16 kHz WAV header");
      check(
          Math.abs(bytes.length - 44 - 96000) < 640,
          "actual clipped 3-second audio: WAV bytes=" + bytes.length);
      Mock stream =
          new Mock(
              "text/event-stream",
              "data: {\"choices\":[{\"delta\":{\"content\":null}}]}\n\n"
                  + "data: {\"choices\":[{\"delta\":{\"content\":\"Hello 世界\"}}]}\n\n"
                  + "data: {\"choices\":[{\"delta\":{\"content\":\"null\"}}]}\n\n"
                  + "data: [DONE]\n\n");
      CloudClient client = new CloudClient(u -> stream);
      List<String> events = new ArrayList<>();
      String answer =
          client.chat(
              "https://example.org/v1",
              "test",
              "mock-secret",
              "system",
              new JSONArray(),
              new CloudClient.Job(),
              events::add);
      check(
          answer.equals("Hello 世界null") && events.size() == 2,
          "stream UTF-8, JSON null and literal null");
      check(new JSONObject(stream.sent.toString("UTF-8")).getBoolean("stream"), "stream flag sent");
      Mock json =
          new Mock("application/json", "{\"choices\":[{\"message\":{\"content\":\"fallback\"}}]}");
      check(
          new CloudClient(u -> json)
              .chat(
                  "https://example.org/v1",
                  "test",
                  "",
                  "system",
                  new JSONArray(),
                  new CloudClient.Job(),
                  x -> {})
              .equals("fallback"),
          "JSON fallback");
      CloudClient.Job cancelled = new CloudClient.Job();
      cancelled.cancel();
      try {
        client.chat(
            "https://example.org/v1", "test", "", "system", new JSONArray(), cancelled, x -> {});
        throw new AssertionError("cancel ignored");
      } catch (java.util.concurrent.CancellationException expected) {
        check(true, "cancelled job sends nothing");
      }
      Mock asr =
          new Mock(
              "application/json",
              "{\"text\":\"Hello\",\"segments\":[{\"start\":0.0,\"end\":1.0,\"text\":\"Hello\"}]}");
      JSONObject transcript =
          new CloudClient(u -> asr)
              .transcribe(
                  "https://example.org/v1", "whisper-1", "test", wav, true, new CloudClient.Job());
      check(transcript.getJSONArray("segments").length() == 1, "transcription timestamps returned");
      String multipart = asr.sent.toString("ISO-8859-1");
      check(
          multipart.contains("verbose_json")
              && multipart.contains("timestamp_granularities[]")
              && multipart.contains("RIFF")
              && multipart.contains("clip.wav"),
          "real multipart WAV and timestamp request");
      Mock azure =
          new Mock(
              "application/json",
              "{\"RecognitionStatus\":\"Success\",\"NBest\":[{\"PronunciationAssessment\":{\"AccuracyScore\":88}}]}");
      new CloudClient(u -> azure).assess("eastasia", "test", wav, "Hello", new CloudClient.Job());
      JSONObject parameters =
          new JSONObject(
              new String(
                  android.util.Base64.decode(
                      azure.getRequestProperty("Pronunciation-Assessment"),
                      android.util.Base64.DEFAULT),
                  "UTF-8"));
      check(
          parameters.getString("Granularity").equals("Phoneme")
              && parameters.getString("ReferenceText").equals("Hello"),
          "Azure real phoneme assessment request");
      check(
          azure.getRequestProperty("Content-Type").contains("16000")
              && azure.sent.size() == wav.length(),
          "Azure sends actual WAV bytes");
      Mock denied = new Mock("application/json", "secret leaked response");
      denied.code = 401;
      try {
        new CloudClient(u -> denied)
            .chat(
                "https://example.org/v1",
                "test",
                "mock-secret",
                "system",
                new JSONArray(),
                new CloudClient.Job(),
                x -> {});
        throw new AssertionError("401 accepted");
      } catch (IOException expected) {
        check(
            !expected.getMessage().contains("mock-secret")
                && !expected.getMessage().contains("leaked"),
            "error redacts key and body");
      }
      try {
        CloudClient.endpoint("http://example.org", "audio/transcriptions");
        throw new AssertionError("HTTP allowed");
      } catch (IOException expected) {
        check(true, "HTTPS enforced for speech");
      }
      out.putString(
          "result",
          "PASS: "
              + checks
              + " storage, migration, decoder, dictionary and mocked cloud protocol checks");
      finish(-1, out);
    } catch (Throwable e) {
      out.putString("error", e.toString());
      finish(0, out);
    } finally {
      if (db != null) db.close();
      c.deleteDatabase("studio-validation.db");
      c.deleteDatabase("studio-migrate.db");
      if (demo != null) demo.delete();
      if (wav != null) wav.delete();
    }
  }

  byte[] read(File f) throws Exception {
    try (InputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return out.toByteArray();
    }
  }

  static class Mock extends HttpURLConnection {
    ByteArrayOutputStream sent = new ByteArrayOutputStream();
    String type, body;
    int code = 200;

    Mock(String type, String body) throws Exception {
      super(new URL("https://example.org/mock"));
      this.type = type;
      this.body = body;
    }

    public void disconnect() {}

    public boolean usingProxy() {
      return false;
    }

    public void connect() {}

    public OutputStream getOutputStream() {
      return sent;
    }

    public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(body.getBytes("UTF-8"));
    }

    public InputStream getErrorStream() {
      return getInputStreamUnchecked();
    }

    private InputStream getInputStreamUnchecked() {
      return new ByteArrayInputStream(new byte[0]);
    }

    public int getResponseCode() {
      return code;
    }

    public String getContentType() {
      return type;
    }
  }
}
