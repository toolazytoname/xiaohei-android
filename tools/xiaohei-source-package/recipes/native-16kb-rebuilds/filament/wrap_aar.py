#!/usr/bin/env python3
"""Replace only jni/arm64-v8a/<so> in an official Filament AAR. Keep classes.jar and other ABIs."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import zipfile


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def copy_zipinfo_meta(src: zipfile.ZipInfo, name: str, data: bytes) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(filename=name, date_time=src.date_time)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = src.external_attr
    info.create_system = src.create_system
    info.file_size = len(data)
    return info


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src-aar", required=True)
    ap.add_argument("--replace-so", required=True, help="local rebuilt .so")
    ap.add_argument("--aar-member", required=True, help="zip member, e.g. jni/arm64-v8a/libfilament-jni.so")
    ap.add_argument("--out-aar", required=True)
    ap.add_argument("--out-json", required=True)
    args = ap.parse_args()

    so_data = open(args.replace_so, "rb").read()
    replaced = []
    kept = []
    os.makedirs(os.path.dirname(os.path.abspath(args.out_aar)) or ".", exist_ok=True)
    found = False
    with zipfile.ZipFile(args.src_aar, "r") as zin, zipfile.ZipFile(
        args.out_aar, "w", compression=zipfile.ZIP_DEFLATED
    ) as zout:
        for info in zin.infolist():
            name = info.filename
            if name == args.aar_member:
                zout.writestr(copy_zipinfo_meta(info, name, so_data), so_data)
                replaced.append(
                    {
                        "name": name,
                        "bytes": len(so_data),
                        "sha256": hashlib.sha256(so_data).hexdigest(),
                    }
                )
                found = True
                continue
            data = zin.read(name)
            zout.writestr(info, data)
            kept.append(name)
    if not found:
        raise SystemExit(f"missing member {args.aar_member} in {args.src_aar}")

    with zipfile.ZipFile(args.src_aar) as z:
        orig_classes = hashlib.sha256(z.read("classes.jar")).hexdigest()
        orig_manifest = hashlib.sha256(z.read("AndroidManifest.xml")).hexdigest()
    with zipfile.ZipFile(args.out_aar) as z:
        new_classes = hashlib.sha256(z.read("classes.jar")).hexdigest()
        new_manifest = hashlib.sha256(z.read("AndroidManifest.xml")).hexdigest()

    payload = {
        "src_aar": os.path.abspath(args.src_aar),
        "out_aar": os.path.abspath(args.out_aar),
        "out_aar_sha256": sha256_file(args.out_aar),
        "out_aar_bytes": os.path.getsize(args.out_aar),
        "classes_jar_sha256_original": orig_classes,
        "classes_jar_sha256_new": new_classes,
        "classes_jar_unchanged": orig_classes == new_classes,
        "android_manifest_unchanged": orig_manifest == new_manifest,
        "replaced_jni": replaced,
        "kept_member_count": len(kept),
        "note": "Only arm64-v8a JNI .so replaced. Other ABI .so files remain official 1.69.2.",
        "independent_verification": False,
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out_json)) or ".", exist_ok=True)
    with open(args.out_json, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    print(json.dumps({"classes_jar_unchanged": payload["classes_jar_unchanged"], "out": args.out_json}))
    return 0 if payload["classes_jar_unchanged"] and payload["android_manifest_unchanged"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
