cmake_minimum_required(VERSION 3.22.1)

project("llama_jni")

include("${CMAKE_CURRENT_LIST_DIR}/../../cmake/operit_git_source.cmake")
include("${CMAKE_CURRENT_LIST_DIR}/../../cmake/xiaohei_page_size.cmake")

set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
# Keep the Clang OpenMP CPU backend. NDK r27 libomp.so fails official RELRO
# end alignment; llama links the locally relinked copy instead of disabling
# GGML_OPENMP.
set(GGML_OPENMP ON CACHE BOOL "" FORCE)
get_filename_component(
    XIAOHEI_LIBOMP_16KB
    "${CMAKE_CURRENT_LIST_DIR}/../../../operit-upstream-build/native-16kb-rebuilds/libomp/out/arm64-v8a/libomp.so"
    ABSOLUTE
)
if(NOT EXISTS "${XIAOHEI_LIBOMP_16KB}")
    message(FATAL_ERROR
        "Missing 16KB-relinked libomp.so: ${XIAOHEI_LIBOMP_16KB}. "
        "Run integrations/operit-upstream-build/native-16kb-rebuilds/libomp/build.sh"
    )
endif()
set(OpenMP_C_FLAGS "-fopenmp" CACHE STRING "" FORCE)
set(OpenMP_CXX_FLAGS "-fopenmp" CACHE STRING "" FORCE)
set(OpenMP_C_LIB_NAMES "omp" CACHE STRING "" FORCE)
set(OpenMP_CXX_LIB_NAMES "omp" CACHE STRING "" FORCE)
set(OpenMP_omp_LIBRARY "${XIAOHEI_LIBOMP_16KB}" CACHE FILEPATH "" FORCE)
if(NOT TARGET omp)
    add_library(omp SHARED IMPORTED GLOBAL)
endif()
set_target_properties(omp PROPERTIES IMPORTED_LOCATION "${XIAOHEI_LIBOMP_16KB}")

operit_prepare_git_source(
    OPERIT_LLAMA_CPP_SOURCE_DIR
    OPERIT_LLAMA_CPP_BINARY_DIR
    llama_cpp
    "https://github.com/ggml-org/llama.cpp.git"
    "720d7fa4097f76e5d0eade5a92c1df87c1faf9d9"
)

add_subdirectory("${OPERIT_LLAMA_CPP_SOURCE_DIR}" "${OPERIT_LLAMA_CPP_BINARY_DIR}" EXCLUDE_FROM_ALL)

add_library(
    LlamaWrapper
    SHARED
    src/main/cpp/llama_jni_stub.cpp
)

target_compile_definitions(LlamaWrapper PRIVATE OPERIT_HAS_LLAMA_CPP=1)
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/common")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/include")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/ggml/include")
target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_SOURCE_DIR}/vendor")
target_link_libraries(LlamaWrapper llama llama-common)

target_link_libraries(
    LlamaWrapper
    android
    log
)

# NDK r27: max+common page-size on every non-IMPORTED SHARED (wrapper, llama, ggml*).
xiaohei_apply_16kb_page_size_graph(LlamaWrapper)
xiaohei_apply_16kb_page_size_tree()
