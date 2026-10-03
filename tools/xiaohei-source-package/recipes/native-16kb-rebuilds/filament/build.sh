#!/usr/bin/env bash
# Relink Google Filament 1.69.2 Android arm64 JNI with max+common 16KB page size.
# Uses official tag source + official android-native static dist (no full engine compile).
# Does not patch ELF headers. Does not disable RELRO. Does not change product Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TAG="v1.69.2"
VERSION="1.69.2"
CACHE="${FILAMENT_CACHE:-/tmp/filament-16kb-rebuild}"
SDK="${ANDROID_SDK_ROOT:-/Users/lazy/Library/Android/sdk}"
NDK="${NDK:-$SDK/ndk/27.1.12297006}"
CMAKE="${CMAKE:-$SDK/cmake/3.22.1/bin/cmake}"
NINJA="${NINJA:-$(command -v ninja)}"
API="${API:-21}"
ABI="arm64-v8a"
JOBS="${FILAMENT_BUILD_JOBS:-4}"
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-z,relro -Wl,-z,now"

DOWNLOADS="$CACHE/downloads"
SRC_PARENT="$CACHE/src"
SRC="$SRC_PARENT/filament-1.69.2"
NATIVE_PARENT="$CACHE/native"
BUILD_DIR="$CACHE/cmake-jni"
OUT="$ROOT/out"
EVIDENCE="$ROOT/evidence"
LOGS="$ROOT/logs"
READELF="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
NM="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-nm"
STRIP="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

SRC_URL="https://github.com/google/filament/archive/refs/tags/${TAG}.tar.gz"
NATIVE_URL="https://github.com/google/filament/releases/download/${TAG}/filament-v${VERSION}-android-native.tgz"
SRC_TAR="$DOWNLOADS/v1.69.2.tar.gz"
NATIVE_TAR="$DOWNLOADS/filament-v${VERSION}-android-native.tgz"

AAR_FILAMENT="${AAR_FILAMENT:-/Users/lazy/Code/crack/1plus/integrations/operit-upstream-build/gradle-home/caches/modules-2/files-2.1/com.google.android.filament/filament-android/1.69.2/5a03cfbde9430e575e37c691d5ebbb48672087be/filament-android-1.69.2.aar}"
AAR_UTILS="${AAR_UTILS:-/Users/lazy/Code/crack/1plus/integrations/operit-upstream-build/gradle-home/caches/modules-2/files-2.1/com.google.android.filament/filament-utils-android/1.69.2/9c2512665441cda43edaa81c124c6f7e205168c7/filament-utils-android-1.69.2.aar}"
AAR_GLTFIO="${AAR_GLTFIO:-/Users/lazy/Code/crack/1plus/integrations/operit-upstream-build/gradle-home/caches/modules-2/files-2.1/com.google.android.filament/gltfio-android/1.69.2/8ea4a9ae3648329b05cdb5c6766e26c416cebde/gltfio-android-1.69.2.aar}"

mkdir -p "$DOWNLOADS" "$SRC_PARENT" "$NATIVE_PARENT" "$BUILD_DIR" "$OUT/arm64-v8a" "$EVIDENCE" "$LOGS"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

fetch() {
  local url="$1" dest="$2"
  if [[ -f "$dest" ]] && gzip -t "$dest" 2>/dev/null; then
    echo "using cached $(basename "$dest")"
    return 0
  fi
  echo "download $url"
  if ! curl -L --fail --retry 3 --connect-timeout 20 -o "$dest.partial" "$url"; then
    echo "direct failed, retry with proxy"
    export https_proxy=http://127.0.0.1:7891 http_proxy=http://127.0.0.1:7891 all_proxy=socks5://127.0.0.1:7891
    curl -L --fail --retry 3 --connect-timeout 20 -o "$dest.partial" "$url"
    unset https_proxy http_proxy all_proxy
  fi
  gzip -t "$dest.partial" || fail "not gzip: $dest.partial"
  mv "$dest.partial" "$dest"
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

for req in "$CMAKE" "$NINJA" "$READELF" "$NM" "$STRIP" "$TOOLCHAIN" "$AAR_FILAMENT" "$AAR_UTILS" "$AAR_GLTFIO"; do
  [[ -e "$req" ]] || fail "missing: $req"
done

fetch "$NATIVE_URL" "$NATIVE_TAR"

if [[ ! -f "$SRC/android/filament-utils-android/CMakeLists.txt" ]]; then
  GIT_SRC="$SRC_PARENT/filament-git"
  if [[ -f "$GIT_SRC/android/filament-utils-android/CMakeLists.txt" ]]; then
    echo "using sparse git checkout $GIT_SRC"
    ln -sfn filament-git "$SRC"
  else
    fetch "$SRC_URL" "$SRC_TAR"
    echo "extract source"
    tar -xzf "$SRC_TAR" -C "$SRC_PARENT"
  fi
fi
[[ -f "$SRC/android/filament-utils-android/CMakeLists.txt" ]] || fail "source extract missing JNI CMake"
[[ -f "$SRC/android/gradle.properties" ]] || fail "missing android/gradle.properties"
grep -q '^VERSION_NAME=1.69.2$' "$SRC/android/gradle.properties" || fail "VERSION_NAME is not 1.69.2"

if [[ ! -d "$NATIVE_PARENT/.extracted" ]]; then
  echo "extract android-native dist"
  tar -xzf "$NATIVE_TAR" -C "$NATIVE_PARENT"
  mkdir -p "$NATIVE_PARENT/.extracted"
fi

DIST=""
if [[ -d "$NATIVE_PARENT/filament/lib/$ABI" ]]; then
  DIST="$NATIVE_PARENT/filament"
elif [[ -d "$NATIVE_PARENT/lib/$ABI" ]]; then
  DIST="$NATIVE_PARENT"
else
  echo "native layout:" >&2
  find "$NATIVE_PARENT" -maxdepth 4 -type d | head -80 >&2
  fail "could not find lib/$ABI in android-native tarball"
fi
[[ -f "$DIST/lib/$ABI/libfilament.a" ]] || fail "missing libfilament.a in $DIST"
[[ -f "$DIST/include/gltfio/materials/uberarchive.h" ]] || echo "WARN: missing uberarchive.h (gltfio JNI may fail)"

# Keep a copy of the Apache license next to the recipe (source tree is in /tmp).
if [[ -f "$SRC/LICENSE" && ! -f "$ROOT/LICENSE-filament.txt" ]]; then
  cp "$SRC/LICENSE" "$ROOT/LICENSE-filament.txt"
fi

CXX_FLAGS="-fno-stack-protector -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-rtti -ffast-math -fno-finite-math-only -ffp-contract=fast -fvisibility-inlines-hidden -fvisibility=hidden -fomit-frame-pointer -ffunction-sections -fdata-sections -no-canonical-prefixes -Wformat -Werror=format-security"
LD_FLAGS="$PAGE_LDFLAGS -Wl,--gc-sections -Wl,-Bsymbolic-functions -Wl,--hash-style=both"

echo "configure JNI cmake"
"$CMAKE" -S "$SRC/android/filament-utils-android" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-${API}" \
  -DANDROID_STL=c++_static \
  -DANDROID_PIE=ON \
  -DANDROID_WEAK_API_DEFS=ON \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_STANDARD=20 \
  -DCMAKE_CXX_FLAGS="$CXX_FLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$LD_FLAGS" \
  -DCMAKE_MODULE_LINKER_FLAGS="$LD_FLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$LD_FLAGS" \
  -DFILAMENT_DIST_DIR="$DIST" \
  -DFILAMENT_SUPPORTS_VULKAN=ON \
  -DFILAMENT_SUPPORTS_WEBGPU=OFF \
  -DFILAMENT_ENABLE_FGVIEWER=OFF \
  -DFILAMENT_ENABLE_MATDBG=OFF \
  -DFILAMENT_DISABLE_MATOPT=OFF \
  -DFILAMENT_SUPPORTS_WEBP_TEXTURES=OFF \
  > "$LOGS/cmake-configure.log" 2>&1 || {
    tail -80 "$LOGS/cmake-configure.log" >&2
    fail "cmake configure failed; see $LOGS/cmake-configure.log"
  }

echo "ninja filament-jni filament-utils-jni gltfio-jni"
"$CMAKE" --build "$BUILD_DIR" --target filament-jni filament-utils-jni gltfio-jni -j "$JOBS" \
  > "$LOGS/ninja-jni.log" 2>&1 || {
    tail -120 "$LOGS/ninja-jni.log" >&2
    fail "ninja failed; see $LOGS/ninja-jni.log"
  }

find_so() {
  local name="$1"
  local found
  found="$(find "$BUILD_DIR" -type f -name "$name" | head -1)"
  [[ -n "$found" ]] || fail "built $name not found under $BUILD_DIR"
  echo "$found"
}

SO_FILAMENT="$(find_so libfilament-jni.so)"
SO_UTILS="$(find_so libfilament-utils-jni.so)"
SO_GLTFIO="$(find_so libgltfio-jni.so)"

"$STRIP" --strip-unneeded "$SO_FILAMENT" "$SO_UTILS" "$SO_GLTFIO"
cp "$SO_FILAMENT" "$OUT/arm64-v8a/libfilament-jni.so"
cp "$SO_UTILS" "$OUT/arm64-v8a/libfilament-utils-jni.so"
cp "$SO_GLTFIO" "$OUT/arm64-v8a/libgltfio-jni.so"

# Baseline originals from Maven AARs (extract if needed).
ORIG="$CACHE/original-aar"
python3 - <<PY
import zipfile
from pathlib import Path
pairs = [
    ("$AAR_FILAMENT", "$ORIG/filament-android"),
    ("$AAR_UTILS", "$ORIG/filament-utils-android"),
    ("$AAR_GLTFIO", "$ORIG/gltfio-android"),
]
for aar, dest in pairs:
    d = Path(dest)
    so = d / "jni/arm64-v8a"
    if any(so.glob("*.so")):
        continue
    d.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(aar) as z:
        z.extractall(d)
PY

for name in libfilament-jni.so libfilament-utils-jni.so libgltfio-jni.so; do
  "$READELF" -lW "$OUT/arm64-v8a/$name" > "$EVIDENCE/${name}.readelf-lW.txt"
  "$READELF" --dyn-syms "$OUT/arm64-v8a/$name" > "$EVIDENCE/${name}.dynsyms.txt"
done

python3 "$ROOT/check_elf.py" --so "$OUT/arm64-v8a/libfilament-jni.so" --expect-soname libfilament-jni.so --out "$EVIDENCE/elf-libfilament-jni.json" || true
python3 "$ROOT/check_elf.py" --so "$OUT/arm64-v8a/libfilament-utils-jni.so" --expect-soname libfilament-utils-jni.so --out "$EVIDENCE/elf-libfilament-utils-jni.json" || true
python3 "$ROOT/check_elf.py" --so "$OUT/arm64-v8a/libgltfio-jni.so" --expect-soname libgltfio-jni.so --out "$EVIDENCE/elf-libgltfio-jni.json" || true

python3 "$ROOT/compare_exports.py" \
  --nm "$NM" \
  --baseline-so "$ORIG/filament-android/jni/arm64-v8a/libfilament-jni.so" \
  --rebuilt-so "$OUT/arm64-v8a/libfilament-jni.so" \
  --baseline-so "$ORIG/filament-utils-android/jni/arm64-v8a/libfilament-utils-jni.so" \
  --rebuilt-so "$OUT/arm64-v8a/libfilament-utils-jni.so" \
  --baseline-so "$ORIG/gltfio-android/jni/arm64-v8a/libgltfio-jni.so" \
  --rebuilt-so "$OUT/arm64-v8a/libgltfio-jni.so" \
  --out-json "$EVIDENCE/export-compare.json" || true

python3 "$ROOT/wrap_aar.py" \
  --src-aar "$AAR_FILAMENT" \
  --replace-so "$OUT/arm64-v8a/libfilament-jni.so" \
  --aar-member jni/arm64-v8a/libfilament-jni.so \
  --out-aar "$OUT/filament-android-1.69.2-arm64-relro.aar" \
  --out-json "$EVIDENCE/wrap-filament-android.json"

python3 "$ROOT/wrap_aar.py" \
  --src-aar "$AAR_UTILS" \
  --replace-so "$OUT/arm64-v8a/libfilament-utils-jni.so" \
  --aar-member jni/arm64-v8a/libfilament-utils-jni.so \
  --out-aar "$OUT/filament-utils-android-1.69.2-arm64-relro.aar" \
  --out-json "$EVIDENCE/wrap-filament-utils-android.json"

GIT_HEAD=""
if git -C "$SRC" rev-parse HEAD >/dev/null 2>&1; then
  GIT_HEAD="$(git -C "$SRC" rev-parse HEAD)"
fi

python3 - <<PY
import hashlib, json, os, time
from pathlib import Path

def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(1024 * 1024), b""):
            h.update(c)
    return h.hexdigest()

root = Path("$ROOT")
out = root / "out" / "arm64-v8a"
ev = root / "evidence"
payload = {
  "status": "built",
  "independent_verification": False,
  "tag": "$TAG",
  "version": "$VERSION",
  "version_name_file": "android/gradle.properties VERSION_NAME=1.69.2",
  "source_url": "$SRC_URL",
  "native_dist_url": "$NATIVE_URL",
  "source_path": "$SRC",
  "git_head": "$GIT_HEAD" or None,
  "source_tar_sha256": sha("$SRC_TAR") if os.path.isfile("$SRC_TAR") else None,
  "native_tar_sha256": sha("$NATIVE_TAR"),
  "ndk": "$NDK",
  "official_android_ndk_pin": "29.0.14206865",
  "ndk_note": "JNI relink used local NDK 27.1; official gradle pin is 29.0.14206865. Static libs come from official android-native tarball.",
  "cmake": "$CMAKE",
  "ninja": "$NINJA",
  "api": "$API",
  "abi": "$ABI",
  "stl": "c++_static",
  "vulkan": "ON",
  "webgpu": "OFF",
  "page_ldflags": "$PAGE_LDFLAGS".split(),
  "filament_dist_dir": "$DIST",
  "license": "Apache-2.0",
  "product_gradle": False,
  "device_test": False,
  "runtime_gltf_tested": False,
  "artifacts": {
    "libfilament-jni.so": {"path": str(out / "libfilament-jni.so"), "sha256": sha(out / "libfilament-jni.so"), "bytes": (out / "libfilament-jni.so").stat().st_size},
    "libfilament-utils-jni.so": {"path": str(out / "libfilament-utils-jni.so"), "sha256": sha(out / "libfilament-utils-jni.so"), "bytes": (out / "libfilament-utils-jni.so").stat().st_size},
    "libgltfio-jni.so": {"path": str(out / "libgltfio-jni.so"), "sha256": sha(out / "libgltfio-jni.so"), "bytes": (out / "libgltfio-jni.so").stat().st_size},
  },
  "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}
(ev / "inputs.json").write_text(json.dumps(payload, indent=2) + "\n")
print(json.dumps(payload, indent=2))
PY

echo "done. evidence in $EVIDENCE"
