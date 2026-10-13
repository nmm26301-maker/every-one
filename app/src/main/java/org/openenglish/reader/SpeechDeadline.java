package org.openenglish.reader;

/** One utterance owns its timeout and callbacks, including after system TTS fails. */
public final class SpeechDeadline {
 private String id="";private boolean fallback,started;private long expires;
 public void system(String token,long now){if(id.equals(token)&&!id.isEmpty())return;id=token;fallback=false;started=false;expires=now+4000;}
 public boolean accepts(String token,boolean fromFallback){return !id.isEmpty()&&id.equals(token)&&fallback==fromFallback;}
 public void fallback(String token,long now){if(id.isEmpty()||!id.equals(token)||fallback)return;fallback=true;started=false;expires=now+20000;}
 public void started(String token,boolean fromFallback,long now,long duration){if(!accepts(token,fromFallback)||started||now>=expires)return;started=true;expires=now+10000+Math.max(0,Math.min(95000,duration));}
 public boolean expired(String token,long now){return id.equals(token)&&!id.isEmpty()&&now>=expires;}
 public boolean isFallback(){return fallback;}
 public void clear(){id="";expires=0;fallback=false;started=false;}
}
