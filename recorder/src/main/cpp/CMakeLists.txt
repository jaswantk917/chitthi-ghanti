cmake_minimum_required(VERSION 3.22.1)
project(shruti CXX)

# the needle engine comes prebuilt (Cactus-Compute/needle3 on Hugging Face) and only arm64 is
# bundled; other ABIs go without Shruti and keep the phone's own recognizer
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_library(needle STATIC IMPORTED)
    set_target_properties(needle PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/needle/${ANDROID_ABI}/libneedle.a)

    add_library(shruti SHARED shruti_jni.cpp)
    target_include_directories(shruti PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/needle)
    target_link_libraries(shruti needle)

    # whisper.cpp with its Vulkan backend, prebuilt by scripts/build-whisper.sh
    foreach(lib whisper ggml ggml-base ggml-cpu ggml-vulkan)
        add_library(${lib} STATIC IMPORTED)
        set_target_properties(${lib} PROPERTIES
            IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/whisper/${ANDROID_ABI}/lib${lib}.a)
    endforeach()

    add_library(whisper_jni SHARED whisper_jni.cpp)
    target_include_directories(whisper_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/whisper/include)
    target_link_libraries(whisper_jni
        -Wl,--start-group whisper ggml ggml-vulkan ggml-cpu ggml-base -Wl,--end-group
        vulkan log)
    # ggml's CPU threads are OpenMP's; its runtime goes inside the library rather than beside it
    target_link_options(whisper_jni PRIVATE -fopenmp -static-openmp)
endif()
