#!/usr/bin/env python3
"""LOAD + official GNU_RELRO end alignment, plus JNI/NEEDED compare."""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import subprocess
from pathlib import Path
from typing import Any

PAGE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552
EXPECTED_SONAME = "libandroidx.graphics.path.so"
EXPECTED_NEEDED = ["libc.so", "libdl.so", "libm.so"]
EXPECTED_DEFINED = ["JNI_OnLoad@@LIBANDROIDX.GRAPHICS.PATH"]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_phdrs(data: bytes) -> list[dict[str, int]]:
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected little-endian ELF64")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size < 56 or not count or offset + size * count > len(data):
        raise ValueError("Invalid ELF program header table")
    phdrs = []
    for i in range(count):
        kind, flags, off, va, _, filesz, memsz, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset + i * size
        )
        phdrs.append(
            {
                "p_type": kind,
                "p_flags": flags,
                "p_offset": off,
                "p_vaddr": va,
                "p_filesz": filesz,
                "p_memsz": memsz,
                "p_align": alignment,
            }
        )
    return phdrs


def elf_alignment(data: bytes) -> dict[str, Any]:
    phdrs = parse_phdrs(data)
    loads, relro, headers = [], [], []
    for p in phdrs:
        kind, flags, off, va, filesz, memsz, alignment = (
            p["p_type"],
            p["p_flags"],
            p["p_offset"],
            p["p_vaddr"],
            p["p_filesz"],
            p["p_memsz"],
            p["p_align"],
        )
        headers.append((kind, flags, off, va, filesz, memsz))
        if kind == PT_LOAD:
            if filesz > memsz or off + filesz > len(data):
                raise ValueError("Invalid LOAD range")
            loads.append(
                {
                    "flags": flags,
                    "address": va,
                    "memory_size": memsz,
                    "alignment": alignment,
                    "offset_mod_16kb": off % PAGE,
                    "address_mod_16kb": va % PAGE,
                    "end": hex(va + memsz),
                    "end_mod_16384": (va + memsz) % PAGE,
                    "aligned_16kb": alignment >= PAGE
                    and alignment & (alignment - 1) == 0
                    and off % PAGE == va % PAGE,
                }
            )
        elif kind == PT_GNU_RELRO:
            relro.append(
                {
                    "address": va,
                    "memory_size": memsz,
                    "end": hex(va + memsz),
                    "end_mod_16kb": (va + memsz) % PAGE,
                    "end_aligned": (va + memsz) % PAGE == 0,
                }
            )
    needed_offsets, string_addr, string_size, soname_off = [], None, None, None
    for kind, _, off, _, filesz, _ in headers:
        if kind != 2:
            continue
        if off + filesz > len(data) or filesz % 16:
            raise ValueError("Invalid DYNAMIC range")
        for pos in range(off, off + filesz, 16):
            tag, value = struct.unpack_from("<qQ", data, pos)
            if tag == 0:
                break
            if tag == 1:
                needed_offsets.append(value)
            elif tag == 5:
                string_addr = value
            elif tag == 10:
                string_size = value
            elif tag == 14:
                soname_off = value
    needed, soname = [], None
    if needed_offsets or soname_off is not None:
        if string_addr is None or string_size is None:
            raise ValueError("Missing dynamic string table")
        string_offset = next(
            (
                off + string_addr - va
                for kind, _, off, va, filesz, _ in headers
                if kind == 1 and va <= string_addr and string_addr + string_size <= va + filesz
            ),
            None,
        )
        if string_offset is None:
            raise ValueError("Unmapped dynamic string table")
        table = data[string_offset : string_offset + string_size]
        for pos in needed_offsets:
            if pos >= len(table) or b"\0" not in table[pos:]:
                raise ValueError("Invalid DT_NEEDED string")
            needed.append(table[pos:].split(b"\0", 1)[0].decode("utf-8"))
        if soname_off is not None:
            soname = table[soname_off:].split(b"\0", 1)[0].decode("utf-8")
    return {
        "loads": loads,
        "relro": relro,
        "needed": sorted(needed),
        "soname": soname,
        "load_16kb_pass": bool(loads) and all(item["aligned_16kb"] for item in loads),
        "relro_end_16kb_pass": bool(relro) and all(item["end_aligned"] for item in relro),
    }


def parse_defined_dynsyms(nm_text: str) -> list[str]:
    names = []
    for line in nm_text.splitlines():
        if not line.strip():
            continue
        token = line.rsplit(None, 1)[-1]
        names.append(token)
    return sorted(names)


def parse_undefined_dynsyms(nm_text: str) -> list[str]:
    names = []
    for line in nm_text.splitlines():
        token = line.rsplit(None, 1)[-1]
        if token and token != "U":
            names.append(token)
    return sorted(names)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--so", required=True, type=Path)
    parser.add_argument("--orig-so", type=Path)
    parser.add_argument("--nm-defined", type=Path)
    parser.add_argument("--nm-defined-orig", type=Path)
    parser.add_argument("--nm-undefined", type=Path)
    parser.add_argument("--nm-undefined-orig", type=Path)
    parser.add_argument("--readelf", type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--meta-json", type=Path)
    args = parser.parse_args()

    data = args.so.read_bytes()
    alignment = elf_alignment(data)
    defined = (
        parse_defined_dynsyms(args.nm_defined.read_text(encoding="utf-8", errors="replace"))
        if args.nm_defined
        else []
    )
    orig_defined = (
        parse_defined_dynsyms(args.nm_defined_orig.read_text(encoding="utf-8", errors="replace"))
        if args.nm_defined_orig
        else []
    )
    undefined = (
        parse_undefined_dynsyms(args.nm_undefined.read_text(encoding="utf-8", errors="replace"))
        if args.nm_undefined
        else []
    )
    orig_undefined = (
        parse_undefined_dynsyms(args.nm_undefined_orig.read_text(encoding="utf-8", errors="replace"))
        if args.nm_undefined_orig
        else []
    )

    jni_match = defined == EXPECTED_DEFINED
    if orig_defined:
        jni_match = jni_match and defined == orig_defined
    undef_match = True
    if orig_undefined:
        undef_match = undefined == orig_undefined

    needed_ok = alignment["needed"] == EXPECTED_NEEDED
    soname_ok = alignment["soname"] == EXPECTED_SONAME
    static_pass = (
        alignment["load_16kb_pass"]
        and alignment["relro_end_16kb_pass"]
        and needed_ok
        and soname_ok
        and jni_match
        and undef_match
        and defined == EXPECTED_DEFINED
    )

    payload: dict[str, Any] = {}
    if args.meta_json and args.meta_json.is_file():
        payload = json.loads(args.meta_json.read_text(encoding="utf-8"))

    orig_sha = sha256_file(args.orig_so) if args.orig_so and args.orig_so.is_file() else None
    payload.update(
        {
            "so": str(args.so),
            "bytes": len(data),
            "sha256": sha256_file(args.so),
            "orig_so": str(args.orig_so) if args.orig_so else None,
            "orig_sha256": orig_sha,
            "alignment": alignment,
            "defined_dynsyms": defined,
            "orig_defined_dynsyms": orig_defined,
            "undefined_dynsyms": undefined,
            "orig_undefined_dynsyms": orig_undefined,
            "jni_defined_match": jni_match,
            "undefined_match": undef_match,
            "needed_ok": needed_ok,
            "soname_ok": soname_ok,
            "load_align_ok": alignment["load_16kb_pass"],
            "relro_end_aligned": alignment["relro_end_16kb_pass"],
            "static_pass": static_pass,
            "success_rule": (
                "all PT_LOAD p_align>=16384 and p_offset%16K==p_vaddr%16K; "
                "every GNU_RELRO (VirtAddr+MemSize)%16384==0; "
                "SONAME libandroidx.graphics.path.so; NEEDED libc/libdl/libm; "
                "defined dynsym only JNI_OnLoad@@LIBANDROIDX.GRAPHICS.PATH"
            ),
            "apk_integrated": False,
            "device_test": False,
            "whole_app_16kb_claim": False,
        }
    )
    if args.readelf and args.readelf.is_file():
        proc = subprocess.run(
            [str(args.readelf), "-lW", str(args.so)],
            check=False,
            capture_output=True,
            text=True,
        )
        payload["readelf_rc"] = proc.returncode
        payload["readelf_program_headers"] = proc.stdout

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    if not static_pass:
        print(json.dumps({"static_pass": False, "out": str(args.out)}, indent=2))
        return 2
    print(f"graphics-path elf pass sha256={payload['sha256']} bytes={payload['bytes']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
