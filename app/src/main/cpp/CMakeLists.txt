cmake_minimum_required(VERSION 3.22.1)

project(
    gasp_ai_native
    LANGUAGES C CXX
)

set(
    CMAKE_CXX_STANDARD
    17
)

set(
    CMAKE_CXX_STANDARD_REQUIRED
    ON
)

#
# Gasp AI:
# CPU-only inicialmente.
#
# Nada de Vulkan/OpenCL nesta primeira versão.
# Primeiro garantimos estabilidade no Exynos 8895.
#

set(
    GGML_NATIVE
    OFF
    CACHE BOOL "" FORCE
)

set(
    GGML_OPENMP
    OFF
    CACHE BOOL "" FORCE
)

set(
    GGML_VULKAN
    OFF
    CACHE BOOL "" FORCE
)

set(
    GGML_OPENCL
    OFF
    CACHE BOOL "" FORCE
)

set(
    LLAMA_BUILD_TESTS
    OFF
    CACHE BOOL "" FORCE
)

set(
    LLAMA_BUILD_EXAMPLES
    OFF
    CACHE BOOL "" FORCE
)

set(
    LLAMA_BUILD_SERVER
    OFF
    CACHE BOOL "" FORCE
)

set(
    BUILD_SHARED_LIBS
    OFF
    CACHE BOOL "" FORCE
)

add_subdirectory(
    llama.cpp
    llama-build
)

add_library(
    gasp_ai_native
    SHARED
    gasp_ai_jni.cpp
)

target_include_directories(
    gasp_ai_native
    PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
)

target_link_libraries(
    gasp_ai_native
    PRIVATE
    llama
    android
    log
)
