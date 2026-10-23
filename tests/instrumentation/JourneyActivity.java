package org.openenglish.reader;

/** Test-only foreground screen used to exercise real Activity onStop in the isolated module. */
public final class JourneyActivity extends android.app.Activity {
  @Override public void onCreate(android.os.Bundle state) {
    super.onCreate(state);
    android.widget.TextView label = new android.widget.TextView(this);
    label.setText("Media validation foreground fixture");
    setContentView(label);
  }
}
