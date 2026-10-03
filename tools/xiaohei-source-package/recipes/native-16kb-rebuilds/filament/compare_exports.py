#!/usr/bin/env python3
"""Compare defined dynamic exports and JNI names vs original AAR .so files."""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess

VER_SUFFIX = re.compile(r"@@.*$")


def defined_dynsyms(nm: str, so: str) -> list[str]:
    proc = subprocess.run(
        [nm, "-D", "--defined-only", so],
        check=True,
        capture_output=True,
        text=True,
    )
    names = []
    for line in proc.stdout.splitlines():
        parts = line.split()
        if len(parts) < 3:
            continue
        names.append(VER_SUFFIX.sub("", parts[-1]))
    return sorted(set(names))


def jni_names(names: list[str]) -> list[str]:
    return [n for n in names if n.startswith("Java_") or n.startswith("JNI_")]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--nm", required=True)
    ap.add_argument("--baseline-so", action="append", required=True)
    ap.add_argument("--rebuilt-so", action="append", required=True)
    ap.add_argument("--out-json", required=True)
    args = ap.parse_args()
    if len(args.baseline_so) != len(args.rebuilt_so):
        raise SystemExit("baseline/rebuilt so counts must match")

    libs = []
    jni_all_equal = True
    all_equal = True
    for base, rebuilt in zip(args.baseline_so, args.rebuilt_so):
        b = defined_dynsyms(args.nm, base)
        r = defined_dynsyms(args.nm, rebuilt)
        bj, rj = jni_names(b), jni_names(r)
        jni_missing = sorted(set(bj) - set(rj))
        jni_extra = sorted(set(rj) - set(bj))
        missing = sorted(set(b) - set(r))
        extra = sorted(set(r) - set(b))
        jni_equal = not jni_missing and not jni_extra
        equal = not missing and not extra
        jni_all_equal = jni_all_equal and jni_equal
        all_equal = all_equal and equal
        libs.append(
            {
                "baseline": os.path.abspath(base),
                "rebuilt": os.path.abspath(rebuilt),
                "baseline_defined_count": len(b),
                "rebuilt_defined_count": len(r),
                "jni_baseline_count": len(bj),
                "jni_rebuilt_count": len(rj),
                "jni_equal": jni_equal,
                "defined_equal": equal,
                "jni_missing_in_rebuilt": jni_missing,
                "jni_extra_in_rebuilt": jni_extra,
                "defined_missing_in_rebuilt": missing,
                "defined_extra_in_rebuilt": extra,
                "jni_baseline": bj,
                "jni_rebuilt": rj,
            }
        )
    payload = {
        "libraries": libs,
        "jni_exports_equal": jni_all_equal,
        "defined_dynsyms_equal": all_equal,
        "note": (
            "JNI/API check is Java_/JNI_* defined dynsyms. defined_dynsyms includes "
            "all --defined-only dynamic symbols (version-script filtered)."
        ),
        "independent_verification": False,
        "runtime_gltf_tested": False,
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out_json)) or ".", exist_ok=True)
    with open(args.out_json, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    print(json.dumps({"jni_exports_equal": jni_all_equal, "defined_dynsyms_equal": all_equal, "out": args.out_json}))
    return 0 if jni_all_equal else 2


if __name__ == "__main__":
    raise SystemExit(main())
