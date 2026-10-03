#!/usr/bin/env python3
"""LOAD + official GNU_RELRO end alignment. Same formula as tools/xiaohei-release / libomp check."""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
from pathlib import Path

PAGE = 16384


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def elf_alignment(data: bytes) -> dict:
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected little-endian ELF64")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size < 56 or not count or offset + size * count > len(data):
        raise ValueError("Invalid ELF program header table")
    loads, relro, headers = [], [], []
    for i in range(count):
        kind, flags, off, va, _, filesz, memsz, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset + i * size
        )
        headers.append((kind, flags, off, va, filesz, memsz))
        if kind == 1:
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
                    "aligned_16kb": alignment >= PAGE
                    and alignment & (alignment - 1) == 0
                    and off % PAGE == va % PAGE,
                }
            )
        elif kind == 0x6474E552:
            relro.append(
                {
                    "address": va,
                    "memory_size": memsz,
                    "end_mod_16kb": (va + memsz) % PAGE,
                }
            )
    if not loads:
        raise ValueError("ELF has no LOAD segment")
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
        "load_16kb_pass": all(item["aligned_16kb"] for item in loads),
        "relro_end_16kb_pass": bool(relro) and all(item["end_mod_16kb"] == 0 for item in relro),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--so", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--expect-soname", default="")
    args = parser.parse_args()
    data = args.so.read_bytes()
    alignment = elf_alignment(data)
    soname_ok = True
    if args.expect_soname:
        soname_ok = alignment["soname"] == args.expect_soname
    payload = {
        "so": str(args.so),
        "bytes": len(data),
        "sha256": sha256(args.so),
        "alignment": alignment,
        "soname_ok": soname_ok,
        "pass": alignment["load_16kb_pass"]
        and alignment["relro_end_16kb_pass"]
        and soname_ok,
        "independent_verification": False,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"so": args.so.name, "pass": payload["pass"], "out": str(args.out)}))
    return 0 if payload["pass"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
