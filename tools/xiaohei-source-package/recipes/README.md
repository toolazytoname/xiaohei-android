# Native recipe inputs

Explicit `ANDROID_SDK_ROOT` is required; no author home or implicit Gradle cache is searched. `NDK`, `CMAKE`, `NINJA` may override paths. Darwin arm64/x86_64 select the NDK `darwin-x86_64` directory; Linux x86_64 selects `linux-x86_64`. Other hosts fail. Downloads respect the caller's normal curl environment; no hardcoded localhost proxy.

| Recipe | Required inputs / limits |
|---|---|
| GIF 1.2.28 | SDK NDK27.0.12077973 + CMake3.22.1; optional `GIF_AAR` with pinned official SHA, otherwise fixed Maven download. Builds native, verifies JNI, wraps AAR preserving classes/resources, excludes nonarm64 JNI. |
| graphics-path 1.0.1 | SDK NDK27.1.12297006 + CMake3.22.1; explicit `GRAPHICS_PATH_AAR` or pinned official download. |
| Filament 1.69.2 | SDK + explicit official AARs (`AAR_FILAMENT`, `AAR_UTILS`, `AAR_GLTFIO`). Uses official prebuilt static archive: this is NOT a whole-library clean-source rebuild. |
| ONNX 1.29.0 | SDK + `JAVA_HOME_OVERRIDE` JDK17, `PY` Python3.12, `CMAKE`3.28.6, `ONNX_AAR` official AAR with pinned hash; predownloaded pinned source under downloads and explicit dependencies required. No promise a clean checkout is self-contained. |

Example GIF: `ANDROID_SDK_ROOT=/path/to/sdk GIF_AAR=/path/to/official-1.2.28.aar bash gif-16k-rebuild/build.sh`.

2026-10-03 independent GIF rebuild used a new source/build directory and a hash-verified previously downloaded archive. Every shared AAR member (including the arm64 .so) matched the production AAR. ZIP SHA differs because the wrapped artifact omits unused ABIs. Full ONNX/Filament rebuild and network bootstrap were NOT repeated. Input/syntax tests are not native builds, device tests or license completion.
