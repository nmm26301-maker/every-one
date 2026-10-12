package org.openenglish.reader;
/** Validates scene metadata at every navigation boundary. */
public final class IslandScene {
 public final String language,scene;
 private IslandScene(String l,String s){language=l;scene=s;}
 public static IslandScene parse(String value){
  if(value==null||!value.startsWith("magic:"))return null;String[] p=value.split(":",3);
  if(p.length!=3||!(p[1].equals("yue")||p[1].equals("ja")||p[1].equals("zh"))||p[2].trim().isEmpty()||p[2].length()>500)return null;
  return new IslandScene(p[1],p[2]);
 }
 public static String name(String lang){return "ja".equals(lang)?"日语":"zh".equals(lang)?"普通话":"粤语";}
}
