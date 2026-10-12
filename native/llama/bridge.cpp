#include "llama.h"
#include "../callback.hpp"
#include <memory>
#include <vector>
#include <mutex>
#include <cstdio>
static std::once_flag backend;
static std::mutex modelMutex;
static std::shared_ptr<llama_model> cachedModel;
static std::string cachedPath;
static long loads=0;
static std::shared_ptr<llama_model> getModel(JNIEnv* env,jstring path,Callbacks& cb){
 std::lock_guard<std::mutex> guard(modelMutex);if(cancelled(env,cb))return {};
 std::call_once(backend,[]{llama_log_set([](ggml_log_level level,const char* text,void*){if(level==GGML_LOG_LEVEL_ERROR || level==GGML_LOG_LEVEL_WARN)std::fputs(text,stderr);},nullptr);llama_backend_init();});
 const char* file=env->GetStringUTFChars(path,nullptr);if(!file)return {};std::string requested(file);env->ReleaseStringUTFChars(path,file);
 if(cachedModel && cachedPath==requested)return cachedModel;
 auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.load_mode=LLAMA_LOAD_MODE_MMAP;mp.progress_callback=[](float,void* data){return !Callbacks::abort(data);};mp.progress_callback_user_data=&cb;
 std::shared_ptr<llama_model> model(llama_model_load_from_file(requested.c_str(),mp),llama_model_free);
 if(!model){if(!cancelled(env,cb))fail(env,"Unable to load the verified DeepSeek model");return {};}
 if(cancelled(env,cb))return {};cachedModel=model;cachedPath=requested;loads++;return model;
}
static jstring infer(JNIEnv* env,jstring path,jstring prompt,jint threads,jint maxTokens,jobject callback,int context){

 try{
  Callbacks cb(env,callback);if(cancelled(env,cb))return nullptr;
  auto model=getModel(env,path,cb);if(!model || cancelled(env,cb))return nullptr;
  auto cp=llama_context_default_params();cp.n_ctx=context;cp.n_batch=256;cp.n_ubatch=128;cp.n_threads=std::clamp((int)threads,1,4);cp.n_threads_batch=cp.n_threads;
  cp.abort_callback=Callbacks::abort;cp.abort_callback_data=&cb;
  std::unique_ptr<llama_context,decltype(&llama_free)> ctx(llama_init_from_model(model.get(),cp),llama_free);
  if(!ctx){fail(env,"Not enough memory for the local tutor context");return nullptr;}
  // JNI strings use modified UTF-8; obtain standard UTF-8 through Java String.getBytes instead.
  jclass strClass=env->FindClass("java/lang/String");jmethodID bytesMethod=env->GetMethodID(strClass,"getBytes","(Ljava/lang/String;)[B");jstring encoding=env->NewStringUTF("UTF-8");
  jbyteArray bytes=(jbyteArray)env->CallObjectMethod(prompt,bytesMethod,encoding);env->DeleteLocalRef(encoding);env->DeleteLocalRef(strClass);if(env->ExceptionCheck())return nullptr;
  std::string input(env->GetArrayLength(bytes),'\0');env->GetByteArrayRegion(bytes,0,input.size(),(jbyte*)input.data());env->DeleteLocalRef(bytes);
  const llama_vocab* vocab=llama_model_get_vocab(model.get());int generation=std::clamp((int)maxTokens,32,384);
  int count=-llama_tokenize(vocab,input.data(),input.size(),nullptr,0,false,true);if(count<=0){fail(env,"Empty local tutor prompt");return nullptr;}
  std::vector<llama_token> tokens(count);count=llama_tokenize(vocab,input.data(),input.size(),tokens.data(),tokens.size(),false,true);
  if(count<1){fail(env,"Local tutor prompt could not be tokenized");return nullptr;}
  const int budget=(int)llama_n_ctx(ctx.get())-generation-8;
  if(count>budget){const int front=std::min(160,budget/3);std::vector<llama_token> recent;recent.reserve(budget);recent.insert(recent.end(),tokens.begin(),tokens.begin()+front);recent.insert(recent.end(),tokens.end()-(budget-front),tokens.end());tokens.swap(recent);count=tokens.size();}
  for(int at=0;at<count;at+=256){if(cancelled(env,cb))return nullptr;int size=std::min(256,count-at);if(llama_decode(ctx.get(),llama_batch_get_one(tokens.data()+at,size))!=0){if(!cancelled(env,cb))fail(env,"DeepSeek prompt decoding failed");return nullptr;}}
  auto sp=llama_sampler_chain_default_params();std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(sp),llama_sampler_free);
  llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(40));llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_p(0.95f,1));llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(0.6f));llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(42));
  std::string output;std::vector<char> piece(256);
  for(int i=0;i<generation;i++){
   if(cancelled(env,cb))return nullptr;llama_token token=llama_sampler_sample(sampler.get(),ctx.get(),-1);
   if(llama_vocab_is_eog(vocab,token))break;
   int size=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);if(size<0){piece.resize(-size);size=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);}
   if(size>0)output.append(piece.data(),size);
   emit(env,cb,output);if(env->ExceptionCheck())return nullptr;
   if(llama_decode(ctx.get(),llama_batch_get_one(&token,1))!=0){if(!cancelled(env,cb))fail(env,"DeepSeek response decoding failed");return nullptr;}
  }
  if(cancelled(env,cb))return nullptr;return utf8(env,output);
 }catch(const std::bad_alloc&){fail(env,"Not enough memory for DeepSeek");return nullptr;}catch(...){fail(env,"Local tutor native inference failed");return nullptr;}
}

extern "C" JNIEXPORT jstring JNICALL Java_org_openenglish_reader_LocalNative_chat(JNIEnv* e,jclass,jstring p,jstring prompt,jint threads,jint max,jobject cb){return infer(e,p,prompt,threads,max,cb,2048);}
extern "C" JNIEXPORT jstring JNICALL Java_org_openenglish_reader_LocalNative_chatFast(JNIEnv* e,jclass,jstring p,jstring prompt,jint threads,jint max,jobject cb){return infer(e,p,prompt,threads,max,cb,1024);}
extern "C" JNIEXPORT void JNICALL Java_org_openenglish_reader_LocalNative_warmLlama(JNIEnv* e,jclass,jstring p,jobject callback){try{Callbacks cb(e,callback);getModel(e,p,cb);}catch(...){fail(e,"DeepSeek warmup failed");}}
extern "C" JNIEXPORT void JNICALL Java_org_openenglish_reader_LocalNative_releaseLlama(JNIEnv*,jclass){std::lock_guard<std::mutex> guard(modelMutex);cachedModel.reset();cachedPath.clear();}
extern "C" JNIEXPORT jlong JNICALL Java_org_openenglish_reader_LocalNative_llamaLoadCount(JNIEnv*,jclass){std::lock_guard<std::mutex> guard(modelMutex);return loads;}
