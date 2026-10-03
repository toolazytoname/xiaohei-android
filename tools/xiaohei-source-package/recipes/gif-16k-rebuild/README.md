# android-gif-drawable 1.2.28 arm64 16KB rebuild

Rebuilds **only** `libpl_droidsonroids_gif.so` from official `v1.2.28` C sources. Does not change Operit/common-base, does not run Gradle, does not install into an APK, and does not claim whole-app 16KB compatibility.

## Provenance

| Field | Value |
|---|---|
| Coordinate | `pl.droidsonroids.gif:android-gif-drawable:1.2.28` |
| Tag | `v1.2.28` |
| Commit | `9080ef9dd83a69d024e692f7fdf6647a99506bb2` |
| Tag API | https://api.github.com/repos/koral--/android-gif-drawable/git/refs/tags/v1.2.28 |
| Archive | https://github.com/koral--/android-gif-drawable/archive/refs/tags/v1.2.28.tar.gz |
| Archive SHA256 | `a2d055d908928d65243d02b12d40d755ef836af1e1c9b4ce0cdf678eaafcb7ce` |
| License | MIT (+ SKIA BSD-3, GIFLIB, ReLinker Apache-2.0, memset.arm.S Apache-2.0 in `LICENSE`) |

Version is **not** upgraded. 1.2.29 Maven prebuilt already had 16KB LOAD but RELRO end `mod 16384 = 8192`; this rebuild stays on 1.2.28 Java/JNI.

## Rebuild

```bash
./build.sh
```

Uses unmodified upstream `android-gif-drawable/src/main/c/CMakeLists.txt` (`file(GLOB_RECURSE *.c)`). Extra flags only:

- NDK `27.0.12077973`, CMake `3.22.1`, Ninja, ABI `arm64-v8a`, `ANDROID_PLATFORM=android-26`, `ANDROID_STL=none`
- `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`
- `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`
- Release C flags from upstream Gradle: `-std=c11 -Weverything -Wall -DNDEBUG -Os -g0 -fvisibility=hidden`

Outputs:

- `out/arm64-v8a/libpl_droidsonroids_gif.so`
- `result.json` (source hashes, toolchain, commands, every PT_LOAD / GNU_RELRO VirtAddr+MemSize+end_mod_16384)
- `LICENSE`

## Static check (not device, not APK)

Success rule: every `PT_LOAD` `p_align >= 16384` **and** every `GNU_RELRO` `(VirtAddr + MemSize) % 16384 == 0`.

This is ELF program-header evidence only. Do not treat it as zip-alignment, 16KB page-size device boot, or Play 16KB status.
