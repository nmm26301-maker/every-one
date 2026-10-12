package org.openenglish.reader;
import java.util.*;
/** A small explicit self-rating session. No acoustic score advances the path. */
public final class PracticePath {
 private final List<String> ids;private int cursor;
 public PracticePath(List<String> candidates,int position){
  LinkedHashSet<String> unique=new LinkedHashSet<>();for(String id:candidates){if(id!=null&&id.matches("[a-zA-Z0-9_-]{1,100}"))unique.add(id);if(unique.size()==3)break;}
  ids=Collections.unmodifiableList(new ArrayList<>(unique));cursor=Math.max(0,Math.min(position,ids.size()));
 }
 public List<String> ids(){return ids;}public int position(){return cursor;}public int size(){return ids.size();}
 public String current(){return cursor<ids.size()?ids.get(cursor):"";}
 public boolean rate(String id){if(id==null||!id.equals(current())||current().isEmpty())return false;cursor++;return true;}
 public boolean finished(){return cursor>=ids.size();}
}
