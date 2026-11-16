#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="$ANDROID_NDK_HOME"
test -n "$NDK" || { echo "Falta ANDROID_NDK_HOME" >&2; exit 1; }
BUILD="$ROOT/.build/acestep"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$ROOT/.build" "$OUT"
git clone --depth 1 --recurse-submodules https://github.com/ServeurpersoCom/acestep.cpp.git "$BUILD"
cd "$BUILD"
git submodule update --init --recursive
cmake -S . -B build-android -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DANDROID_STL=c++_static \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF \
  -DGGML_BACKEND_DL=OFF \
  -DGGML_BLAS=OFF \
  -DGGML_CPU_ALL_VARIANTS=OFF \
  -DGGML_CUDA=OFF -DGGML_VULKAN=OFF \
  -DGGML_BUILD_TESTS=OFF -DGGML_BUILD_EXAMPLES=OFF \
  -DBUILD_TESTING=OFF
cmake --build build-android --target ace-server --parallel 2
BIN="$BUILD/build-android/ace-server"
test -s "$BIN"
READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
"$READELF" -h "$BIN" | grep -E "Machine:.*AArch64"
"$READELF" -h "$BIN" | grep -E "Type:.*DYN"
"$READELF" -d "$BIN" | grep NEEDED || true
if "$READELF" -d "$BIN" | grep -E 'NEEDED.*(libggml|libomp|libc\+\+_shared)'; then
  echo 'Error: falta enlazado estático de bibliotecas del motor' >&2
  exit 1
fi
cp "$BIN" "$OUT/libace-server.so"
chmod 755 "$OUT/libace-server.so"
echo "Motor ARM64 creado:"
du -h "$OUT/libace-server.so"
