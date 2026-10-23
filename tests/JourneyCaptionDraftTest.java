package org.openenglish.reader;

import java.util.*;

public final class JourneyCaptionDraftTest {
  private static void yes(boolean value,String message) {if(!value)throw new AssertionError(message);}
  private static void invalid(double start,double end) throws Exception {
    try {new JourneyCaptionDraft(Arrays.asList(new JourneyCaptionDraft.Segment(start,end,"text")),3000,4000);
      throw new AssertionError("invalid timestamp accepted");}
    catch(java.io.IOException expected) {}
  }
  public static void main(String[] args) throws Exception {
    JourneyCaptionDraft draft=new JourneyCaptionDraft(Arrays.asList(
        new JourneyCaptionDraft.Segment(0,2,"New one"),new JourneyCaptionDraft.Segment(2,4,"New two")),3000,4000);
    List<JourneySubtitles.Cue> old=Arrays.asList(new JourneySubtitles.Cue(0,10000,"old"),
        new JourneySubtitles.Cue(12000,14000,"later"));
    List<JourneySubtitles.Cue> merged=draft.merge(old);
    yes(merged.size()==5 && merged.get(0).endMs==3000 && merged.get(3).startMs==7000
        && merged.get(4).text.equals("later"),"preserve subtitles outside replacement interval");
    yes(draft.cues.get(0).startMs==3000 && draft.cues.get(1).endMs==7000,"restore video time origin");
    List<JourneySubtitles.Cue> roundTrip=JourneySubtitles.parse(JourneyCaptionDraft.srt(merged));
    yes(roundTrip.size()==5 && roundTrip.get(2).text.equals("New two"),"SRT round trip");
    for(double[] times:new double[][]{{-1,1},{0,Double.NaN},{0,Double.POSITIVE_INFINITY},{2,1},{0,5}})
      invalid(times[0],times[1]);
    try{new JourneyCaptionDraft(Collections.emptyList(),0,1000);throw new AssertionError("empty");}
    catch(java.io.IOException expected){}
    try{new JourneyCaptionDraft(Arrays.asList(new JourneyCaptionDraft.Segment(0,2,"a"),
        new JourneyCaptionDraft.Segment(1,3,"b")),0,4000);throw new AssertionError("overlap");}
    catch(java.io.IOException expected){}
    JourneyCaptionDraft edge=new JourneyCaptionDraft(Arrays.asList(
        new JourneyCaptionDraft.Segment(0,1.02,"edge")),Integer.MAX_VALUE-1000,1000);
    yes(edge.cues.get(0).endMs==Integer.MAX_VALUE,"rounding tolerance without integer overflow");
    yes(JourneySubtitles.parse(JourneyCaptionDraft.srt(edge.cues)).get(0).endMs==Integer.MAX_VALUE,
        "long media timestamps survive export");
    List<JourneySubtitles.Cue> shifted=JourneyCaptionDraft.shift(old,-1000);
    yes(shifted.get(0).startMs==0 && shifted.get(0).endMs==9000,"export applies sync offset");
    yes(old.get(0).endMs==10000,"original captions remain immutable");
    System.out.println("JourneyCaptionDraft: OK");
  }
}
