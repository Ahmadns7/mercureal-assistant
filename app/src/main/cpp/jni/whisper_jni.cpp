#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "whisper.h"

#define TAG "mercureal-stt"

extern "C" JNIEXPORT jlong JNICALL
Java_com_ahmadns7_mercureal_assistant_engine_SttEngine_nativeInit(
        JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // CPU only for the MVP, keeps the build predictable
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!ctx) __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_init failed");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ahmadns7_mercureal_assistant_engine_SttEngine_nativeTranscribe(
        JNIEnv *env, jobject, jlong jctx, jfloatArray jpcm, jstring jlang) {
    auto *ctx = reinterpret_cast<whisper_context *>(jctx);
    jsize n = env->GetArrayLength(jpcm);
    std::vector<float> pcm(n);
    env->GetFloatArrayRegion(jpcm, 0, n, pcm.data());

    const char *lang_c = env->GetStringUTFChars(jlang, nullptr);
    std::string lang(lang_c);
    env->ReleaseStringUTFChars(jlang, lang_c);
    // "auto" asks whisper to detect the spoken language (multilingual models only).
    const bool detect = lang == "auto";

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = detect ? "auto" : lang.c_str();
    params.detect_language = false;
    params.translate = false;
    params.no_context = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.n_threads = 4;

    std::string out;
    if (whisper_full(ctx, params, pcm.data(), static_cast<int>(n)) == 0) {
        const int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; ++i) out += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ahmadns7_mercureal_assistant_engine_SttEngine_nativeFree(
        JNIEnv *, jobject, jlong jctx) {
    auto *ctx = reinterpret_cast<whisper_context *>(jctx);
    if (ctx) whisper_free(ctx);
}
