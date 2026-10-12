package org.openenglish.reader;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Bounded, checksummed portable archive. No Android dependencies. */
public final class PortableZip {
  public static final long MAX_TOTAL = 2L * 1024 * 1024 * 1024;
  public static final long MAX_FILE = 350L * 1024 * 1024;
  public static final int MAX_ENTRIES = 10000;
  public interface Source { InputStream open() throws Exception; }
  public interface Check { void run(); }
  public interface Progress { void update(String name, int files, long bytes); }
  public static final class Receipt {
    public final long size;
    public final String sha;
    Receipt(long n, String s) { size = n; sha = s; }
  }
  private PortableZip() {}

  public static boolean allowed(String name) {
    return name.equals("magic.json") || name.equals("learning.json") || name.equals("settings.json") || name.equals("manifest.tsv")
        || name.matches("language-takes/[a-zA-Z0-9_-]{1,100}\\.wav")
        || name.matches("media/material-[1-9][0-9]*\\.bin")
        || name.matches("studio-recordings/[a-zA-Z0-9_.-]{1,180}\\.m4a")
        || name.matches("recordings/sentence_[0-9]{1,5}\\.m4a");
  }
  private static long limit(String name) {
    if (name.equals("magic.json")) return 4L * 1024 * 1024;
    if (name.startsWith("language-takes/")) return 400000;
    if (name.equals("manifest.tsv")) return 2L * 1024 * 1024;
    if (name.equals("learning.json")) return 20L * 1024 * 1024;
    if (name.equals("settings.json")) return 65536;
    return MAX_FILE;
  }
  private static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.US, "%02x", b & 255)); return s.toString();
  }
  private static Receipt copy(InputStream in, OutputStream out, long maximum, long remaining, Check check) throws Exception {
    MessageDigest hash = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[65536]; long size = 0; int n;
    while ((n = in.read(buffer)) != -1) {
      check.run();
      size += n;
      if (size > maximum || size > remaining) throw new IOException("迁移包超过文件或总大小限制");
      out.write(buffer, 0, n); hash.update(buffer, 0, n);
    }
    return new Receipt(size, hex(hash.digest()));
  }

  public static void write(OutputStream target, Map<String, Source> sources, Check check, Progress progress) throws Exception {
    if (!sources.containsKey("learning.json") || !sources.containsKey("settings.json") || sources.size() > MAX_ENTRIES - 1)
      throw new IOException("迁移包文件列表无效");
    long total = 0; int files = 0;
    StringBuilder manifest = new StringBuilder("EVERYONE-PORTABLE-1\n");
    try (ZipOutputStream zip = new ZipOutputStream(target)) {
      zip.setLevel(1);
      for (Map.Entry<String, Source> source : sources.entrySet()) {
        check.run(); String name = source.getKey();
        if (!allowed(name) || name.equals("manifest.tsv")) throw new IOException("迁移包文件名无效");
        zip.putNextEntry(new ZipEntry(name));
        Receipt receipt;
        try (InputStream in = source.getValue().open()) {
          if (in == null) throw new IOException("材料文件无法读取，请重新关联");
          receipt = copy(in, zip, limit(name), MAX_TOTAL - total, check);
        }
        zip.closeEntry(); total += receipt.size; files++;
        manifest.append(name).append('\t').append(receipt.size).append('\t').append(receipt.sha).append('\n');
        progress.update(name, files, total);
      }
      byte[] bytes = manifest.toString().getBytes("UTF-8");
      if (bytes.length > limit("manifest.tsv") || total + bytes.length > MAX_TOTAL) throw new IOException("迁移包清单过大");
      check.run(); zip.putNextEntry(new ZipEntry("manifest.tsv")); zip.write(bytes); zip.closeEntry();
    }
  }

  /** Destination must be a new, empty app-private staging directory. */
  public static Map<String, Receipt> read(InputStream source, File stage, Check check, Progress progress) throws Exception {
    if (!stage.isDirectory() || stage.list() == null || stage.list().length != 0) throw new IOException("恢复暂存目录无效");
    Map<String, Receipt> receipts = new LinkedHashMap<>(); long total = 0;
    try (ZipInputStream zip = new ZipInputStream(source)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        check.run(); String name = entry.getName();
        if (entry.isDirectory() || !allowed(name) || receipts.containsKey(name) || receipts.size() >= MAX_ENTRIES)
          throw new IOException("迁移包包含无效或重复文件");
        File file = new File(stage, name).getCanonicalFile();
        if (!file.getPath().startsWith(stage.getCanonicalPath() + File.separator)) throw new IOException("迁移包路径无效");
        File parent = file.getParentFile(); if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建恢复目录");
        Receipt receipt;
        try (OutputStream out = new FileOutputStream(file)) { receipt = copy(zip, out, limit(name), MAX_TOTAL - total, check); }
        zip.closeEntry(); total += receipt.size; receipts.put(name, receipt);
        progress.update(name, receipts.size(), total);
      }
    }
    if (!receipts.containsKey("learning.json") || !receipts.containsKey("settings.json") || !receipts.containsKey("manifest.tsv"))
      throw new IOException("迁移包缺少数据或校验清单");
    Set<String> listed = new HashSet<>();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(stage, "manifest.tsv")), "UTF-8"))) {
      if (!"EVERYONE-PORTABLE-1".equals(in.readLine())) throw new IOException("未知迁移包版本");
      String line;
      while ((line = in.readLine()) != null) {
        check.run(); String[] fields = line.split("\\t", -1);
        if (fields.length != 3 || fields[0].equals("manifest.tsv") || !listed.add(fields[0]) || !fields[1].matches("[0-9]{1,12}") || !fields[2].matches("[a-f0-9]{64}"))
          throw new IOException("迁移包清单无效");
        Receipt actual = receipts.get(fields[0]);
        if (actual == null || actual.size != Long.parseLong(fields[1]) || !actual.sha.equals(fields[2])) throw new IOException("迁移包文件校验失败，请重新下载或导出");
      }
    }
    if (listed.size() != receipts.size() - 1) throw new IOException("迁移包清单与文件不一致");
    return receipts;
  }

  public static void deleteTree(File file) {
    if (file == null) return;
    File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
    file.delete();
  }
}
