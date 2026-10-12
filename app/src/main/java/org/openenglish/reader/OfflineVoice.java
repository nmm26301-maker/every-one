package org.openenglish.reader;
import android.speech.tts.*;
import java.util.*;
/** Local mode selects an installed offline English voice rather than a network TTS voice. */
public final class OfflineVoice {
 private OfflineVoice() {}
 public static boolean select(TextToSpeech tts,String lang){try{Set<Voice> all=tts.getVoices();if(all==null)return false;Voice best=null;for(Voice v:all){if(v==null||v.isNetworkConnectionRequired()||(v.getFeatures()!=null&&v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))continue;Locale l=v.getLocale();String code=l.getLanguage();boolean match=lang.equals("yue")?(code.equals("yue") || code.equals("zh")&&l.getCountry().equals("HK")):lang.equals("ja")?code.equals("ja"):(code.equals("zh")&&!l.getCountry().equals("HK")||code.equals("cmn"));if(match&&(best==null||v.getQuality()>best.getQuality()))best=v;}return best!=null&&tts.setVoice(best)==TextToSpeech.SUCCESS;}catch(Exception e){return false;}}
 private static boolean usable(Voice voice){return voice!=null&&!voice.isNetworkConnectionRequired()&&"en".equals(voice.getLocale().getLanguage())&&(voice.getFeatures()==null||!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));}
 public static boolean select(TextToSpeech tts){try{
  if(usable(tts.getVoice()))return true;Set<Voice> voices=tts.getVoices();if(voices==null)return false;
  Voice other=null;for(Voice voice:voices){if(!usable(voice))continue;if("US".equals(voice.getLocale().getCountry())&&tts.setVoice(voice)==TextToSpeech.SUCCESS)return true;other=voice;}
  return other!=null&&tts.setVoice(other)==TextToSpeech.SUCCESS;
 }catch(Exception e){return false;}}
}
