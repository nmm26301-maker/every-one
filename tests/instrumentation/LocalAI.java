package org.openenglish.reader;

/** Compile-only local-model path for cloud-only module tests; never part of full APK. */
public final class LocalAI {
  public static boolean enabled(android.content.Context context) { return false; }
  public static String chat(android.content.Context context, String system, org.json.JSONArray history,
      CloudClient.Job job, CloudClient.Delta delta) { throw new UnsupportedOperationException(); }
  public static org.json.JSONObject transcribe(android.content.Context context, java.io.File audio,
      boolean timestamps, CloudClient.Job job) { throw new UnsupportedOperationException(); }
}
