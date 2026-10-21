import java.util.*;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.dexlib2.writer.pool.DexPool;

/**
 * Replace only the Everyone 5.0 JourneyActivity implementation, then add
 * the separate immersion Activity and subtitle parser. Preserve all other
 * baseline DEX definitions and fail if any target contract is unexpected.
 */
public final class DexImmersionMerge {
  private static final String OWNER = "Lorg/openenglish/reader/";
  private static final String ENTRY = OWNER + "JourneyActivity";
  private static final String VIEW = OWNER + "JourneyImmersionActivity";
  private static final String SUBTITLES = OWNER + "JourneySubtitles";
  private static final String CANTONESE = OWNER + "JourneyCantonese";
  private static final String AUDIO = OWNER + "JourneyPracticeAudio";
  private static final String TONE = OWNER + "JourneyToneContour";
  private static final String DECODE = OWNER + "JourneyToneAudio";
  private static final String TTS = OWNER + "JourneySpeechGuide";
  private static final String PLOT = OWNER + "JourneyTonePlotView";
  private static final String JYUTPING = OWNER + "JourneyJyutping";
  private static final String ASR = OWNER + "JourneyCantoneseAsr";

  private static boolean within(String type, String base) {
    return type.equals(base + ";") || type.startsWith(base + "$");
  }

  private static boolean selected(String type) {
    return within(type, ENTRY) || within(type, VIEW) || within(type, SUBTITLES) || within(type, CANTONESE) || within(type, AUDIO) || within(type, TONE) || within(type, DECODE) || within(type, TTS) || within(type, PLOT) || within(type, JYUTPING) || within(type, ASR);
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("base.dex compiled.dex merged.dex");
    }
    DexFile original = DexFileFactory.loadDexFile(args[0], Opcodes.getDefault());
    DexFile replacement = DexFileFactory.loadDexFile(args[1], original.getOpcodes());
    Set<String> originalTypes = new HashSet<>();
    Set<String> newTypes = new HashSet<>();
    List<ClassDef> output = new ArrayList<>();
    int removed = 0, added = 0, activityRoot = 0, viewRoot = 0, subtitleRoot = 0, cantoneseRoot = 0, audioRoot = 0, toneRoot = 0, decodeRoot = 0, ttsRoot = 0, plotRoot = 0, jyutpingRoot = 0, asrRoot = 0;

    for (ClassDef c : original.getClasses()) {
      String type = c.getType();
      if (!originalTypes.add(type)) throw new IllegalStateException("Duplicate source " + type);
      if (within(type, VIEW) || within(type, SUBTITLES) || within(type, CANTONESE) || within(type, AUDIO) || within(type, TONE) || within(type, DECODE) || within(type, TTS) || within(type, PLOT) || within(type, JYUTPING) || within(type, ASR)) {
        throw new IllegalStateException("New immersion type already installed " + type);
      }
      if (within(type, ENTRY)) {
        removed++;
      } else {
        output.add(c);
      }
    }
    if (!originalTypes.contains(ENTRY + ";") || removed == 0) {
      throw new IllegalStateException("Not an Everyone 5.0 JourneyActivity snapshot");
    }

    for (ClassDef c : replacement.getClasses()) {
      String type = c.getType();
      if (!selected(type)) continue;
      if (!newTypes.add(type)) throw new IllegalStateException("Duplicate patch " + type);
      if (type.equals(ENTRY + ";")) activityRoot++;
      if (type.equals(VIEW + ";")) viewRoot++;
      if (type.equals(SUBTITLES + ";")) subtitleRoot++;
      if (type.equals(CANTONESE + ";")) cantoneseRoot++;
      if (type.equals(AUDIO + ";")) audioRoot++;
      if (type.equals(TONE + ";")) toneRoot++;
      if (type.equals(DECODE + ";")) decodeRoot++;
      if (type.equals(TTS + ";")) ttsRoot++;
      if (type.equals(PLOT + ";")) plotRoot++;
      if (type.equals(JYUTPING + ";")) jyutpingRoot++;
      if (type.equals(ASR + ";")) asrRoot++;
      if (originalTypes.contains(type) && !within(type, ENTRY)) {
        throw new IllegalStateException("Collision " + type);
      }
      output.add(c);
      added++;
    }
    if (activityRoot != 1 || viewRoot != 1 || subtitleRoot != 1 || cantoneseRoot != 1 || audioRoot != 1 || toneRoot != 1 || decodeRoot != 1 || ttsRoot != 1 || plotRoot != 1 || jyutpingRoot != 1 || asrRoot != 1) {
      throw new IllegalStateException("Patch is missing an Activity or parser root class");
    }
    if (added < removed + 10) {
      throw new IllegalStateException("Unexpected missing JourneyActivity synthetic definitions");
    }
    DexPool.writeTo(args[2], new ImmutableDexFile(original.getOpcodes(), output));
    System.out.println("Preserved other classes: " + (original.getClasses().size() - removed)
        + "; replaced JourneyActivity classes: " + removed
        + "; wrote immersion classes: " + added);
  }
}
