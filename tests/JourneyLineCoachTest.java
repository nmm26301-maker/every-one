package org.openenglish.reader;

import java.util.*;

public final class JourneyLineCoachTest {
  private static void yes(boolean value, String name) { if (!value) throw new AssertionError(name); }
  public static void main(String[] args) throws Exception {
    List<JourneySubtitles.Cue> cues = JourneySubtitles.parse("1\n00:00:00,000 --> 00:00:01,000\nHello.\n\n2\n00:00:01,000 --> 00:00:02,000\nHow are you?\n\n3\n00:00:02,000 --> 00:00:03,000\nFine.\n");
    JourneyLineCoach.Request r = JourneyLineCoach.prepare(cues, 1, "en");
    yes(r.input.contains("当前句：How are you?") && r.input.contains("前文：\nHello.")
        && r.input.contains("后文：\nFine."), "ordered subtitle context");
    yes(!r.key.equals(JourneyLineCoach.prepare(cues, 1, "yue").key), "language-specific context");
    yes(!r.key.equals(JourneyLineCoach.prepare(cues, 0, "en").key), "selected line in cache identity");
    JourneyLineCoach cache = new JourneyLineCoach();
    cache.remember(r, "https://one/v1", "model", "explanation");
    yes("explanation".equals(cache.cached(r,"https://one/v1","model")), "memory cache");
    yes(cache.cached(r,"https://two/v1","model")==null && cache.cached(r,"https://one/v1","other")==null,
        "providers and models do not share explanations");
    for (int i=0;i<21;i++) cache.remember(r,"https://one/v1","other"+i,"next");
    yes(cache.cached(r,"https://one/v1","model")==null, "cache is bounded");
    try { JourneyLineCoach.prepare(cues,-1,"en"); throw new AssertionError("missing selection"); }
    catch (java.io.IOException expected) {}
    try { cache.remember(r,"b","m",""); throw new AssertionError("empty response"); }
    catch (java.io.IOException expected) {}
    try { cache.remember(r,"b","m",String.join("",Collections.nCopies(8001,"x"))); throw new AssertionError("large response"); }
    catch (java.io.IOException expected) {}
    String huge = String.join("", Collections.nCopies(8000,"x"));
    List<JourneySubtitles.Cue> longCues = JourneySubtitles.parse("1\n00:00:00,000 --> 00:00:01,000\n"+huge+"\n");
    yes(JourneyLineCoach.prepare(longCues,0,"en").input.length()<700,"bounded current line");
    yes(JourneyLineCoach.SYSTEM.contains("不判断用户发音好坏"), "text explanation is not acoustic assessment");
    System.out.println("JourneyLineCoach: OK");
  }
}
