#!/usr/bin/env python3
"""Build a local LGPL-3.0 corresponding-source bundle for the common store APK.

Stdlib only. No network. Writes under this directory's out/source-bundle/<date>/.

APK metadata must come from an explicitly selected existing APK file. This
generator records the input hash and a source-snapshot fingerprint. It does not
treat historical hashes, hardcoded SDK, or hardcoded signer CN as verified, and
it does not claim strict APK↔source correspondence unless a caller-supplied
fingerprint matches the current snapshot.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import zipfile
from datetime import date
from pathlib import Path
from typing import Mapping

COMMIT = "4faa5cd2ae0b5ee2ffa94f21d6e43c5ca011f84a"
SHORT = COMMIT[:8]
ARCHIVE_PREFIX = f"operit-{SHORT}/"
ARCHIVE_NAME = f"operit-{SHORT}.tar.gz"
TERMINAL_COMMIT = "e4442bc6a047b6165bf59103721ad143149c620d"
TERMINAL_ARCHIVE_NAME = f"terminal-{TERMINAL_COMMIT[:8]}.tar.gz"

# Conventional location only. Never hashed or claimed unless passed as --apk.
CONVENTIONAL_APK_REL = (
    "integrations/xiaohei-common-base/app/build/outputs/apk/"
    "commonRelease/app-common-release.apk"
)

# Former generator defaults. Must not be used as metadata when no APK is selected.
RETIRED_DEFAULT_APK_HASHES = frozenset(
    {
        "cef6799be522f71a8a7ab42c3e85533e8fa721669e079fb6ebeb74b8b18af484",
    }
)

CORRESPONDENCE_PENDING = "待验证"
CORRESPONDENCE_STRICT = "strict"

ONNX_COMMIT = "2e2543fbe9fae542f921d47a72d21d5a4ef0b710"
ONNX_TAG = "v1.29.0"
ONNX_RECIPE_AAR_SHA256 = (
    "263f1ea768438829ee145892aa65f8f3ed7cc5d857438cf40a0bf753c00369fc"
)

SOURCE_EXTS = {
    ".kt",
    ".kts",
    ".java",
    ".xml",
    ".pro",
    ".gradle",
    ".cpp",
    ".cc",
    ".cxx",
    ".c",
    ".h",
    ".hpp",
    ".hxx",
    ".cmake",
    ".md",
    ".txt",
    ".json",
    ".properties",
    ".patch",
    ".toml",
    ".aidl",
    ".inc",
}
SOURCE_NAMES = {
    "cmakelists.txt",
    "license",
    "notice",
    "readme",
    "androidmanifest.xml",
}
SKIP_EXTS = {
    ".aar",
    ".so",
    ".apk",
    ".zip",
    ".jar",
    ".onnx",
    ".bin",
    ".wav",
    ".mp3",
    ".tflite",
    ".gguf",
    ".pt",
    ".pth",
    ".pkl",
    ".tar",
    ".gz",
    ".xz",
    ".7z",
    ".log",
    ".o",
    ".obj",
    ".class",
    ".dex",
    ".model",
}

# Directory components never copied from a recipe tree, even if listed.
RECIPE_DENY_DIR_COMPONENTS = frozenset(
    {
        "build",
        "source",
        "cache",
        "cache-aar",
        "private",
        "logs",
        ".gradle",
        "downloads",
        "gradle-home",
    }
)
RECIPE_DENY_SUFFIXES = frozenset(
    {
        ".so",
        ".aar",
        ".apk",
        ".jar",
        ".zip",
        ".jks",
        ".keystore",
        ".bin",
        ".onnx",
        ".log",
    }
)
RECIPE_DENY_NAMES = frozenset(
    {
        "local.properties",
        ".env",
        "credentials.json",
        "keystore.properties",
    }
)

BUILD_INPUT_RECIPES = [
    {
        "path": "app/libs/ffmpeg-kit-local.aar",
        "recipe": "ffmpeg-build-config.json",
        "copy_whitelist": ["ffmpeg-build-config.json"],
        "note": "Gitignored local AAR. SHA256 in ffmpeg-build-config.json (artifact_sha256). 37 license members listed there. commonRelease also excludes bundled FFmpeg JNI from the APK.",
    },
    {
        "path": "app/libs/android-gif-drawable-1.2.28-16kb.aar",
        "recipe": "gif-16k-rebuild/README.md",
        "recipe_root": "gif-16k-rebuild",
        "copy_whitelist": ["README.md", "build.sh", "check_elf.py", "LICENSE"],
        "note": "Gitignored. Rebuild arm64 .so with gif-16k-rebuild/build.sh and check_elf.py; wrap with upstream 1.2.28 Java AAR. Coordinate pl.droidsonroids.gif:android-gif-drawable:1.2.28 is excluded from Maven in app/build.gradle.kts.",
    },
    {
        "path": "app/libs/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar",
        "recipe": "onnx-16kb-rebuild/README.md",
        "recipe_root": "onnx-16kb-rebuild",
        "commit": ONNX_COMMIT,
        "tag": ONNX_TAG,
        "license": (
            "MIT (ONNX Runtime). Source/FetchContent dependency licenses are not "
            "a complete transitive audit; MIT is not a claim that all deps are verified."
        ),
        "cpu_only": True,
        "telemetry": "OFF",
        "copy_whitelist": [
            "README.md",
            "build.sh",
            "evidence/status.json",
            "evidence/providers.json",
        ],
        "note": (
            f"Gitignored local AAR. Pin {ONNX_TAG} commit {ONNX_COMMIT}. "
            "CPU-only; NNAPI/XNNPACK/WebGPU OFF; onnxruntime_USE_TELEMETRY=OFF. "
            f"Recipe AAR SHA256 {ONNX_RECIPE_AAR_SHA256}. "
            "Not equivalent to Maven multi-ABI/multi-EP. Copy recipe via whitelist only "
            "(no build/source/cache/private/logs)."
        ),
    },
    {'path': 'app/libs/graphics-path-1.0.1-16kb-arm64.aar', 'recipe': 'native-16kb-rebuilds/graphics-path/README.md', 'recipe_root': 'native-16kb-rebuilds/graphics-path', 'copy_whitelist': ['README.md', 'build.sh', 'check_elf.py', 'LICENSE'], 'note': 'Local arm64 rebuild/relink. Recipe included, clean rebuild and full licensing review still pending.'},
    {'path': 'app/libs/filament-android-1.69.2-arm64-relro.aar', 'recipe': 'native-16kb-rebuilds/filament/README.md', 'recipe_root': 'native-16kb-rebuilds/filament', 'copy_whitelist': ['README.md', 'build.sh', 'check_elf.py', 'wrap_aar.py', 'compare_exports.py', 'no-version-script.patch', 'LICENSE-filament.txt', 'NOTICE'], 'note': 'Local arm64 rebuild/relink. Recipe included, clean rebuild and full licensing review still pending.'},
    {'path': 'app/libs/filament-utils-android-1.69.2-arm64-relro.aar', 'recipe': 'native-16kb-rebuilds/filament/README.md', 'recipe_root': 'native-16kb-rebuilds/filament', 'copy_whitelist': ['README.md', 'build.sh', 'check_elf.py', 'wrap_aar.py', 'compare_exports.py', 'no-version-script.patch', 'LICENSE-filament.txt', 'NOTICE'], 'note': 'Local arm64 rebuild/relink. Recipe included, clean rebuild and full licensing review still pending.'},
    {
        "path": "app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so",
        "recipe": "artifacts/manifest.json",
        "note": "Gitignored jniLibs member. tools/native_ripgrep/build_native_ripgrep.ps1 in upstream tree.",
    },
    {
        "path": "app/src/main/jniLibs/arm64-v8a/libc++_shared.so",
        "recipe": "artifacts/manifest.json",
        "note": "Gitignored jniLibs member from the same zip as liboperit_ripgrep.so.",
    },
    {
        "path": "app/src/main/assets/subpack/android.apk",
        "recipe": "artifacts/subpack-manifest.json",
        "note": "Gitignored. common/commonRelease drop this via CommonBaseAssetPolicy; still a build-tree input if assembling other variants.",
    },
    {
        "path": "app/src/main/assets/subpack/windows.zip",
        "recipe": "artifacts/subpack-manifest.json",
        "note": "Gitignored. Trimmed from common/commonRelease packaged assets.",
    },
    {
        "path": "app/src/main/assets/models/* (STT, generated at assemble)",
        "recipe": "stt-verification.json and stt-license-evidence/notice-packaging.json",
        "note": "Not stored in git. syncSttModelAssets downloads/verifies from app/config/stt-model-assets.properties.",
    },
]


class MissingApkError(Exception):
    pass


class RecipeCopyError(Exception):
    pass


def here() -> Path:
    return Path(__file__).resolve().parent


def workspace_root() -> Path:
    # lgpl-source-package -> operit-upstream-build -> integrations -> 1plus
    return here().parent.parent.parent


def upstream_build() -> Path:
    if os.environ.get("SOURCE_PACKAGE_RECIPES_ROOT"):
        return Path(os.environ["SOURCE_PACKAGE_RECIPES_ROOT"]).resolve()
    if (here() / "recipes").is_dir():
        return here() / "recipes"
    return here().parent


def common_base() -> Path:
    if os.environ.get("SOURCE_PACKAGE_REPO"):
        return Path(os.environ["SOURCE_PACKAGE_REPO"]).resolve()
    # Public copy lives at <android-repo>/tools/xiaohei-source-package/.
    public_repo = here().parent.parent
    if (public_repo / "app/build.gradle.kts").is_file():
        return public_repo
    return workspace_root() / "integrations" / "xiaohei-common-base"


def run_git(args: list[str], cwd: Path) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def is_source_file(path: Path) -> bool:
    suffix = path.suffix.lower()
    if suffix in SKIP_EXTS:
        return False
    if suffix in SOURCE_EXTS:
        return True
    return path.name.lower() in SOURCE_NAMES


def porcelain_untracked(repo: Path) -> list[str]:
    # -z preserves spaces, non-ASCII names and newlines. Directory summaries from
    # status -u previously omitted nested source files entirely.
    raw = run_git(["ls-files", "--others", "--exclude-standard", "-z"], cwd=repo).stdout
    return [os.fsdecode(path) for path in raw.split(b"\0") if path]


def binary_source_patch(repo: Path, base: str = COMMIT) -> bytes:
    # Plain git diff only says "Binary files differ" and cannot reconstruct icons.
    return run_git(["diff", "--binary", "--full-index", "--no-ext-diff",
                    "--ignore-submodules=dirty", base], cwd=repo).stdout


def write_sha256sums(bundle: Path) -> Path:
    rows: list[tuple[str, str]] = []
    for path in sorted(p for p in bundle.rglob("*") if p.is_file()):
        if path.name == "SHA256SUMS":
            continue
        rel = path.relative_to(bundle).as_posix()
        rows.append((sha256_file(path), rel))
    out = bundle / "SHA256SUMS"
    text = "".join(f"{digest}  {rel}\n" for digest, rel in rows)
    out.write_text(text, encoding="utf-8")
    return out


def count_patch_files(patch_text: str) -> int:
    files: set[str] = set()
    for line in patch_text.splitlines():
        if line.startswith("diff --git "):
            parts = line.split()
            # diff --git a/foo b/foo
            if len(parts) >= 3:
                files.add(parts[2][2:] if parts[2].startswith("a/") else parts[2])
    return len(files)


def tar_toplevel_names(archive: Path) -> set[str]:
    names: set[str] = set()
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar.getmembers():
            top = member.name.split("/", 1)[0]
            if top:
                names.add(top)
            if len(names) > 8:
                break
    return names


def normalize_recipe_rel(rel: str) -> str:
    posix = rel.replace("\\", "/").strip()
    while posix.startswith("./"):
        posix = posix[2:]
    return posix


def assert_whitelist_safe(rel: str) -> str:
    posix = normalize_recipe_rel(rel)
    if not posix or posix.startswith("/") or posix.startswith("../") or "/../" in f"/{posix}/":
        raise RecipeCopyError(f"unsafe recipe path: {rel}")
    parts = [p for p in posix.split("/") if p and p != "."]
    if not parts:
        raise RecipeCopyError(f"empty recipe path: {rel}")
    for component in parts[:-1]:
        if component in RECIPE_DENY_DIR_COMPONENTS:
            raise RecipeCopyError(
                f"denied directory component '{component}' in recipe path {rel}"
            )
    name = parts[-1]
    if name.lower() in RECIPE_DENY_NAMES or name in RECIPE_DENY_NAMES:
        raise RecipeCopyError(f"denied recipe filename: {rel}")
    suffix = Path(name).suffix.lower()
    if suffix in RECIPE_DENY_SUFFIXES:
        raise RecipeCopyError(f"denied recipe suffix {suffix} in {rel}")
    return "/".join(parts)


def copy_whitelisted_recipe(
    src_root: Path,
    dest_root: Path,
    whitelist: list[str],
) -> list[dict[str, object]]:
    """Copy only named recipe files. Never walk build/source/cache/private/logs."""
    if not whitelist:
        raise RecipeCopyError("empty whitelist")
    copied: list[dict[str, object]] = []
    for rel in whitelist:
        safe = assert_whitelist_safe(rel)
        src = src_root / safe
        if not src.is_file():
            raise RecipeCopyError(f"missing recipe file: {safe}")
        dest = dest_root / safe
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
        copied.append(
            {
                "path": safe,
                "bytes": dest.stat().st_size,
                "sha256": sha256_file(dest),
            }
        )
    return copied


def existing_build_inputs(repo: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    extra_keys = (
        "commit",
        "tag",
        "license",
        "cpu_only",
        "telemetry",
        "recipe_root",
        "copy_whitelist",
    )
    for spec in BUILD_INPUT_RECIPES:
        rel = spec["path"]
        row: dict[str, object] = {
            "path": rel,
            "disposition": "构建输入，见配方",
            "recipe": spec["recipe"],
            "note": spec["note"],
        }
        for key in extra_keys:
            if key in spec:
                row[key] = spec[key]
        if "*" in rel:
            row.update({"present": False, "bytes": None, "sha256": None})
            rows.append(row)
            continue
        path = repo / rel
        present = path.is_file()
        row.update(
            {
                "present": present,
                "bytes": path.stat().st_size if present else None,
                "sha256": sha256_file(path) if present else None,
            }
        )
        rows.append(row)
    return rows


def copy_recipe_specs(
    dest_recipes: Path,
    specs: list[dict[str, object]] | None = None,
    *,
    recipe_base: Path | None = None,
) -> list[dict[str, object]]:
    base = recipe_base if recipe_base is not None else upstream_build()
    copied_specs: list[dict[str, object]] = []
    for spec in specs if specs is not None else BUILD_INPUT_RECIPES:
        whitelist = spec.get("copy_whitelist")
        if not whitelist:
            continue
        recipe_root = spec.get("recipe_root")
        src_root = base / str(recipe_root) if recipe_root else base
        label = str(recipe_root or spec.get("recipe") or "recipe")
        dest_root = dest_recipes / label.replace("/", "_")
        dest_root.mkdir(parents=True, exist_ok=True)
        files = copy_whitelisted_recipe(src_root, dest_root, list(whitelist))
        copied_specs.append(
            {
                "recipe_root": recipe_root,
                "dest": dest_root.name,
                "files": files,
            }
        )
    return copied_specs


def copy_untracked(repo: Path, dest_root: Path) -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    included: list[dict[str, object]] = []
    skipped: list[dict[str, object]] = []
    for rel in porcelain_untracked(repo):
        src = repo / rel
        if src.is_dir():
            continue
        if src.is_symlink() or not src.is_file():
            skipped.append(
                {
                    "path": rel,
                    "reason": "not a regular file",
                    "disposition": "skipped",
                }
            )
            continue
        if "_verify_work" in src.parts or not is_source_file(src):
            skipped.append(
                {
                    "path": rel,
                    "bytes": src.stat().st_size,
                    "reason": f"non-source or binary asset ({src.suffix or src.name})",
                    "disposition": "构建输入，见配方" if src.suffix.lower() in SKIP_EXTS else "skipped",
                }
            )
            continue
        dest = dest_root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
        included.append(
            {
                "path": rel,
                "bytes": dest.stat().st_size,
                "sha256": sha256_file(dest),
            }
        )
    return included, skipped


def resolve_explicit_apk(
    argv: list[str] | None = None,
    env: Mapping[str, str] | None = None,
) -> Path:
    argv = list(argv) if argv is not None else sys.argv[1:]
    env = env if env is not None else os.environ
    apk: str | None = None
    if "--apk" in argv:
        index = argv.index("--apk")
        if index + 1 >= len(argv):
            raise MissingApkError("--apk requires a path")
        apk = argv[index + 1]
    elif env.get("SOURCE_PACKAGE_APK"):
        apk = env["SOURCE_PACKAGE_APK"]
    if not apk:
        raise MissingApkError(
            "explicit --apk or SOURCE_PACKAGE_APK is required; "
            f"refusing hardcoded APK hash defaults (conventional path {CONVENTIONAL_APK_REL} "
            "is not used unless selected)"
        )
    return Path(apk)


def inspect_apk_metadata(apk_path: Path, *, extras: bool = False) -> dict[str, object]:
    if not apk_path.is_file():
        raise MissingApkError(f"APK not found: {apk_path}")
    digest = sha256_file(apk_path)
    meta: dict[str, object] = {
        "path": str(apk_path),
        "sha256": digest,
        "bytes": apk_path.stat().st_size,
        "targetSdk": None,
        "targetSdk_status": "未检查",
        "targetSdk_basis": "not inspected; generator does not assume targetSdk 36",
        "signing": None,
        "signing_status": "未检查",
        "signing_basis": "not inspected; generator does not assume v2+v3 or a signer CN",
    }
    if extras:
        apply_apk_inspect_extras(meta, apk_path)
    return meta


def apply_apk_inspect_extras(meta: dict[str, object], apk_path: Path) -> None:
    sdk = try_aapt_target_sdk(apk_path)
    if sdk is not None:
        meta["targetSdk"] = sdk
        meta["targetSdk_status"] = "inspected"
        meta["targetSdk_basis"] = "aapt dump badging"
    signer = try_v1_cert_subject(apk_path)
    if signer is not None:
        meta["signing"] = signer
        meta["signing_status"] = "inspected-v1-cert-subject"
        meta["signing_basis"] = (
            "openssl pkcs7 on APK META-INF; v2/v3 signing is not claimed by this generator"
        )


def try_aapt_target_sdk(apk_path: Path) -> int | None:
    for binary in ("aapt", "aapt2"):
        try:
            proc = subprocess.run(
                [binary, "dump", "badging", str(apk_path)],
                check=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=30,
            )
        except (FileNotFoundError, subprocess.TimeoutExpired):
            continue
        if proc.returncode != 0:
            continue
        text = proc.stdout.decode("utf-8", "replace")
        for token in ("targetSdkVersion:'", "targetSdkVersion:"):
            if token not in text:
                continue
            rest = text.split(token, 1)[1]
            digits = []
            for char in rest:
                if char.isdigit():
                    digits.append(char)
                elif digits:
                    break
            if digits:
                return int("".join(digits))
    return None


def try_v1_cert_subject(apk_path: Path) -> str | None:
    try:
        with zipfile.ZipFile(apk_path) as zf:
            names = [
                name
                for name in zf.namelist()
                if name.upper().startswith("META-INF/")
                and name.upper().endswith((".RSA", ".DSA", ".EC"))
            ]
            if not names:
                return None
            cert_bytes = zf.read(names[0])
    except (zipfile.BadZipFile, KeyError, OSError):
        return None
    try:
        proc = subprocess.run(
            ["openssl", "pkcs7", "-inform", "DER", "-print_certs", "-noout"],
            input=cert_bytes,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=15,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None
    if proc.returncode != 0:
        return None
    for line in proc.stdout.decode("utf-8", "replace").splitlines():
        stripped = line.strip()
        if stripped.lower().startswith("subject"):
            return stripped
    return None


def source_snapshot_fingerprint(
    *,
    upstream_commit: str,
    head: str,
    patch_sha256: str,
    untracked_sha256s: list[tuple[str, str]],
) -> str:
    body = {
        "upstream_commit": upstream_commit,
        "head": head,
        "patch_sha256": patch_sha256,
        "untracked": untracked_sha256s,
    }
    blob = json.dumps(body, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode(
        "utf-8"
    )
    return hashlib.sha256(blob).hexdigest()


def correspondence_status(
    *,
    apk_sha256: str,
    current_fingerprint: str,
    claimed_fingerprint: str | None,
) -> str:
    if not apk_sha256 or not current_fingerprint:
        return CORRESPONDENCE_PENDING
    if claimed_fingerprint and claimed_fingerprint == current_fingerprint:
        return CORRESPONDENCE_STRICT
    return CORRESPONDENCE_PENDING


def untracked_fingerprint_pairs(
    untracked_included: list[dict[str, object]],
) -> list[tuple[str, str]]:
    pairs: list[tuple[str, str]] = []
    for item in untracked_included:
        path = item.get("path")
        digest = item.get("sha256")
        if isinstance(path, str) and isinstance(digest, str):
            pairs.append((path, digest))
    pairs.sort()
    return pairs


def build_correspondence_record(
    *,
    apk_path: Path,
    head: str,
    patch_bytes: bytes,
    untracked_included: list[dict[str, object]],
    claimed_fingerprint: str | None,
    extras: bool = False,
) -> dict[str, object]:
    meta = inspect_apk_metadata(apk_path, extras=extras)
    patch_sha256 = hashlib.sha256(patch_bytes).hexdigest()
    untracked_pairs = untracked_fingerprint_pairs(untracked_included)
    fingerprint = source_snapshot_fingerprint(
        upstream_commit=COMMIT,
        head=head,
        patch_sha256=patch_sha256,
        untracked_sha256s=untracked_pairs,
    )
    status = correspondence_status(
        apk_sha256=str(meta["sha256"]),
        current_fingerprint=fingerprint,
        claimed_fingerprint=claimed_fingerprint,
    )
    meta["correspondence"] = status
    meta["correspondence_note"] = (
        "strict only if --correspondence-fingerprint matches the current source snapshot; "
        "source changes make a previous APK 待验证"
    )
    snapshot = {
        "upstream_commit": COMMIT,
        "head": head,
        "patch_sha256": patch_sha256,
        "untracked_count": len(untracked_pairs),
        "fingerprint": fingerprint,
        "claimed_fingerprint": claimed_fingerprint,
    }
    return {"apk": meta, "source_snapshot": snapshot}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a local LGPL corresponding-source bundle from an explicit APK."
    )
    parser.add_argument(
        "--apk",
        help="Existing APK selected by the caller. Required unless SOURCE_PACKAGE_APK is set.",
    )
    parser.add_argument(
        "--correspondence-fingerprint",
        dest="correspondence_fingerprint",
        default=None,
        help="Source snapshot fingerprint recorded when this APK was built. Mismatch → 待验证.",
    )
    return parser.parse_args(argv)


def render_readme(
    *,
    bundle_date: str,
    patch_files: int,
    untracked_included: int,
    untracked_skipped: int,
    archive_rel: str,
    terminal_rel: str | None,
    apk: dict[str, object],
    snapshot: dict[str, object],
) -> str:
    evidence = [
        ("dependency-audit.md", "JDK/NDK/外部 zip 与 assemble 前置检查"),
        ("artifacts/manifest.json", "libs.zip / jniLibs.zip 成员与 SHA256"),
        ("artifacts/subpack-manifest.json", "subpack android.apk / windows.zip"),
        ("stt-verification.json", "STT 八个固定资产存在与校验"),
        ("stt-license-evidence/notice-packaging.json", "STT NOTICE 文本与 APK 内打包证据"),
        ("ffmpeg-build-config.json", "ffmpeg-kit-local.aar SHA256、configure、37 份 license 成员"),
        ("gif-16k-rebuild/README.md", "android-gif-drawable 1.2.28 arm64 16KB 重制"),
        ("gif-16k-rebuild/build.sh", "重制脚本"),
        ("gif-16k-rebuild/check_elf.py", "ELF PT_LOAD / GNU_RELRO 静态检查"),
        ("gif-16k-rebuild/out/SHA256SUMS", "重制 .so SHA256"),
        ("onnx-16kb-rebuild/README.md", "ONNX Runtime v1.29.0 CPU-only 16KB 本地 AAR 配方"),
        ("onnx-16kb-rebuild/evidence/status.json", "CPU-only、telemetry OFF、工件哈希"),
        ("ndk-offline.md", "NDK 27.0.12077973 离线展开"),
        ("toolchains/adoptium-ga-21-mac-aarch64.json", "Temurin 21.0.12.1+1 mac aarch64"),
    ]
    evidence_lines = "\n".join(
        f"- `integrations/operit-upstream-build/{path}` — {desc}"
        for path, desc in evidence
    )
    terminal_line = (
        f"- `{terminal_rel}` — `terminal/` 子模块对应源码（gitlink `{TERMINAL_COMMIT}`）；父仓 archive 不含 submodule 内容。\n"
        if terminal_rel
        else ""
    )
    apk_sha = apk.get("sha256")
    apk_bytes = apk.get("bytes")
    apk_path = apk.get("path")
    correspondence = apk.get("correspondence", CORRESPONDENCE_PENDING)
    sdk = apk.get("targetSdk")
    sdk_status = apk.get("targetSdk_status", "未检查")
    signing = apk.get("signing")
    signing_status = apk.get("signing_status", "未检查")
    fingerprint = snapshot.get("fingerprint")
    return f"""# Operit 共同商店版对应源码包（LGPL-3.0-only）

生成日期：{bundle_date}

本目录是 **LGPL-3.0-only** 对应源码履行包，仅供本地分发准备。不包含密钥、keystore、签名材料，也不是已发布的远端仓库。生成本包不等于源码已公开，也不等于许可履行完成。

对应二进制（用户明确选择的现有 APK，哈希为对该文件实测，不是历史常数）：

| 项 | 值 |
|---|---|
| APK | `{apk_path}` |
| 输入 SHA256 | `{apk_sha}` |
| 大小 | {apk_bytes} 字节 |
| targetSdk | {sdk}（{sdk_status}） |
| 签名 | {signing}（{signing_status}） |
| 上游 commit | `{COMMIT}`（v1.12.1） |
| 源码快照指纹 | `{fingerprint}` |
| APK↔源码对应 | **{correspondence}** |
| 共同版改动 | `integrations/xiaohei-common-base` 工作树，分支 `feat/common-base`，相对该 commit 的 diff + 未跟踪源码文件 |

许可证：仓库主体 **LGPL-3.0-only**（见 archive 内 `LICENSE`）。第三方组件见同级目录 `../NOTICE-third-party.md`。

精确复现 **已签名 APK 的 SHA256** 还需要同一把上传密钥；本包提供对应源码与构建输入配方，不提供密钥。未签名产物哈希会不同。源码变动后，旧 APK 不得称严格对应（标 {CORRESPONDENCE_PENDING}）。不得把本说明当成商店验收通过。

## 包结构

- `{archive_rel}` — `git archive` 在 `{COMMIT}` 上的上游源码，顶层目录 `{ARCHIVE_PREFIX.rstrip("/")}`。
{terminal_line}- `common-base.patch` — 相对 `{COMMIT}` 的已跟踪修改（`git diff --binary --full-index`，含图标二进制补丁，忽略脏 submodule 工作树）。本包统计涉及 **{patch_files}** 个路径。
- `untracked/` — porcelain 未跟踪、非忽略、非大资产的源码文件（.kt/.kts/.xml/.pro/.java/.cpp 等）。本包复制 **{untracked_included}** 个，跳过 **{untracked_skipped}** 个。
- `recipes/` — 构建输入配方的**白名单**副本（不含 build/source/cache/private/logs 或二进制）。
- `MANIFEST.json` — 未跟踪收录/跳过清单、输入 APK 哈希、源码快照指纹，以及 gitignored 构建输入（AAR/JNI/模型/subpack）记为「构建输入，见配方」。
- `SHA256SUMS` — 本目录除自身外全部文件的 SHA256。
- 本 `README.md`

`app/libs/*.aar`（`ffmpeg-kit-local.aar`、`android-gif-drawable-1.2.28-16kb.aar`、`onnxruntime-android-1.29.0-arm64-cpu-16kb.aar`）在上游 `.gitignore` 中，**不入本源码包**。获取/重制路径见下方配方。

## 如何从上游 commit + patch + 构建输入组装

工作区根：含同样相对布局的本地树。证据文件相对 `integrations/operit-upstream-build/`。

### 1. 展开上游源码并套用共同版改动

```bash
tar -xzf {archive_rel}
cd {ARCHIVE_PREFIX.rstrip("/")}
git apply --binary ../common-base.patch
cp -R ../untracked/. .
```

若使用 `{TERMINAL_ARCHIVE_NAME}`：

```bash
# 仍在 bundle 目录
tar -xzf {TERMINAL_ARCHIVE_NAME}
```

展开 terminal 后，从源码目录执行 `git -C terminal apply --check ../patches/terminal-16kb.patch`，
确认通过再 `git -C terminal apply ../patches/terminal-16kb.patch`。不要遗漏此本地链接改动。

`terminal` gitlink 固定为 `{TERMINAL_COMMIT}`。父仓 archive 只有 gitlink，没有 submodule 树。

### 2. 放置构建输入（不在本 tar 内）

| 输入 | 放置位置 | 配方 |
|---|---|---|
| FFmpegKit 本地 AAR | `app/libs/ffmpeg-kit-local.aar` | `ffmpeg-build-config.json`（`artifact_sha256`、license_members） |
| 16KB gif AAR | `app/libs/android-gif-drawable-1.2.28-16kb.aar` | `gif-16k-rebuild/`（`build.sh`、`check_elf.py`、`out/SHA256SUMS`） |
| ONNX Runtime 本地 AAR（CPU-only, telemetry OFF） | `app/libs/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar` | `onnx-16kb-rebuild/README.md`（commit `{ONNX_COMMIT}`；MIT 不等于传递依赖已核） |
| graphics-path / Filament / Filament utils 本地 AAR | `app/libs/` 对应文件名见 MANIFEST | `recipes/native-16kb-rebuilds_*` 白名单配方，未声称完整源码/重建验收 |
| `liboperit_ripgrep.so` 与 `libc++_shared.so` | `app/src/main/jniLibs/` | `artifacts/manifest.json` |
| STT 模型八文件 | assemble 时由 `syncSttModelAssets` 生成 | `stt-verification.json`、`app/config/stt-model-assets.properties`、`stt-license-evidence/` |
| subpack（商店 commonRelease 会裁掉） | `app/src/main/assets/subpack/` | `artifacts/subpack-manifest.json` |

MNN `kFdChunk` 最小补丁在 `cmake/patches/mnn-kfdchunk/`，以当前源码补丁为准，临时 `_verify_work` 不收入包。CMake 引脚：

- `OPERIT_SHERPA_NCNN_GIT_REF=c61e50d61e9fbed5972afa4d95bc560e168affe2`
- `OPERIT_NCNN_GIT_REF=713bd01928b350150ad5594218512b8669588fef`

### 3. 工具链

- JDK：Eclipse Temurin **21.0.12.1+1**（mac aarch64 记录于 `toolchains/adoptium-ga-21-mac-aarch64.json`）
- NDK：**27.0.12077973**（`ndk-offline.md`）；ONNX 本地重制另见配方中的 NDK 27.1.12297006 / JDK 17
- 构建系统事实与历史坑：`dependency-audit.md`

### 4. 构建商店变体

在源码树根（已 apply patch）：

```bash
./gradlew :app:assembleCommonRelease
```

产物名：`app/build/outputs/apk/commonRelease/app-common-release.apk`。

`commonRelease`：`applicationId` 后缀 `.common`，`debuggable=false`，`COMMON_BASE=true`，`COMMON_STORE=true`，签名取决于 `local.properties`（本包不提供密钥）。**不要**跑 `:app:assembleRelease` / `assembleNightly`（那些仍挂 Operit APK rotation signing）。

本包记录的输入 APK SHA256 为 `{apk_sha}`，对应状态为 **{correspondence}**。源码快照指纹 `{fingerprint}`。不得把未匹配指纹的旧 build 当成当前源码的严格对应。

## 证据文件（相对工作区）

{evidence_lines}

第三方 NOTICE 由 `make_notice.py` 写到 `lgpl-source-package/out/NOTICE-third-party.md`。

## 不在本包内

- 签名密钥 / `local.properties` / keystore
- gitignored `.aar` / `.so` / 模型权重 / subpack 二进制
- `onnx-16kb-rebuild` 的 `build/`、`source/`、`cache/`、`cache-aar/`、`logs/`、`private/`、重制 `.so`/`.aar`
- 远端仓库、Play 上传材料
- 把未经验收的语音/商店能力标为通过的任何声明
"""


def main(argv: list[str] | None = None, env: Mapping[str, str] | None = None) -> int:
    args = parse_args(argv)
    environ = env if env is not None else os.environ
    try:
        argv_for_apk = []
        if args.apk:
            argv_for_apk = ["--apk", args.apk]
        apk_path = resolve_explicit_apk(argv_for_apk, environ)
        inspect_apk_metadata(apk_path, extras=False)
    except MissingApkError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    repo = common_base()
    git_dir = repo / ".git"
    if not git_dir.exists():
        print(f"error: not a git repo: {repo}", file=sys.stderr)
        return 1

    head = run_git(["rev-parse", "HEAD"], cwd=repo).stdout.decode().strip()
    if head != COMMIT:
        print(
            f"warning: HEAD is {head}, expected {COMMIT}; archive still uses pinned commit",
            file=sys.stderr,
        )

    bundle_date = os.environ.get("SOURCE_BUNDLE_DATE", date.today().isoformat())
    bundle = here() / "out" / "source-bundle" / bundle_date
    if bundle.exists():
        print(f"error: output exists; select a new SOURCE_BUNDLE_DATE: {bundle}", file=sys.stderr)
        return 1
    bundle.mkdir(parents=True)

    archive_path = bundle / ARCHIVE_NAME
    print(f"git archive {COMMIT} -> {archive_path}")
    run_git(
        [
            "archive",
            f"--prefix={ARCHIVE_PREFIX}",
            "--format=tar.gz",
            f"--output={archive_path}",
            COMMIT,
        ],
        cwd=repo,
    )

    terminal_rel = None
    terminal_repo = repo / "terminal"
    if (terminal_repo / ".git").exists() or (terminal_repo / ".git").is_file():
        term_head = run_git(["rev-parse", "HEAD"], cwd=terminal_repo).stdout.decode().strip()
        if term_head == TERMINAL_COMMIT:
            term_archive = bundle / TERMINAL_ARCHIVE_NAME
            print(f"git archive terminal {TERMINAL_COMMIT} -> {term_archive}")
            run_git(
                [
                    "archive",
                    f"--prefix={ARCHIVE_PREFIX}terminal/",
                    "--format=tar.gz",
                    f"--output={term_archive}",
                    TERMINAL_COMMIT,
                ],
                cwd=terminal_repo,
            )
            terminal_rel = TERMINAL_ARCHIVE_NAME
        else:
            print(
                f"warning: terminal HEAD {term_head} != {TERMINAL_COMMIT}; skip terminal archive",
                file=sys.stderr,
            )

    patch_bytes = binary_source_patch(repo)
    patch_path = bundle / "common-base.patch"
    patch_path.write_bytes(patch_bytes)
    patch_text = patch_bytes.decode("utf-8", "surrogateescape")
    patch_files = count_patch_files(patch_text)

    untracked_root = bundle / "untracked"
    untracked_root.mkdir()
    included, skipped = copy_untracked(repo, untracked_root)
    if not any(untracked_root.iterdir()):
        (untracked_root / ".keep").write_text("", encoding="utf-8")

    try:
        recipe_copies = copy_recipe_specs(bundle / "recipes")
    except RecipeCopyError as exc:
        print(f"error: recipe copy failed: {exc}", file=sys.stderr)
        return 1

    try:
        correspondence = build_correspondence_record(
            apk_path=apk_path,
            head=head,
            patch_bytes=patch_bytes,
            untracked_included=included,
            claimed_fingerprint=args.correspondence_fingerprint,
            extras=True,
        )
    except MissingApkError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    apk_meta = correspondence["apk"]
    snapshot = correspondence["source_snapshot"]
    assert isinstance(apk_meta, dict)
    assert isinstance(snapshot, dict)

    build_inputs = existing_build_inputs(repo)
    manifest = {
        "date": bundle_date,
        "upstream_commit": COMMIT,
        "archive_prefix": ARCHIVE_PREFIX.rstrip("/"),
        "apk": apk_meta,
        "source_snapshot": snapshot,
        "license": "LGPL-3.0-only",
        "patch_files": patch_files,
        "untracked_included": included,
        "untracked_skipped": skipped,
        "ignored_build_inputs": build_inputs,
        "recipe_whitelist_copies": recipe_copies,
        "terminal_commit": TERMINAL_COMMIT,
        "terminal_archive": terminal_rel,
        "not_a_publication_or_license_fulfillment": True,
    }
    (bundle / "MANIFEST.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    readme = render_readme(
        bundle_date=bundle_date,
        patch_files=patch_files,
        untracked_included=len(included),
        untracked_skipped=len(skipped),
        archive_rel=ARCHIVE_NAME,
        terminal_rel=terminal_rel,
        apk=apk_meta,
        snapshot=snapshot,
    )
    (bundle / "README.md").write_text(readme, encoding="utf-8")

    write_sha256sums(bundle)

    tops = tar_toplevel_names(archive_path)
    expected_top = ARCHIVE_PREFIX.rstrip("/")
    if expected_top not in tops:
        print(f"error: archive toplevel {sorted(tops)} missing {expected_top}", file=sys.stderr)
        return 1

    files = sorted(p for p in bundle.rglob("*") if p.is_file())
    summary = {
        "status": "wrote",
        "bundle": str(bundle),
        "archive_toplevel": expected_top,
        "patch_files": patch_files,
        "untracked_included": len(included),
        "untracked_skipped": len(skipped),
        "apk_sha256": apk_meta.get("sha256"),
        "source_snapshot_fingerprint": snapshot.get("fingerprint"),
        "correspondence": apk_meta.get("correspondence"),
        "files": [
            {
                "path": str(p.relative_to(bundle)),
                "bytes": p.stat().st_size,
                "sha256": sha256_file(p) if p.name != "SHA256SUMS" else None,
            }
            for p in files
        ],
    }
    for item in summary["files"]:
        if item["path"] == "SHA256SUMS":
            item["sha256"] = sha256_file(bundle / "SHA256SUMS")
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
