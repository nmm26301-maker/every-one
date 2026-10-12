#include "../callback.hpp"
#include "espeak-ng/speak_lib.h"
#include <mutex>
#include <vector>
#include <string>
#include <cmath>
static std::mutex speechMutex;
static bool initialized=false;
static int rate=0;
static std::vector<short>* output=nullptr;
static Callbacks* active=nullptr;
static int receive(short* wav,int count,espeak_EVENT*){
 if(active && Callbacks::abort(active))return 1;
 if(output && wav && count>0){if(output->size()+count>22050*90)return 1;output->insert(output->end(),wav,wav+count);}return 0;
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_org_openenglish_reader_IslandSpeech_render(JNIEnv* env,jclass,jstring directory,jstring language,jstring text,jobject callback){
 std::lock_guard<std::mutex> lock(speechMutex);try{Callbacks cb(env,callback);if(cancelled(env,cb))return nullptr;
  if(!initialized){const char* path=env->GetStringUTFChars(directory,nullptr);if(!path)return nullptr;rate=espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS,0,path,espeakINITIALIZE_DONT_EXIT);env->ReleaseStringUTFChars(directory,path);if(rate<=0){fail(env,"Offline speech data could not be opened");return nullptr;}espeak_SetSynthCallback(receive);initialized=true;}
  const char* lang=env->GetStringUTFChars(language,nullptr);if(!lang)return nullptr;std::string code(lang);env->ReleaseStringUTFChars(language,lang);if(code!="yue"&&code!="ja"&&code!="cmn"&&code!="en"&&code!="en-us"){fail(env,"Unsupported offline speech language");return nullptr;}
  if(espeak_SetVoiceByName(code.c_str())!=EE_OK){fail(env,"Offline speech voice is unavailable");return nullptr;}
  jclass cls=env->FindClass("java/lang/String");jmethodID method=env->GetMethodID(cls,"getBytes","(Ljava/lang/String;)[B");jstring enc=env->NewStringUTF("UTF-8");jbyteArray bytes=(jbyteArray)env->CallObjectMethod(text,method,enc);env->DeleteLocalRef(enc);env->DeleteLocalRef(cls);if(env->ExceptionCheck())return nullptr;int length=env->GetArrayLength(bytes);if(length>4096){env->DeleteLocalRef(bytes);fail(env,"Speech sentence is too long");return nullptr;}std::string input(length,'\0');env->GetByteArrayRegion(bytes,0,length,(jbyte*)input.data());env->DeleteLocalRef(bytes);
  std::vector<short> pcm;output=&pcm;active=&cb;espeak_SetParameter(espeakRATE,150,0);espeak_ERROR codeResult=espeak_Synth(input.c_str(),input.size()+1,0,POS_CHARACTER,0,espeakCHARS_UTF8|espeakENDPAUSE,nullptr,nullptr);output=nullptr;active=nullptr;if(cancelled(env,cb))return nullptr;if(codeResult!=EE_OK||pcm.empty()){fail(env,"Offline speech synthesis failed");return nullptr;}
  int count=(int)((int64_t)pcm.size()*16000/rate);std::vector<float> resampled(count);for(int i=0;i<count;i++){double pos=(double)i*rate/16000;size_t at=(size_t)pos;double f=pos-at;resampled[i]=(float)((pcm[at]*(1-f)+pcm[std::min(at+1,pcm.size()-1)]*f)/32768.0);}
  jfloatArray result=env->NewFloatArray(count);if(result)env->SetFloatArrayRegion(result,0,count,resampled.data());return result;
 }catch(...){output=nullptr;active=nullptr;fail(env,"Offline speech synthesis failed");return nullptr;}
}
