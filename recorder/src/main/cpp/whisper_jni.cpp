// JNI bridge to whisper.cpp (Whisper.kt). Kotlin keeps one context and calls it from one thread.

#include <jni.h>
#include <atomic>
#include <cstdio>
#include <string>
#include "whisper.h"

namespace {

std::atomic<bool> stopping{false};

void escaped(std::string& out, const std::string& text) {
    for (const unsigned char c : text) {
        if (c == '"') out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else if (c < 0x20) {
            char code[8];
            snprintf(code, sizeof code, "\\u%04x", c);
            out += code;
        } else out += static_cast<char>(c);
    }
}

// whisper.cpp's text is UTF-8, which NewStringUTF can't take whole (it wants modified UTF-8): go through a Java String
jstring string(JNIEnv* env, const std::string& utf8) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(utf8.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(utf8.size()), reinterpret_cast<const jbyte*>(utf8.data()));
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    return static_cast<jstring>(env->NewObject(stringClass, constructor, bytes, env->NewStringUTF("UTF-8")));
}

std::string text(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" {

// 0 when the model can't be read
JNIEXPORT jlong JNICALL
Java_io_github_jaswantk917_tring_recorder_WhisperNative_load(JNIEnv* env, jobject, jstring model) {
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = true;
    params.flash_attn = true;
    return reinterpret_cast<jlong>(whisper_init_from_file_with_params(text(env, model).c_str(), params));
}

JNIEXPORT void JNICALL
Java_io_github_jaswantk917_tring_recorder_WhisperNative_free(JNIEnv*, jobject, jlong context) {
    whisper_free(reinterpret_cast<whisper_context*>(context));
}

// the transcribe running now returns soon, with nothing
JNIEXPORT void JNICALL
Java_io_github_jaswantk917_tring_recorder_WhisperNative_stop(JNIEnv*, jobject) {
    stopping = true;
}

// {"words": [{"w": text, "s": start ms, "e": end ms, "p": probability, "g": segment}]} for 16 kHz mono pcm, or null
JNIEXPORT jstring JNICALL
Java_io_github_jaswantk917_tring_recorder_WhisperNative_transcribe(
    JNIEnv* env, jobject, jlong handle, jfloatArray pcm, jstring vadModel, jint threads) {
    auto* context = reinterpret_cast<whisper_context*>(handle);
    stopping = false;
    const std::string vad = text(env, vadModel);

    // what whisper-cli ran when the calls were judged: -l hi -mc 0 -sns --vad, beam search over five
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.strategy = WHISPER_SAMPLING_BEAM_SEARCH;
    params.greedy.best_of = 5;
    params.beam_search.beam_size = 5;
    params.language = "hi";
    params.n_threads = threads;
    params.n_max_text_ctx = 0;
    params.suppress_nst = true;
    params.vad = true;
    params.vad_model_path = vad.c_str();
    params.token_timestamps = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.abort_callback = [](void*) { return stopping.load(); };

    jfloat* audio = env->GetFloatArrayElements(pcm, nullptr);
    const int result = whisper_full(context, params, audio, env->GetArrayLength(pcm));
    env->ReleaseFloatArrayElements(pcm, audio, JNI_ABORT);
    if (result != 0 || stopping) return nullptr;

    // tokens into words (a token starting with a space starts one), each word timed in the call: token
    // times are in the speech-only audio, so they're spread over the segment's times in the call
    const whisper_token last = whisper_token_eot(context);
    std::string json = "{\"words\":[";
    bool first = true;
    for (int segment = 0; segment < whisper_full_n_segments(context); ++segment) {
        const int tokens = whisper_full_n_tokens(context, segment);
        const int64_t callStart = whisper_full_get_segment_t0(context, segment);
        const int64_t callEnd = whisper_full_get_segment_t1(context, segment);
        int64_t heardStart = -1, heardEnd = -1;
        for (int i = 0; i < tokens; ++i) {
            const whisper_token_data data = whisper_full_get_token_data(context, segment, i);
            if (data.id >= last) continue;
            if (heardStart < 0) heardStart = data.t0;
            heardEnd = data.t1;
        }
        const auto inCall = [&](int64_t heard) -> int64_t {
            if (heardEnd <= heardStart) return callStart * 10;
            const double along = static_cast<double>(heard - heardStart) / static_cast<double>(heardEnd - heardStart);
            return static_cast<int64_t>((callStart + along * static_cast<double>(callEnd - callStart)) * 10);
        };

        std::string word;
        int64_t start = 0, end = 0;
        double probability = 0;
        int pieces = 0;
        const auto flush = [&]() {
            if (word.empty()) return;
            json += first ? "{\"w\":\"" : ",{\"w\":\"";
            first = false;
            escaped(json, word);
            char rest[96];
            snprintf(rest, sizeof rest, "\",\"s\":%lld,\"e\":%lld,\"p\":%.3f,\"g\":%d}",
                     static_cast<long long>(start), static_cast<long long>(end), probability / pieces, segment);
            json += rest;
            word.clear();
            probability = 0;
            pieces = 0;
        };
        for (int i = 0; i < tokens; ++i) {
            const whisper_token_data data = whisper_full_get_token_data(context, segment, i);
            if (data.id >= last) continue;
            const char* piece = whisper_full_get_token_text(context, segment, i);
            if (piece[0] == ' ') flush();
            if (word.empty()) start = inCall(data.t0);
            word += piece;
            end = inCall(data.t1);
            probability += data.p;
            ++pieces;
        }
        flush();
    }
    json += "]}";
    return string(env, json);
}

}  // extern "C"
