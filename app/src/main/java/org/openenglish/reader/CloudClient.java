package org.openenglish.reader;

import android.util.Base64;
import java.io.*;
import java.net.*;
import java.util.concurrent.CancellationException;
import org.json.*;

/** User configured HTTPS services. No keys or response bodies appear in errors. */
public final class CloudClient {
  public interface ConnectionFactory {
    HttpURLConnection open(URL url) throws Exception;
  }

  public interface Delta {
    void text(String complete);
  }

  public static class Job {
    private volatile boolean cancelled;
    private volatile boolean committing;
    private HttpURLConnection connection;

    public synchronized void cancel() {
      if (committing) return;
      cancelled = true;
      if (connection != null) connection.disconnect();
    }

    public void check() {
      if (!committing && (cancelled || Thread.currentThread().isInterrupted()))
        throw new CancellationException("已取消");
    }

    /** Used for the final local database transaction, after cancellable preparation has completed. */
    public synchronized void beginCommit() { check(); committing = true; }

    synchronized void attach(HttpURLConnection c) {
      check();
      connection = c;
    }

    public boolean cancelled() {
      return cancelled;
    }
  }

  private android.content.Context localContext;
  private final ConnectionFactory factory;

  public CloudClient() {
    this(url -> (HttpURLConnection) url.openConnection());
  }

  public CloudClient(android.content.Context context) { this(); localContext=context.getApplicationContext(); }

  public CloudClient(ConnectionFactory factory) {
    this.factory = factory;
  }

  public static String endpoint(String base, String path) throws Exception {
    String s = base.trim().replaceAll("/+$", "");
    URI u = new URI(s);
    if (!"https".equalsIgnoreCase(u.getScheme())
        || u.getHost() == null
        || u.getUserInfo() != null
        || u.getQuery() != null
        || u.getFragment() != null) throw new IOException("请填写有效的 HTTPS 服务地址");
    return s.endsWith("/" + path) ? s : s + "/" + path;
  }

  private HttpURLConnection open(String url, String key, Job job) throws Exception {
    job.check();
    HttpURLConnection c = factory.open(new URL(url));
    job.attach(c);
    c.setInstanceFollowRedirects(false);
    c.setConnectTimeout(15000);
    c.setReadTimeout(90000);
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
    return c;
  }

  private InputStream response(HttpURLConnection c, Job job) throws Exception {
    job.check();
    int code = c.getResponseCode();
    job.check();
    if (code < 200 || code >= 300) {
      if (code == 401 || code == 403) throw new IOException("服务拒绝访问（" + code + "），请检查密钥和权限");
      if (code == 429) throw new IOException("服务额度不足或请求过多（429）");
      throw new IOException("服务返回 HTTP " + code + "，请检查地址、模型与文件格式");
    }
    return c.getInputStream();
  }

  public String chat(
      String base, String model, String key, String system, JSONArray history, Job job, Delta delta)
      throws Exception {
    if (localContext!=null && LocalAI.enabled(localContext)) return LocalAI.chat(localContext,system,history,job,delta);
    if (model.trim().isEmpty()) throw new IOException("请设置模型名称");
    JSONArray messages =
        new JSONArray().put(new JSONObject().put("role", "system").put("content", system));
    for (int i = Math.max(0, history.length() - 30); i < history.length(); i++) {
      JSONObject m = history.getJSONObject(i);
      messages.put(
          new JSONObject().put("role", m.getString("role")).put("content", m.getString("content")));
    }
    HttpURLConnection c = open(endpoint(base, "chat/completions"), key, job);
    try {
      c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      c.setRequestProperty("Accept", "text/event-stream");
      byte[] payload =
          new JSONObject()
              .put("model", model)
              .put("messages", messages)
              .put("max_tokens", 1200)
              .put("stream", true)
              .toString()
              .getBytes("UTF-8");
      c.setFixedLengthStreamingMode(payload.length);
      try (OutputStream out = c.getOutputStream()) {
        job.check();
        out.write(payload);
      }
      InputStream in = response(c, job);
      String type = c.getContentType();
      if (type != null && !type.contains("text/event-stream")) {
        JSONObject data = new JSONObject(AiClient.read(in, 2000000));
        String result =
            data.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "");
        job.check();
        if (result.trim().isEmpty()) throw new IOException("模型返回空回复");
        delta.text(result);
        return result;
      }
      StringBuilder text = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) {
          job.check();
          if (line.length() > 2000000) throw new IOException("响应过大");
          if (!line.startsWith("data:")) continue;
          String data = line.substring(5).trim();
          if (data.equals("[DONE]")) break;
          if (data.isEmpty()) continue;
          JSONObject o = new JSONObject(data);
          if (o.has("error")) throw new IOException("模型报告服务错误");
          JSONArray choices = o.optJSONArray("choices");
          if (choices == null || choices.length() == 0) continue;
          JSONObject d = choices.getJSONObject(0).optJSONObject("delta");
          if (d != null && !d.isNull("content")) {
            String part = d.optString("content", "");
            text.append(part);
            if (text.length() > 200000) throw new IOException("回复过长");
            delta.text(text.toString());
          }
        }
      }
      job.check();
      if (text.toString().trim().isEmpty()) throw new IOException("模型未返回文字，请检查流式接口兼容性");
      return text.toString();
    } finally {
      c.disconnect();
    }
  }

  public JSONObject transcribe(
      String base, String model, String key, File wav, boolean timestamps, Job job)
      throws Exception {
    if (localContext!=null && LocalAI.enabled(localContext)) return LocalAI.transcribe(localContext,wav,timestamps,job);
    if (model.trim().isEmpty()) throw new IOException("请设置转写模型");
    if (wav.length() > 24000000) throw new IOException("音频片段过大");
    String boundary = "EnglishStudio" + Long.toHexString(System.nanoTime());
    HttpURLConnection c = open(endpoint(base, "audio/transcriptions"), key, job);
    try {
      c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      c.setChunkedStreamingMode(65536);
      try (OutputStream out = c.getOutputStream()) {
        field(out, boundary, "model", model);
        field(out, boundary, "language", "en");
        field(out, boundary, "response_format", timestamps ? "verbose_json" : "json");
        if (timestamps) field(out, boundary, "timestamp_granularities[]", "segment");
        out.write(
            ("--"
                    + boundary
                    + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"clip.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n")
                .getBytes("UTF-8"));
        try (InputStream in = new FileInputStream(wav)) {
          byte[] b = new byte[65536];
          int n;
          while ((n = in.read(b)) != -1) {
            job.check();
            out.write(b, 0, n);
          }
        }
        out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
      }
      JSONObject result = new JSONObject(AiClient.read(response(c, job), 2000000));
      job.check();
      if (!result.has("text")) throw new IOException("转写服务未返回文字");
      if (timestamps && result.optJSONArray("segments") == null)
        throw new IOException("服务未返回 segments 时间轴，请使用支持 verbose_json 的模型");
      return result;
    } finally {
      c.disconnect();
    }
  }

  private void field(OutputStream out, String b, String name, String value) throws Exception {
    out.write(
        ("--"
                + b
                + "\r\nContent-Disposition: form-data; name=\""
                + name
                + "\"\r\n\r\n"
                + value
                + "\r\n")
            .getBytes("UTF-8"));
  }

  public JSONObject assess(String region, String key, File wav, String reference, Job job)
      throws Exception {
    if (!region.matches("[a-z0-9-]{3,40}") || key.trim().isEmpty())
      throw new IOException("请填写 Azure Speech 区域和密钥");
    HttpURLConnection c =
        open(
            "https://"
                + region
                + ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed",
            "",
            job);
    try {
      JSONObject params =
          new JSONObject()
              .put("ReferenceText", reference)
              .put("GradingSystem", "HundredMark")
              .put("Granularity", "Phoneme")
              .put("Dimension", "Comprehensive")
              .put("EnableMiscue", true);
      c.setRequestProperty("Ocp-Apim-Subscription-Key", key);
      c.setRequestProperty(
          "Pronunciation-Assessment",
          Base64.encodeToString(params.toString().getBytes("UTF-8"), Base64.NO_WRAP));
      c.setRequestProperty("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000");
      c.setFixedLengthStreamingMode(wav.length());
      try (InputStream in = new FileInputStream(wav);
          OutputStream out = c.getOutputStream()) {
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) != -1) {
          job.check();
          out.write(b, 0, n);
        }
      }
      JSONObject result = new JSONObject(AiClient.read(response(c, job), 2000000));
      job.check();
      if (!"Success".equals(result.optString("RecognitionStatus")))
        throw new IOException("服务未能评测这段录音，请重新清晰朗读");
      return result;
    } finally {
      c.disconnect();
    }
  }
}
