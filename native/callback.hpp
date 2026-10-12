#pragma once
#include <jni.h>
#include <atomic>
#include <string>
#include <algorithm>
struct Callbacks {
 JavaVM* vm; jobject object; jmethodID abortMethod,textMethod,segmentMethod;
 std::atomic<bool> failed{false};
 Callbacks(JNIEnv* env,jobject cb) {
  env->GetJavaVM(&vm);object=env->NewGlobalRef(cb);jclass cls=env->GetObjectClass(cb);
  abortMethod=env->GetMethodID(cls,"abort","()Z");
  textMethod=env->GetMethodID(cls,"text","(Ljava/lang/String;)V");
  segmentMethod=env->GetMethodID(cls,"segment","(JJLjava/lang/String;)V");env->DeleteLocalRef(cls);
 }
 ~Callbacks(){JNIEnv* env=nullptr;if(vm->GetEnv((void**)&env,JNI_VERSION_1_6)==JNI_OK)env->DeleteGlobalRef(object);}
 static bool abort(void* data) {
  auto* c=(Callbacks*)data;if(c->failed)return true;
  JNIEnv* env=nullptr;bool detach=false;
  if(c->vm->GetEnv((void**)&env,JNI_VERSION_1_6)!=JNI_OK){
#ifdef __ANDROID__
   if(c->vm->AttachCurrentThread(&env,nullptr)!=JNI_OK)return true;
#else
   if(c->vm->AttachCurrentThread((void**)&env,nullptr)!=JNI_OK)return true;
#endif
   detach=true;
  }
  bool result=env->CallBooleanMethod(c->object,c->abortMethod);
  if(env->ExceptionCheck()){env->ExceptionClear();c->failed=true;result=true;}
  if(detach)c->vm->DetachCurrentThread();return result;
 }
};
inline jstring utf8(JNIEnv* env,const std::string& s) {
 jbyteArray bytes=env->NewByteArray(s.size());if(!bytes)return nullptr;
 env->SetByteArrayRegion(bytes,0,s.size(),(const jbyte*)s.data());
 jclass cls=env->FindClass("java/lang/String");jmethodID ctor=env->GetMethodID(cls,"<init>","([BLjava/lang/String;)V");
 jstring encoding=env->NewStringUTF("UTF-8");jstring result=(jstring)env->NewObject(cls,ctor,bytes,encoding);
 env->DeleteLocalRef(encoding);env->DeleteLocalRef(bytes);env->DeleteLocalRef(cls);return result;
}
inline void fail(JNIEnv* env,const char* message,const char* cls="java/io/IOException") {if(!env->ExceptionCheck()){jclass c=env->FindClass(cls);env->ThrowNew(c,message);env->DeleteLocalRef(c);}}
inline bool cancelled(JNIEnv* env,Callbacks& cb){if(Callbacks::abort(&cb)){fail(env,"Local inference cancelled","java/util/concurrent/CancellationException");return true;}return false;}
inline void emit(JNIEnv* env,Callbacks& cb,const std::string& text){jstring s=utf8(env,text);if(!s)return;env->CallVoidMethod(cb.object,cb.textMethod,s);env->DeleteLocalRef(s);}
