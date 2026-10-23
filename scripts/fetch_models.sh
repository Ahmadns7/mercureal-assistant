#!/usr/bin/env bash
# Downloads the models ONCE on your laptop, then sideloads them to the phone.
# The app itself never downloads anything.
set -euo pipefail
OUT="${1:-models}"
mkdir -p "$OUT"
curl -L -o "$OUT/ggml-tiny.en.bin" \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
curl -L -o "$OUT/qwen2.5-0.5b-instruct-q4_k_m.gguf" \
  https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf
PKG=com.ahmadns7.mercureal.assistant
DEST=/sdcard/Android/data/$PKG/files
adb shell mkdir -p "$DEST"
adb push "$OUT/ggml-tiny.en.bin" "$DEST/"
adb push "$OUT/qwen2.5-0.5b-instruct-q4_k_m.gguf" "$DEST/"
echo "models pushed to $DEST"
