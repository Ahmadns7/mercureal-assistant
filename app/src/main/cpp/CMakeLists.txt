cmake_minimum_required(VERSION 3.22.1)
project(mercureal_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)

# whisper.cpp and llama.cpp are vendored as git submodules under third_party/.
# Run scripts/setup_native.sh once after cloning.
set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp)
set(LLAMA_DIR   ${CMAKE_SOURCE_DIR}/../../../../third_party/llama.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# llama.cpp first: both trees vendor ggml and whisper guards its ggml build with
# "if (NOT TARGET ggml)", so adding llama first makes its (newer) ggml the single shared copy.
add_subdirectory(${LLAMA_DIR} llama_build)
add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(mercureal_native SHARED
    jni/whisper_jni.cpp
    jni/llama_jni.cpp
)

target_include_directories(mercureal_native PRIVATE
    ${WHISPER_DIR}/include
    ${LLAMA_DIR}/include
)

target_link_libraries(mercureal_native
    whisper
    llama
    android
    log
)
