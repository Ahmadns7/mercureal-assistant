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
# sherpa-onnx (offline TTS). Not on Maven Central, so the prebuilt AAR from the official GitHub
# release is fetched here and checked against a pinned sha256 before the build can use it.
SHERPA_VER=2.0.0
SHERPA_SHA256=f0f8716982d27cb4ce695e3934018b1342d9d3d6fdb6ef1f1658fe8bad50b4fc
mkdir -p app/libs
AAR=app/libs/sherpa-onnx-$SHERPA_VER.aar
if ! (echo "$SHERPA_SHA256  $AAR" | sha256sum -c --status 2>/dev/null); then
  curl -fsSL -o "$AAR.part" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$SHERPA_VER/sherpa-onnx-$SHERPA_VER.aar"
  echo "$SHERPA_SHA256  $AAR.part" | sha256sum -c --quiet || { echo "sherpa-onnx AAR checksum mismatch"; rm -f "$AAR.part"; exit 1; }
  mv "$AAR.part" "$AAR"
fi
echo "native deps ready: whisper.cpp $WHISPER_TAG, llama.cpp $LLAMA_TAG, sherpa-onnx $SHERPA_VER"
