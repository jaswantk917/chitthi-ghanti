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
endif()
