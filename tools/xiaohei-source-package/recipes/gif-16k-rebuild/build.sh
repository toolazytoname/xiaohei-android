#!/usr/bin/env bash
# Rebuild pl.droidsonroids.gif 1.2.28 arm64-v8a native lib with 16KB LOAD/RELRO flags.
# Wraps the official 1.2.28 AAR: replace jni/arm64-v8a/*.so, drop other JNI ABIs
# (product ndk abiFilters is arm64-v8a only). Does not run Gradle, does not modify
# product source, does not install to a device.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

TAG="v1.2.28"
COMMIT="9080ef9dd83a69d024e692f7fdf6647a99506bb2"
ARCHIVE_URL="https://github.com/koral--/android-gif-drawable/archive/refs/tags/v1.2.28.tar.gz"
ARCHIVE_SHA256="a2d055d908928d65243d02b12d40d755ef836af1e1c9b4ce0cdf678eaafcb7ce"
TAG_REF_URL="https://api.github.com/repos/koral--/android-gif-drawable/git/refs/tags/v1.2.28"
# Official Maven 1.2.28 AAR. Hash from native-16k-remediation/evidence/maven-native-elf.json
# and gif-16k-integration/patch.py (EXPECTED_AAR_SHA256).
AAR_URL="https://repo.maven.apache.org/maven2/pl/droidsonroids/gif/android-gif-drawable/1.2.28/android-gif-drawable-1.2.28.aar"
AAR_SHA256="2183dff4126811e7d20397d1989b7f6a577b0e96d5929c0a74547cc1770c8ea8"
NDK_VERSION="27.0.12077973"
CMAKE_VERSION="3.22.1"
ABI="arm64-v8a"
API="26"
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
SO_NAME="libpl_droidsonroids_gif.so"

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

require_python() {
  command -v python3 >/dev/null 2>&1 || fail "python3 is required on PATH"
}

require_sdk() {
  SDK="${ANDROID_SDK_ROOT:-}"
  if [[ -z "$SDK" ]]; then
    fail "ANDROID_SDK_ROOT is unset. Set it to an Android SDK directory containing ndk/${NDK_VERSION} and cmake/${CMAKE_VERSION}. This recipe does not guess a home SDK path."
  fi
  if [[ ! -d "$SDK" ]]; then
    fail "ANDROID_SDK_ROOT is not a directory: $SDK"
  fi
}

require_toolchain() {
  NDK="${NDK:-$SDK/ndk/$NDK_VERSION}"
  CMAKE="${CMAKE:-$SDK/cmake/${CMAKE_VERSION}/bin/cmake}"
  NINJA="${NINJA:-$SDK/cmake/${CMAKE_VERSION}/bin/ninja}"
  HOST_PREBUILT="$(detect_host_prebuilt)"
  PRE="$NDK/toolchains/llvm/prebuilt/$HOST_PREBUILT"
  READELF="$PRE/bin/llvm-readelf"
  NM="$PRE/bin/llvm-nm"
  CLANG="$PRE/bin/clang"
  if [[ ! -x "$CMAKE" ]]; then
    fail "cmake ${CMAKE_VERSION} missing at $CMAKE. Install SDK cmake/${CMAKE_VERSION} or set CMAKE to that executable."
  fi
  if [[ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]]; then
    fail "NDK ${NDK_VERSION} missing toolchain at $NDK. Install ndk/${NDK_VERSION} under ANDROID_SDK_ROOT or set NDK to that directory."
  fi
  if [[ ! -x "$NINJA" ]]; then
    fail "ninja missing at $NINJA. Install SDK cmake/${CMAKE_VERSION} (includes ninja) or set NINJA to that executable."
  fi
  if [[ ! -x "$READELF" ]]; then
    fail "llvm-readelf missing at $READELF (host prebuilt $HOST_PREBUILT). NDK layout does not match this host."
  fi
  if [[ ! -x "$NM" ]]; then
    fail "llvm-nm missing at $NM (host prebuilt $HOST_PREBUILT)."
  fi
  if [[ ! -x "$CLANG" ]]; then
    fail "clang missing at $CLANG (host prebuilt $HOST_PREBUILT)."
  fi
}

resolve_aar_path() {
  if [[ -n "${GIF_AAR:-}" ]]; then
    [[ -f "$GIF_AAR" ]] || fail "GIF_AAR is set but not a file: $GIF_AAR"
    AAR_LOCAL="$GIF_AAR"
  else
    AAR_LOCAL="$DOWNLOADS/android-gif-drawable-1.2.28.aar"
  fi
}

verify_aar_hash() {
  local got
  got="$(sha256_of "$AAR_LOCAL")"
  if [[ "$got" != "$AAR_SHA256" ]]; then
    fail "official GIF 1.2.28 AAR sha256 mismatch for $AAR_LOCAL: got $got want $AAR_SHA256"
  fi
}

require_python
require_sdk
require_toolchain

DOWNLOADS="$ROOT/downloads"
SRC_PARENT="$ROOT/source"
SRC_ROOT="$SRC_PARENT/android-gif-drawable-1.2.28"
CMAKE_LIST="$SRC_ROOT/android-gif-drawable/src/main/c/CMakeLists.txt"
BUILD_DIR="$ROOT/build/arm64-v8a"
OUT_DIR="$ROOT/out/arm64-v8a"
ARCHIVE="$DOWNLOADS/android-gif-drawable-1.2.28.tar.gz"
AAR_OUT="$ROOT/out/android-gif-drawable-1.2.28-16kb.aar"

resolve_aar_path
if [[ -n "${GIF_AAR:-}" ]]; then
  verify_aar_hash
fi

if [[ "${RECIPE_INPUT_CHECK_ONLY:-}" == "1" ]]; then
  python3 - "$SDK" "$NDK" "$CMAKE" "$NINJA" "$HOST_PREBUILT" "$PRE" "${GIF_AAR:-}" "$NDK_VERSION" "$CMAKE_VERSION" <<'PY'
import json, sys
print(json.dumps({
    "sdk": sys.argv[1],
    "ndk": sys.argv[2],
    "cmake": sys.argv[3],
    "ninja": sys.argv[4],
    "host_prebuilt": sys.argv[5],
    "prebuilt_dir": sys.argv[6],
    "aar": sys.argv[7],
    "ndk_version": sys.argv[8],
    "cmake_version": sys.argv[9],
}, indent=2))
PY
  exit 0
fi

mkdir -p "$DOWNLOADS" "$SRC_PARENT" "$BUILD_DIR" "$OUT_DIR"

if [[ ! -f "$AAR_LOCAL" ]]; then
  [[ -z "${GIF_AAR:-}" ]] || fail "GIF_AAR is set but not a file: $GIF_AAR"
  echo "downloading $AAR_URL"
  curl -fL --max-filesize 31457280 -o "$AAR_LOCAL" "$AAR_URL"
fi
verify_aar_hash

if [[ ! -f "$ARCHIVE" ]]; then
  echo "downloading $ARCHIVE_URL"
  curl -fL --max-filesize 31457280 -o "$ARCHIVE" "$ARCHIVE_URL"
fi

got="$(sha256_of "$ARCHIVE")"
if [[ "$got" != "$ARCHIVE_SHA256" ]]; then
  fail "archive sha256 mismatch: got $got want $ARCHIVE_SHA256"
fi

if [[ ! -f "$CMAKE_LIST" ]]; then
  rm -rf "$SRC_ROOT"
  tar -xzf "$ARCHIVE" -C "$SRC_PARENT"
fi
if [[ ! -f "$CMAKE_LIST" ]]; then
  fail "upstream CMakeLists.txt missing after extract: $CMAKE_LIST"
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
  fail "expected $BUILD_DIR/$SO_NAME"
fi
cp "$BUILD_DIR/$SO_NAME" "$OUT_DIR/$SO_NAME"

"$READELF" -lW "$OUT_DIR/$SO_NAME" > "$OUT_DIR/libpl_droidsonroids_gif.readelf-lW.txt"
"$NM" -D --defined-only "$OUT_DIR/$SO_NAME" | awk '/Java_|JNI_On/ {print $3}' | sort > "$ROOT/build/rebuilt-jni.txt"

CMPDIR="$ROOT/build/compare-1.2.28-aar"
mkdir -p "$CMPDIR"
cp "$ROOT/build/rebuilt-jni.txt" "$CMPDIR/rebuilt-jni.txt"
unzip -qo -j "$AAR_LOCAL" "jni/arm64-v8a/$SO_NAME" -d "$CMPDIR"
"$NM" -D --defined-only "$CMPDIR/$SO_NAME" | awk '/Java_|JNI_On/ {print $3}' | sort > "$CMPDIR/aar-jni.txt"
if ! cmp -s "$CMPDIR/aar-jni.txt" "$CMPDIR/rebuilt-jni.txt"; then
  fail "JNI defined symbols do not match official 1.2.28 AAR arm64 .so"
fi

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

python3 - "$AAR_LOCAL" "$OUT_DIR/$SO_NAME" "$AAR_OUT" "$ROOT/build/aar-wrap.json" <<'PY'
import hashlib, json, sys, zipfile
from pathlib import Path

src, so, dst, note = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
so_name = "jni/arm64-v8a/libpl_droidsonroids_gif.so"
so_bytes = Path(so).read_bytes()
skipped = []
keep = {}

def copy_info(src_info, name, data):
    info = zipfile.ZipInfo(filename=name, date_time=src_info.date_time)
    info.compress_type = src_info.compress_type
    info.comment = src_info.comment
    info.extra = src_info.extra
    info.create_system = src_info.create_system
    info.create_version = src_info.create_version
    info.extract_version = src_info.extract_version
    info.flag_bits = src_info.flag_bits
    info.external_attr = src_info.external_attr
    info.internal_attr = src_info.internal_attr
    info.file_size = len(data)
    return info

Path(dst).parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
    for info in zin.infolist():
        name = info.filename
        if name.startswith("jni/") and not name.startswith("jni/arm64-v8a/") and name != "jni/":
            skipped.append(name)
            continue
        data = so_bytes if name == so_name else zin.read(name)
        if name in ("classes.jar", "AndroidManifest.xml"):
            keep[name] = hashlib.sha256(data).hexdigest()
        zout.writestr(copy_info(info, name, data), data)

def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

with zipfile.ZipFile(src) as a, zipfile.ZipFile(dst) as b:
    classes_match = a.read("classes.jar") == b.read("classes.jar")
    manifest_match = a.read("AndroidManifest.xml") == b.read("AndroidManifest.xml")
    wrapped_so = b.read(so_name)
    other_jni = [n for n in b.namelist() if n.startswith("jni/") and n.endswith(".so") and n != so_name]

payload = {
    "src_aar": src,
    "src_aar_sha256": sha(src),
    "out_aar": dst,
    "out_aar_sha256": sha(dst),
    "out_aar_bytes": Path(dst).stat().st_size,
    "classes_jar_unchanged": classes_match,
    "android_manifest_unchanged": manifest_match,
    "arm64_so_replaced": wrapped_so == so_bytes,
    "arm64_so_sha256": hashlib.sha256(wrapped_so).hexdigest(),
    "skipped_non_arm64_jni": skipped,
    "remaining_other_abi_so": other_jni,
    "preserved_sha256": keep,
}
Path(note).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
if not (classes_match and manifest_match and wrapped_so == so_bytes and not other_jni):
    raise SystemExit("AAR wrap verification failed: " + json.dumps(payload, indent=2))
print(json.dumps({"classes_jar_unchanged": True, "out_aar": dst}, indent=2))
PY

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
  echo "host_prebuilt=$HOST_PREBUILT"
  echo "abi=$ABI"
  echo "android_platform=android-$API"
  echo "android_stl=none"
  echo "android_support_flexible_page_sizes=ON"
  echo "page_ldflags=$PAGE_LDFLAGS"
  echo "cmake_lists=$CMAKE_LIST"
  echo "so=$OUT_DIR/$SO_NAME"
  echo "aar=$AAR_OUT"
  echo "link_flags=$(cat "$ROOT/build/link-flags.txt")"
} > "$ROOT/build/toolchain.txt"

META="$ROOT/build/meta.json"
python3 - "$META" "$ROOT" "$NDK" "$CMAKE" "$NINJA" "$CLANG" "$HOST_PREBUILT" "$AAR_LOCAL" "$AAR_OUT" "$OUT_DIR/$SO_NAME" <<'PY'
import json, os, subprocess, sys
(
    meta_path, root, ndk, cmake, ninja, clang, host_prebuilt,
    aar_local, aar_out, so,
) = sys.argv[1:]
cmake_ver = subprocess.check_output([cmake, "--version"], text=True).splitlines()[0]
clang_ver = subprocess.check_output([clang, "--version"], text=True).splitlines()[0]
link_txt = os.path.join(root, "build/link-flags.txt")
link_flags = open(link_txt, encoding="utf-8").read().strip() if os.path.exists(link_txt) else ""
link_full = os.path.join(root, "build/link-command.txt")
link_cmd = open(link_full, encoding="utf-8").read().strip() if os.path.exists(link_full) else link_flags
aar_jni = os.path.join(root, "build/compare-1.2.28-aar/aar-jni.txt")
reb_jni = os.path.join(root, "build/compare-1.2.28-aar/rebuilt-jni.txt")
aar_lines = open(aar_jni, encoding="utf-8").read().splitlines()
reb_lines = open(reb_jni, encoding="utf-8").read().splitlines()
jni_match = aar_lines == reb_lines
if not jni_match:
    raise SystemExit("JNI defined symbols do not match official 1.2.28 AAR")
meta = {
  "library": "pl.droidsonroids.gif:android-gif-drawable:1.2.28",
  "version": "1.2.28",
  "tag": "v1.2.28",
  "commit": "9080ef9dd83a69d024e692f7fdf6647a99506bb2",
  "commit_url": "https://github.com/koral--/android-gif-drawable/commit/9080ef9dd83a69d024e692f7fdf6647a99506bb2",
  "tag_ref_url": "https://api.github.com/repos/koral--/android-gif-drawable/git/refs/tags/v1.2.28",
  "source_url": "https://github.com/koral--/android-gif-drawable/archive/refs/tags/v1.2.28.tar.gz",
  "source_sha256": "a2d055d908928d65243d02b12d40d755ef836af1e1c9b4ce0cdf678eaafcb7ce",
  "original_aar_url": "https://repo.maven.apache.org/maven2/pl/droidsonroids/gif/android-gif-drawable/1.2.28/android-gif-drawable-1.2.28.aar",
  "original_aar_sha256": "2183dff4126811e7d20397d1989b7f6a577b0e96d5929c0a74547cc1770c8ea8",
  "original_aar": aar_local,
  "out_aar": aar_out,
  "license": "MIT (upstream LICENSE also records SKIA BSD-3, GIFLIB, ReLinker Apache-2.0, memset.arm.S Apache-2.0)",
  "license_file": "LICENSE",
  "cmake_recipe": "android-gif-drawable/src/main/c/CMakeLists.txt (unmodified)",
  "abi": "arm64-v8a",
  "min_api": 26,
  "toolchain": {
    "ndk": ndk,
    "ndk_version": os.path.basename(ndk.rstrip("/")),
    "cmake": cmake,
    "cmake_version": cmake_ver,
    "ninja": ninja,
    "clang": clang,
    "clang_version": clang_ver,
    "host_prebuilt": host_prebuilt,
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
  "jni_count": len(reb_lines),
  "other_abis_in_wrapped_aar": False,
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
echo "wrote $AAR_OUT"
echo "wrote $ROOT/result.json"
