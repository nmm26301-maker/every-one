package android.content;
import android.content.res.AssetManager;import java.io.*;import java.util.*;
/** Typed, durable host fixture. It is not an Android runtime implementation. */
public class Context {
 private final File files,assets;public String failCommit="";
 public Context(File f,File a){files=f;assets=a;}public File getFilesDir(){return files;}public AssetManager getAssets(){return new AssetManager(assets);}public SharedPreferences getSharedPreferences(String name,int mode){return new Prefs(name,new File(files,name+".properties"));}
 private class Prefs implements SharedPreferences{
  final String name;final File path;Prefs(String n,File p){name=n;path=p;}
  private Map<String,Object> read(){try(ObjectInputStream in=new ObjectInputStream(new FileInputStream(path))){return (Map<String,Object>)in.readObject();}catch(Exception ignored){return new HashMap<>();}}
  public Map<String,?> getAll(){return read();}public boolean getBoolean(String k,boolean d){return (Boolean)read().getOrDefault(k,d);}public String getString(String k,String d){return (String)read().getOrDefault(k,d);}public int getInt(String k,int d){return (Integer)read().getOrDefault(k,d);}public long getLong(String k,long d){return (Long)read().getOrDefault(k,d);}public float getFloat(String k,float d){return (Float)read().getOrDefault(k,d);}public Set<String> getStringSet(String k,Set<String> d){return new HashSet<>((Set<String>)read().getOrDefault(k,d));}public boolean contains(String k){return read().containsKey(k);}
  public Editor edit(){return new Editor(){final Map<String,Object> changes=new HashMap<>();boolean clear;public Editor putString(String k,String v){changes.put(k,v);return this;}public Editor putInt(String k,int v){changes.put(k,v);return this;}public Editor putLong(String k,long v){changes.put(k,v);return this;}public Editor putFloat(String k,float v){changes.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){changes.put(k,new HashSet<>(v));return this;}public Editor remove(String k){changes.put(k,null);return this;}public Editor clear(){clear=true;return this;}public boolean commit(){if(name.equals(failCommit)){failCommit="";return false;}Map<String,Object> v=clear?new HashMap<>():read();for(Map.Entry<String,Object> e:changes.entrySet()){if(e.getValue()==null)v.remove(e.getKey());else v.put(e.getKey(),e.getValue());}try(ObjectOutputStream o=new ObjectOutputStream(new FileOutputStream(path))){o.writeObject(v);return true;}catch(IOException e){return false;}}public void apply(){commit();}};
  }
 }
 public ContentResolver getContentResolver(){return new ContentResolver();}
}
