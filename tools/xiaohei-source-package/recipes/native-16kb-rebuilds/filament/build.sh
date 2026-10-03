#!/usr/bin/env bash
# Relink Google Filament 1.69.2 Android arm64 JNI with max+common 16KB page size.
# Uses official tag source + official android-native static dist (no full engine compile).
# Does not patch ELF headers. Does not disable RELRO. Does not change product Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TAG="v1.69.2"
VERSION="1.69.2"
CACHE="${FILAMENT_CACHE:-/tmp/filament-16kb-rebuild}"
NDK_VERSION="27.1.12297006"
CMAKE_VERSION="3.22.1"
API="${API:-21}"
ABI="arm64-v8a"
JOBS="${FILAMENT_BUILD_JOBS:-4}"
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-z,relro -Wl,-z,now"
# Official Maven AARs. sha256 values match the Gradle module checksums for the
# sha1 directory names previously hardcoded in this recipe (filament-android
# 5a03cfbde9430e575e37c691d5ebbb48672087be, filament-utils
# 9c2512665441cda43edaa81c124c6f7e205168c7, gltfio
# 08ea4a9ae3648329b05cdb5c6766e26c416cebde).
AAR_FILAMENT_URL="https://repo1.maven.org/maven2/com/google/android/filament/filament-android/1.69.2/filament-android-1.69.2.aar"
AAR_UTILS_URL="https://repo1.maven.org/maven2/com/google/android/filament/filament-utils-android/1.69.2/filament-utils-android-1.69.2.aar"
AAR_GLTFIO_URL="https://repo1.maven.org/maven2/com/google/android/filament/gltfio-android/1.69.2/gltfio-android-1.69.2.aar"
AAR_FILAMENT_SHA256="3108fd8a943904f278911de5a0ecbfe64fd41f4ce53596ee7ed5f0dadf616e31"
AAR_UTILS_SHA256="1b3499cab6c612e5ef6f8fe1163d50ec25be33a57ce41f238edb38d02a36a137"
AAR_GLTFIO_SHA256="7c7a9c3cd1bd9550a9fc61dac4be713b3c62175d09bbc6359b4e253a4d0fce66"
SRC_TAR_SHA256="f9b0dae06f7c0ed557b8fa9e3bf11864e52f2e7ac9c86e2a018469c27b626af9"
NATIVE_TAR_SHA256="95869e8edec9b5cd3e09f0985ea871b49cdc259317a9eb8818c38981e9cb6f56"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

detect_host_prebuilt() {
  local sys mach
  sys="$(uname -s)"
  mach="$(uname -m)"
  case "${sys}-${mach}" in
    Darwin-x86_64|Darwin-arm64) echo darwin-x86_64 ;;
    Linux-x86_64) echo linux-x86_64 ;;
    *)
      fail "unsupported host ${sys}-${mach}. This recipe supports darwin-x86_64 and linux-x86_64 NDK prebuilts only."
      ;;
  esac
}

sha256_of() {
  python3 -c 'import hashlib,sys;h=hashlib.sha256()
f=open(sys.argv[1],"rb")
for c in iter(lambda: f.read(1024*1024), b""):
    h.update(c)
print(h.hexdigest())' "$1"
}

command -v python3 >/dev/null 2>&1 || fail "python3 is required on PATH"

SDK="${ANDROID_SDK_ROOT:-}"
if [[ -z "$SDK" ]]; then
  fail "ANDROID_SDK_ROOT is unset. Set it to an Android SDK directory containing ndk/${NDK_VERSION} and cmake/${CMAKE_VERSION}. This recipe does not guess a home SDK path."
fi
if [[ ! -d "$SDK" ]]; then
  fail "ANDROID_SDK_ROOT is not a directory: $SDK"
fi

NDK="${NDK:-$SDK/ndk/$NDK_VERSION}"
CMAKE="${CMAKE:-$SDK/cmake/${CMAKE_VERSION}/bin/cmake}"
NINJA="${NINJA:-$SDK/cmake/${CMAKE_VERSION}/bin/ninja}"
HOST_PREBUILT="$(detect_host_prebuilt)"
PRE="$NDK/toolchains/llvm/prebuilt/$HOST_PREBUILT"
READELF="$PRE/bin/llvm-readelf"
NM="$PRE/bin/llvm-nm"
STRIP="$PRE/bin/llvm-strip"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

DOWNLOADS="$CACHE/downloads"
SRC_PARENT="$CACHE/src"
SRC="$SRC_PARENT/filament-1.69.2"
NATIVE_PARENT="$CACHE/native"
BUILD_DIR="$CACHE/cmake-jni"
OUT="$ROOT/out"
EVIDENCE="$ROOT/evidence"
LOGS="$ROOT/logs"

SRC_URL="https://github.com/google/filament/archive/refs/tags/${TAG}.tar.gz"
NATIVE_URL="https://github.com/google/filament/releases/download/${TAG}/filament-v${VERSION}-android-native.tgz"
SRC_TAR="$DOWNLOADS/v1.69.2.tar.gz"
NATIVE_TAR="$DOWNLOADS/filament-v${VERSION}-android-native.tgz"
AAR_FILAMENT_DOWNLOAD="$DOWNLOADS/filament-android-1.69.2.aar"
AAR_UTILS_DOWNLOAD="$DOWNLOADS/filament-utils-android-1.69.2.aar"
AAR_GLTFIO_DOWNLOAD="$DOWNLOADS/gltfio-android-1.69.2.aar"

if [[ -n "${AAR_FILAMENT:-}" ]]; then
  [[ -f "$AAR_FILAMENT" ]] || fail "AAR_FILAMENT is set but not a file: $AAR_FILAMENT"
else
  AAR_FILAMENT="$AAR_FILAMENT_DOWNLOAD"
fi
if [[ -n "${AAR_UTILS:-}" ]]; then
  [[ -f "$AAR_UTILS" ]] || fail "AAR_UTILS is set but not a file: $AAR_UTILS"
else
  AAR_UTILS="$AAR_UTILS_DOWNLOAD"
fi
if [[ -n "${AAR_GLTFIO:-}" ]]; then
  [[ -f "$AAR_GLTFIO" ]] || fail "AAR_GLTFIO is set but not a file: $AAR_GLTFIO"
else
  AAR_GLTFIO="$AAR_GLTFIO_DOWNLOAD"
fi

[[ -x "$CMAKE" ]] || fail "cmake ${CMAKE_VERSION} missing at $CMAKE. Install SDK cmake/${CMAKE_VERSION} or set CMAKE to that executable."
[[ -f "$TOOLCHAIN" ]] || fail "NDK ${NDK_VERSION} missing toolchain at $NDK. Install ndk/${NDK_VERSION} under ANDROID_SDK_ROOT or set NDK to that directory."
[[ -x "$NINJA" ]] || fail "ninja missing at $NINJA. Install SDK cmake/${CMAKE_VERSION} (includes ninja) or set NINJA to that executable."
[[ -x "$READELF" ]] || fail "llvm-readelf missing at $READELF (host prebuilt $HOST_PREBUILT). NDK layout does not match this host."
[[ -x "$NM" ]] || fail "llvm-nm missing at $NM (host prebuilt $HOST_PREBUILT)."
[[ -x "$STRIP" ]] || fail "llvm-strip missing at $STRIP (host prebuilt $HOST_PREBUILT)."
[[ -f "$ROOT/wrap_aar.py" ]] || fail "wrap_aar.py missing next to build.sh"
[[ -f "$ROOT/check_elf.py" ]] || fail "check_elf.py missing next to build.sh"
[[ -f "$ROOT/compare_exports.py" ]] || fail "compare_exports.py missing next to build.sh"

verify_aar_if_present() {
  local path="$1" want="$2" label="$3"
  if [[ -f "$path" ]]; then
    local got
    got="$(sha256_of "$path")"
    if [[ "$got" != "$want" ]]; then
      fail "official $label AAR sha256 mismatch for $path: got $got want $want"
    fi
  fi
}

# Env-provided AARs are verified immediately. Downloaded AARs are verified after fetch.
if [[ -n "${AAR_FILAMENT:-}" && "$AAR_FILAMENT" != "$AAR_FILAMENT_DOWNLOAD" ]]; then
  verify_aar_if_present "$AAR_FILAMENT" "$AAR_FILAMENT_SHA256" "filament-android 1.69.2"
fi
if [[ -n "${AAR_UTILS:-}" && "$AAR_UTILS" != "$AAR_UTILS_DOWNLOAD" ]]; then
  verify_aar_if_present "$AAR_UTILS" "$AAR_UTILS_SHA256" "filament-utils-android 1.69.2"
fi
if [[ -n "${AAR_GLTFIO:-}" && "$AAR_GLTFIO" != "$AAR_GLTFIO_DOWNLOAD" ]]; then
  verify_aar_if_present "$AAR_GLTFIO" "$AAR_GLTFIO_SHA256" "gltfio-android 1.69.2"
fi

if [[ "${RECIPE_INPUT_CHECK_ONLY:-}" == "1" ]]; then
  python3 - "$SDK" "$NDK" "$CMAKE" "$NINJA" "$HOST_PREBUILT" "$PRE" "${AAR_FILAMENT}" "${AAR_UTILS}" "${AAR_GLTFIO}" "$NDK_VERSION" "$CMAKE_VERSION" <<'PY'
import json, sys
print(json.dumps({
    "sdk": sys.argv[1],
    "ndk": sys.argv[2],
    "cmake": sys.argv[3],
    "ninja": sys.argv[4],
    "host_prebuilt": sys.argv[5],
    "prebuilt_dir": sys.argv[6],
    "aar_filament": sys.argv[7],
    "aar_utils": sys.argv[8],
    "aar_gltfio": sys.argv[9],
    "ndk_version": sys.argv[10],
    "cmake_version": sys.argv[11],
    "uses_official_prebuilt_static_archives": True,
    "not_full_corresponding_source": True,
}, indent=2))
PY
  exit 0
fi

mkdir -p "$DOWNLOADS" "$SRC_PARENT" "$NATIVE_PARENT" "$BUILD_DIR" "$OUT/arm64-v8a" "$EVIDENCE" "$LOGS"

fetch() {
  local url="$1" dest="$2"
  if [[ -f "$dest" ]]; then
    echo "using cached $(basename "$dest")"
    return 0
  fi
  echo "download $url"
  curl -L --fail --retry 3 --connect-timeout 20 -o "$dest.partial" "$url"
  mv "$dest.partial" "$dest"
}

require_aar() {
  local path="$1" url="$2" sha="$3" label="$4"
  if [[ ! -f "$path" ]]; then
    fetch "$url" "$path"
  fi
  [[ -f "$path" ]] || fail "official $label AAR missing: $path (set AAR env or allow download from $url)"
  local got
  got="$(sha256_of "$path")"
  if [[ "$got" != "$sha" ]]; then
    fail "official $label AAR sha256 mismatch for $path: got $got want $sha"
  fi
}

require_aar "$AAR_FILAMENT" "$AAR_FILAMENT_URL" "$AAR_FILAMENT_SHA256" "filament-android 1.69.2"
require_aar "$AAR_UTILS" "$AAR_UTILS_URL" "$AAR_UTILS_SHA256" "filament-utils-android 1.69.2"
require_aar "$AAR_GLTFIO" "$AAR_GLTFIO_URL" "$AAR_GLTFIO_SHA256" "gltfio-android 1.69.2"

fetch "$NATIVE_URL" "$NATIVE_TAR"
native_got="$(sha256_of "$NATIVE_TAR")"
if [[ "$native_got" != "$NATIVE_TAR_SHA256" ]]; then
  fail "filament android-native tarball sha256 mismatch: got $native_got want $NATIVE_TAR_SHA256"
fi

if [[ ! -f "$SRC/android/filament-utils-android/CMakeLists.txt" ]]; then
  GIT_SRC="$SRC_PARENT/filament-git"
  if [[ -f "$GIT_SRC/android/filament-utils-android/CMakeLists.txt" ]]; then
    echo "using sparse git checkout $GIT_SRC"
    ln -sfn filament-git "$SRC"
  else
    fetch "$SRC_URL" "$SRC_TAR"
    src_got="$(sha256_of "$SRC_TAR")"
    if [[ "$src_got" != "$SRC_TAR_SHA256" ]]; then
      fail "filament source tarball sha256 mismatch: got $src_got want $SRC_TAR_SHA256"
    fi
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
  "host_prebuilt": "$HOST_PREBUILT",
  "official_android_ndk_pin": "29.0.14206865",
  "ndk_note": "JNI relink used local NDK ${NDK_VERSION}; official gradle pin is 29.0.14206865. Static libs come from official android-native tarball. Path fixes do not make this full corresponding source.",
  "cmake": "$CMAKE",
  "ninja": "$NINJA",
  "uses_official_prebuilt_static_archives": True,
  "not_full_corresponding_source": True,
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
