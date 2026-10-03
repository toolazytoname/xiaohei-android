add_library(ggml SHARED "${XIAOHEI_PAGE_SIZE_PROBE_C}")
add_library(ggml_cpu SHARED "${XIAOHEI_PAGE_SIZE_PROBE_C}")
add_library(ggml_static STATIC "${XIAOHEI_PAGE_SIZE_PROBE_C}")
target_link_libraries(ggml PRIVATE ggml_cpu)
