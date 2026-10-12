package org.openenglish.reader;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

/** All learning data shares the existing learning.db. Media stays in app files. */
public class StudioStore extends LearningStore {
  public StudioStore(Context c) {
    super(c);
  }

  public StudioStore(Context c, String name) {
    super(c, name);
  }

  public static final String REVIEW_PLAN_TABLE = "CREATE TABLE IF NOT EXISTS review_plan(material INTEGER NOT NULL,reference TEXT NOT NULL,attempt INTEGER NOT NULL,due INTEGER NOT NULL CHECK(due>=0),interval INTEGER NOT NULL CHECK(interval IN (0,1,3,7,14,30)),PRIMARY KEY(material,reference))";

  public static void create(SQLiteDatabase db) {
    db.execSQL(REVIEW_PLAN_TABLE);
    db.execSQL("CREATE TABLE IF NOT EXISTS portable_state(id INTEGER PRIMARY KEY CHECK(id=1),root TEXT NOT NULL,settings TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS materials(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT"
            + " NULL,title TEXT NOT NULL,uri TEXT NOT NULL DEFAULT '',body TEXT NOT NULL DEFAULT"
            + " '',cues TEXT NOT NULL DEFAULT '[]',position INTEGER NOT NULL DEFAULT 0,transcribed"
            + " INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT"
            + " NULL,scene TEXT NOT NULL,draft TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY AUTOINCREMENT,session INTEGER"
            + " NOT NULL,role TEXT NOT NULL,content TEXT NOT NULL,created INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX IF NOT EXISTS messages_session ON messages(session,id)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS practice(id INTEGER PRIMARY KEY AUTOINCREMENT,material INTEGER"
            + " NOT NULL,anchor INTEGER NOT NULL,reference TEXT NOT NULL,heard TEXT NOT NULL"
            + " DEFAULT '',recording TEXT NOT NULL DEFAULT '',assessment TEXT NOT NULL DEFAULT"
            + " '',created INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS annotations(id INTEGER PRIMARY KEY AUTOINCREMENT,material"
            + " INTEGER NOT NULL,anchor INTEGER NOT NULL,kind TEXT NOT NULL,en TEXT NOT NULL,zh"
            + " TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");
  }

  public static void migrateChat(SQLiteDatabase db) {
    try (Cursor c =
        db.rawQuery(
            "SELECT kind,en,created FROM items WHERE kind IN ('user','assistant') ORDER BY id",
            null)) {
      if (!c.moveToFirst()) return;
      ContentValues v =
          values("title", "旧版口语对话", "scene", "日常聊天", "created", System.currentTimeMillis());
      long id = db.insertOrThrow("sessions", null, v);
      do {
        db.insertOrThrow(
            "messages",
            null,
            values(
                "session",
                id,
                "role",
                c.getString(0),
                "content",
                c.getString(1),
                "created",
                c.getLong(2)));
      } while (c.moveToNext());
    }
  }

  public static ContentValues values(Object... a) {
    ContentValues v = new ContentValues();
    for (int i = 0; i < a.length; i += 2) {
      String k = (String) a[i];
      Object x = a[i + 1];
      if (x instanceof Number) v.put(k, ((Number) x).longValue());
      else v.put(k, String.valueOf(x));
    }
    return v;
  }

  public JSONArray query(String sql, String... args) {
    JSONArray a = new JSONArray();
    try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
      while (c.moveToNext()) {
        JSONObject o = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++)
          try {
            o.put(
                c.getColumnName(i),
                c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
          } catch (JSONException e) {
            throw new IllegalStateException(e);
          }
        a.put(o);
      }
    }
    return a;
  }

  public JSONArray materials() {
    return query("SELECT * FROM materials ORDER BY created DESC,id DESC");
  }

  public JSONObject material(long id) {
    return query("SELECT * FROM materials WHERE id=?", "" + id).optJSONObject(0);
  }

  public long addMaterial(String type, String title, String uri, String body) {
    return getWritableDatabase()
        .insertOrThrow(
            "materials",
            null,
            values(
                "type",
                type,
                "title",
                title,
                "uri",
                uri,
                "body",
                body,
                "created",
                System.currentTimeMillis()));
  }

  public void updateMaterial(long id, ContentValues v) {
    getWritableDatabase().update("materials", v, "id=?", new String[] {"" + id});
  }

  public void removeMaterial(long id) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (String t : new String[] {"practice", "annotations", "review_plan"})
        db.delete(t, "material=?", new String[] {"" + id});
      db.delete("materials", "id=?", new String[] {"" + id});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public JSONArray sessions() {
    return query("SELECT * FROM sessions ORDER BY created DESC,id DESC");
  }

  public long addSession(String title, String scene) {
    return getWritableDatabase()
        .insertOrThrow(
            "sessions",
            null,
            values("title", title, "scene", scene, "created", System.currentTimeMillis()));
  }

  public JSONObject session(long id) {
    return query("SELECT * FROM sessions WHERE id=?", "" + id).optJSONObject(0);
  }

  public void draft(long id, String draft) {
    getWritableDatabase()
        .update("sessions", values("draft", draft), "id=?", new String[] {"" + id});
  }

  public void removeSession(long id) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("messages", "session=?", new String[] {"" + id});
      db.delete("sessions", "id=?", new String[] {"" + id});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public JSONArray messages(long id) {
    return query(
        "SELECT role,content,id,created FROM messages WHERE session=? ORDER BY id", "" + id);
  }

  public JSONArray context(long id) {
    return query(
        "SELECT role,content FROM (SELECT * FROM messages WHERE session=? ORDER BY id DESC LIMIT"
            + " 30) ORDER BY id",
        "" + id);
  }

  public long message(long id, String role, String text) {
    return getWritableDatabase()
        .insertOrThrow(
            "messages",
            null,
            values(
                "session",
                id,
                "role",
                role,
                "content",
                text,
                "created",
                System.currentTimeMillis()));
  }

  public long practice(
      long material,
      int anchor,
      String reference,
      String heard,
      String recording,
      String assessment) {
    SQLiteDatabase db = getWritableDatabase();
    long now = System.currentTimeMillis();
    db.beginTransaction();
    try {
      boolean dictation=recording.isEmpty() && !heard.trim().isEmpty() && !reference.isEmpty();
      ReviewPlanner.Plan previous=null;
      if (dictation) {
        try (Cursor c=db.rawQuery(PREVIOUS_REVIEW_SQL,new String[]{""+material,reference})) {
          if (c.moveToFirst()) previous=new ReviewPlanner.Plan(c.getInt(0),c.getLong(1));
        }
      }
      long id = db.insertOrThrow("practice", null, values("material", material, "anchor", anchor,
          "reference", reference, "heard", heard, "recording", recording, "assessment", assessment, "created", now));
      if (dictation) {
        ReviewPlanner.Plan plan = ReviewPlanner.afterWriting(now, TrainingCore.reviewMatch(reference,heard)==100,previous,TimeZone.getDefault());
        if (db.insertWithOnConflict("review_plan", null, values("material",material,"reference",reference,
            "attempt",id,"due",plan.due,"interval",plan.interval),SQLiteDatabase.CONFLICT_REPLACE)<0) throw new IllegalStateException("复习计划保存失败");
      }
      db.setTransactionSuccessful(); return id;
    } finally { db.endTransaction(); }
  }

  public void updatePractice(long id, String heard, String assessment) {
    getWritableDatabase()
        .update(
            "practice",
            values("heard", heard, "assessment", assessment),
            "id=?",
            new String[] {"" + id});
  }

  public JSONArray practices(long material, int anchor) {
    return query(
        "SELECT * FROM practice WHERE material=? AND anchor=? ORDER BY created DESC,id DESC",
        "" + material,
        "" + anchor);
  }

  // Shared by native paging/export and the host SQLite regression fixture.
  public static final String PRACTICE_FILTER = " FROM practice p JOIN materials m ON m.id=p.material WHERE (?='全部' OR (?='录音' AND p.recording<>'') OR (?='听写' AND p.recording='' AND p.heard<>'')) AND (p.reference LIKE ? ESCAPE '!' OR p.heard LIKE ? ESCAPE '!' OR m.title LIKE ? ESCAPE '!')";
  public static final String DAY_SENTENCES = "SELECT count(*) AS n FROM (SELECT material,anchor,reference FROM practice WHERE created>=? AND created<? GROUP BY material,anchor,reference)";
  public static final String DAY_ATTEMPTS = "SELECT count(*) AS n FROM practice WHERE created>=? AND created<?";

  @Override
  public void onOpen(SQLiteDatabase db) {
    super.onOpen(db);
    if (!db.isReadOnly()) {
      db.execSQL(REVIEW_PLAN_TABLE);
      db.execSQL("CREATE TABLE IF NOT EXISTS portable_state(id INTEGER PRIMARY KEY CHECK(id=1),root TEXT NOT NULL,settings TEXT NOT NULL)");
      db.execSQL("CREATE INDEX IF NOT EXISTS practice_reference_time ON practice(material,reference,created,id)");
      db.execSQL("CREATE INDEX IF NOT EXISTS practice_created ON practice(created)");
      db.execSQL("CREATE INDEX IF NOT EXISTS practice_material_anchor ON practice(material,anchor,created)");
    }
  }

  private String[] practiceArgs(String search, String kind) {
    if (!Arrays.asList("全部", "录音", "听写").contains(kind)) throw new IllegalArgumentException("训练类型");
    String pattern = TrainingCore.searchPattern(search);
    return new String[] {kind, kind, kind, pattern, pattern, pattern};
  }

  public JSONArray practicePage(String search, String kind, int limit, int offset) {
    if (limit < 1 || limit > 50 || offset < 0) throw new IllegalArgumentException("分页范围");
    return query("SELECT p.*,m.title AS material_title" + PRACTICE_FILTER + " ORDER BY p.created DESC,p.id DESC LIMIT " + limit + " OFFSET " + offset, practiceArgs(search, kind));
  }

  public int filteredPracticeCount(String search, String kind) {
    return query("SELECT count(*) AS n" + PRACTICE_FILTER, practiceArgs(search, kind)).optJSONObject(0).optInt("n");
  }

  public JSONObject practiceRecord(long id) {
    return query("SELECT p.*,m.title AS material_title FROM practice p JOIN materials m ON m.id=p.material WHERE p.id=?", "" + id).optJSONObject(0);
  }

  public boolean removePractice(long id) {
    SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
    try {
      db.delete("review_plan", "attempt=?", new String[]{""+id});
      boolean removed = db.delete("practice", "id=?", new String[]{""+id}) > 0;
      db.setTransactionSuccessful(); return removed;
    } finally { db.endTransaction(); }
  }

  public int trainedSentences(long start, long end) {
    return query(DAY_SENTENCES, "" + start, "" + end).optJSONObject(0).optInt("n");
  }

  public int attempts(long start, long end) {
    return query(DAY_ATTEMPTS, "" + start, "" + end).optJSONObject(0).optInt("n");
  }

  public void exportPractices(java.io.Writer out, String search, String kind, CloudClient.Job job) throws Exception {
    out.write("\uFEFF材料,句子编号,训练类型,训练时间,原句,输入或识别文字,文字匹配百分比,录音文件名\r\n");
    java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    try (Cursor c = getReadableDatabase().rawQuery("SELECT p.*,m.title AS material_title" + PRACTICE_FILTER + " ORDER BY p.created DESC,p.id DESC", practiceArgs(search, kind))) {
      while (c.moveToNext()) {
        job.check();
        String recording = c.getString(c.getColumnIndexOrThrow("recording"));
        String reference = c.getString(c.getColumnIndexOrThrow("reference"));
        String heard = c.getString(c.getColumnIndexOrThrow("heard"));
        int score = heard.isEmpty() ? -1 : TrainingCore.reviewMatch(reference, heard);
        String[] row = {c.getString(c.getColumnIndexOrThrow("material_title")),
          "" + (c.getInt(c.getColumnIndexOrThrow("anchor")) + 1), recording.isEmpty() ? "听写" : "录音",
          date.format(new Date(c.getLong(c.getColumnIndexOrThrow("created")))), reference, heard,
          score < 0 ? "" : "" + score, recording};
        for (int i = 0; i < row.length; i++) {
          if (i > 0) out.write(',');
          out.write(TrainingCore.csvCell(row[i]));
        }
        out.write("\r\n");
      }
    }
  }

  public static final String REVIEW_SQL = "SELECT p.*,m.title AS material_title,m.cues AS material_cues FROM practice p JOIN materials m ON m.id=p.material WHERE m.type IN ('audio','video') AND p.id IN (SELECT MAX(t.id) FROM practice t JOIN (SELECT material,reference,MAX(created) AS latest FROM practice WHERE recording='' AND heard<>'' GROUP BY material,reference) g ON t.material=g.material AND t.reference=g.reference AND t.created=g.latest WHERE t.recording='' AND t.heard<>'' GROUP BY t.material,t.reference) ORDER BY p.created DESC,p.id DESC";

  public static final String PLAN_REFERENCE_SQL = "SELECT heard FROM practice WHERE id=? AND material=? AND reference=? AND recording='' AND heard<>''";
  public static final String PREVIOUS_REVIEW_SQL = "SELECT q.interval,q.due FROM review_plan q JOIN practice p ON p.id=q.attempt AND p.material=q.material AND p.reference=q.reference WHERE q.material=? AND q.reference=? AND p.recording='' AND p.heard<>'' AND p.id IN (SELECT id FROM (" + REVIEW_SQL + "))";
  public static final String REVIEW_SCHEDULE_SQL = "SELECT p.*,m.title AS material_title,m.cues AS material_cues,q.attempt AS planned_attempt,q.due AS planned_due,q.interval AS planned_interval FROM practice p JOIN materials m ON m.id=p.material LEFT JOIN review_plan q ON q.material=p.material AND q.reference=p.reference AND q.attempt=p.id WHERE p.id IN (SELECT id FROM (" + REVIEW_SQL + ")) ORDER BY COALESCE(q.due,0),p.created,p.id";
  public static final String DEFER_REVIEW_SQL = "INSERT OR REPLACE INTO review_plan(material,reference,attempt,due,interval) SELECT p.material,p.reference,p.id,?,COALESCE(q.interval,0) FROM practice p LEFT JOIN review_plan q ON q.material=p.material AND q.reference=p.reference AND q.attempt=p.id WHERE p.id=? AND p.reference<>'' AND p.id IN (SELECT id FROM (" + REVIEW_SQL + "))";

  public boolean deferReview(long attempt, boolean tomorrow) {
    SQLiteDatabase db = getWritableDatabase();
    long now = System.currentTimeMillis();
    long due = tomorrow ? ReviewPlanner.morningAfter(now,1,TimeZone.getDefault()) : now;
    db.beginTransaction();
    try {
      db.execSQL(DEFER_REVIEW_SQL,new Object[]{due,attempt});
      boolean updated;
      try (Cursor c = db.rawQuery("SELECT changes()",null)) { c.moveToFirst(); updated = c.getInt(0)>0; }
      db.setTransactionSuccessful(); return updated;
    } finally { db.endTransaction(); }
  }

  public JSONObject reviewPage(int offset, int limit, CloudClient.Job job) throws Exception {
    return reviewPage(offset,limit,"待巩固",job);
  }

  public JSONObject reviewPage(int offset, int limit, String filter, CloudClient.Job job) throws Exception {
    if (offset < 0 || limit < 1 || limit > 30) throw new IllegalArgumentException("错句分页范围");
    long now = System.currentTimeMillis();
    ReviewPlanner.include(filter,0,false,0,now);
    int total = 0, dueCount = 0, pending = 0, planned = 0; JSONArray records = new JSONArray();
    Map<Long,List<LearningCore.Cue>> cache = new LinkedHashMap<Long,List<LearningCore.Cue>>(8,.75f,true) {
      protected boolean removeEldestEntry(Map.Entry<Long,List<LearningCore.Cue>> e) { return size() > 8; }
    };
    job.check();
    try (Cursor cursor = getReadableDatabase().rawQuery(REVIEW_SCHEDULE_SQL, null)) {
      while (cursor.moveToNext()) {
        job.check();
        long material = cursor.getLong(cursor.getColumnIndexOrThrow("material"));
        String reference = cursor.getString(cursor.getColumnIndexOrThrow("reference"));
        String written = cursor.getString(cursor.getColumnIndexOrThrow("heard"));
        if (reference.isEmpty()) continue;
        int match = TrainingCore.reviewMatch(reference, written);
        boolean hasPlan = !cursor.isNull(cursor.getColumnIndexOrThrow("planned_attempt"));
        if (match == 100 && !hasPlan) continue;
        long due = hasPlan ? cursor.getLong(cursor.getColumnIndexOrThrow("planned_due")) : 0;
        int interval = hasPlan ? cursor.getInt(cursor.getColumnIndexOrThrow("planned_interval")) : 0;
        List<LearningCore.Cue> current = cache.get(material);
        if (current == null) {
          try { current = cues(cursor.getString(cursor.getColumnIndexOrThrow("material_cues"))); }
          catch (Exception e) { current = new ArrayList<>(); }
          cache.put(material,current);
        }
        int anchor = TrainingCore.resolveCue(current,cursor.getInt(cursor.getColumnIndexOrThrow("anchor")),reference);
        if (anchor < 0) continue;
        planned++; if (match != 100) pending++; if (due <= now) dueCount++;
        if (!ReviewPlanner.include(filter,match,hasPlan,due,now)) continue;
        if (total >= offset && records.length() < limit) {
          records.put(new JSONObject().put("id",cursor.getLong(cursor.getColumnIndexOrThrow("id")))
              .put("material",material).put("anchor",anchor).put("reference",reference).put("heard",written)
              .put("material_title",cursor.getString(cursor.getColumnIndexOrThrow("material_title")))
              .put("created",cursor.getLong(cursor.getColumnIndexOrThrow("created"))).put("match",match)
              .put("due",due).put("interval",interval).put("is_due",due<=now));
        }
        total++;
      }
    }
    return new JSONObject().put("total",total).put("offset",offset).put("records",records)
        .put("due_count",dueCount).put("pending_count",pending).put("planned_count",planned).put("filter",filter);
  }

  public int practiceCount() {
    return query("SELECT count(*) AS n FROM practice").optJSONObject(0).optInt("n");
  }

  public long annotate(long material, int anchor, String kind, String en, String zh) {
    return getWritableDatabase()
        .insertOrThrow(
            "annotations",
            null,
            values(
                "material",
                material,
                "anchor",
                anchor,
                "kind",
                kind,
                "en",
                en,
                "zh",
                zh,
                "created",
                System.currentTimeMillis()));
  }

  public JSONArray annotations(long material, int anchor) {
    return query(
        "SELECT * FROM annotations WHERE material=? AND anchor=? ORDER BY id DESC",
        "" + material,
        "" + anchor);
  }

  public static JSONArray cues(List<LearningCore.Cue> c) throws JSONException {
    JSONArray a = new JSONArray();
    for (LearningCore.Cue x : c)
      a.put(new JSONObject().put("start", x.start).put("end", x.end).put("text", x.text));
    return a;
  }

  public static List<LearningCore.Cue> cues(String json) throws JSONException {
    JSONArray a = new JSONArray(json);
    if (a.length() > 50000) throw new JSONException("字幕数量过多");
    List<LearningCore.Cue> list = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      long s = o.getLong("start"), e = o.getLong("end");
      String t = o.getString("text");
      if (s < 0 || e <= s || e > Integer.MAX_VALUE || t.length() > 30000)
        throw new JSONException("无效字幕");
      list.add(new LearningCore.Cue(s, e, t));
    }
    Collections.sort(list, LearningCore.CUE_ORDER);
    return list;
  }

  @Override
  public JSONObject backup() throws JSONException {
    SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
    try {
      JSONObject out=super.backup().put("schema",2);
      for (String t:new String[]{"materials","sessions","messages","practice","annotations"})
        out.put(t,query("SELECT * FROM " + t + " ORDER BY id"));
      out.put("review_plan",query("SELECT * FROM review_plan ORDER BY material,reference"));
      db.setTransactionSuccessful(); return out;
    } finally { db.endTransaction(); }
  }

  @Override
  public void restore(JSONObject data) throws JSONException {
    int schema = data.getInt("schema");
    if (schema != 1 && schema != 2) throw new JSONException("未知备份版本");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (String t : new String[] {"review_plan", "messages", "practice", "annotations", "materials", "sessions"})
        db.delete(t, null, null);
      JSONObject legacy = new JSONObject(data.toString()).put("schema", 1);
      super.restore(legacy);
      if (schema == 1) migrateChat(db);
      else {
        String[][] cols = {
          {
            "materials",
            "id",
            "type",
            "title",
            "uri",
            "body",
            "cues",
            "position",
            "transcribed",
            "created"
          },
          {"sessions", "id", "title", "scene", "draft", "created"},
          {"messages", "id", "session", "role", "content", "created"},
          {
            "practice",
            "id",
            "material",
            "anchor",
            "reference",
            "heard",
            "recording",
            "assessment",
            "created"
          },
          {"annotations", "id", "material", "anchor", "kind", "en", "zh", "created"}
        };
        Set<String> ints =
            new HashSet<>(
                Arrays.asList(
                    "id", "position", "transcribed", "created", "session", "material", "anchor"));
        for (String[] table : cols) {
          JSONArray rows = data.getJSONArray(table[0]);
          if (rows.length() > 50000) throw new JSONException("记录数量过多");
          for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.getJSONObject(i);
            ContentValues v = new ContentValues();
            for (int j = 1; j < table.length; j++) {
              String k = table[j];
              if (ints.contains(k)) {
                long n = o.getLong(k);
                if (n < 0 || (k.equals("id") && n == 0)) throw new JSONException("无效编号");
                v.put(k, n);
              } else {
                String s = o.getString(k);
                if (s.length() > 2000000) throw new JSONException("记录过长");
                v.put(k, s);
              }
            }
            if (table[0].equals("materials")) {
              if (!Arrays.asList("audio", "video", "text").contains(o.getString("type")))
                throw new JSONException("无效材料类型");
              cues(o.getString("cues"));
            }
            if (table[0].equals("messages")
                && !Arrays.asList("user", "assistant").contains(o.getString("role")))
              throw new JSONException("无效消息类型");
            if (table[0].equals("practice")
                && !o.getString("recording").isEmpty()
                && !o.getString("recording").matches("studio-recordings/[a-zA-Z0-9_.-]+\\.m4a"))
              throw new JSONException("无效录音路径");
            db.insertOrThrow(table[0], null, v);
          }
        }
        for (String t : new String[] {"practice", "annotations"})
          try (Cursor c =
              db.rawQuery(
                  "SELECT 1 FROM "
                      + t
                      + " WHERE material NOT IN (SELECT id FROM materials) LIMIT 1",
                  null)) {
            if (c.moveToFirst()) throw new JSONException("材料关联缺失");
          }
        try (Cursor c =
            db.rawQuery(
                "SELECT 1 FROM messages WHERE session NOT IN (SELECT id FROM sessions) LIMIT 1",
                null)) {
          if (c.moveToFirst()) throw new JSONException("对话关联缺失");
        }
        if (data.has("review_plan")) {
          JSONArray plans = data.getJSONArray("review_plan");
          if (plans.length()>50000) throw new JSONException("复习计划过多");
          for (int i=0;i<plans.length();i++) {
            JSONObject plan=plans.getJSONObject(i);
            long material=plan.getLong("material"),attempt=plan.getLong("attempt"),due=plan.getLong("due");
            long rawInterval=plan.getLong("interval");
            String reference=plan.getString("reference");
            if (material<=0 || attempt<=0 || due<0 || due>ReviewPlanner.MAX_DUE || rawInterval<0 || rawInterval>30 ||
                !ReviewPlanner.validInterval((int)rawInterval) || reference.isEmpty() || reference.length()>30000)
              throw new JSONException("无效复习计划");
            try (Cursor c=db.rawQuery(PLAN_REFERENCE_SQL,new String[]{""+attempt,""+material,reference})) {
              if (!c.moveToFirst()) throw new JSONException("复习训练关联缺失");
              if (rawInterval>0 && TrainingCore.reviewMatch(reference,c.getString(0))!=100)
                throw new JSONException("错句不能使用正确复习间隔");
            }
            db.insertOrThrow("review_plan",null,values("material",material,"reference",reference,
                "attempt",attempt,"due",due,"interval",rawInterval));
          }
        }
      }
      db.setTransactionSuccessful();
    } catch (JSONException e) {
      throw e;
    } catch (Exception e) {
      throw new JSONException("无效备份：" + e.getClass().getSimpleName());
    } finally {
      db.endTransaction();
    }
  }
}
