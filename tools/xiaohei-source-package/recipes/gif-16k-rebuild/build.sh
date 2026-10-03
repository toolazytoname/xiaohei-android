#!/usr/bin/env bash
# Rebuild pl.droidsonroids.gif 1.2.28 arm64-v8a native lib with 16KB LOAD/RELRO flags.
# Does not run Gradle, does not modify product source, does not install to a device.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

TAG="v1.2.28"
COMMIT="9080ef9dd83a69d024e692f7fdf6647a99506bb2"
ARCHIVE_URL="https://github.com/koral--/android-gif-drawable/archive/refs/tags/v1.2.28.tar.gz"
ARCHIVE_SHA256="a2d055d908928d65243d02b12d40d755ef836af1e1c9b4ce0cdf678eaafcb7ce"
TAG_REF_URL="https://api.github.com/repos/koral--/android-gif-drawable/git/refs/tags/v1.2.28"

NDK="${NDK:-/Users/lazy/Library/Android/sdk/ndk/27.0.12077973}"
CMAKE="${CMAKE:-/Users/lazy/Library/Android/sdk/cmake/3.22.1/bin/cmake}"
NINJA="${NINJA:-/Users/lazy/Library/Android/sdk/cmake/3.22.1/bin/ninja}"
ABI="arm64-v8a"
API="26"
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

DOWNLOADS="$ROOT/downloads"
SRC_PARENT="$ROOT/source"
SRC_ROOT="$SRC_PARENT/android-gif-drawable-1.2.28"
CMAKE_LIST="$SRC_ROOT/android-gif-drawable/src/main/c/CMakeLists.txt"
BUILD_DIR="$ROOT/build/arm64-v8a"
OUT_DIR="$ROOT/out/arm64-v8a"
SO_NAME="libpl_droidsonroids_gif.so"
ARCHIVE="$DOWNLOADS/android-gif-drawable-1.2.28.tar.gz"
READELF="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
CLANG="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang"

mkdir -p "$DOWNLOADS" "$SRC_PARENT" "$BUILD_DIR" "$OUT_DIR"

if [[ ! -x "$CMAKE" ]]; then
  echo "missing cmake: $CMAKE" >&2
  exit 1
fi
if [[ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]]; then
  echo "missing NDK toolchain: $NDK" >&2
  exit 1
fi
if [[ ! -x "$NINJA" ]]; then
  echo "missing ninja: $NINJA" >&2
  exit 1
fi
if [[ ! -x "$READELF" ]]; then
  echo "missing llvm-readelf: $READELF" >&2
  exit 1
fi

if [[ ! -f "$ARCHIVE" ]]; then
  echo "downloading $ARCHIVE_URL"
  curl -fL --max-filesize 31457280 -o "$ARCHIVE" "$ARCHIVE_URL"
fi

got="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
if [[ "$got" != "$ARCHIVE_SHA256" ]]; then
  echo "archive sha256 mismatch: got $got want $ARCHIVE_SHA256" >&2
  exit 1
fi

if [[ ! -f "$CMAKE_LIST" ]]; then
  rm -rf "$SRC_ROOT"
  tar -xzf "$ARCHIVE" -C "$SRC_PARENT"
fi
if [[ ! -f "$CMAKE_LIST" ]]; then
  echo "upstream CMakeLists.txt missing after extract: $CMAKE_LIST" >&2
  exit 1
fi

cp "$SRC_ROOT/LICENSE" "$ROOT/LICENSE"

# Upstream android-gif-drawable/build.gradle release native flags, plus required 16KB ldflags.
# CMakeLists.txt is used unmodified (file(GLOB_RECURSE *.c)).
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

"$CMAKE" -S "$SRC_ROOT/android-gif-drawable/src/main/c" -B "$BUILD_DIR" \
  -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$NINJA" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-$API" \
  -DANDROID_STL=none \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS="-std=c11 -Weverything -Wall -DNDEBUG -Os -g0 -fvisibility=hidden" \
  -DCMAKE_SHARED_LINKER_FLAGS="$PAGE_LDFLAGS"

"$CMAKE" --build "$BUILD_DIR"

if [[ ! -f "$BUILD_DIR/$SO_NAME" ]]; then
  echo "expected $BUILD_DIR/$SO_NAME" >&2
  exit 1
fi
cp "$BUILD_DIR/$SO_NAME" "$OUT_DIR/$SO_NAME"

NM="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-nm"
"$READELF" -lW "$OUT_DIR/$SO_NAME" > "$OUT_DIR/libpl_droidsonroids_gif.readelf-lW.txt"
"$NM" -D --defined-only "$OUT_DIR/$SO_NAME" | awk '/Java_|JNI_On/ {print $3}' | sort > "$ROOT/build/rebuilt-jni.txt"

AAR_CANDIDATES=(
  /Users/lazy/Code/crack/1plus/integrations/operit-upstream-build/gradle-home/caches/modules-2/files-2.1/pl.droidsonroids.gif/android-gif-drawable/1.2.28/7292ee966412dcadfee75b50813029eea3bb7b15/android-gif-drawable-1.2.28.aar
)
CMPDIR="$ROOT/build/compare-1.2.28-aar"
mkdir -p "$CMPDIR"
cp "$ROOT/build/rebuilt-jni.txt" "$CMPDIR/rebuilt-jni.txt"
for aar in "${AAR_CANDIDATES[@]}"; do
  if [[ -f "$aar" ]]; then
    unzip -qo -j "$aar" "jni/arm64-v8a/libpl_droidsonroids_gif.so" -d "$CMPDIR"
    "$NM" -D --defined-only "$CMPDIR/libpl_droidsonroids_gif.so" | awk '/Java_|JNI_On/ {print $3}' | sort > "$CMPDIR/aar-jni.txt"
    break
  fi
done

python3 - "$BUILD_DIR/build.ninja" "$ROOT/build/link-flags.txt" <<'PY'
import re, sys
ninja, out = sys.argv[1], sys.argv[2]
text = open(ninja, encoding="utf-8").read()
m = re.search(r"^  LINK_FLAGS = (.+)$", text, re.M)
if not m:
    raise SystemExit("LINK_FLAGS not found in build.ninja")
open(out, "w", encoding="utf-8").write(m.group(1).strip() + "\n")
print(m.group(1).strip())
PY
"$NINJA" -C "$BUILD_DIR" -t commands "$SO_NAME" > "$ROOT/build/ninja-commands.txt"
tail -n 1 "$ROOT/build/ninja-commands.txt" > "$ROOT/build/link-command.txt"

{
  echo "tag=$TAG"
  echo "commit=$COMMIT"
  echo "archive_url=$ARCHIVE_URL"
  echo "archive_sha256=$ARCHIVE_SHA256"
  echo "tag_ref_url=$TAG_REF_URL"
  echo "cmake=$CMAKE"
  echo "cmake_version=$("$CMAKE" --version | head -n 1)"
  echo "ninja=$NINJA"
  echo "ndk=$NDK"
  echo "clang=$CLANG"
  echo "clang_version=$("$CLANG" --version | head -n 1)"
  echo "abi=$ABI"
  echo "android_platform=android-$API"
  echo "android_stl=none"
  echo "android_support_flexible_page_sizes=ON"
  echo "page_ldflags=$PAGE_LDFLAGS"
  echo "cmake_lists=$CMAKE_LIST"
  echo "so=$OUT_DIR/$SO_NAME"
  echo "link_flags=$(cat "$ROOT/build/link-flags.txt")"
} > "$ROOT/build/toolchain.txt"

META="$ROOT/build/meta.json"
python3 - "$META" "$ROOT" <<'PY'
import json, os, subprocess, sys
meta_path, root = sys.argv[1], sys.argv[2]
ndk = os.environ.get("NDK", "/Users/lazy/Library/Android/sdk/ndk/27.0.12077973")
cmake = os.environ.get("CMAKE", "/Users/lazy/Library/Android/sdk/cmake/3.22.1/bin/cmake")
clang = os.path.join(ndk, "toolchains/llvm/prebuilt/darwin-x86_64/bin/clang")
cmake_ver = subprocess.check_output([cmake, "--version"], text=True).splitlines()[0]
clang_ver = subprocess.check_output([clang, "--version"], text=True).splitlines()[0]
ninja = "/Users/lazy/Library/Android/sdk/cmake/3.22.1/bin/ninja"
link_txt = os.path.join(root, "build/link-flags.txt")
link_flags = open(link_txt, encoding="utf-8").read().strip() if os.path.exists(link_txt) else ""
link_full = os.path.join(root, "build/link-command.txt")
link_cmd = open(link_full, encoding="utf-8").read().strip() if os.path.exists(link_full) else link_flags
aar_jni = os.path.join(root, "build/compare-1.2.28-aar/aar-jni.txt")
reb_jni = os.path.join(root, "build/compare-1.2.28-aar/rebuilt-jni.txt")
jni_match = None
jni_count = None
if os.path.exists(aar_jni) and os.path.exists(reb_jni):
    aar_lines = open(aar_jni, encoding="utf-8").read().splitlines()
    reb_lines = open(reb_jni, encoding="utf-8").read().splitlines()
    jni_match = aar_lines == reb_lines
    jni_count = len(reb_lines)
meta = {
  "library": "pl.droidsonroids.gif:android-gif-drawable:1.2.28",
  "version": "1.2.28",
  "tag": "v1.2.28",
  "commit": "9080ef9dd83a69d024e692f7fdf6647a99506bb2",
  "commit_url": "https://github.com/koral--/android-gif-drawable/commit/9080ef9dd83a69d024e692f7fdf6647a99506bb2",
  "tag_ref_url": "https://api.github.com/repos/koral--/android-gif-drawable/git/refs/tags/v1.2.28",
  "source_url": "https://github.com/koral--/android-gif-drawable/archive/refs/tags/v1.2.28.tar.gz",
  "source_sha256": "a2d055d908928d65243d02b12d40d755ef836af1e1c9b4ce0cdf678eaafcb7ce",
  "license": "MIT (upstream LICENSE also records SKIA BSD-3, GIFLIB, ReLinker Apache-2.0, memset.arm.S Apache-2.0)",
  "license_file": "LICENSE",
  "cmake_recipe": "android-gif-drawable/src/main/c/CMakeLists.txt (unmodified)",
  "abi": "arm64-v8a",
  "min_api": 26,
  "toolchain": {
    "ndk": ndk,
    "ndk_version": "27.0.12077973",
    "cmake": cmake,
    "cmake_version": cmake_ver,
    "ninja": ninja,
    "clang": clang,
    "clang_version": clang_ver,
    "host_prebuilt": "darwin-x86_64",
  },
  "cmake_args": [
    f"-DCMAKE_TOOLCHAIN_FILE={ndk}/build/cmake/android.toolchain.cmake",
    "-DANDROID_ABI=arm64-v8a",
    "-DANDROID_PLATFORM=android-26",
    "-DANDROID_STL=none",
    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DCMAKE_C_FLAGS=-std=c11 -Weverything -Wall -DNDEBUG -Os -g0 -fvisibility=hidden",
    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384",
  ],
  "commands": [
    "./build.sh",
  ],
  "link_flags": link_flags,
  "link_command": link_cmd,
  "java_api": "JNI symbols from official 1.2.28 C sources; Java not rebuilt",
  "jni_defined_symbols_match_1_2_28_aar": jni_match,
  "jni_count": jni_count,
  "version_upgrade": False,
}
with open(meta_path, "w", encoding="utf-8") as f:
    json.dump(meta, f, indent=2)
    f.write("\n")
PY

python3 "$ROOT/check_elf.py" \
  --so "$OUT_DIR/$SO_NAME" \
  --readelf "$READELF" \
  --out-json "$ROOT/result.json" \
  --meta-json "$META"

echo "wrote $OUT_DIR/$SO_NAME"
echo "wrote $ROOT/result.json"
