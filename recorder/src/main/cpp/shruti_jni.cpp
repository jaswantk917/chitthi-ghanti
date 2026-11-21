// JNI bridge to the needle engine running the Shruti speech model (Shruti.kt), copied from Hey re.
// needle is process-global and not thread-safe: Kotlin calls all of this from one thread (Shruti.engine).

#include <jni.h>
#include <cstring>
#include <string>
#include <vector>
#include "needle.h"

namespace {

constexpr int OUT_CAPACITY = 64 * 1024;

std::string text(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

// needle answers in UTF-8 JSON, which NewStringUTF can't take whole (it wants modified UTF-8,
// and Devanagari is fine but emoji or a stray byte would not be): go through a Java String
jstring json(JNIEnv* env, const std::vector<char>& out) {
    const auto length = static_cast<jsize>(strnlen(out.data(), out.size()));
    jbyteArray bytes = env->NewByteArray(length);
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte*>(out.data()));
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    return static_cast<jstring>(env->NewObject(stringClass, constructor, bytes, env->NewStringUTF("UTF-8")));
}

jstring failure(JNIEnv* env) {
    const char* error = needle_last_error();
    return env->NewStringUTF(error != nullptr ? error : "needle failed");
}

}  // namespace

extern "C" {

// the model bytes must outlive the engine, so they come in a direct buffer Kotlin never frees
JNIEXPORT jint JNICALL
Java_io_github_jaswantk917_tring_recorder_Needle_load(JNIEnv* env, jobject, jobject model) {
    auto* bytes = static_cast<const unsigned char*>(env->GetDirectBufferAddress(model));
    return needle_load(bytes, static_cast<unsigned long long>(env->GetDirectBufferCapacity(model)));
}

JNIEXPORT jstring JNICALL
Java_io_github_jaswantk917_tring_recorder_Needle_lastError(JNIEnv* env, jobject) {
    return failure(env);
}

JNIEXPORT jstring JNICALL
Java_io_github_jaswantk917_tring_recorder_Needle_transcribe(
    JNIEnv* env, jobject, jfloatArray pcm, jint samples, jstring language, jstring keywords) {
    std::vector<char> out(OUT_CAPACITY, 0);
    const std::string spoken = text(env, language);
    const std::string words = text(env, keywords);
    jfloat* audio = env->GetFloatArrayElements(pcm, nullptr);
    const int result = needle_transcribe(
        audio, samples, spoken.empty() ? nullptr : spoken.c_str(), words.empty() ? nullptr : words.c_str(),
        0, out.data(), OUT_CAPACITY);
    env->ReleaseFloatArrayElements(pcm, audio, JNI_ABORT);
    return result < 0 ? nullptr : json(env, out);
}

}  // extern "C"
