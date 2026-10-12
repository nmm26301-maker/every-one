package android.content;
import java.util.*;
public interface SharedPreferences {
 Map<String,?> getAll();boolean getBoolean(String key,boolean fallback);String getString(String key,String fallback);int getInt(String key,int fallback);long getLong(String key,long fallback);float getFloat(String key,float fallback);Set<String> getStringSet(String key,Set<String> fallback);boolean contains(String key);Editor edit();
 interface Editor{Editor putString(String key,String value);Editor putInt(String key,int value);Editor putLong(String key,long value);Editor putFloat(String key,float value);Editor putStringSet(String key,Set<String> value);Editor remove(String key);Editor clear();boolean commit();void apply();}
}
