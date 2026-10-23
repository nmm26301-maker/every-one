package org.openenglish.reader;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.*;

/** Whisper-compatible protocol adapter; reuses the existing endpoint, reader and cancellable job. */
public final class JourneyCaptionService {
  private final CloudClient.ConnectionFactory factory;
  public JourneyCaptionService() { this(url -> (HttpURLConnection) url.openConnection()); }
  public JourneyCaptionService(CloudClient.ConnectionFactory factory) { this.factory = factory; }

  public JourneyCaptionDraft transcribe(String base,String model,String key,String language,
      File audio,int startMs,int durationMs,CloudClient.Job job) throws Exception {
    if (model.trim().isEmpty() || model.length()>200 || model.contains("\r") || model.contains("\n"))
      throw new IOException("请填写有效的转写模型名称");
    if (audio == null || !audio.isFile() || audio.length()<44 || audio.length()>24000000)
      throw new IOException("音频片段无效或超过 24MB");
    job.check();
    HttpURLConnection connection = factory.open(new URL(CloudClient.endpoint(base,"audio/transcriptions")));
    job.attach(connection);
    try {
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(15000); connection.setReadTimeout(90000);
      connection.setRequestMethod("POST"); connection.setDoOutput(true);
      if (!key.isEmpty()) connection.setRequestProperty("Authorization","Bearer "+key);
      String boundary="EveryoneCaption"+Long.toHexString(System.nanoTime());
      connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
      connection.setChunkedStreamingMode(65536);
      try (OutputStream out=connection.getOutputStream()) {
        field(out,boundary,"model",model);
        field(out,boundary,"response_format","verbose_json");
        field(out,boundary,"timestamp_granularities[]","segment");
        // Do not force English for Cantonese. The selected multilingual model detects it.
        if ("en".equals(language) || "ja".equals(language)) field(out,boundary,"language",language);
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.wav\""
            +"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (InputStream in=new FileInputStream(audio)) {
          byte[] bytes=new byte[65536]; int count;
          while((count=in.read(bytes))!=-1) { job.check(); out.write(bytes,0,count); }
        }
        out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
      }
      job.check(); int status=connection.getResponseCode(); job.check();
      if (status<200 || status>=300) throw new IOException("转写服务未完成请求（HTTP "+status+"）");
      JSONObject response=new JSONObject(AiClient.read(connection.getInputStream(),2000000));
      JSONArray raw=response.optJSONArray("segments");
      if (raw==null || raw.length()>1000) throw new IOException("服务未返回 segments，请选择支持 verbose_json 时间轴的模型");
      List<JourneyCaptionDraft.Segment> segments=new ArrayList<>();
      for(int i=0;i<raw.length();i++) {
        JSONObject item=raw.getJSONObject(i);
        segments.add(new JourneyCaptionDraft.Segment(item.getDouble("start"),item.getDouble("end"),item.getString("text")));
      }
      job.check(); return new JourneyCaptionDraft(segments,startMs,durationMs);
    } finally { connection.disconnect(); }
  }

  private static void field(OutputStream out,String boundary,String name,String value) throws IOException {
    out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"
        +value+"\r\n").getBytes(StandardCharsets.UTF_8));
  }
}
