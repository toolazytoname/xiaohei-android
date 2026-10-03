# ONNX 1.29.0 arm64 CPU-only 16KB rebuild (2026-10-02)

Worker documentation of the local rebuild under `integrations/operit-upstream-build/onnx-16kb-rebuild/`. This note is **not** independent verification, not a product Gradle result, and not a device/VAD/VITS runtime result.

## Pin

- Upstream tag `v1.29.0`, commit `2e2543fbe9fae542f921d47a72d21d5a4ef0b710`, `VERSION_NUMBER` `1.29.0`.
- NDK `27.1.12297006`, Android API `26`, ABI `arm64-v8a` only.
- Official entry: `tools/ci_build/build.py` (`--android --build_java --build_shared_lib --enable_lto --skip_tests`), CMake extra defines for page-size, providers, and telemetry.
- Operators: default full. Not a minimal/reduced-ops build.

## CPU-only / EP / telemetry

- Built EP: **CPU**.
- Explicitly OFF: `onnxruntime_USE_NNAPI_BUILTIN`, `onnxruntime_USE_XNNPACK`, `onnxruntime_USE_WEBGPU`, `onnxruntime_USE_TELEMETRY`.
- CMakeCache after the successful run: those four flags `BOOL=OFF`; `ANDROID_ABI=arm64-v8a`; `ANDROID_PLATFORM=android-26`.
- Local AAR is **not equivalent** to Maven `onnxruntime-android:1.29.0` (multi-ABI + CPU/NNAPI/XNNPACK/WebGPU). `classes.jar` is copied unchanged, so unused Java EP methods can still exist.

## JDK 17

JDK **17** is required. JDK 21 `jlink` is known to fail on this Android Java package.

- Successful run `JAVA_HOME`: `/Users/lazy/Library/Java/JavaVirtualMachines/openjdk-17.0.2/Contents/Home` (`evidence/inputs.json`).
- `build.sh` now defaults to that home. Override only with `JAVA_HOME_OVERRIDE` (must still be JDK 17). Ambient `JAVA_HOME` is ignored.
- Missing or non-17 JDK fails with an explicit message. This documentation pass did **not** rebuild.

CMakeCache `Java_*` executables may still list the private Temurin 21 from an earlier configure. Do not read that as the successful Gradle JDK.

## Successful artifacts (static, 2026-10-02T14:21:53Z)

Re-hashed locally this pass; match `evidence/assemble-aar.json` / `evidence/elf-check.json`.

| File | Bytes | SHA256 |
|---|---|---|
| `out/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar` | 8286755 | `263f1ea768438829ee145892aa65f8f3ed7cc5d857438cf40a0bf753c00369fc` |
| `out/arm64-v8a/libonnxruntime.so` | 22224184 | `eb8e9924ed01828126df267623fe3f8c1aaf89d535e51c00ac805e47aac1010b` |
| `out/arm64-v8a/libonnxruntime4j_jni.so` | 116464 | `16bd8dc0b7462821bb069b9f52f50aa993d17347dc09a019aeda8dc360d15edc` |

`classes.jar` SHA256 `65e2e2d76d672253aaf0792a06c71cc40d09b0e2e251ccf925bee7175b91a1b0` (unchanged). Local ELF `all_static_pass: true` for the two rebuilt `.so` files only. Not a 32-ELF APK claim.

## JNI

From `evidence/jni-compare.json`, vs cached Maven AAR arm64:

- `libonnxruntime4j_jni.so`: **173/173 identical**.
- `libonnxruntime.so`: Maven has 8 `Java_ai_onnxruntime_telemetry_HttpClient_*` exports; rebuilt has 0 (`USE_TELEMETRY=OFF`). Overall `jni_exports_equal: false`. Compare exit 2.
- This app is **not expected to call** those telemetry native entry points. App ONNX use is `OnnxSileroVad.kt` and `VitsVoiceProvider.kt` with SessionOptions threads/optimization only (no `addNnapi` / `addXnnpack` / `addWebGPU`).

## Runtime

**Runtime VAD/VITS has not been tested** with this AAR. No product Gradle, no APK integration, no 16KB device run in this pass.

## Repro command (caches present; no download)

```bash
cd integrations/operit-upstream-build/onnx-16kb-rebuild
ONNX_BUILD_TIMEOUT_SEC=14400 ONNX_BUILD_JOBS=4 ./build.sh
```

Recipe README: `integrations/operit-upstream-build/onnx-16kb-rebuild/README.md`.
