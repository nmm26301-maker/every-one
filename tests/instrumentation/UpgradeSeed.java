package org.openenglish.validation;

import android.app.Instrumentation;
import android.os.Bundle;
import org.openenglish.reader.LearningStore;

/** Only use on a disposable emulator with the old 2.0 APK installed. */
public class UpgradeSeed extends Instrumentation {
  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  public void onStart() {
    Bundle b = new Bundle();
    LearningStore s = new LearningStore(getTargetContext());
    try {
      s.add("word", "morning", "早晨");
      s.add("note", "The cat is sleeping.", "旧笔记");
      s.add("user", "I like coffee.", "");
      s.add("assistant", "Hot or iced?", "");
      s.complete(1);
      b.putString("result", "PASS: created old 2.0 word, note, progress and chat fixture");
      finish(-1, b);
    } catch (Throwable e) {
      b.putString("error", e.toString());
      finish(0, b);
    } finally {
      s.close();
    }
  }
}
