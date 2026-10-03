#!/usr/bin/env python3
"""Static PT_LOAD / GNU_RELRO check for the rebuilt arm64 GIF library."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from typing import Any

PAGE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_phdrs_bytes(data: bytes) -> list[dict[str, int]]:
    import struct

    if data[:4] != b"\x7fELF":
        raise ValueError("not ELF")
    if data[4] != 2:
        raise ValueError("not ELF64")
    endian = "<" if data[5] == 1 else ">"
    e_phoff = struct.unpack_from(endian + "Q", data, 32)[0]
    e_phentsize = struct.unpack_from(endian + "H", data, 54)[0]
    e_phnum = struct.unpack_from(endian + "H", data, 56)[0]
    phdrs = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_flags = struct.unpack_from(endian + "II", data, off)
        p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from(
            endian + "QQQQQQ", data, off + 8
        )
        phdrs.append(
            {
                "index": i,
                "p_type": p_type,
                "p_flags": p_flags,
                "p_offset": p_offset,
                "p_vaddr": p_vaddr,
                "p_paddr": p_paddr,
                "p_filesz": p_filesz,
                "p_memsz": p_memsz,
                "p_align": p_align,
            }
        )
    return phdrs


def hex0(n: int) -> str:
    return hex(n)


def load_rows(phdrs: list[dict[str, int]]) -> list[dict[str, Any]]:
    rows = []
    for p in phdrs:
        if p["p_type"] != PT_LOAD:
            continue
        align = p["p_align"]
        rows.append(
            {
                "type": "LOAD",
                "VirtAddr": hex0(p["p_vaddr"]),
                "MemSize": hex0(p["p_memsz"]),
                "Align": hex0(align),
                "VirtAddr_int": p["p_vaddr"],
                "MemSize_int": p["p_memsz"],
                "Align_int": align,
                "end": hex0(p["p_vaddr"] + p["p_memsz"]),
                "end_mod_16384": (p["p_vaddr"] + p["p_memsz"]) % PAGE,
                "align_ok": align >= PAGE,
            }
        )
    return rows


def relro_rows(phdrs: list[dict[str, int]]) -> list[dict[str, Any]]:
    rows = []
    for p in phdrs:
        if p["p_type"] != PT_GNU_RELRO:
            continue
        end = p["p_vaddr"] + p["p_memsz"]
        rows.append(
            {
                "type": "GNU_RELRO",
                "VirtAddr": hex0(p["p_vaddr"]),
                "MemSize": hex0(p["p_memsz"]),
                "Align": hex0(p["p_align"]),
                "VirtAddr_int": p["p_vaddr"],
                "MemSize_int": p["p_memsz"],
                "Align_int": p["p_align"],
                "end": hex0(end),
                "end_mod_16384": end % PAGE,
                "end_aligned": (end % PAGE) == 0,
            }
        )
    return rows


def parse_readelf_hex_rows(text: str) -> dict[str, list[dict[str, str]]]:
    loads = []
    relros = []
    for line in text.splitlines():
        stripped = line.strip()
        hexes = re.findall(r"0x[0-9a-fA-F]+", stripped)
        if stripped.startswith("LOAD") and len(hexes) >= 6:
            loads.append(
                {
                    "Offset": hexes[0],
                    "VirtAddr": hexes[1],
                    "PhysAddr": hexes[2],
                    "FileSiz": hexes[3],
                    "MemSize": hexes[4],
                    "Align": hexes[5],
                }
            )
        elif stripped.startswith("GNU_RELRO") and len(hexes) >= 6:
            vaddr = int(hexes[1], 16)
            memsz = int(hexes[4], 16)
            relros.append(
                {
                    "Offset": hexes[0],
                    "VirtAddr": hexes[1],
                    "PhysAddr": hexes[2],
                    "FileSiz": hexes[3],
                    "MemSize": hexes[4],
                    "Align": hexes[5],
                    "end": hex(vaddr + memsz),
                    "end_mod_16384": (vaddr + memsz) % PAGE,
                }
            )
    return {"LOAD": loads, "GNU_RELRO": relros}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--so", required=True)
    ap.add_argument("--readelf", required=True)
    ap.add_argument("--out-json", required=True)
    ap.add_argument("--meta-json", default="")
    args = ap.parse_args()

    so = os.path.abspath(args.so)
    data = open(so, "rb").read()
    phdrs = parse_phdrs_bytes(data)
    loads = load_rows(phdrs)
    relros = relro_rows(phdrs)

    proc = subprocess.run(
        [args.readelf, "-lW", so],
        check=False,
        capture_output=True,
        text=True,
    )
    parsed_text = parse_readelf_hex_rows(proc.stdout)
    load_ok = bool(loads) and all(x["align_ok"] for x in loads)
    relro_ok = bool(relros) and all(x["end_aligned"] for x in relros)
    static_pass = load_ok and relro_ok

    result: dict[str, Any] = {}
    if args.meta_json:
        with open(args.meta_json, encoding="utf-8") as f:
            result = json.load(f)

    result.update(
        {
            "artifact": so,
            "artifact_sha256": sha256_file(so),
            "artifact_bytes": len(data),
            "readelf": args.readelf,
            "readelf_rc": proc.returncode,
            "LOAD": [
                {
                    "VirtAddr": x["VirtAddr"],
                    "MemSize": x["MemSize"],
                    "Align": x["Align"],
                    "end": x["end"],
                    "end_mod_16384": x["end_mod_16384"],
                }
                for x in loads
            ],
            "GNU_RELRO": [
                {
                    "VirtAddr": x["VirtAddr"],
                    "MemSize": x["MemSize"],
                    "Align": x["Align"],
                    "end": x["end"],
                    "end_mod_16384": x["end_mod_16384"],
                }
                for x in relros
            ],
            "readelf_LOAD": parsed_text["LOAD"],
            "readelf_GNU_RELRO": parsed_text["GNU_RELRO"],
            "readelf_program_headers": proc.stdout,
            "load_align_ok": load_ok,
            "relro_end_aligned": relro_ok,
            "static_pass": static_pass,
            "success_rule": "all PT_LOAD p_align>=16384 and every GNU_RELRO (VirtAddr+MemSize) % 16384 == 0",
            "apk_integrated": False,
            "device_test": False,
            "whole_app_16kb_claim": False,
        }
    )
    if proc.stderr.strip():
        result["readelf_stderr"] = proc.stderr.strip()[:2000]
    if not static_pass:
        reasons = []
        if not loads:
            reasons.append("no PT_LOAD")
        elif not load_ok:
            reasons.append(
                "PT_LOAD Align < 16384: "
                + ", ".join(x["Align"] for x in loads if not x["align_ok"])
            )
        if not relros:
            reasons.append("no GNU_RELRO")
        elif not relro_ok:
            reasons.append(
                "GNU_RELRO end not 16K aligned: "
                + ", ".join(
                    f"end={x['end']} mod={x['end_mod_16384']}"
                    for x in relros
                    if not x["end_aligned"]
                )
            )
        result["static_fail_reason"] = "; ".join(reasons)

    os.makedirs(os.path.dirname(os.path.abspath(args.out_json)), exist_ok=True)
    with open(args.out_json, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, sort_keys=False)
        f.write("\n")
    print(json.dumps({"static_pass": static_pass, "out": args.out_json}, indent=2))
    return 0 if static_pass else 2


if __name__ == "__main__":
    sys.exit(main())
