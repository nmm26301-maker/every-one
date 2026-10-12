package org.openenglish.reader;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

public class LearningStore extends SQLiteOpenHelper {
  public LearningStore(Context c) {
    this(c, "learning.db");
  }

  public LearningStore(Context c, String name) {
    super(c, name, null, 2);
  }

  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,en TEXT NOT"
            + " NULL,zh TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL,due INTEGER NOT NULL"
            + " DEFAULT 0,interval INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE UNIQUE INDEX unique_vocab ON items(en) WHERE kind='word'");
    db.execSQL("CREATE TABLE days(day TEXT PRIMARY KEY,count INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE TABLE completed(sentence INTEGER PRIMARY KEY)");
    StudioStore.create(db);
  }

  public void onUpgrade(SQLiteDatabase db, int old, int next) {
    if (old < 2) {
      StudioStore.create(db);
      StudioStore.migrateChat(db);
    }
  }

  public long add(String kind, String en, String zh) {
    ContentValues v = new ContentValues();
    v.put("kind", kind);
    v.put("en", en.trim());
    v.put("zh", zh.trim());
    v.put("created", System.currentTimeMillis());
    return getWritableDatabase()
        .insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  public JSONArray items(String kind) {
    JSONArray a = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .query("items", null, "kind=?", new String[] {kind}, null, null, "created DESC")) {
      while (c.moveToNext()) {
        JSONObject o = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++)
          try {
            o.put(
                c.getColumnName(i),
                c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
          } catch (JSONException ignored) {
          }
        a.put(o);
      }
    }
    return a;
  }

  public void delete(long id) {
    getWritableDatabase().delete("items", "id=?", new String[] {"" + id});
  }

  public void clearChat() {
    getWritableDatabase().delete("items", "kind IN ('user','assistant')", null);
  }

  public JSONArray conversation() {
    JSONArray out = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT kind,en FROM (SELECT id,kind,en FROM items WHERE kind IN"
                    + " ('user','assistant') ORDER BY id DESC LIMIT 30) ORDER BY id",
                null)) {
      while (c.moveToNext())
        try {
          out.put(new JSONObject().put("role", c.getString(0)).put("content", c.getString(1)));
        } catch (JSONException ignored) {
        }
    }
    return out;
  }

  public void review(long id, boolean yes) {
    int interval = 0;
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT interval FROM items WHERE id=?", new String[] {"" + id})) {
      if (c.moveToFirst()) interval = c.getInt(0);
    }
    interval = LearningCore.nextInterval(interval, yes);
    ContentValues v = new ContentValues();
    v.put("interval", interval);
    v.put("due", LearningCore.nextDue(System.currentTimeMillis(), interval));
    getWritableDatabase().update("items", v, "id=?", new String[] {"" + id});
  }

  public int due() {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT count(*) FROM items WHERE kind='word' AND due<=?",
                new String[] {"" + System.currentTimeMillis()})) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  public void complete(int sentence) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("sentence", sentence);
      db.insertWithOnConflict("completed", null, v, SQLiteDatabase.CONFLICT_IGNORE);
      String day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
      db.execSQL("INSERT OR IGNORE INTO days(day,count) VALUES(?,0)", new Object[] {day});
      db.execSQL("UPDATE days SET count=count+1 WHERE day=?", new Object[] {day});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public int total() {
    try (Cursor c = getReadableDatabase().rawQuery("SELECT count(*) FROM completed", null)) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  public int today() {
    String d = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT count FROM days WHERE day=?", new String[] {d})) {
      return c.moveToFirst() ? c.getInt(0) : 0;
    }
  }

  public JSONObject backup() throws JSONException {
    JSONObject o = new JSONObject().put("schema", 1).put("app", "org.openenglish.reader");
    JSONArray items = new JSONArray(), days = new JSONArray(), done = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM items WHERE kind IN ('word','note','user','assistant')", null)) {
      while (c.moveToNext()) {
        JSONObject row = new JSONObject();
        for (int i = 0; i < c.getColumnCount(); i++)
          row.put(
              c.getColumnName(i),
              c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
        items.put(row);
      }
    }
    try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM days", null)) {
      while (c.moveToNext())
        days.put(new JSONObject().put("day", c.getString(0)).put("count", c.getInt(1)));
    }
    try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM completed", null)) {
      while (c.moveToNext()) done.put(c.getInt(0));
    }
    return o.put("items", items).put("days", days).put("completed", done);
  }

  public void restore(JSONObject data) throws JSONException {
    if (data.getInt("schema") != 1 || !"org.openenglish.reader".equals(data.getString("app")))
      throw new JSONException("不是本应用的学习备份");
    JSONArray items = data.getJSONArray("items"),
        days = data.getJSONArray("days"),
        done = data.getJSONArray("completed");
    if (items.length() > 50000 || days.length() > 10000 || done.length() > 10000)
      throw new JSONException("备份超出可导入的数量");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("items", null, null);
      db.delete("days", null, null);
      db.delete("completed", null, null);
      for (int i = 0; i < items.length(); i++) {
        JSONObject o = items.getJSONObject(i);
        String k = o.getString("kind");
        if (!Arrays.asList("word", "note", "user", "assistant").contains(k))
          throw new JSONException("无效数据类型");
        String en = o.getString("en"), zh = o.getString("zh");
        if (en.length() > 30000 || zh.length() > 30000) throw new JSONException("单条记录太长");
        ContentValues v = new ContentValues();
        v.put("kind", k);
        v.put("en", en);
        v.put("zh", zh);
        v.put("created", o.getLong("created"));
        v.put("due", o.optLong("due"));
        v.put("interval", Math.max(0, Math.min(90, o.optInt("interval"))));
        if (db.insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE) < 0)
          throw new JSONException("重复或无效记录");
      }
      for (int i = 0; i < days.length(); i++) {
        JSONObject o = days.getJSONObject(i);
        String d = o.getString("day");
        if (!d.matches("\\d{4}-\\d{2}-\\d{2}") || o.getInt("count") < 0)
          throw new JSONException("无效学习日期");
        ContentValues v = new ContentValues();
        v.put("day", d);
        v.put("count", o.getInt("count"));
        db.insertOrThrow("days", null, v);
      }
      for (int i = 0; i < done.length(); i++) {
        int s = done.getInt(i);
        if (s < 0 || s > 10000) throw new JSONException("无效句子编号");
        ContentValues v = new ContentValues();
        v.put("sentence", s);
        db.insertOrThrow("completed", null, v);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}
