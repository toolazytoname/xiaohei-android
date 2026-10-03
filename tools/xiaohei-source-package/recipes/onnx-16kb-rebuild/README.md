# ONNX Runtime 1.29.0 arm64 CPU-only 16KB local rebuild

Local recipe for Microsoft ONNX Runtime **v1.29.0** / commit `2e2543fbe9fae542f921d47a72d21d5a4ef0b710`. Official Android/Java entry is `tools/ci_build/build.py`. This directory is **not** a Maven multi-ABI / multi-EP replacement, and it is **not** a product Gradle or device result.

Do not re-download source or CMake. Do not copy Maven `.so` files. Do not patch ELF program headers. Do not disable RELRO.

## Pin

| Item | Value |
|---|---|
| Tag | `v1.29.0` |
| Commit | `2e2543fbe9fae542f921d47a72d21d5a4ef0b710` |
| `VERSION_NUMBER` | `1.29.0` |
| NDK | `27.1.12297006` |
| Android API | `26` |
| ABI | `arm64-v8a` only (`multi_abi: false`) |
| Operators | default full (`MINIMAL` / `REDUCED_OPS` / `DISABLE_CONTRIB` not enabled) |
| Execution providers built | CPU |
| EPs explicitly OFF | NNAPI, XNNPACK, WebGPU |
| Telemetry | `onnxruntime_USE_TELEMETRY=OFF` |
| JDK | **17 required**. JDK 21 `jlink` is known to fail. Ambient `JAVA_HOME` is ignored. Override only with `JAVA_HOME_OVERRIDE` pointing at another JDK 17 home. Default: `/Users/lazy/Library/Java/JavaVirtualMachines/openjdk-17.0.2/Contents/Home`. |
| CMake (task-local) | 3.28.6 |
| Page-size flags | `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` |

Source archive SHA256 `00a7483d894037b23e5f2a7d9b18c4026e3585e2636b316cd2870b6e1bc660cb`. CMake archive SHA256 `e992f382a98839aefc9292142699af5d43952e3087509d7671aef0dbeb33e852`.

## Scope

- CPU-only shared `libonnxruntime.so` + `libonnxruntime4j_jni.so`, then a local AAR that keeps official 1.29.0 `classes.jar` / manifest / headers / proguard and replaces only arm64 JNI.
- Skips Maven AAR `armeabi-v7a` / `x86` / `x86_64` JNI. Java methods such as `addNnapi` may still exist in `classes.jar`; those native EPs were not built.
- App ONNX usage in this product is Silero VAD and VITS CPU sessions (threads/optimization only). **Runtime VAD/VITS has not been tested** with this AAR.
- Telemetry native `Java_ai_onnxruntime_telemetry_HttpClient_*` entry points are absent in the rebuilt `libonnxruntime.so`. This app is **not expected to call** those native entry points.

## Reproduce (no network if caches are present)

```bash
cd integrations/operit-upstream-build/onnx-16kb-rebuild
# optional: JAVA_HOME_OVERRIDE=/path/to/jdk-17
ONNX_BUILD_TIMEOUT_SEC=14400 ONNX_BUILD_JOBS=4 ./build.sh
```

Fails fast if JDK 17 is missing or `JAVA_HOME_OVERRIDE` is not JDK 17. Fails if the source/CMake archives are missing (no re-download). Uses `FETCHCONTENT_UPDATES_DISCONNECTED` plus the local dep mirror / `SOURCE_DIR` cache. Remaining FetchContent follows the calling environment; this script does not add a proxy fallback.

Successful local artifacts already exist under `out/`. Do not rebuild just to refresh documentation.

## Successful local artifacts (2026-10-02T14:21:53Z)

| File | Bytes | SHA256 |
|---|---|---|
| `out/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar` | 8286755 | `263f1ea768438829ee145892aa65f8f3ed7cc5d857438cf40a0bf753c00369fc` |
| `out/arm64-v8a/libonnxruntime.so` | 22224184 | `eb8e9924ed01828126df267623fe3f8c1aaf89d535e51c00ac805e47aac1010b` |
| `out/arm64-v8a/libonnxruntime4j_jni.so` | 116464 | `16bd8dc0b7462821bb069b9f52f50aa993d17347dc09a019aeda8dc360d15edc` |

`classes.jar` SHA256 `65e2e2d76d672253aaf0792a06c71cc40d09b0e2e251ccf925bee7175b91a1b0` (unchanged from official Maven 1.29.0 AAR). Cached Maven AAR SHA256 `e97540ca78fe36f6fe2013f82843414fb843b6c7681fb04644cba5e1406662dd`.

Local ELF check: both rebuilt `.so` files had `all_static_pass: true` for LOAD align/congruence and GNU_RELRO end alignment. That is **not** a whole-app 16KB claim and **not** a device result.

## JNI export comparison vs Maven AAR arm64

- `libonnxruntime4j_jni.so`: **173/173 identical** `Java_` / `JNI_On*` dynamic symbols.
- `libonnxruntime.so`: Maven baseline has **8** `Java_ai_onnxruntime_telemetry_HttpClient_*` exports; rebuilt has **0** because telemetry is OFF. Overall `jni_exports_equal` is false for that reason. `jni-compare` exit 2 records the difference; it does not mean the JNI API used by VAD/VITS is missing.

## Not claimed

- Equivalence to Maven `onnxruntime-android:1.29.0` (multi-ABI + CPU/NNAPI/XNNPACK/WebGPU).
- Product APK/AAB integration, Play 16KB, or 16KB device run.
- Runtime VAD/VITS accuracy or barge-in.
- Independent verification. CMakeCache `Java_*` paths may still point at Temurin 21 from an earlier configure; the successful Java/Gradle portion used JDK 17 via `JAVA_HOME` (`evidence/inputs.json`).
