#!/usr/bin/env bash
# Vendors whisper.cpp and llama.cpp as git submodules at tags verified against the JNI sources
# (app/src/main/cpp/jni). Run once after cloning, and again after changing a pin below.
set -euo pipefail
cd "$(dirname "$0")/.."
WHISPER_TAG=v1.7.6      # exports whisper_init_from_file_with_params, whisper_full_*, whisper_full_get_segment_text
LLAMA_TAG=b5957         # exports llama_model_load_from_file, llama_init_from_model, llama_memory_clear, llama_get_memory, llama_vocab_is_eog

git submodule update --init --recursive
git -C third_party/whisper.cpp fetch --quiet --tags origin
git -C third_party/whisper.cpp checkout --quiet "$WHISPER_TAG"
git -C third_party/llama.cpp fetch --quiet --tags origin
git -C third_party/llama.cpp checkout --quiet "$LLAMA_TAG"
echo "native deps ready: whisper.cpp $WHISPER_TAG, llama.cpp $LLAMA_TAG"
