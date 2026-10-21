package org.openenglish.reader;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * User-opt-in HTTPS client for a self-hosted open-source Cantonese ASR backend.
 * Does not contact a server until the user configures one and confirms this request.
 */
public final class JourneyCantoneseAsr {
  public static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;
  private JourneyCantoneseAsr() {}

  /** Reject non-HTTPS and unexpected paths before any microphone data is sent. */
  public static boolean validEndpoint(String address) {
    if (address == null || address.length() > 512) return false;
    try {
      URL parsed = new URL(address.trim());
      return "https".equalsIgnoreCase(parsed.getProtocol())
          && parsed.getHost() != null && !parsed.getHost().isEmpty()
          && new java.net.URI(address.trim()).getUserInfo() == null && parsed.getRef() == null
          && parsed.getQuery() == null
          && "/v1/yue/transcribe".equals(parsed.getPath());
    } catch (Exception e) {
      return false;
    }
  }

  public static final class Transcript {
    public final String language, text, model;
    Transcript(String lang, String recognized, String name) {
      language = lang;
      text = recognized;
      model = name;
    }
  }

  /**
   * Sends only the deliberately selected short M4A to the user's HTTPS endpoint.
   * This runs on a background thread. Token is never stored by this class.
   */
  public static Transcript transcribe(String endpoint, String bearer, File audio)
      throws Exception {
    if (!validEndpoint(endpoint))
      throw new IllegalArgumentException("请填写 HTTPS 地址，且路径为 /v1/yue/transcribe");
    if (bearer == null || bearer.trim().length() < 16 || bearer.length() > 512)
      throw new IllegalArgumentException("请填写服务端设置的访问令牌（至少 16 字符）");
    if (audio == null || !audio.isFile() || audio.length() < 256
        || audio.length() > MAX_AUDIO_BYTES)
      throw new IllegalArgumentException("录音文件缺失或超过 2MB，不能上传");
    HttpsURLConnection conn = (HttpsURLConnection) new URL(endpoint.trim()).openConnection();
    try {
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(90000);
      conn.setInstanceFollowRedirects(false);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Authorization", "Bearer " + bearer.trim());
      conn.setRequestProperty("Content-Type", "audio/mp4");
      conn.setRequestProperty("Accept", "application/json");
      conn.setFixedLengthStreamingMode(audio.length());
      conn.setDoOutput(true);
      try (OutputStream out = conn.getOutputStream(); FileInputStream in = new FileInputStream(audio)) {
        byte[] chunk = new byte[8192];
        int count;
        while ((count = in.read(chunk)) != -1) out.write(chunk, 0, count);
      }
      int status = conn.getResponseCode();
      if (status != 200) throw new IllegalStateException(
          status == 401 ? "认证失败：检查服务器访问令牌"
            : "粤语服务响应 HTTP " + status + "，检查服务部署和录音格式");
      String json = readSmall(conn.getInputStream());
      JSONObject data = new JSONObject(json);
      String text = data.optString("text", "").trim();
      String lang = data.optString("language", "");
      String model = data.optString("model", "SenseVoiceSmall");
      if (text.isEmpty() || !"yue".equals(lang))
        throw new IllegalStateException("模型没有返回可用的粤语转写，请重试");
      return new Transcript(lang, text, model);
    } finally {
      conn.disconnect();
    }
  }

  private static String readSmall(InputStream stream) throws Exception {
    try (InputStream in = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] chunk = new byte[4096];
      int n;
      while ((n = in.read(chunk)) != -1) {
        if (output.size() + n > 32768)
          throw new IllegalStateException("识别结果太大，已拒绝");
        output.write(chunk, 0, n);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
