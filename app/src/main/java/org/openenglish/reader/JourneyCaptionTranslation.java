package org.openenglish.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Strict parser and prompt builder for automatic subtitle translation.
 * The model may translate text, but it must not be allowed to change timing.
 */
public final class JourneyCaptionTranslation {
  public static final int MAX_CUES_PER_REQUEST = 80;
  public static final int MAX_TEXT_LENGTH = 4000;
  private JourneyCaptionTranslation() {}

  public static String prompt(List<JourneySubtitles.Cue> cues, String targetLanguage)
      throws IOException {
    if (cues == null || cues.isEmpty() || cues.size() > MAX_CUES_PER_REQUEST)
      throw new IOException("一次翻译应包含 1–80 条字幕");
    if (targetLanguage == null || targetLanguage.trim().isEmpty()
        || targetLanguage.length() > 40 || targetLanguage.indexOf('\n') >= 0)
      throw new IOException("目标语言无效");
    StringBuilder out = new StringBuilder();
    out.append("将以下字幕翻译成").append(targetLanguage.trim())
        .append("。只返回 JSON 数组，每项格式 {\"index\":整数,\"text\":字符串}。")
        .append("保持 index 原样，不要添加解释，不要合并或拆分字幕，不要输出 Markdown。\n");
    for (int i = 0; i < cues.size(); i++) {
      String text = cues.get(i).text == null ? "" : cues.get(i).text.trim();
      if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH)
        throw new IOException("字幕文字为空或过长");
      out.append(i + 1).append(". ").append(text).append('\n');
    }
    return out.toString();
  }

  /** Parse direct JSON or a single fenced JSON response from an LLM. */
  public static List<String> parse(String response, int expected) throws IOException {
    if (response == null || expected < 1 || expected > MAX_CUES_PER_REQUEST)
      throw new IOException("翻译回复为空或数量无效");
    String body = response.trim();
    if (body.startsWith("```") && body.endsWith("```")) {
      int first = body.indexOf('\n');
      body = first < 0 ? "" : body.substring(first + 1, body.length() - 3).trim();
    }
    String array = extractArray(body);
    List<String> objects = splitObjects(array);
    if (objects.size() != expected)
      throw new IOException("翻译回复没有返回完整的字幕数组");
    List<String> result = new ArrayList<>(Collections.nCopies(expected, ""));
    for (String item : objects) {
      int index = integerField(item, "index");
      String text = stringField(item, "text");
      if (index < 1 || index > expected || text == null)
        throw new IOException("翻译回复缺少 index 或 text");
      text = text.replace('\r', ' ').replace('\n', ' ').trim();
      if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH
          || !result.get(index - 1).isEmpty())
        throw new IOException("翻译回复的编号或文字无效");
      result.set(index - 1, text);
    }
    for (String text : result) if (text.isEmpty()) throw new IOException("翻译回复缺少字幕");
    return Collections.unmodifiableList(result);
  }

  private static String extractArray(String body) throws IOException {
    int start = body.indexOf('['), end = body.lastIndexOf(']');
    if (start < 0 || end <= start) {
      int key = body.indexOf("\"translations\"");
      start = key < 0 ? -1 : body.indexOf('[', key);
      end = start < 0 ? -1 : body.lastIndexOf(']');
    }
    if (start < 0 || end <= start) throw new IOException("翻译回复不是 JSON 数组");
    return body.substring(start + 1, end);
  }

  private static List<String> splitObjects(String array) throws IOException {
    List<String> result = new ArrayList<>();
    boolean quoted = false, escaped = false;
    int depth = 0, start = -1;
    boolean expectObject = true;
    for (int i = 0; i < array.length(); i++) {
      char c = array.charAt(i);
      if (quoted) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == '"') quoted = false;
        continue;
      }
      if (depth == 0) {
        if (Character.isWhitespace(c)) continue;
        if (c == ',') {
          if (expectObject) throw new IOException("翻译回复数组格式无效");
          expectObject = true;
          continue;
        }
        if (c != '{' || !expectObject) throw new IOException("翻译回复数组包含非对象项");
        start = i;
        depth = 1;
        expectObject = false;
        continue;
      }
      if (c == '"') { quoted = true; continue; }
      if (c == '{') depth++;
      else if (c == '}' && --depth == 0) result.add(array.substring(start, i + 1));
    }
    if (quoted || depth != 0 || escaped || expectObject && !result.isEmpty())
      throw new IOException("翻译回复 JSON 不完整");
    return result;
  }

  private static int integerField(String object, String name) throws IOException {
    String marker = "\"" + name + "\"";
    int at = object.indexOf(marker);
    if (at < 0) throw new IOException("翻译回复缺少 " + name);
    int colon = object.indexOf(':', at + marker.length());
    if (colon < 0) throw new IOException("翻译回复字段无效");
    int end = colon + 1;
    while (end < object.length() && Character.isWhitespace(object.charAt(end))) end++;
    int start = end;
    while (end < object.length() && Character.isDigit(object.charAt(end))) end++;
    if (start == end) throw new IOException("翻译编号无效");
    if (end < object.length() && !Character.isWhitespace(object.charAt(end))
        && object.charAt(end) != ',') throw new IOException("翻译编号格式无效");
    try { return Integer.parseInt(object.substring(start, end)); }
    catch (NumberFormatException e) { throw new IOException("翻译编号过大"); }
  }

  private static String stringField(String object, String name) throws IOException {
    String marker = "\"" + name + "\"";
    int at = object.indexOf(marker);
    if (at < 0) throw new IOException("翻译回复缺少 " + name);
    int colon = object.indexOf(':', at + marker.length());
    if (colon < 0) throw new IOException("翻译回复字段无效");
    int start = colon + 1;
    while (start < object.length() && Character.isWhitespace(object.charAt(start))) start++;
    if (start >= object.length() || object.charAt(start) != '"') throw new IOException("翻译文字无效");
    StringBuilder text = new StringBuilder();
    boolean escaped = false;
    for (int i = start + 1; i < object.length(); i++) {
      char c = object.charAt(i);
      if (escaped) {
        if (c == 'n') text.append('\n');
        else if (c == 'r') text.append('\r');
        else if (c == 't') text.append('\t');
        else if (c == 'b') text.append('\b');
        else if (c == 'f') text.append('\f');
        else if (c == '"' || c == '\\' || c == '/') text.append(c);
        else if (c == 'u') {
          if (i + 4 >= object.length()) throw new IOException("翻译文字 Unicode 转义不完整");
          int value = 0;
          for (int j = 1; j <= 4; j++) {
            int digit = Character.digit(object.charAt(i + j), 16);
            if (digit < 0) throw new IOException("翻译文字 Unicode 转义无效");
            value = (value << 4) | digit;
          }
          text.append((char) value);
          i += 4;
        } else throw new IOException("翻译文字包含未知转义");
        escaped = false;
      } else if (c == '\\') escaped = true;
      else if (c == '"') return text.toString();
      else text.append(c);
    }
    throw new IOException("翻译文字字符串未闭合");
  }

  public static List<JourneySubtitles.Cue> apply(
      List<JourneySubtitles.Cue> source, List<String> translations) throws IOException {
    if (source == null || translations == null || source.size() != translations.size())
      throw new IOException("翻译数量与原字幕不一致");
    List<JourneySubtitles.Cue> result = new ArrayList<>();
    for (int i = 0; i < source.size(); i++) {
      String text = translations.get(i);
      if (text == null || text.trim().isEmpty()) throw new IOException("翻译包含空字幕");
      JourneySubtitles.Cue cue = source.get(i);
      result.add(new JourneySubtitles.Cue(cue.startMs, cue.endMs, text.trim()));
    }
    return Collections.unmodifiableList(result);
  }
}
