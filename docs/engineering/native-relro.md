# Native 16 KB RELRO：本地可重链范围

Date: 2026-10-02.

商店候选 APK 的 32 个 ELF 全部满足 LOAD 16 KB，但 17 个 `GNU_RELRO` 末端 `(VirtAddr + MemSiz) % 16384 != 0`。旧“全合规”只覆盖了 LOAD。NDK r27 及以下必须同时使用：

```
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

只加 `max-page-size` 可以把 LOAD 对齐到 0x4000，仍可能留下 RELRO 末端余数。本轮只改共同版 CMake 可重链目标；不替换预编译库、不裁剪功能、不二进制 patch、不跑 Gradle 整包。

## Helper

`cmake/xiaohei_page_size.cmake`：

- `xiaohei_apply_16kb_page_size`：给已存在的非 IMPORTED `SHARED`/`MODULE` 补缺的 max/common 标志，已有标志不重复。
- `xiaohei_apply_16kb_page_size_graph`：从 Wrapper 沿 `LINK_LIBRARIES` 走到 llama/ggml 等依赖。
- `xiaohei_apply_16kb_page_size_tree`：当前 CMake 目录树里所有非 IMPORTED SHARED/MODULE。
- 静态库、OBJECT、IMPORTED、NDK 预编译（如 `libomp.so`）一律跳过，不能当完成证明。

接入：`llm/llama`、`llm/mnn`、`avator/{fbx,mmd,dragonbones}`、`quickjs`、`app/src/main/cpp`（仅对齐函数，未改 native pin）。`terminal` 的 `libpty.so` 此前已有双标志，本轮写集未改。

## 本轮 CMake 能覆盖的本地库（须等主代理 Gradle 重链后用 APK readelf 验收）

基线 RELRO 失败、且由上述 CMake 产出：

- `libFbxWrapper.so`
- `libMmdWrapper.so`
- `libdragonbones_native.so`
- `libLlamaWrapper.so`
- `libllama.so`
- `libggml.so`
- `libggml-base.so`
- `libggml-cpu.so`

基线 RELRO 已过、本轮改为统一 helper（避免以后漏 `common-page-size`）：

- `libMNN.so`、`libMNNWrapper.so`
- `libquickjsjni.so`（原先只有 max，末端碰巧为 0）
- `libstreamnative.so`、`libtoolpkgwasm.so`、`libsherpa-ncnn-jni.so`
- `libllama-common.so`（基线已过；若仍是 SHARED，tree/graph 会补双标志）

MNN 的 `llm` 目标在当前配置是 OBJECT，没有独立 ELF；不能把给它加 link flags 写成修复。

## 预编译 / 非本轮写集，仍欠修复

基线 RELRO 仍失败，且不是这次 CMake 重链对象：

- `libandroidx.graphics.path.so`
- `libfilament-jni.so`、`libfilament-utils-jni.so`
- `libobjectbox-jni.so`
- `libomp.so`（NDK 预编译）
- `libonnxruntime.so`
- `liboperit_ripgrep.so`
- `libsherpa-mnn-jni.so`（Java 加载，无本写集 CMake 目标）
- `libtensorflowlite_jni.so`

未在本轮改 `build.gradle`、terminal gitlink、`.cxx`、jniLibs 预编译。

## 验证

`cmake/tests/run_page_size_tests.sh`：在 `/tmp` 独立工程里调用 helper，检查 `LlamaWrapper`/`ggml`/`ggml_cpu` 的 `LINK_OPTIONS`，静态与 IMPORTED 不得带标志；再用 NDK r27 链微型 `.so`，`llvm-readelf -lW` 查 LOAD 对齐与 RELRO 末端。这不是商店 APK 32 ELF 验收。

## 未完成

- 商店 APK/AAB 未按新标志重编，不能写 32/32 或 17 RELRO 已清零。
- 预编译 9 个 RELRO 失败库仍在。
- 16 KB 真机运行未测。
