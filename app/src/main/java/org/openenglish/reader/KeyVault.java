package org.openenglish.reader;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public class KeyVault {
  private static javax.crypto.SecretKey key() throws Exception {
    KeyStore k = KeyStore.getInstance("AndroidKeyStore");
    k.load(null);
    if (!k.containsAlias("english_api")) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  "english_api", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) k.getKey("english_api", null);
  }

  public static void save(Context c, String value) throws Exception {
    save(c, "connection", value);
  }

  public static String read(Context c) throws Exception {
    return read(c, "connection");
  }

  public static void save(Context c, String namespace, String value) throws Exception {
    android.content.SharedPreferences p = c.getSharedPreferences(namespace, 0);
    if (value.trim().isEmpty()) {
      p.edit().remove("token").remove("iv").apply();
      return;
    }
    Cipher e = Cipher.getInstance("AES/GCM/NoPadding");
    e.init(Cipher.ENCRYPT_MODE, key());
    p.edit()
        .putString(
            "token",
            Base64.encodeToString(e.doFinal(value.trim().getBytes("UTF-8")), Base64.NO_WRAP))
        .putString("iv", Base64.encodeToString(e.getIV(), Base64.NO_WRAP))
        .apply();
  }

  public static String read(Context c, String namespace) throws Exception {
    android.content.SharedPreferences p = c.getSharedPreferences(namespace, 0);
    if (!p.contains("token")) return "";
    Cipher e = Cipher.getInstance("AES/GCM/NoPadding");
    e.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(p.getString("iv", ""), Base64.NO_WRAP)));
    return new String(e.doFinal(Base64.decode(p.getString("token", ""), Base64.NO_WRAP)), "UTF-8");
  }
}
