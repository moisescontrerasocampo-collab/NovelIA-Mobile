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
# GGML se genera como librerías compartidas, incluso con BUILD_SHARED_LIBS=OFF.
# Debemos incluirlas en la APK junto al ejecutable, y no hacer una
# declaración errónea de que el binario está enlazado 100% estáticamente.
cp "$BIN" "$OUT/libace-server.so"
for lib in "$BUILD"/build-android/libggml*.so; do
  test -f "$lib" || continue
  cp "$lib" "$OUT/"
done
if "$READELF" -d "$BIN" | grep -q 'libc++_shared.so'; then
  cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$OUT/"
fi
if "$READELF" -d "$BIN" | grep -E 'NEEDED.*libomp.so'; then
  echo "No se admite libomp sin empaquetar" >&2; exit 1
fi
for needed in $("$READELF" -d "$BIN" | sed -n 's/.*Shared library: \[\(libggml[^]]*\.so\)\].*/\1/p'); do
  test -s "$OUT/$needed" || { echo "Falta biblioteca $needed" >&2;exit 1; }
done
echo "Bibliotecas incluidas:"
find "$OUT" -maxdepth 1 -type f -name '*.so' -printf '%f %s bytes\n'
echo "Motor ARM64 creado:"
du -h "$OUT/libace-server.so"
