package org.openenglish.reader;
import android.content.Context;
import com.atilika.kuromoji.ipadic.*;
import com.atilika.kuromoji.dict.*;
import com.atilika.kuromoji.trie.DoubleArrayTrie;
import java.util.*;
/** Kuromoji/IPADIC supplies kana so the fallback does not silently skip kanji. */
public final class JapaneseReading {
 private static Tokenizer tokenizer;private JapaneseReading(){}
 public static synchronized void warm(Context c){if(tokenizer!=null)return;final Context app=c;tokenizer=new Tokenizer.Builder(){
  @Override protected void loadDictionaries(){
   // Asset-stream equivalent of Kuromoji's Apache-2.0 dictionary loader.
   penalties=Arrays.asList(2,3000,7,1700);resolver=name->app.getAssets().open("kuromoji/"+name);
   try{doubleArrayTrie=DoubleArrayTrie.newInstance(resolver);connectionCosts=ConnectionCosts.newInstance(resolver);tokenInfoDictionary=TokenInfoDictionary.newInstance(resolver);characterDefinitions=CharacterDefinitions.newInstance(resolver);unknownDictionary=UnknownDictionary.newInstance(resolver,characterDefinitions,totalFeatures);insertedDictionary=new InsertedDictionary(totalFeatures);}catch(Exception e){throw new IllegalStateException("日语读音词典未能打开",e);}
  }
 }.build();}
 public static synchronized String kana(Context c,String text){warm(c);StringBuilder out=new StringBuilder();for(Token t:tokenizer.tokenize(text)){String reading=t.getPronunciation();if(reading==null||reading.equals("*"))reading=t.getReading();if(reading==null||reading.equals("*"))reading=t.getSurface();out.append(reading);}return out.toString();}
}
