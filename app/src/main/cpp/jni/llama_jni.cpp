#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "mercureal-llm"

struct LlmHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_ahmadns7_mercureal_assistant_engine_LlmEngine_nativeInit(
        JNIEnv *env, jobject, jstring jpath, jint threads, jint nCtx) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    llama_model *model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "llama model load failed");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        llama_free_model(model);
        return 0;
    }
    auto *h = new LlmHandle{model, ctx};
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ahmadns7_mercureal_assistant_engine_LlmEngine_nativeGenerate(
        JNIEnv *env, jobject, jlong jh, jstring jprompt, jint maxTokens) {
    auto *h = reinterpret_cast<LlmHandle *>(jh);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string text(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    const llama_vocab *vocab = llama_model_get_vocab(h->model);
    std::vector<llama_token> toks(text.size() + 8);
    int n = llama_tokenize(vocab, text.c_str(), text.size(), toks.data(), toks.size(), true, true);
    if (n < 0) {
        toks.resize(-n);
        n = llama_tokenize(vocab, text.c_str(), text.size(), toks.data(), toks.size(), true, true);
    }
    toks.resize(n);

    llama_memory_clear(llama_get_memory(h->ctx), true);  // stateless turns

    llama_batch batch = llama_batch_get_one(toks.data(), toks.size());
    std::string out;
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    for (int i = 0; i < maxTokens; ++i) {
        if (llama_decode(h->ctx, batch) != 0) break;
        llama_token tok = llama_sampler_sample(sampler, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;
        char piece[256];
        int len = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, true);
        if (len > 0) out.append(piece, len);
        batch = llama_batch_get_one(&tok, 1);
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ahmadns7_mercureal_assistant_engine_LlmEngine_nativeFree(
        JNIEnv *, jobject, jlong jh) {
    auto *h = reinterpret_cast<LlmHandle *>(jh);
    if (!h) return;
    llama_free(h->ctx);
    llama_free_model(h->model);
    delete h;
}
