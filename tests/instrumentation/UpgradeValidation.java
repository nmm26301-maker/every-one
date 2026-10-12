package org.openenglish.validation;

import android.app.Instrumentation;
import android.os.Bundle;
import org.openenglish.reader.StudioStore;

/** Read-only check after installing 3.0 over the UpgradeSeed fixture. */
public class UpgradeValidation extends Instrumentation {
  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  public void onStart() {
    Bundle b = new Bundle();
    StudioStore s = new StudioStore(getTargetContext());
    try {
      if (s.items("word").length() != 1
          || s.items("note").length() != 1
          || s.total() != 1
          || s.sessions().length() != 1
          || s.context(s.sessions().optJSONObject(0).optLong("id")).length() != 2)
        throw new AssertionError("old learning data did not survive signed update");
      b.putString(
          "result",
          "PASS: signed 2.0 to 3.0 update preserves word, note, progress and accessible chat"
              + " topic");
      finish(-1, b);
    } catch (Throwable e) {
      b.putString("error", e.toString());
      finish(0, b);
    } finally {
      s.close();
    }
  }
}
