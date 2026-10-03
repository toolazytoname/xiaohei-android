cmake_minimum_required(VERSION 3.22.1)

project("fbx_jni")

include("${CMAKE_CURRENT_LIST_DIR}/../../cmake/operit_git_source.cmake")
include("${CMAKE_CURRENT_LIST_DIR}/../../cmake/xiaohei_page_size.cmake")

operit_prepare_git_source(
    OPERIT_UFBX_SOURCE_DIR
    OPERIT_UFBX_BINARY_DIR
    ufbx
    "https://github.com/ufbx/ufbx.git"
    "5955c5c0b042ac2dc6f32955ab206aaec0620cfc"
)

add_library(
    FbxWrapper
    SHARED
    src/main/cpp/fbx_jni.cpp
    "${OPERIT_UFBX_SOURCE_DIR}/ufbx.c"
)

set_target_properties(
    FbxWrapper
    PROPERTIES
    C_STANDARD 99
    C_STANDARD_REQUIRED ON
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_include_directories(
    FbxWrapper
    PRIVATE
    "${OPERIT_UFBX_SOURCE_DIR}"
)

target_link_libraries(
    FbxWrapper
    log
)

xiaohei_apply_16kb_page_size(FbxWrapper)
