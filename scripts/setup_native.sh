#!/usr/bin/env bash
# Vendors whisper.cpp and llama.cpp as git submodules (pinned tags). Run once after cloning.
set -euo pipefail
cd "$(dirname "$0")/.."
git submodule add -f https://github.com/ggerganov/whisper.cpp third_party/whisper.cpp || true
git submodule add -f https://github.com/ggerganov/llama.cpp third_party/llama.cpp || true
git submodule update --init --recursive
echo "native deps ready. pin tags with: git -C third_party/<name> checkout <tag>"
