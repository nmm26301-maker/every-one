#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_NDK:?Set ANDROID_NDK to Android NDK r27c or newer}"
CMAKE_EXEC="${CMAKE_EXEC:-cmake}"
NINJA_EXEC="${NINJA_EXEC:-ninja}"
for engine in whisper llama speech; do
 "$CMAKE_EXEC" -S native -B "build/native-$engine-arm64" -G Ninja -DCMAKE_MAKE_PROGRAM="$NINJA_EXEC" -DLOCAL_ENGINE="$engine" -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-23 -DANDROID_STL=c++_shared -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON
 "$CMAKE_EXEC" --build "build/native-$engine-arm64" -j4 --target "english_$engine"
 mkdir -p app/src/main/jniLibs/arm64-v8a
 cp "build/native-$engine-arm64/libenglish_$engine.so" app/src/main/jniLibs/arm64-v8a/
 "$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "app/src/main/jniLibs/arm64-v8a/libenglish_$engine.so"
done
cp "$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" app/src/main/jniLibs/arm64-v8a/
