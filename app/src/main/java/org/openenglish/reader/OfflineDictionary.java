package org.openenglish.reader;

import android.content.Context;
import android.database.*;
import android.database.sqlite.*;
import java.io.*;
import java.util.Locale;
import org.json.*;

public final class OfflineDictionary {
  private static synchronized File install(Context c) throws Exception {
    File target = new File(c.getFilesDir(), "lexicon-v1.db");
    if (target.exists() && target.length() > 100000) return target;
    File temp = new File(c.getFilesDir(), "lexicon-v1.tmp");
    try (InputStream in = c.getAssets().open("lexicon.db");
        OutputStream out = new FileOutputStream(temp)) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
    if (!temp.renameTo(target)) {
      temp.delete();
      throw new IOException("离线词典初始化失败");
    }
    return target;
  }

  public static JSONObject lookup(Context c, String input) throws Exception {
    String word = input.trim().toLowerCase(Locale.US).replaceAll("^[^a-z]+|[^a-z]+$", "");
    if (word.isEmpty() || word.length() > 80) return null;
    SQLiteDatabase db =
        SQLiteDatabase.openDatabase(
            install(c).getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    try {
      JSONObject entry = find(db, word);
      if (entry != null) return entry;
      try (Cursor forms =
          db.rawQuery("SELECT lemma FROM forms WHERE surface=?", new String[] {word})) {
        return forms.moveToFirst() ? find(db, forms.getString(0)) : null;
      }
    } finally {
      db.close();
    }
  }

  private static JSONObject find(SQLiteDatabase db, String word) throws Exception {
    try (Cursor c = db.rawQuery("SELECT * FROM lexicon WHERE word=?", new String[] {word})) {
      if (!c.moveToFirst()) return null;
      JSONObject o = new JSONObject();
      for (int i = 0; i < c.getColumnCount(); i++) o.put(c.getColumnName(i), c.getString(i));
      return o;
    }
  }

  public static int count(Context c) throws Exception {
    SQLiteDatabase db =
        SQLiteDatabase.openDatabase(
            install(c).getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    try (Cursor cursor = db.rawQuery("SELECT count(*) FROM lexicon", null)) {
      cursor.moveToFirst();
      return cursor.getInt(0);
    } finally {
      db.close();
    }
  }
}
