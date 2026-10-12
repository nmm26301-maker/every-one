package org.openenglish.reader;

/** One utterance owns its timeout and callbacks, including after system TTS fails. */
public final class SpeechDeadline {
 private String id="";private boolean fallback;private long expires;
 public void system(String token,long now){id=token;fallback=false;expires=now+4000;}
 public boolean accepts(String token,boolean fromFallback){return !id.isEmpty()&&id.equals(token)&&fallback==fromFallback;}
 public void fallback(String token,long now){if(!id.equals(token))return;fallback=true;expires=now+20000;}
 public void started(String token,boolean fromFallback,long now,long duration){if(accepts(token,fromFallback))expires=now+Math.max(10000,Math.min(105000,duration+10000));}
 public boolean expired(String token,long now){return id.equals(token)&&!id.isEmpty()&&now>=expires;}
 public boolean isFallback(){return fallback;}
 public void clear(){id="";expires=0;fallback=false;}
}
