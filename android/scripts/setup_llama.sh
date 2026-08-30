#!/usr/bin/env bash
# Kayan X — clone and prepare llama.cpp for NDK build
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CPP_DIR="$SCRIPT_DIR/../app/src/main/cpp"
LLAMA_DIR="$CPP_DIR/llama"

if [ -d "$LLAMA_DIR/.git" ]; then
  echo "llama.cpp already present — pulling latest"
  git -C "$LLAMA_DIR" pull --ff-only
else
  echo "Cloning llama.cpp (shallow)…"
  git clone --depth 1 https://github.com/ggml-org/llama.cpp.git "$LLAMA_DIR"
fi

echo "llama.cpp ready at $LLAMA_DIR"
echo "Now open the project in Android Studio and build."
echo "First build will compile the native library (several minutes)."
