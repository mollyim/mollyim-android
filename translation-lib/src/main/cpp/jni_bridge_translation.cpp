#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>
#include "translation_engine.h"

#define TAG "TranslationJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

using namespace molly::translation;

static std::unique_ptr<TranslationEngine> g_engine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_im_molly_translation_TranslationEngine_nativeInitialize(
    JNIEnv* env, jobject /* this */, jstring modelPath) {

    if (!modelPath) {
        return JNI_FALSE;
    }

    const char* path_cstr = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(path_cstr);
    env->ReleaseStringUTFChars(modelPath, path_cstr);

    try {
        g_engine = std::make_unique<TranslationEngine>();
        return g_engine->initialize(path) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jobject JNICALL
Java_im_molly_translation_TranslationEngine_nativeTranslate(
    JNIEnv* env, jobject /* this */,
    jstring sourceText, jstring sourceLang, jstring targetLang) {

    if (!g_engine || !sourceText) {
        return nullptr;
    }

    const char* text_cstr = env->GetStringUTFChars(sourceText, nullptr);
    const char* src_lang_cstr = env->GetStringUTFChars(sourceLang, nullptr);
    const char* tgt_lang_cstr = env->GetStringUTFChars(targetLang, nullptr);

    std::string text(text_cstr);
    std::string src_lang(src_lang_cstr);
    std::string tgt_lang(tgt_lang_cstr);

    env->ReleaseStringUTFChars(sourceText, text_cstr);
    env->ReleaseStringUTFChars(sourceLang, src_lang_cstr);
    env->ReleaseStringUTFChars(targetLang, tgt_lang_cstr);

    TranslationResult result = g_engine->translate(text, src_lang, tgt_lang);

    // Create TranslationResult Java object
    jclass resultClass = env->FindClass("im/molly/translation/TranslationResult");
    if (!resultClass) return nullptr;

    jmethodID constructor = env->GetMethodID(resultClass, "<init>",
        "(Ljava/lang/String;FJZ)V");
    if (!constructor) return nullptr;

    jstring translatedText = env->NewStringUTF(result.translated_text.c_str());

    jobject jresult = env->NewObject(resultClass, constructor,
        translatedText,
        result.confidence,
        (jlong)result.inference_time_us,
        result.used_network ? JNI_TRUE : JNI_FALSE
    );

    return jresult;
}

} // extern "C"
