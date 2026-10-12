#include "whisper.h"
#include "../callback.hpp"
#include <memory>
#include <vector>
#include <mutex>
#include <cstdio>
#include <map>
static std::once_flag logging;
static std::mutex whisperMutex;
static std::unique_ptr<whisper_context,decltype(&whisper_free)> cached(nullptr,whisper_free);
static std::string cachedPath;
static long loads=0;
static whisper_context* getWhisper(JNIEnv* env,jstring path,Callbacks& cb){
 if(cancelled(env,cb))return nullptr;
 std::call_once(logging,[]{whisper_log_set([](ggml_log_level level,const char* text,void*){if(level==GGML_LOG_LEVEL_ERROR || level==GGML_LOG_LEVEL_WARN)std::fputs(text,stderr);},nullptr);});
 const char* file=env->GetStringUTFChars(path,nullptr);if(!file)return nullptr;std::string requested(file);env->ReleaseStringUTFChars(path,file);
 if(cached && cachedPath==requested)return cached.get();
 auto params=whisper_context_default_params();params.use_gpu=false;
 std::unique_ptr<whisper_context,decltype(&whisper_free)> next(whisper_init_from_file_with_params(requested.c_str(),params),whisper_free);
 if(!next){fail(env,"Unable to load the verified Whisper model");return nullptr;}
 if(cancelled(env,cb))return nullptr;cached=std::move(next);cachedPath=requested;loads++;return cached.get();
}
static jstring transcribe(JNIEnv* env,jstring path,jfloatArray samples,jint threads,const char* language,jobject callback) {
 try {
  Callbacks cb(env,callback);std::lock_guard<std::mutex> guard(whisperMutex);
  whisper_context* ctx=getWhisper(env,path,cb);if(!ctx || cancelled(env,cb))return nullptr;
  jsize count=env->GetArrayLength(samples);if(count<1 || count>16000*180){fail(env,"Invalid 16 kHz mono audio length");return nullptr;}
  std::vector<float> pcm(count);env->GetFloatArrayRegion(samples,0,count,pcm.data());if(env->ExceptionCheck())return nullptr;
  if(whisper_lang_id(language)<0 || (!whisper_is_multilingual(ctx)&&std::string(language)!="en")){fail(env,"A multilingual Whisper model is required for this language");return nullptr;}
  auto p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);p.n_threads=std::clamp((int)threads,1,4);p.language=language;p.translate=false;p.no_context=true;p.single_segment=false;
  p.print_progress=false;p.print_realtime=false;p.print_timestamps=false;p.print_special=false;p.suppress_blank=true;
  p.abort_callback=Callbacks::abort;p.abort_callback_user_data=&cb;
  int code=whisper_full(ctx,p,pcm.data(),count);if(cancelled(env,cb))return nullptr;
  if(code!=0){fail(env,"Whisper transcription failed");return nullptr;}
  std::string text;
  for(int i=0;i<whisper_full_n_segments(ctx);i++){
   if(whisper_full_get_segment_no_speech_prob(ctx,i)>0.6f)continue;
   std::string line=whisper_full_get_segment_text(ctx,i);if(line.empty())continue;
   text+=line;jstring s=utf8(env,line);if(!s)return nullptr;
   env->CallVoidMethod(cb.object,cb.segmentMethod,(jlong)whisper_full_get_segment_t0(ctx,i)*10,(jlong)whisper_full_get_segment_t1(ctx,i)*10,s);env->DeleteLocalRef(s);
   if(env->ExceptionCheck() || cancelled(env,cb))return nullptr;
  }
  return utf8(env,text);
 } catch(const std::bad_alloc&){fail(env,"Not enough memory for Whisper");return nullptr;}catch(...){fail(env,"Whisper native inference failed");return nullptr;}
}

extern "C" JNIEXPORT jstring JNICALL Java_org_openenglish_reader_LocalNative_whisper(JNIEnv* e,jclass,jstring p,jfloatArray a,jint t,jobject cb){return transcribe(e,p,a,t,"en",cb);}
extern "C" JNIEXPORT jstring JNICALL Java_org_openenglish_reader_LocalNative_whisperLanguage(JNIEnv* e,jclass,jstring p,jfloatArray a,jint t,jstring language,jobject cb){
 const char* code=e->GetStringUTFChars(language,nullptr);if(!code)return nullptr;std::string lang(code);e->ReleaseStringUTFChars(language,code);
 if(lang!="zh" && lang!="ja" && lang!="en"){fail(e,"Unsupported island recognition language");return nullptr;}
 return transcribe(e,p,a,t,lang.c_str(),cb);
}
extern "C" JNIEXPORT void JNICALL Java_org_openenglish_reader_LocalNative_warmWhisper(JNIEnv* e,jclass,jstring p,jobject callback){try{Callbacks cb(e,callback);std::lock_guard<std::mutex> guard(whisperMutex);getWhisper(e,p,cb);}catch(...){fail(e,"Whisper warmup failed");}}
extern "C" JNIEXPORT void JNICALL Java_org_openenglish_reader_LocalNative_releaseWhisper(JNIEnv*,jclass){std::lock_guard<std::mutex> guard(whisperMutex);cached.reset();cachedPath.clear();}
extern "C" JNIEXPORT jlong JNICALL Java_org_openenglish_reader_LocalNative_whisperLoadCount(JNIEnv*,jclass){std::lock_guard<std::mutex> guard(whisperMutex);return loads;}

// Independent VAD contexts keep microphone analysis moving while Whisper decodes an utterance.
static std::mutex vadMutex;
static std::map<jlong,std::unique_ptr<whisper_vad_context,decltype(&whisper_vad_free)>> vadContexts;
static jlong nextVad=1;
extern "C" JNIEXPORT jlong JNICALL Java_org_openenglish_reader_LocalNative_vadOpen(JNIEnv* e,jclass,jstring path){try{
 const char* file=e->GetStringUTFChars(path,nullptr);if(!file)return 0;auto p=whisper_vad_default_context_params();p.n_threads=1;p.use_gpu=false;
 std::unique_ptr<whisper_vad_context,decltype(&whisper_vad_free)> ctx(whisper_vad_init_from_file_with_params(file,p),whisper_vad_free);e->ReleaseStringUTFChars(path,file);
 if(!ctx){fail(e,"Silero VAD model could not be loaded");return 0;}std::lock_guard<std::mutex> guard(vadMutex);jlong id=nextVad++;vadContexts.emplace(id,std::move(ctx));return id;
}catch(...){fail(e,"Unable to initialize streaming VAD");return 0;}}
extern "C" JNIEXPORT jfloat JNICALL Java_org_openenglish_reader_LocalNative_vadProbability(JNIEnv* e,jclass,jlong id,jfloatArray frame){try{
 if(e->GetArrayLength(frame)!=512){fail(e,"Silero VAD requires a 512-sample frame");return 0;}float pcm[512];e->GetFloatArrayRegion(frame,0,512,pcm);if(e->ExceptionCheck())return 0;
 std::lock_guard<std::mutex> guard(vadMutex);auto at=vadContexts.find(id);if(at==vadContexts.end()){fail(e,"Streaming VAD handle is closed");return 0;}
 auto* ctx=at->second.get();if(!whisper_vad_detect_speech_no_reset(ctx,pcm,512)){fail(e,"Silero VAD inference failed");return 0;}int count=whisper_vad_n_probs(ctx);if(count<1){fail(e,"Silero VAD returned no probability");return 0;}return whisper_vad_probs(ctx)[count-1];
}catch(...){fail(e,"Streaming VAD inference failed");return 0;}}
extern "C" JNIEXPORT void JNICALL Java_org_openenglish_reader_LocalNative_vadReset(JNIEnv* e,jclass,jlong id){std::lock_guard<std::mutex> guard(vadMutex);auto at=vadContexts.find(id);if(at==vadContexts.end()){fail(e,"Streaming VAD handle is closed");return;}whisper_vad_reset_state(at->second.get());}
extern "C" JNIEXPORT void JNICALL Java_org_openenglish_reader_LocalNative_vadClose(JNIEnv*,jclass,jlong id){std::lock_guard<std::mutex> guard(vadMutex);vadContexts.erase(id);}
