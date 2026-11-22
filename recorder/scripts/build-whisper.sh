#!/bin/zsh
# Builds whisper.cpp for arm64 Android with its Vulkan (GPU) backend, into src/main/cpp/whisper.
# Needs the NDK below, cmake, ninja, a host clang and shaderc's glslc (brew install shaderc); fetches
# whisper.cpp and the Vulkan and SPIR-V headers at pinned versions. Run it again only to move to
# another whisper.cpp.
#
# Shaders: glslc-mali leaves out the features Mali GPUs lack (NVIDIA's matrix extensions, bf16,
# fp8, fp4). Cooperative matrices and integer dot products stay in: the NDK's older glslc, which
# has neither, made a build that encoded faster on the Pixel 9 but wrote different, worse text on
# a real call (it lost half a minute of talk), while this one writes what whisper-cli on a Mac does.
#
# OpenMP: without it ggml starts its worker threads afresh for every graph, and speech detection
# runs one tiny graph per 32 ms of audio: 36 s for 88 s of call against 2 s with it. Linked
# statically (-static-openmp in CMakeLists.txt), so no libomp.so ships.
set -euo pipefail

WHISPER=v1.9.5
VULKAN_HEADERS=v1.4.357
SPIRV_HEADERS=vulkan-sdk-1.4.357.0
NDK=${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/28.2.13676358}
API=29

HERE=${0:A:h}
OUT=$HERE/../src/main/cpp/whisper
WORK=$(mktemp -d)
trap 'rm -rf $WORK' EXIT

git clone -q --depth 1 --branch $WHISPER https://github.com/ggml-org/whisper.cpp $WORK/whisper.cpp
git clone -q --depth 1 --branch $VULKAN_HEADERS https://github.com/KhronosGroup/Vulkan-Headers $WORK/vulkan-headers
git clone -q --depth 1 --branch $SPIRV_HEADERS https://github.com/KhronosGroup/SPIRV-Headers $WORK/spirv-headers
cmake -S $WORK/spirv-headers -B $WORK/spirv-build -DCMAKE_INSTALL_PREFIX=$WORK/spirv -DSPIRV_HEADERS_ENABLE_TESTS=OFF >/dev/null
cmake --install $WORK/spirv-build >/dev/null

# the NDK has Vulkan's C headers but not vulkan.hpp, which ggml's backend is written against
mkdir -p $WORK/include
cp -R $WORK/vulkan-headers/include/vulkan $WORK/vulkan-headers/include/vk_video $WORK/spirv/include/spirv $WORK/include/

SYSROOT=$NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot
cmake -S $WORK/whisper.cpp -B $WORK/build -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$API -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF \
  -DGGML_OPENMP=ON -DGGML_NATIVE=OFF -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16 \
  -DGGML_VULKAN=ON \
  -DVulkan_INCLUDE_DIR=$WORK/include \
  -DVulkan_LIBRARY=$SYSROOT/usr/lib/aarch64-linux-android/$API/libvulkan.so \
  -DVulkan_GLSLC_EXECUTABLE=$HERE/glslc-mali \
  -DSPIRV-Headers_DIR=$WORK/spirv/share/cmake/SPIRV-Headers \
  -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_SERVER=OFF -DWHISPER_SDL2=OFF >/dev/null
cmake --build $WORK/build --target whisper -j 6

rm -rf $OUT
mkdir -p $OUT/arm64-v8a $OUT/include
B=$WORK/build
cp $B/src/libwhisper.a $B/ggml/src/libggml.a $B/ggml/src/libggml-base.a $B/ggml/src/libggml-cpu.a \
   $B/ggml/src/ggml-vulkan/libggml-vulkan.a $OUT/arm64-v8a/
$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip -g $OUT/arm64-v8a/*.a
cp $WORK/whisper.cpp/include/whisper.h $WORK/whisper.cpp/ggml/include/*.h $OUT/include/
cp $WORK/whisper.cpp/LICENSE $OUT/LICENSE
echo "whisper.cpp $WHISPER, Vulkan-Headers $VULKAN_HEADERS, SPIRV-Headers $SPIRV_HEADERS, NDK ${NDK:t}, android-$API" > $OUT/VERSION
du -sh $OUT/arm64-v8a
