# graphics-path 1.0.1 arm64 16KB RELRO rebuild

Rebuilds **only** `jni/arm64-v8a/libandroidx.graphics.path.so` from AndroidX `graphics/graphics-path` at the 1.0.1 release commit, then wraps the official Maven 1.0.1 AAR. Does not change Operit/common-base Gradle, does not install into an APK, and does not claim whole-app 16KB compatibility.

## Provenance

| Field | Value |
|---|---|
| Coordinate | `androidx.graphics:graphics-path:1.0.1` |
| Release notes | https://developer.android.com/jetpack/androidx/releases/graphics#graphics-path-1.0.1 (2024-05-01) |
| Git tag | **none** on `platform/frameworks/support` (`git ls-remote --tags`, 1753 tags, no `graphics-path`) |
| Commit | `8a05a22af450d589ef911d772a001a49dcb05b71` (end of official “these commits” range) |
| Previous | `4fcd99eacd92d7c73fb1d3580fd423ed7704a98a` (1.0.0 tip) |
| Commit URL | https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71 |
| Archive | https://android.googlesource.com/platform/frameworks/support/+archive/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path.tar.gz |
| Archive SHA256 | `e220621aacabaef495fc02a61f90566ac7e324ed2f8e806c7204d0a7376d1a78` |
| Maven AAR SHA256 | `8ca4032b6d79b351f0b59ad4b580eddbb9423e1652f7c958830687f1eee2ec03` |
| Sources JAR SHA256 | `9f1b5995b9577a8876525c3411ebb2a49f9ef0e875f6aec3059e807596dc6ca6` |
| License | Apache-2.0 |

Four Kotlin files in that git tree match the official `graphics-path-1.0.1-sources.jar` byte-for-byte. Native C++ is not in the sources JAR; it is taken from the same commit directory.

Cached Maven 1.1.0 still has GNU_RELRO `end_mod_16kb=8192`. This rebuild stays on 1.0.1 Java/JNI.

## Why RELRO failed

Upstream `CMakeLists.txt` already passes `-Wl,-z,max-page-size=16384`, so PT_LOAD is 16KB. Official RELRO end alignment needs `-Wl,-z,common-page-size=16384` as well. Production 1.0.1 arm64: RELRO end `0x6000`, `0x6000 % 16384 = 8192`.

## Rebuild

```bash
cd integrations/operit-upstream-build/native-16kb-rebuilds/graphics-path
./build.sh
```

Uses unmodified upstream `src/main/cpp/CMakeLists.txt`. Extra flags only:

- NDK `27.1.12297006`, CMake `3.22.1`, Ninja, ABI `arm64-v8a`, `ANDROID_PLATFORM=android-21` (upstream minSdk), `ANDROID_STL=none`
- libc++ **headers** via `-isystem` (upstream Gradle uses `-nostdlib++`; `STL=none` alone drops `<cstdlib>`)
- `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`
- `-Wl,-z,common-page-size=16384` (in addition to upstream max-page-size)
- Release C++ flags from upstream `build.gradle` `cppFlags` (split compiler vs linker)

Outputs:

- `out/arm64-v8a/libandroidx.graphics.path.so`
- `out/graphics-path-1.0.1-16kb-arm64.aar` (official AAR with only arm64 `.so` replaced; classes/manifest/other ABIs unchanged)
- `result.json`

## Static check (not device, not APK)

Success rule: every `PT_LOAD` `p_align >= 16384` **and** `p_offset % 16384 == p_vaddr % 16384`; every `GNU_RELRO` `(VirtAddr + MemSize) % 16384 == 0`; defined dynsym only `JNI_OnLoad@@LIBANDROIDX.GRAPHICS.PATH`; `NEEDED` `libc.so`/`libdl.so`/`libm.so`.

This is ELF program-header evidence only. Other ABIs in the wrapped AAR are still the Maven originals. Do not treat as zip-alignment, 16KB device boot, Play status, or Gradle integration.

## 2026-10-02 integration review

主代理复核 `build.sh`、AAR wrapper、`check_elf.py` 与 JNI/undefined dynsym 证据：通过。输出 AAR `out/graphics-path-1.0.1-16kb-arm64.aar` SHA256 `551a1ce3e12b0212e8d1f17e6bb6d6d5611c8fdd82aa9cf7c6451245b872b6ca`；重链 arm64 `.so` SHA256 `ce5bafbe6bbbf49b561a8a8199528dd2270752da9de226aa03e182b74d23188c`。

共同版通过全局排除 Maven `androidx.graphics:graphics-path` 并接入 `app/libs/graphics-path-1.0.1-16kb-arm64.aar`；APK 中的 stripped `.so` 仍通过 16KB LOAD/RELRO 检查。非 arm64 成员保持官方 AAR，但共同版/商店版当前只打包 arm64。无设备运行结论。
