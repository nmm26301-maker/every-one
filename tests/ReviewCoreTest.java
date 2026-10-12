import org.openenglish.reader.*;
import java.util.*;
public class ReviewCoreTest {
  static int count;
  static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);count++;}
  public static void main(String[] args){
    List<LearningCore.Cue> cues=Arrays.asList(new LearningCore.Cue(0,1,"first"),new LearningCore.Cue(2,3,"target"),new LearningCore.Cue(4,5,"last"));
    check(TrainingCore.resolveCue(cues,1,"target")==1,"anchor exact");
    check(TrainingCore.resolveCue(cues,0,"target")==1,"subtitle reordered");
    check(TrainingCore.resolveCue(cues,-1,"target")==1,"missing old anchor");
    check(TrainingCore.resolveCue(cues,10,"target")==1,"anchor outside current subtitle");
    check(TrainingCore.resolveCue(cues,1,"changed")==-1,"edited text rejected");
    List<LearningCore.Cue> repeated=Arrays.asList(new LearningCore.Cue(0,1,"same"),new LearningCore.Cue(2,3,"same"));
    check(TrainingCore.resolveCue(repeated,0,"same")==0,"exact anchor for repeated sentence");
    check(TrainingCore.resolveCue(repeated,9,"same")==-1,"ambiguous relocated sentence rejected");
    check(TrainingCore.reviewMatch("Take your time!","take YOUR time.")==100,"correct attempt leaves queue");
    check(TrainingCore.reviewMatch("Take your time","Take time")<100,"missing word remains");
    check(TrainingCore.reviewMatch("","hello")==-1,"no English reference ungraded");
    StringBuilder a=new StringBuilder(),b=new StringBuilder();for(int i=0;i<201;i++){a.append("a ");b.append("b ");}
    check(TrainingCore.reviewMatch(a.toString(),b.toString())==-1,"work bound");
    check(TrainingCore.reviewMatch(a.toString(),a.toString())==100,"long exact match remains correct");
    StringBuilder longOriginal=new StringBuilder(),longMissing=new StringBuilder();
    for(int i=0;i<200;i++){longOriginal.append("word ");if(i<199)longMissing.append("word ");}
    check(TrainingCore.reviewMatch(longOriginal.toString(),longMissing.toString())==99,"rounded score cannot clear a missing word");
    check(TrainingCore.reviewMatch(longOriginal.toString(),longOriginal.toString())==100,"only normalized exact match reaches 100");
    System.out.println("PASS: "+count+" review scoring and subtitle-identity checks");
  }
}
