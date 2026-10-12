package org.openenglish.validation;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.*;
import org.xmlpull.v1.XmlSerializer;

/**
 * Persistent snapshots avoid launching uiautomator for every step on slow software emulators.
 * Commands live in the target's private files directory; adb root is required in this test AVD.
 * Never enabled or packaged in the application APK.
 */
public class UiBridge extends Instrumentation {
  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  private void node(XmlSerializer xml, AccessibilityNodeInfo n) throws Exception {
    if (n == null || !n.isVisibleToUser()) return;
    xml.startTag(null, "node");
    xml.attribute(null, "text", n.getText() == null ? "" : n.getText().toString());
    xml.attribute(
        null,
        "content-desc",
        n.getContentDescription() == null ? "" : n.getContentDescription().toString());
    xml.attribute(null, "class", n.getClassName() == null ? "" : n.getClassName().toString());
    xml.attribute(null, "package", n.getPackageName() == null ? "" : n.getPackageName().toString());
    Rect r = new Rect();
    n.getBoundsInScreen(r);
    xml.attribute(
        null, "bounds", "[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "]");
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo c = n.getChild(i);
      if (c != null) {
        node(xml, c);
        c.recycle();
      }
    }
    xml.endTag(null, "node");
  }

  public void onStart() {
    Bundle out = new Bundle();
    File base = new File(getTargetContext().getFilesDir(), "ui-bridge");
    base.mkdirs();
    File cmd = new File(base, "command"), response = new File(base, "response.xml");
    try {
      AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
      info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
      getUiAutomation().setServiceInfo(info);
      try (FileWriter w = new FileWriter(new File(base, "ready"))) {
        w.write("READY");
      }
      String previous = "";
      long deadline = System.currentTimeMillis() + 3600000;
      while (System.currentTimeMillis() < deadline) {
        Thread.sleep(80);
        if (!cmd.exists()) continue;
        String current;
        try (BufferedReader r = new BufferedReader(new FileReader(cmd))) {
          current = r.readLine();
        }
        if (current == null || current.equals(previous)) continue;
        previous = current;
        if (current.equals("stop")) break;
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        File temp = new File(base, "response.tmp");
        try (FileOutputStream stream = new FileOutputStream(temp)) {
          XmlSerializer xml = Xml.newSerializer();
          xml.setOutput(stream, "UTF-8");
          xml.startDocument("UTF-8", true);
          xml.startTag(null, "hierarchy");
          xml.attribute(null, "seq", current);
          for (AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
            AccessibilityNodeInfo n = window.getRoot();
            if (n != null) {
              node(xml, n);
              n.recycle();
            }
            window.recycle();
          }
          if (root != null && getUiAutomation().getWindows().isEmpty()) node(xml, root);
          xml.endTag(null, "hierarchy");
          xml.endDocument();
        } finally {
          if (root != null) root.recycle();
        }
        if (!temp.renameTo(response)) throw new IOException("Snapshot rename failed");
      }
      out.putString("result", "PASS: snapshot bridge stopped");
      finish(-1, out);
    } catch (Throwable e) {
      out.putString("error", e.toString());
      finish(0, out);
    }
  }
}
