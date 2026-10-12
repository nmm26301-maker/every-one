package org.openenglish.reader;

import java.io.*;
import java.net.*;

public final class MediaDownload {
  public static File download(File directory, String input, CloudClient.Job job) throws Exception {
    URL url = new URL(input);
    HttpURLConnection c = null;
    File result = null;
    try {
      for (int redirects = 0; redirects < 6; redirects++) {
        job.check();
        if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null)
          throw new IOException("仅支持直接音视频文件的 HTTPS 链接");
        c = (HttpURLConnection) url.openConnection();
        job.attach(c);
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
          String next = c.getHeaderField("Location");
          if (next == null) throw new IOException("下载链接重定向无效");
          url = new URL(url, next);
          c.disconnect();
          c = null;
          continue;
        }
        if (code != 200) throw new IOException("下载服务返回 HTTP " + code);
        String type = c.getContentType();
        if (type == null
            || type.contains("text/")
            || type.contains("html")
            || type.contains("json")) throw new IOException("此链接不是音视频文件，请使用直接文件链接");
        String length = c.getHeaderField("Content-Length");
        if (length != null) {
          try {
            if (Long.parseLong(length) > 300L * 1024 * 1024) throw new IOException("文件超过 300 MB");
          } catch (NumberFormatException ignored) {
            /* Unknown length still bounded while streaming. */
          }
        }
        directory.mkdirs();
        result =
            new File(
                directory,
                "download-"
                    + System.currentTimeMillis()
                    + (type.startsWith("video/") ? ".mp4" : ".audio"));
        long total = 0;
        try (InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(result)) {
          byte[] b = new byte[65536];
          int n;
          while ((n = in.read(b)) != -1) {
            job.check();
            total += n;
            if (total > 300L * 1024 * 1024) throw new IOException("文件超过 300 MB");
            out.write(b, 0, n);
          }
        }
        if (total < 100) throw new IOException("音视频文件为空");
        job.check();
        return result;
      }
      throw new IOException("链接重定向过多");
    } catch (Exception e) {
      if (result != null) result.delete();
      throw e;
    } finally {
      if (c != null) c.disconnect();
    }
  }
}
