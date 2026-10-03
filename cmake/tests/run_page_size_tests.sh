#!/bin/sh
# Behavioral check for cmake/xiaohei_page_size.cmake.
# 1) Independent /tmp CMake project: real target LINK_OPTIONS (not source grep).
# 2) NDK r27 link of a tiny .so + llvm-readelf LOAD + GNU_RELRO end.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
HELPER="$ROOT/cmake/xiaohei_page_size.cmake"
TEST_SRC="$ROOT/cmake/tests/page_size"
STAMP=$(date +%Y%m%dT%H%M%S)
WORKDIR="/tmp/xiaohei-page-size-test-$$-${STAMP}"
mkdir -p "$WORKDIR"

fail() {
    echo "FAIL: $*" >&2
    echo "workdir=$WORKDIR" >&2
    exit 1
}

[ -f "$HELPER" ] || fail "helper missing: $HELPER"
[ -f "$TEST_SRC/CMakeLists.txt" ] || fail "test cmake missing"
command -v cmake >/dev/null 2>&1 || fail "cmake not on PATH"
command -v python3 >/dev/null 2>&1 || fail "python3 not on PATH"

echo "== host configure LINK_OPTIONS =="
HOST_BUILD="$WORKDIR/host"
cmake -S "$TEST_SRC" -B "$HOST_BUILD" \
    -DXIAOHEI_PAGE_SIZE_CMAKE="$HELPER" \
    >"$WORKDIR/host-cmake.log" 2>&1 || {
    cat "$WORKDIR/host-cmake.log" >&2
    fail "host cmake configure"
}
grep -q '^LlamaWrapper|ok|' "$HOST_BUILD/observed_link_options.txt" || fail "wrapper flags missing"
grep -q '^ggml|ok|' "$HOST_BUILD/observed_link_options.txt" || fail "ggml flags missing"
grep -q '^ggml_cpu|ok|' "$HOST_BUILD/observed_link_options.txt" || fail "ggml_cpu flags missing"
grep -q '^already_max|ok|' "$HOST_BUILD/observed_link_options.txt" || fail "already_max flags missing"
grep -q '^ggml_static|skipped|' "$HOST_BUILD/observed_link_options.txt" || fail "static not skipped"
grep -q '^prebuilt_relro|skipped|' "$HOST_BUILD/observed_link_options.txt" || fail "imported not skipped"
echo "host LINK_OPTIONS dump:"
cat "$HOST_BUILD/observed_link_options.txt"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
NDK=""
for cand in "$SDK"/ndk/27 "$SDK"/ndk/27.*; do
    if [ -f "$cand/build/cmake/android.toolchain.cmake" ]; then
        NDK=$cand
    fi
done
[ -n "$NDK" ] || fail "NDK r27 toolchain not found under $SDK/ndk"

PREBUILT=""
for p in "$NDK"/toolchains/llvm/prebuilt/*; do
    if [ -x "$p/bin/llvm-readelf" ]; then
        PREBUILT=$p
        break
    fi
done
[ -n "$PREBUILT" ] || fail "llvm-readelf not found in $NDK"
READELF="$PREBUILT/bin/llvm-readelf"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

echo "== NDK cmake link + readelf (NDK=$(basename "$NDK")) =="
NDK_BUILD="$WORKDIR/ndk"
cmake -S "$TEST_SRC" -B "$NDK_BUILD" \
    -DXIAOHEI_PAGE_SIZE_CMAKE="$HELPER" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_NDK="$NDK" \
    >"$WORKDIR/ndk-cmake.log" 2>&1 || {
    cat "$WORKDIR/ndk-cmake.log" >&2
    fail "ndk cmake configure"
}
cmake --build "$NDK_BUILD" --target LlamaWrapper ggml ggml_cpu already_max interface_only debug_only \
    >"$WORKDIR/ndk-build.log" 2>&1 || {
    cat "$WORKDIR/ndk-build.log" >&2
    fail "ndk cmake build"
}

python3 - "$READELF" "$NDK_BUILD" <<'PY'
import glob, os, subprocess, sys

readelf, build = sys.argv[1], sys.argv[2]
page = 16384
sos = []
for pattern in (
    "**/libLlamaWrapper.so",
    "**/libggml.so",
    "**/libggml_cpu.so",
    "**/libalready_max.so",
    "**/libinterface_only.so",
    "**/libdebug_only.so",
):
    sos.extend(glob.glob(os.path.join(build, pattern), recursive=True))
sos = sorted(set(sos))
if len(sos) != 6:
    sys.stderr.write("expected 6 shared objects, got %s\n" % sos)
    sys.exit(1)

def parse_int(token):
    token = token.strip(",")
    if token.startswith("0x") or token.startswith("0X"):
        return int(token, 16)
    if token.startswith("2**"):
        return 1 << int(token[3:])
    return int(token, 0)

failed = []
for so in sos:
    out = subprocess.check_output([readelf, "-lW", so], text=True)
    loads = []
    relros = []
    for line in out.splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "LOAD":
            loads.append(parse_int(parts[-1]))
        elif parts[0] == "GNU_RELRO":
            virt = parse_int(parts[2])
            memsz = parse_int(parts[5])
            relros.append((virt, memsz, (virt + memsz) % page))
    load_ok = bool(loads) and all(a >= page for a in loads)
    relro_ok = all(rem == 0 for _, _, rem in relros)
    status = "LOAD+RELRO ok" if load_ok and relro_ok else "FAIL"
    print("%s  loads=%s relro_remainders=%s  %s" % (
        os.path.basename(so),
        loads,
        [r[2] for r in relros],
        status,
    ))
    if not load_ok or not relro_ok:
        failed.append(so)
        sys.stderr.write(out + "\n")

if failed:
    sys.exit(1)
PY

echo "OK workdir=$WORKDIR"
