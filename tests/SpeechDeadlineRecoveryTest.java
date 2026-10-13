import org.openenglish.reader.SpeechDeadline;
public final class SpeechDeadlineRecoveryTest {
 static int checks;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 public static void main(String[] args){
  SpeechDeadline d=new SpeechDeadline();d.system("a",1000);d.started("a",false,2000,35000);d.started("a",false,40000,35000);
  check(!d.expired("a",46999)&&d.expired("a",47000),"duplicate system start cannot extend playback deadline");
  d.system("a",48000);check(d.expired("a",48000),"duplicate scheduling cannot revive expired utterance");
  d.fallback("a",48000);check(d.accepts("a",true)&&!d.accepts("a",false),"fallback owns callbacks after system timeout");
  d.fallback("a",67000);check(!d.expired("a",67999)&&d.expired("a",68000),"duplicate fallback cannot prolong synthesis wait");
  d.started("a",true,68000,12000);check(d.expired("a",68000),"late fallback start cannot reopen expired preparation");
  d.clear();check(!d.accepts("a",true)&&!d.expired("a",999999),"clearing drops callbacks and timers");
  d.system("b",100000);d.started("a",false,101000,35000);d.fallback("a",102000);check(d.accepts("b",false)&&d.expired("b",104000),"old utterance cannot alter current timer");
  d.started("b",false,104000,35000);check(d.expired("b",104000),"late system start does not defeat start timeout");
  d.fallback("b",104000);d.started("b",true,105000,12000);d.started("b",true,120000,95000);check(!d.expired("b",126999)&&d.expired("b",127000),"fallback playback ignores duplicate start events");
  d.fallback("b",128000);check(d.expired("b",128000),"late fallback callback cannot restart finished phase");
  d.system("c",200000);check(d.accepts("c",false)&&!d.accepts("b",true),"fresh utterance resets phase ownership");
  d.started("c",false,201000,Long.MAX_VALUE);check(!d.expired("c",305999)&&d.expired("c",306000),"extreme duration is capped without integer overflow");
  d.system("d",400000);d.started("d",false,401000,-50);check(!d.expired("d",410999)&&d.expired("d",411000),"negative duration retains minimum playback allowance");
  d.system("e",500000);d.fallback("e",501000);d.started("e",false,502000,35000);check(d.expired("e",521000),"late system event cannot modify fallback phase");
  d.clear();d.system("e",600000);check(!d.expired("e",603999)&&d.expired("e",604000),"cleared identity can be used in fresh session");
  System.out.println("DEADLINE_RECOVERY_CHECKS: "+checks);
 }
}
