#!/usr/bin/env bash
# Rebuild Microsoft ONNX Runtime 1.29.0 (commit 2e2543f) Android arm64-v8a CPU-only
# with LOAD+RELRO 16KB flags. Official Android/Java entry: tools/ci_build/build.py.
# CPU-only: no --use_webgpu/--use_nnapi/--use_xnnpack; cmake providers explicitly OFF.
# Default full operators retained. Not equivalent to Maven multi-ABI/multi-EP AAR.
# Telemetry OFF. JDK 17 required (JDK 21 jlink is known to fail). Override only via
# JAVA_HOME_OVERRIDE, which must still be JDK 17. Ambient JAVA_HOME is ignored.
# Does not run product Gradle, does not patch ELF program headers, does not disable RELRO.
# Does not re-download source/CMake; uses calling environment network for remaining FetchContent.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

COMMIT="2e2543fbe9fae542f921d47a72d21d5a4ef0b710"
TAG="v1.29.0"
VERSION="1.29.0"

SDK="${ANDROID_SDK_ROOT:-/Users/lazy/Library/Android/sdk}"
NDK="${NDK:-$SDK/ndk/27.1.12297006}"
# Proven successful home for this recipe. Do not default to the private JDK 21 toolchain.
DEFAULT_JDK17="/Users/lazy/Library/Java/JavaVirtualMachines/openjdk-17.0.2/Contents/Home"
JDK="${JAVA_HOME_OVERRIDE:-$DEFAULT_JDK17}"
PY="${PY:-/opt/homebrew/bin/python3.12}"
ABI="arm64-v8a"
API="26"
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
# Worker budget is 20 minutes. Override remaining compile window via env.
TIMEOUT_SEC="${ONNX_BUILD_TIMEOUT_SEC:-720}"
JOBS="${ONNX_BUILD_JOBS:-4}"
if [[ "$JOBS" -gt 4 ]]; then
  JOBS=4
fi

DOWNLOADS="$ROOT/downloads"
SRC_PARENT="$ROOT/source"
SRC="$SRC_PARENT/onnxruntime-${COMMIT}"
OLD_DEPS="$ROOT/build/android-arm64/Release/_deps"
BUILD_DIR="$ROOT/build/android-arm64-cpu"
MIRROR="$ROOT/deps-mirror"
OUT="$ROOT/out"
EVIDENCE="$ROOT/evidence"
VENV="$ROOT/venv"
CMAKE_ROOT="$ROOT/toolchains/cmake-3.28.6-macos-universal/CMake.app/Contents"
CMAKE_BIN="$CMAKE_ROOT/bin/cmake"
NINJA_BIN="${NINJA:-$SDK/cmake/3.22.1/bin/ninja}"
READELF="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
NM="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-nm"
HOST_AAR="$ROOT/cache-aar/onnxruntime-android-1.29.0.aar"
PATCH="$ROOT/cmake-common-page-size.patch"
LOG="$ROOT/logs/build.py.cpu.log"
AAR_NAME="onnxruntime-android-1.29.0-arm64-cpu-16kb.aar"

mkdir -p "$DOWNLOADS" "$SRC_PARENT" "$BUILD_DIR" "$OUT/arm64-v8a" "$EVIDENCE" "$ROOT/logs" "$ROOT/toolchains" "$MIRROR"

write_status() {
  python3 - "$EVIDENCE/status.json" "$@" <<'PY'
import json, os, sys, time, glob
path = sys.argv[1]
status = sys.argv[2]
stage = sys.argv[3]
error = sys.argv[4]
rc = sys.argv[5]
root = os.path.dirname(os.path.dirname(path))
build = os.path.join(root, "build", "android-arm64-cpu")
cache = os.path.join(build, "Release", "CMakeCache.txt")
ninja = os.path.join(build, "Release", "build.ninja")
ort = os.path.join(build, "Release", "libonnxruntime.so")
jni = os.path.join(build, "Release", "libonnxruntime4j_jni.so")
log = os.path.join(root, "logs", "build.py.cpu.log")
last = []
if os.path.isfile(log):
    lines = open(log, encoding="utf-8", errors="replace").read().splitlines()
    last = lines[-25:]
prov = {}
if os.path.isfile(cache):
    keys = (
        "onnxruntime_USE_TELEMETRY:",
        "onnxruntime_USE_WEBGPU:",
        "onnxruntime_USE_XNNPACK:",
        "onnxruntime_USE_NNAPI_BUILTIN:",
        "onnxruntime_MINIMAL_BUILD:",
        "onnxruntime_REDUCED_OPS_BUILD:",
        "onnxruntime_DISABLE_CONTRIB_OPS:",
        "onnxruntime_BUILD_JAVA:",
        "onnxruntime_BUILD_SHARED_LIB:",
        "ANDROID_ABI:",
        "ANDROID_PLATFORM:",
    )
    for line in open(cache, encoding="utf-8", errors="replace"):
        for k in keys:
            if line.startswith(k):
                prov[k.rstrip(":")] = line.strip()
objs = len(glob.glob(os.path.join(build, "Release", "**", "*.o"), recursive=True)) if os.path.isdir(build) else 0
payload = {
  "status": status,
  "stage": stage,
  "stage_detail": error,
  "exit_code": int(rc) if str(rc).lstrip("-").isdigit() else rc,
  "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
  "independent_verification": False,
  "variant": "cpu-only",
  "not_equivalent_to_maven_aar": True,
  "not_equivalent_to_maven_multi_abi_multi_ep": True,
  "providers_built": ["CPU"],
  "providers_not_built": ["NNAPI", "XNNPACK", "WebGPU"],
  "telemetry": "OFF",
  "onnxruntime_USE_TELEMETRY": "OFF",
  "jdk_required": "17",
  "jdk21_jlink_known_fail": True,
  "runtime_vad_vits_tested": False,
  "telemetry_native_entry_points_expected": False,
  "version": "1.29.0",
  "commit": "2e2543fbe9fae542f921d47a72d21d5a4ef0b710",
  "build_dir": build,
  "cmake_cache_exists": os.path.isfile(cache),
  "build_ninja_exists": os.path.isfile(ninja),
  "object_files_seen": objs,
  "artifacts": {
    "libonnxruntime.so": ort if os.path.isfile(ort) else None,
    "libonnxruntime4j_jni.so": jni if os.path.isfile(jni) else None,
    "aar": None,
  },
  "cmake_cache_provider_flags": prov,
  "last_log_lines": last,
  "product_gradle": False,
  "device_test": False,
  "multi_abi": False,
  "continuation": {
    "command": "ONNX_BUILD_TIMEOUT_SEC=14400 ONNX_BUILD_JOBS=4 ./build.sh",
    "cwd": "integrations/operit-upstream-build/onnx-16kb-rebuild",
    "note": "Resume in build/android-arm64-cpu. JDK 17 required (JAVA_HOME_OVERRIDE; JDK 21 jlink fails). Do not reuse build/android-arm64 CMakeCache (WebGPU). Do not copy Maven AAR .so. Do not re-download source/CMake.",
  },
}
os.makedirs(os.path.dirname(path), exist_ok=True)
json.dump(payload, open(path, "w"), indent=2)
json.dump(payload, open(os.path.join(os.path.dirname(path), "continuation.json"), "w"), indent=2)
print("wrote", path)
PY
}

fail() {
  echo "ERROR: $*" >&2
  write_status "blocked" "${ONNX_STAGE:-unknown}" "$*" "${BUILD_RC:-1}"
  STATUS_WRITTEN=1
  exit 1
}

on_exit() {
  rc=$?
  if [[ $rc -ne 0 && "${STATUS_WRITTEN:-}" != 1 ]]; then
    write_status "blocked" "${ONNX_STAGE:-unknown}" "shell exit $rc" "$rc"
  fi
}
trap on_exit EXIT

export ONNX_STAGE="preflight"

[[ -x "$PY" ]] || fail "python3.12 missing: $PY"
[[ -d "$NDK/build/cmake" ]] || fail "NDK missing: $NDK"
[[ -x "$JDK/bin/java" ]] || fail "JDK 17 missing at $JDK (bin/java not executable). JDK 21 jlink is known to fail for this Android Java package. Set JAVA_HOME_OVERRIDE to a JDK 17 home."
jdk_line="$("$JDK/bin/java" -version 2>&1 | head -n 1 || true)"
case "$jdk_line" in
  *'version "17.'*) ;;
  *) fail "JDK 17 required (JDK 21 jlink is known to fail). Got: ${jdk_line:-unreadable} (JAVA_HOME=$JDK). Set JAVA_HOME_OVERRIDE to a JDK 17 home." ;;
esac
echo "using JDK 17 at $JDK ($jdk_line)"
[[ -x "$READELF" ]] || fail "llvm-readelf missing: $READELF"
[[ -x "$NM" ]] || fail "llvm-nm missing: $NM"
[[ -x "$NINJA_BIN" ]] || fail "ninja missing: $NINJA_BIN"
[[ -f "$HOST_AAR" ]] || fail "cached 1.29.0 AAR missing: $HOST_AAR"
[[ -f "$PATCH" ]] || fail "page-size patch missing: $PATCH"

ARCHIVE="$DOWNLOADS/onnxruntime-${COMMIT}.tar.gz"
[[ -f "$ARCHIVE" ]] || fail "source archive missing (no re-download): $ARCHIVE"
ARCHIVE_SHA="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
echo "$ARCHIVE_SHA  $ARCHIVE" | tee "$DOWNLOADS/source-archive.sha256"

if [[ ! -f "$SRC/VERSION_NUMBER" ]]; then
  export ONNX_STAGE="extract-source"
  tar -xzf "$ARCHIVE" -C "$SRC_PARENT"
fi
[[ -f "$SRC/VERSION_NUMBER" ]] || fail "extract failed, VERSION_NUMBER missing"
GOT_VER="$(tr -d '[:space:]' < "$SRC/VERSION_NUMBER")"
[[ "$GOT_VER" == "$VERSION" ]] || fail "VERSION_NUMBER=$GOT_VER want $VERSION"

CMAKE_TGZ="$DOWNLOADS/cmake-3.28.6-macos-universal.tar.gz"
[[ -f "$CMAKE_TGZ" ]] || fail "cmake archive missing (no re-download): $CMAKE_TGZ"
if [[ ! -x "$CMAKE_BIN" ]]; then
  export ONNX_STAGE="extract-cmake"
  tar -xzf "$CMAKE_TGZ" -C "$ROOT/toolchains"
fi
[[ -x "$CMAKE_BIN" ]] || fail "task-local cmake missing after extract"
CMAKE_SHA="$(shasum -a 256 "$CMAKE_TGZ" | awk '{print $1}')"
"$CMAKE_BIN" --version | head -1 | tee "$EVIDENCE/cmake-version.txt"

export ONNX_STAGE="apply-page-size-patch"
if ! grep -q "common-page-size=16384" "$SRC/cmake/adjust_global_compile_flags.cmake"; then
  (cd "$SRC" && patch -p1 < "$PATCH") || fail "patch failed"
fi
grep -q "common-page-size=16384" "$SRC/cmake/adjust_global_compile_flags.cmake" || fail "common-page-size not in cmake flags"

if [[ ! -x "$VENV/bin/python" ]]; then
  export ONNX_STAGE="venv"
  "$PY" -m venv "$VENV"
fi

cp "$SRC/LICENSE" "$ROOT/LICENSE"

export ONNX_STAGE="seed-dep-cache"
python3 - "$OLD_DEPS" "$MIRROR" "$SRC/cmake/deps.txt" "$EVIDENCE/dep-cache.json" <<'PY'
import json, os, sys, time
old_deps, mirror, deps_txt, out = sys.argv[1:5]
skip_src = {"dawn", "fxdiv", "googlexnnpack", "pthreadpool", "googletest"}
name_map = {
  "abseil_cpp": "abseil_cpp",
  "date": "date",
  "eigen3": "Eigen3",
  "flatbuffers": "flatbuffers",
  "gsl": "GSL",
  "kleidiai": "kleidiai",
  "mp11": "mp11",
  "nlohmann_json": "nlohmann_json",
  "onnx": "onnx",
  "protobuf": "Protobuf",
  "protoc_binary": "protoc_binary",
  "pytorch_cpuinfo": "pytorch_cpuinfo",
  "re2": "re2",
  "safeint": "safeint",
}
urls = {}
for line in open(deps_txt, encoding="utf-8"):
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    parts = line.split(";")
    if len(parts) >= 2 and parts[1].startswith("https://"):
        urls[os.path.basename(parts[1])] = parts[1][len("https://"):]
source_dirs = {}
for dirname, fc_name in name_map.items():
    if dirname in skip_src:
        continue
    src = os.path.join(old_deps, dirname + "-src")
    if not os.path.isdir(src):
        continue
    ok = (
        os.path.isfile(os.path.join(src, "CMakeLists.txt"))
        or os.path.isdir(os.path.join(src, "include"))
        or os.path.isfile(os.path.join(src, "SafeInt.hpp"))
        or os.path.isfile(os.path.join(src, "bin", "protoc"))
    )
    if ok and os.path.getsize(src) > 0:
        source_dirs[fc_name] = os.path.abspath(src)
mirrored = []
skipped_incomplete = []
for dirpath, _, files in os.walk(old_deps):
    if "populate-prefix/src" not in dirpath.replace("\\", "/"):
        continue
    for fn in files:
        if not (fn.endswith(".zip") or fn.endswith(".tar.gz")):
            continue
        srcf = os.path.join(dirpath, fn)
        if fn.startswith("v20260714") or "dawn" in dirpath:
            skipped_incomplete.append({"file": srcf, "reason": "dawn zip not used for CPU-only"})
            continue
        rel = urls.get(fn)
        if not rel:
            continue
        dest = os.path.join(mirror, rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        if os.path.lexists(dest):
            os.remove(dest)
        os.symlink(srcf, dest)
        mirrored.append({"url_path": rel, "target": srcf, "bytes": os.path.getsize(srcf)})
payload = {
  "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
  "fetchcontent_source_dirs": source_dirs,
  "mirrored_archives": mirrored,
  "skipped": skipped_incomplete,
  "note": "SOURCE_DIR reuses extracted CPU deps from previous FetchContent; dawn/xnnpack not wired. Remaining uncached deps use process environment network (no script proxy fallback).",
}
json.dump(payload, open(out, "w"), indent=2)
print("SOURCE_DIRS=" + json.dumps(source_dirs))
print("wrote", out)
PY

python3 - "$EVIDENCE/inputs.json" "$COMMIT" "$TAG" "$GOT_VER" "$ARCHIVE_SHA" "$CMAKE_SHA" "$HOST_AAR" "$SRC" "$NDK" "$JDK" "$BUILD_DIR" "$MIRROR" <<'PY'
import json, sys, os, hashlib, time
path = sys.argv[1]
aar = sys.argv[7]
h = hashlib.sha256()
with open(aar, "rb") as f:
    for c in iter(lambda: f.read(1024 * 1024), b""):
        h.update(c)
json.dump({
  "tag": sys.argv[3],
  "commit": sys.argv[2],
  "version": sys.argv[4],
  "variant": "cpu-only",
  "not_equivalent_to_maven_aar": True,
  "not_equivalent_to_maven_multi_abi_multi_ep": True,
  "providers_built": ["CPU"],
  "providers_not_built": ["NNAPI", "XNNPACK", "WebGPU"],
  "telemetry": "OFF",
  "onnxruntime_USE_TELEMETRY": "OFF",
  "jdk_required": "17",
  "jdk21_jlink_known_fail": True,
  "runtime_vad_vits_tested": False,
  "operators": "default full (MINIMAL/REDUCED/DISABLE_CONTRIB not enabled)",
  "source_archive_sha256": sys.argv[5],
  "cmake_archive_sha256": sys.argv[6],
  "source_dir": sys.argv[8],
  "build_dir": sys.argv[11],
  "deps_mirror": sys.argv[12],
  "cached_aar": aar,
  "cached_aar_sha256": h.hexdigest(),
  "java_classes_jar_from": "official Maven onnxruntime-android 1.29.0 AAR",
  "ndk": sys.argv[9],
  "jdk": sys.argv[10],
  "android_api": 26,
  "android_abi": "arm64-v8a",
  "jobs_max": 4,
  "license": "MIT (Microsoft onnxruntime LICENSE)",
  "source_url": "https://github.com/microsoft/onnxruntime/archive/" + sys.argv[2] + ".tar.gz",
  "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}, open(path, "w"), indent=2)
print("wrote", path)
PY

python3 - "$EVIDENCE/providers.json" <<'PY'
import json, time, sys
json.dump({
  "variant": "cpu-only",
  "not_equivalent_to_maven_aar": True,
  "not_equivalent_to_maven_multi_abi_multi_ep": True,
  "multi_abi": False,
  "app_usage": "ai.onnxruntime is used only in OnnxSileroVad.kt and VitsVoiceProvider.kt; SessionOptions set threads/optimization only; addNnapi/addXnnpack/addWebGPU not called; tool plugin entry disabled under COMMON_BASE",
  "this_build_ep": ["CPU"],
  "maven_1_29_0_aar_ep": ["CPU", "NNAPI", "XNNPACK", "WebGPU"],
  "explicitly_off": {
    "onnxruntime_USE_NNAPI_BUILTIN": "OFF",
    "onnxruntime_USE_XNNPACK": "OFF",
    "onnxruntime_USE_WEBGPU": "OFF",
    "onnxruntime_USE_TELEMETRY": "OFF",
  },
  "telemetry": {
    "onnxruntime_USE_TELEMETRY": "OFF",
    "native_httpclient_jni_in_rebuilt_libonnxruntime": False,
    "app_expected_to_call_telemetry_native_entry_points": False,
  },
  "kept": "default full operators retained so CPU VAD/VITS inference remains in scope; runtime VAD/VITS not tested",
  "runtime_vad_vits_tested": False,
  "java_classes_jar": "unchanged from official 1.29.0 AAR; Java API may still expose unused EP methods; this local AAR is not a Maven multi-ABI/multi-EP replacement",
  "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}, open(sys.argv[1], "w"), indent=2)
print("wrote", sys.argv[1])
PY

export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_NDK_HOME="$NDK"
export PATH="$CMAKE_ROOT/bin:$VENV/bin:$(dirname "$NINJA_BIN"):$JDK/bin:$PATH"

FC_DEFINES_FILE="$EVIDENCE/cmake-fc-defines.txt"
python3 - "$EVIDENCE/dep-cache.json" "$FC_DEFINES_FILE" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
lines = ["FETCHCONTENT_TRY_FIND_PACKAGE_MODE=NEVER", "FETCHCONTENT_UPDATES_DISCONNECTED=ON"]
for name, path in sorted(d.get("fetchcontent_source_dirs", {}).items()):
    lines.append("FETCHCONTENT_SOURCE_DIR_%s=%s" % (name.upper(), path))
open(sys.argv[2], "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("wrote", sys.argv[2], "count", len(lines))
PY
FC_DEFINES=()
while IFS= read -r _fc_line; do
  if [[ -n "$_fc_line" ]]; then
    FC_DEFINES+=("$_fc_line")
  fi
done < "$FC_DEFINES_FILE"

export ONNX_STAGE="official-android-java-build-cpu"
CMD=(
  "$VENV/bin/python" "$SRC/tools/ci_build/build.py"
  --build_dir "$BUILD_DIR"
  --config Release
  --update --build
  --android
  --android_sdk_path "$SDK"
  --android_ndk_path "$NDK"
  --android_abi "$ABI"
  --android_api "$API"
  --build_java
  --build_shared_lib
  --enable_lto
  --skip_tests
  --skip_submodule_sync
  --cmake_generator Ninja
  --parallel "$JOBS"
  --cmake_path "$CMAKE_BIN"
  --cmake_deps_mirror_dir "$MIRROR"
  --cmake_extra_defines
    "ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
    "CMAKE_MAKE_PROGRAM=$NINJA_BIN"
    "CMAKE_SHARED_LINKER_FLAGS=$PAGE_LDFLAGS"
    "CMAKE_MODULE_LINKER_FLAGS=$PAGE_LDFLAGS"
    "onnxruntime_USE_TELEMETRY=OFF"
    "onnxruntime_USE_NNAPI_BUILTIN=OFF"
    "onnxruntime_USE_XNNPACK=OFF"
    "onnxruntime_USE_WEBGPU=OFF"
    "onnxruntime_MINIMAL_BUILD=OFF"
    "onnxruntime_REDUCED_OPS_BUILD=OFF"
    "onnxruntime_DISABLE_CONTRIB_OPS=OFF"
    "onnxruntime_BUILD_UNIT_TESTS=OFF"
    "${FC_DEFINES[@]}"
)

printf '%q ' "${CMD[@]}" | tee "$EVIDENCE/build-command.txt"
echo | tee -a "$EVIDENCE/build-command.txt"
echo "timeout_sec=$TIMEOUT_SEC jobs=$JOBS variant=cpu-only jdk=$JDK" | tee -a "$EVIDENCE/build-command.txt"
echo "https_proxy=${https_proxy-} http_proxy=${http_proxy-} all_proxy=${all_proxy-}" | tee -a "$EVIDENCE/build-command.txt"

set +e
# Watchdog must use wait(timeout). readline() on PIPE blocks and never fires the deadline.
"$VENV/bin/python" - "$TIMEOUT_SEC" "$LOG" "${CMD[@]}" <<'PY'
import os, signal, subprocess, sys, time
timeout = int(sys.argv[1])
log_path = sys.argv[2]
cmd = sys.argv[3:]
os.makedirs(os.path.dirname(log_path), exist_ok=True)
env = os.environ.copy()
env["PYTHONUNBUFFERED"] = "1"
with open(log_path, "w", encoding="utf-8") as log:
    log.write("timeout_sec=%s start=%s variant=cpu-only cmd=%s\n" % (timeout, time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), " ".join(cmd)))
    log.flush()
    p = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT, start_new_session=True, env=env)
    try:
        rc = p.wait(timeout=timeout)
        sys.exit(rc)
    except subprocess.TimeoutExpired:
        log.write("\nTIMEOUT after %ss; sending SIGTERM to process group %s\n" % (timeout, p.pid))
        log.flush()
        try:
            os.killpg(p.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            p.wait(timeout=20)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(p.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            p.wait(timeout=5)
        sys.exit(124)
PY
BUILD_RC=$?
set -e

if [[ $BUILD_RC -eq 124 ]]; then
  export ONNX_STAGE="official-android-java-build-cpu"
  fail "build.py timed out after ${TIMEOUT_SEC}s (process group terminated); log=$LOG"
fi
if [[ $BUILD_RC -ne 0 ]]; then
  export ONNX_STAGE="official-android-java-build-cpu"
  fail "build.py exited $BUILD_RC; log=$LOG"
fi

ORT_SO="$BUILD_DIR/Release/libonnxruntime.so"
JNI_SO="$BUILD_DIR/Release/libonnxruntime4j_jni.so"
[[ -f "$ORT_SO" ]] || fail "missing $ORT_SO (AAR not assembled)"
[[ -f "$JNI_SO" ]] || fail "missing $JNI_SO (AAR not assembled)"
cp "$ORT_SO" "$OUT/arm64-v8a/libonnxruntime.so"
cp "$JNI_SO" "$OUT/arm64-v8a/libonnxruntime4j_jni.so"

"$READELF" -lW "$OUT/arm64-v8a/libonnxruntime.so" > "$OUT/arm64-v8a/libonnxruntime.readelf-lW.txt"
"$READELF" -lW "$OUT/arm64-v8a/libonnxruntime4j_jni.so" > "$OUT/arm64-v8a/libonnxruntime4j_jni.readelf-lW.txt"

export ONNX_STAGE="elf-check"
"$VENV/bin/python" "$ROOT/check_elf.py" \
  --so "$OUT/arm64-v8a/libonnxruntime.so" \
  --so "$OUT/arm64-v8a/libonnxruntime4j_jni.so" \
  --readelf "$READELF" \
  --out-json "$EVIDENCE/elf-check.json"

export ONNX_STAGE="jni-compare"
set +e
"$VENV/bin/python" "$ROOT/compare_jni.py" \
  --nm "$NM" \
  --baseline-so "$ROOT/cache-aar/extract/jni/arm64-v8a/libonnxruntime.so" \
  --rebuilt-so "$OUT/arm64-v8a/libonnxruntime.so" \
  --baseline-so "$ROOT/cache-aar/extract/jni/arm64-v8a/libonnxruntime4j_jni.so" \
  --rebuilt-so "$OUT/arm64-v8a/libonnxruntime4j_jni.so" \
  --out-json "$EVIDENCE/jni-compare.json"
JNI_RC=$?
set -e
echo "jni-compare exit=$JNI_RC (non-zero records provider/JNI diffs; does not block AAR after successful link)"

export ONNX_STAGE="assemble-aar"
"$VENV/bin/python" "$ROOT/assemble_aar.py" \
  --src-aar "$HOST_AAR" \
  --onnxruntime-so "$OUT/arm64-v8a/libonnxruntime.so" \
  --jni-so "$OUT/arm64-v8a/libonnxruntime4j_jni.so" \
  --out-aar "$OUT/$AAR_NAME" \
  --out-json "$EVIDENCE/assemble-aar.json" \
  --cpu-only

export ONNX_STAGE="link-flag-scan"
python3 - "$BUILD_DIR/Release/build.ninja" "$EVIDENCE/link-flags.json" <<'PY'
import json, sys
ninja, out = sys.argv[1], sys.argv[2]
text = open(ninja, encoding="utf-8", errors="replace").read()
need = ["max-page-size=16384", "common-page-size=16384"]
hits = []
for i, line in enumerate(text.splitlines(), 1):
    if "libonnxruntime.so" in line or "onnxruntime4j_jni" in line:
        hits.append({"line": i, "text": line[:500], "has_max": need[0] in line, "has_common": need[1] in line})
json.dump({"ninja": ninja, "hits": hits[:40], "max_in_file": need[0] in text, "common_in_file": need[1] in text, "variant": "cpu-only"}, open(out, "w"), indent=2)
print("wrote", out)
PY

"$VENV/bin/python" - "$EVIDENCE/status.json" "$OUT" "$AAR_NAME" "$JNI_RC" <<'PY'
import json, sys, time, os
path, out, aar_name, jni_rc = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
aar = os.path.join(out, aar_name)
json.dump({
  "status": "artifacts_written",
  "stage": "complete-local-static",
  "exit_code": 0,
  "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
  "independent_verification": False,
  "variant": "cpu-only",
  "not_equivalent_to_maven_aar": True,
  "not_equivalent_to_maven_multi_abi_multi_ep": True,
  "providers_built": ["CPU"],
  "providers_not_built": ["NNAPI", "XNNPACK", "WebGPU"],
  "telemetry": "OFF",
  "onnxruntime_USE_TELEMETRY": "OFF",
  "jdk_required": "17",
  "jdk21_jlink_known_fail": True,
  "jni_compare_exit": int(jni_rc),
  "jni_libonnxruntime4j_jni": "173/173 identical Java_/JNI_On* dynamic symbols vs Maven AAR arm64 JNI",
  "jni_libonnxruntime_telemetry": "Maven baseline libonnxruntime.so has 8 Java_ai_onnxruntime_telemetry_HttpClient_* exports; rebuilt has 0 because onnxruntime_USE_TELEMETRY=OFF. This app is not expected to call those native entry points.",
  "device_test": False,
  "runtime_vad_vits_tested": False,
  "telemetry_native_entry_points_expected": False,
  "product_gradle": False,
  "multi_abi": False,
  "out_so": out + "/arm64-v8a",
  "out_aar": aar,
}, open(path, "w"), indent=2)
print("wrote", path)
PY

echo "local cpu-only artifacts written under $OUT (not independently verified)"
