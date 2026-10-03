#!/usr/bin/env bash
# Rebuild androidx.graphics:graphics-path:1.0.1 arm64 .so with 16KB LOAD + RELRO.
# Wraps the official 1.0.1 AAR, replacing only jni/arm64-v8a/libandroidx.graphics.path.so.
# Does not modify product Gradle, does not install to a device.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

COMMIT="8a05a22af450d589ef911d772a001a49dcb05b71"
PREV_COMMIT="4fcd99eacd92d7c73fb1d3580fd423ed7704a98a"
ARCHIVE_URL="https://android.googlesource.com/platform/frameworks/support/+archive/${COMMIT}/graphics/graphics-path.tar.gz"
ARCHIVE_SHA256="e220621aacabaef495fc02a61f90566ac7e324ed2f8e806c7204d0a7376d1a78"
AAR_URL="https://dl.google.com/dl/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1.aar"
AAR_SHA256="8ca4032b6d79b351f0b59ad4b580eddbb9423e1652f7c958830687f1eee2ec03"
SOURCES_JAR_URL="https://dl.google.com/dl/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1-sources.jar"
SOURCES_JAR_SHA256="9f1b5995b9577a8876525c3411ebb2a49f9ef0e875f6aec3059e807596dc6ca6"
DEFAULT_AAR="/Users/lazy/Code/crack/1plus/integrations/operit-upstream-build/gradle-home/caches/modules-2/files-2.1/androidx.graphics/graphics-path/1.0.1/7e243033abd313c7202428422a01d48691378e33/graphics-path-1.0.1.aar"

NDK="${NDK:-/Users/lazy/Library/Android/sdk/ndk/27.1.12297006}"
CMAKE="${CMAKE:-/Users/lazy/Library/Android/sdk/cmake/3.22.1/bin/cmake}"
NINJA="${NINJA:-/Users/lazy/Library/Android/sdk/cmake/3.22.1/bin/ninja}"
ABI="arm64-v8a"
API="${API:-21}"
SO_NAME="libandroidx.graphics.path.so"
PAGE_LDFLAGS="-Wl,-z,common-page-size=16384"

DOWNLOADS="$ROOT/downloads"
SRC_PARENT="$ROOT/source"
SRC_ROOT="$SRC_PARENT/graphics-path-1.0.1"
CMAKE_LIST="$SRC_ROOT/src/main/cpp/CMakeLists.txt"
BUILD_DIR="$ROOT/build/arm64-v8a"
OUT_DIR="$ROOT/out/arm64-v8a"
EVIDENCE="$ROOT/evidence"
ARCHIVE="$DOWNLOADS/graphics-path-${COMMIT:0:7}.tar.gz"
AAR_LOCAL="${GRAPHICS_PATH_AAR:-$DEFAULT_AAR}"
AAR_DOWNLOAD="$DOWNLOADS/graphics-path-1.0.1.aar"
PRE="$NDK/toolchains/llvm/prebuilt/darwin-x86_64"
READELF="${READELF:-$PRE/bin/llvm-readelf}"
NM="${NM:-$PRE/bin/llvm-nm}"
CLANG="$PRE/bin/clang"
LIBCXX_INC="$PRE/sysroot/usr/include/c++/v1"
CACHE_DIR="${GRAPHICS_PATH_CACHE:-/tmp/graphics-path-16kb-rebuild}"

mkdir -p "$DOWNLOADS" "$SRC_PARENT" "$BUILD_DIR" "$OUT_DIR" "$EVIDENCE" "$CACHE_DIR"

for req in "$CMAKE" "$NINJA" "$NDK/build/cmake/android.toolchain.cmake" "$READELF" "$NM" "$LIBCXX_INC/cstdlib"; do
  if [[ ! -e "$req" ]]; then
    echo "missing: $req" >&2
    exit 1
  fi
done

fetch() {
  local url="$1" dest="$2"
  if [[ -f "$dest" ]]; then
    return 0
  fi
  echo "downloading $url"
  if curl -fsSL --max-time 60 -o "$dest" "$url"; then
    return 0
  fi
  echo "direct fetch failed, retry with local proxy" >&2
  https_proxy=http://127.0.0.1:7891 http_proxy=http://127.0.0.1:7891 \
    all_proxy=socks5://127.0.0.1:7891 \
    curl -fsSL --max-time 60 -o "$dest" "$url"
}

sha_ok() {
  local path="$1" want="$2"
  local got
  got="$(shasum -a 256 "$path" | awk '{print $1}')"
  if [[ "$got" != "$want" ]]; then
    echo "sha256 mismatch for $path: got $got want $want" >&2
    return 1
  fi
}

fetch "$ARCHIVE_URL" "$ARCHIVE"
sha_ok "$ARCHIVE" "$ARCHIVE_SHA256"

if [[ ! -f "$CMAKE_LIST" ]]; then
  rm -rf "$SRC_ROOT"
  mkdir -p "$SRC_ROOT"
  tar -xzf "$ARCHIVE" -C "$SRC_ROOT"
fi
if [[ ! -f "$CMAKE_LIST" ]]; then
  echo "upstream CMakeLists.txt missing after extract: $CMAKE_LIST" >&2
  exit 1
fi

if [[ ! -f "$AAR_LOCAL" ]]; then
  fetch "$AAR_URL" "$AAR_DOWNLOAD"
  AAR_LOCAL="$AAR_DOWNLOAD"
fi
sha_ok "$AAR_LOCAL" "$AAR_SHA256"

SOURCES_JAR="$DOWNLOADS/graphics-path-1.0.1-sources.jar"
if [[ ! -f "$SOURCES_JAR" ]]; then
  fetch "$SOURCES_JAR_URL" "$SOURCES_JAR" || true
fi
if [[ -f "$SOURCES_JAR" ]]; then
  sha_ok "$SOURCES_JAR" "$SOURCES_JAR_SHA256"
  python3 - "$SOURCES_JAR" "$SRC_ROOT/src/main/java" "$EVIDENCE/kotlin-match.json" <<'PY'
import hashlib, json, zipfile, sys
from pathlib import Path
jar_path, src_root, out = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
rows = []
all_equal = True
with zipfile.ZipFile(jar_path) as z:
    for name in sorted(n for n in z.namelist() if n.endswith(".kt")):
        jar_bytes = z.read(name)
        git_path = src_root / name
        git_bytes = git_path.read_bytes() if git_path.is_file() else None
        equal = git_bytes == jar_bytes
        all_equal = all_equal and equal
        rows.append(
            {
                "path": name,
                "equal": equal,
                "jar_sha256": hashlib.sha256(jar_bytes).hexdigest(),
                "git_sha256": hashlib.sha256(git_bytes).hexdigest() if git_bytes is not None else None,
            }
        )
payload = {
    "sources_jar": jar_path,
    "sources_jar_sha256": hashlib.sha256(Path(jar_path).read_bytes()).hexdigest(),
    "all_kotlin_equal": all_equal,
    "files": rows,
}
out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
if not all_equal:
    raise SystemExit("Kotlin sources.jar does not match git tree")
print("kotlin sources.jar match", all_equal, "files", len(rows))
PY
fi

CXXFLAGS="-std=c++17 -g0 -Wno-unused-command-line-argument -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-rtti -ffast-math -ffp-contract=fast -fvisibility-inlines-hidden -fvisibility=hidden -fomit-frame-pointer -ffunction-sections -fdata-sections -isystem ${LIBCXX_INC}"
LDFLAGS="-Wl,-z,common-page-size=16384 -Wl,--hash-style=both -Wl,--gc-sections -Wl,-Bsymbolic-functions -Wl,-z,relro -Wl,-z,now"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

"$CMAKE" -S "$SRC_ROOT/src/main/cpp" -B "$BUILD_DIR" \
  -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$NINJA" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-$API" \
  -DANDROID_STL=none \
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$LDFLAGS"

"$CMAKE" --build "$BUILD_DIR"

if [[ ! -f "$BUILD_DIR/$SO_NAME" ]]; then
  echo "expected $BUILD_DIR/$SO_NAME" >&2
  exit 1
fi
cp "$BUILD_DIR/$SO_NAME" "$OUT_DIR/$SO_NAME"

"$READELF" -lW "$OUT_DIR/$SO_NAME" > "$EVIDENCE/readelf-lW.txt"
"$READELF" --dyn-syms "$OUT_DIR/$SO_NAME" > "$EVIDENCE/dynsyms.txt"
"$NM" -D --defined-only "$OUT_DIR/$SO_NAME" > "$EVIDENCE/nm-defined.txt"
"$NM" -D --undefined-only "$OUT_DIR/$SO_NAME" > "$EVIDENCE/nm-undefined.txt"
awk '{print $NF}' "$EVIDENCE/nm-defined.txt" | sort > "$EVIDENCE/rebuilt-jni.txt"

CMPDIR="$BUILD_DIR/compare-1.0.1-aar"
mkdir -p "$CMPDIR"
unzip -qo -j "$AAR_LOCAL" "jni/arm64-v8a/$SO_NAME" -d "$CMPDIR"
"$NM" -D --defined-only "$CMPDIR/$SO_NAME" > "$EVIDENCE/nm-defined-orig.txt"
"$NM" -D --undefined-only "$CMPDIR/$SO_NAME" > "$EVIDENCE/nm-undefined-orig.txt"
awk '{print $NF}' "$EVIDENCE/nm-defined-orig.txt" | sort > "$EVIDENCE/aar-jni.txt"
cp "$CMPDIR/$SO_NAME" "$EVIDENCE/orig-arm64.so"

python3 - "$BUILD_DIR/build.ninja" "$EVIDENCE/link-flags.txt" <<'PY'
import re, sys
ninja, out = sys.argv[1], sys.argv[2]
text = open(ninja, encoding="utf-8").read()
m = re.search(r"^  LINK_FLAGS = (.+)$", text, re.M)
if not m:
    raise SystemExit("LINK_FLAGS not found in build.ninja")
open(out, "w", encoding="utf-8").write(m.group(1).strip() + "\n")
print(m.group(1).strip())
PY
"$NINJA" -C "$BUILD_DIR" -t commands "$SO_NAME" > "$EVIDENCE/ninja-commands.txt"
tail -n 1 "$EVIDENCE/ninja-commands.txt" > "$EVIDENCE/link-command.txt"

AAR_OUT="$ROOT/out/graphics-path-1.0.1-16kb-arm64.aar"
python3 - "$AAR_LOCAL" "$OUT_DIR/$SO_NAME" "$AAR_OUT" "$EVIDENCE/aar-wrap.json" <<'PY'
import hashlib, json, zipfile, sys
from pathlib import Path

src, so, dst, note = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
so_name = "jni/arm64-v8a/libandroidx.graphics.path.so"
so_bytes = Path(so).read_bytes()
keep = {}
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename in (
            "classes.jar",
            "AndroidManifest.xml",
            "proguard.txt",
            "R.txt",
            "public.txt",
        ) or info.filename.startswith("res/") or info.filename.startswith("META-INF/"):
            keep[info.filename] = hashlib.sha256(data).hexdigest()
        if info.filename == so_name:
            info.file_size = len(so_bytes)
            if info.compress_type == zipfile.ZIP_STORED:
                info.compress_size = len(so_bytes)
            zout.writestr(info, so_bytes)
        else:
            zout.writestr(info, data)

def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

with zipfile.ZipFile(src) as a, zipfile.ZipFile(dst) as b:
    orig_names = set(a.namelist())
    new_names = set(b.namelist())
    classes_match = a.read("classes.jar") == b.read("classes.jar")
    manifest_match = a.read("AndroidManifest.xml") == b.read("AndroidManifest.xml")
    other_jni = []
    for name in sorted(orig_names):
        if name.startswith("jni/") and name.endswith(".so") and name != so_name:
            other_jni.append(
                {
                    "name": name,
                    "unchanged": a.read(name) == b.read(name),
                    "sha256": hashlib.sha256(b.read(name)).hexdigest(),
                }
            )
    wrapped_so = b.read(so_name)

payload = {
    "src_aar": src,
    "src_aar_sha256": sha(src),
    "out_aar": dst,
    "out_aar_sha256": sha(dst),
    "out_aar_bytes": Path(dst).stat().st_size,
    "names_equal": orig_names == new_names,
    "classes_jar_unchanged": classes_match,
    "android_manifest_unchanged": manifest_match,
    "arm64_so_replaced": wrapped_so == so_bytes,
    "arm64_so_sha256": hashlib.sha256(wrapped_so).hexdigest(),
    "other_jni_unchanged": all(x["unchanged"] for x in other_jni),
    "other_jni": other_jni,
    "preserved_sha256": keep,
}
Path(note).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
if not (
    payload["names_equal"]
    and payload["classes_jar_unchanged"]
    and payload["android_manifest_unchanged"]
    and payload["arm64_so_replaced"]
    and payload["other_jni_unchanged"]
):
    raise SystemExit("AAR wrap verification failed: " + json.dumps(payload, indent=2))
print(json.dumps(payload, indent=2))
PY

META="$EVIDENCE/meta.json"
python3 - "$META" "$ROOT" "$COMMIT" "$PREV_COMMIT" "$ARCHIVE_URL" "$ARCHIVE_SHA256" \
  "$AAR_LOCAL" "$AAR_SHA256" "$NDK" "$CMAKE" "$NINJA" "$CLANG" "$API" "$ABI" \
  "$CXXFLAGS" "$LDFLAGS" "$OUT_DIR/$SO_NAME" "$AAR_OUT" "$ARCHIVE" <<'PY'
import hashlib, json, os, subprocess, sys
from pathlib import Path

(
    meta_path, root, commit, prev, archive_url, archive_sha,
    aar, aar_sha, ndk, cmake, ninja, clang, api, abi,
    cxxflags, ldflags, so, aar_out, archive,
) = sys.argv[1:]

def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def read(p):
    path = Path(root) / "evidence" / p
    return path.read_text(encoding="utf-8", errors="replace").strip() if path.is_file() else ""

cmake_ver = subprocess.check_output([cmake, "--version"], text=True).splitlines()[0]
clang_ver = subprocess.check_output([clang, "--version"], text=True).splitlines()[0]
src_root = Path(root) / "source/graphics-path-1.0.1"
kt = {
    "ConicConverter.kt": "src/main/java/androidx/graphics/path/ConicConverter.kt",
    "PathIterator.kt": "src/main/java/androidx/graphics/path/PathIterator.kt",
    "PathIteratorImpl.kt": "src/main/java/androidx/graphics/path/PathIteratorImpl.kt",
    "PathSegment.kt": "src/main/java/androidx/graphics/path/PathSegment.kt",
}
kt_sha = {name: sha(src_root / rel) for name, rel in kt.items()}
cpp_files = [
    "src/main/cpp/CMakeLists.txt",
    "src/main/cpp/Conic.cpp",
    "src/main/cpp/Conic.h",
    "src/main/cpp/Path.h",
    "src/main/cpp/PathIterator.cpp",
    "src/main/cpp/PathIterator.h",
    "src/main/cpp/pathway.cpp",
    "src/main/cpp/libandroidx.graphics.path.map",
]
meta = {
    "library": "androidx.graphics:graphics-path:1.0.1",
    "version": "1.0.1",
    "git_tag": None,
    "git_tag_note": "No androidx.graphics:graphics-path:1.0.1 tag on platform/frameworks/support (1753 tags listed 2026-10-02). Pin is the official release-notes commit range end.",
    "commit": commit,
    "previous_release_commit": prev,
    "commit_url": f"https://android.googlesource.com/platform/frameworks/support/+/{commit}",
    "release_notes": "https://developer.android.com/jetpack/androidx/releases/graphics#graphics-path-1.0.1",
    "release_date": "2024-05-01",
    "source_url": archive_url,
    "source_sha256": archive_sha,
    "source_archive_sha256_actual": sha(archive),
    "license": "Apache-2.0",
    "license_file": "LICENSE",
    "cmake_recipe": "src/main/cpp/CMakeLists.txt (unmodified; already has -z,max-page-size=16384)",
    "extra_ldflags": ["-Wl,-z,common-page-size=16384"],
    "abi": abi,
    "min_api": int(api),
    "upstream_min_sdk": 21,
    "stl": "none (libc++ headers only via -isystem; matches upstream -nostdlib++)",
    "toolchain": {
        "ndk": ndk,
        "ndk_version": Path(ndk).name,
        "cmake": cmake,
        "cmake_version": cmake_ver,
        "ninja": ninja,
        "clang": clang,
        "clang_version": clang_ver,
        "host_prebuilt": "darwin-x86_64",
    },
    "original_aar": aar,
    "original_aar_sha256": aar_sha,
    "out_so": so,
    "out_so_sha256": sha(so),
    "out_so_bytes": os.path.getsize(so),
    "out_aar": aar_out,
    "out_aar_sha256": sha(aar_out),
    "out_aar_bytes": os.path.getsize(aar_out),
    "kotlin_sha256": kt_sha,
    "cpp_sha256": {p: sha(src_root / p) for p in cpp_files},
    "link_flags": read("link-flags.txt"),
    "link_command": read("link-command.txt"),
    "version_upgrade": False,
    "other_abis_rebuilt": False,
    "apk_integrated": False,
    "device_test": False,
}
Path(meta_path).write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"out_so": so, "out_aar": aar_out, "so_sha256": meta["out_so_sha256"]}, indent=2))
PY

python3 "$ROOT/check_elf.py" \
  --so "$OUT_DIR/$SO_NAME" \
  --orig-so "$EVIDENCE/orig-arm64.so" \
  --nm-defined "$EVIDENCE/nm-defined.txt" \
  --nm-defined-orig "$EVIDENCE/nm-defined-orig.txt" \
  --nm-undefined "$EVIDENCE/nm-undefined.txt" \
  --nm-undefined-orig "$EVIDENCE/nm-undefined-orig.txt" \
  --readelf "$READELF" \
  --meta-json "$META" \
  --out "$ROOT/result.json"

python3 - "$ROOT/result.json" "$EVIDENCE/aar-wrap.json" "$EVIDENCE/kotlin-match.json" <<'PY'
import json, sys
from pathlib import Path
result_path, wrap_path, kotlin_path = sys.argv[1], sys.argv[2], sys.argv[3]
result = json.loads(Path(result_path).read_text(encoding="utf-8"))
wrap = json.loads(Path(wrap_path).read_text(encoding="utf-8"))
kotlin = {}
if Path(kotlin_path).is_file():
    kotlin = json.loads(Path(kotlin_path).read_text(encoding="utf-8"))
result["aar_wrap"] = wrap
if kotlin:
    result["kotlin_sources_jar_match"] = kotlin
result["worker_status"] = "completed_unverified"
result["independent_verification"] = False
Path(result_path).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
if not result.get("static_pass") or not wrap.get("classes_jar_unchanged"):
    raise SystemExit("result merge failed")
PY

echo "wrote $OUT_DIR/$SO_NAME"
echo "wrote $AAR_OUT"
echo "wrote $ROOT/result.json"
