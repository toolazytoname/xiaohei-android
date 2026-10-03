#!/usr/bin/env python3
"""Generator behavior tests. Does not write a full corresponding-source archive."""

from __future__ import annotations

import hashlib
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

PACKAGE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PACKAGE_DIR))

import make_source_package as msp  # noqa: E402


RETIRED_HASH = "cef6799be522f71a8a7ab42c3e85533e8fa721669e079fb6ebeb74b8b18af484"


def write_fake_apk(path: Path, payload: bytes) -> str:
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("AndroidManifest.xml", payload)
        zf.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n")
    return msp.sha256_file(path)


class SourceReconstructionTests(unittest.TestCase):
    def test_binary_patch_reconstructs_changed_and_added_icons(self) -> None:
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            repo.mkdir()
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=repo)
            git("init", "-q")
            git("config", "user.name", "Test")
            git("config", "user.email", "test@example.invalid")
            (repo / "old.png").write_bytes(b"\x00PNG original\xff")
            git("add", ".")
            git("commit", "-qm", "baseline")
            base = git("rev-parse", "HEAD").decode().strip()
            changed = b"\x00PNG changed\xfe"
            added = b"\x00PNG enhanced badge\xff"
            (repo / "old.png").write_bytes(changed)
            (repo / "new.png").write_bytes(added)
            git("add", ".")
            patch = msp.binary_source_patch(repo, base)
            self.assertIn(b"GIT binary patch", patch)
            restored = Path(tmp) / "restored"
            subprocess.run(["git", "clone", "-q", str(repo), str(restored)], check=True)
            subprocess.run(["git", "apply", "--binary", "-"], cwd=restored,
                           input=patch, check=True)
            self.assertEqual((restored / "old.png").read_bytes(), changed)
            self.assertEqual((restored / "new.png").read_bytes(), added)

    def test_nested_untracked_unicode_sources_not_directory_summary(self) -> None:
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            repo.mkdir()
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            src = repo / "nested" / "名字 with space.kt"
            src.parent.mkdir()
            src.write_text("val message = 1")
            self.assertEqual(msp.porcelain_untracked(repo), ["nested/名字 with space.kt"])
            included, _ = msp.copy_untracked(repo, Path(tmp) / "copied")
            self.assertEqual(included[0]["path"], "nested/名字 with space.kt")

    def test_symlink_cannot_export_external_private_source(self) -> None:
        import subprocess
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            repo.mkdir()
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            secret = Path(tmp) / "outside.kt"
            secret.write_text("private outside data")
            (repo / "link.kt").symlink_to(secret)
            included, skipped = msp.copy_untracked(repo, Path(tmp) / "copied")
            self.assertEqual(included, [])
            self.assertEqual(skipped[0]["path"], "link.kt")


class ApkMetadataTests(unittest.TestCase):
    def test_module_has_no_hardcoded_apk_sha_default(self) -> None:
        self.assertFalse(hasattr(msp, "APK_SHA256"))
        self.assertFalse(hasattr(msp, "APK_BYTES"))
        self.assertFalse(hasattr(msp, "APK_SIGNER"))
        self.assertFalse(hasattr(msp, "APK_TARGET_SDK"))
        source = (PACKAGE_DIR / "make_source_package.py").read_text(encoding="utf-8")
        self.assertNotIn(f'APK_SHA256 = "{RETIRED_HASH}"', source)
        self.assertIn(RETIRED_HASH, msp.RETIRED_DEFAULT_APK_HASHES)

    def test_inspect_apk_uses_actual_file_hash_not_retired_constant(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "probe.apk"
            digest = write_fake_apk(apk, b"unique-apk-payload-for-hash-test-2026-10-02")
            self.assertNotEqual(digest, RETIRED_HASH)
            meta = msp.inspect_apk_metadata(apk)
            self.assertEqual(meta["sha256"], digest)
            self.assertEqual(meta["bytes"], apk.stat().st_size)
            self.assertEqual(meta["targetSdk_status"], "未检查")
            self.assertEqual(meta["signing_status"], "未检查")
            self.assertIsNone(meta["targetSdk"])
            self.assertIsNone(meta["signing"])
            self.assertNotEqual(meta["sha256"], RETIRED_HASH)

    def test_missing_apk_raises_and_does_not_invent_hash(self) -> None:
        missing = Path("/tmp/does-not-exist-xiaohei-source-package.apk")
        with self.assertRaises(msp.MissingApkError):
            msp.inspect_apk_metadata(missing)

    def test_explicit_apk_required(self) -> None:
        with self.assertRaises(msp.MissingApkError) as ctx:
            msp.resolve_explicit_apk([], env={})
        self.assertIn("explicit", str(ctx.exception).lower())

    def test_main_without_apk_fails_before_archive(self) -> None:
        rc = msp.main([], env={})
        self.assertEqual(rc, 1)


class CorrespondenceTests(unittest.TestCase):
    def test_pending_when_no_claimed_fingerprint(self) -> None:
        status = msp.correspondence_status(
            apk_sha256="abc",
            current_fingerprint="fp-now",
            claimed_fingerprint=None,
        )
        self.assertEqual(status, msp.CORRESPONDENCE_PENDING)

    def test_pending_when_source_fingerprint_changes(self) -> None:
        fp_old = msp.source_snapshot_fingerprint(
            upstream_commit=msp.COMMIT,
            head=msp.COMMIT,
            patch_sha256="aaaa",
            untracked_sha256s=[],
        )
        fp_new = msp.source_snapshot_fingerprint(
            upstream_commit=msp.COMMIT,
            head=msp.COMMIT,
            patch_sha256="bbbb",
            untracked_sha256s=[],
        )
        self.assertNotEqual(fp_old, fp_new)
        status = msp.correspondence_status(
            apk_sha256="abc",
            current_fingerprint=fp_new,
            claimed_fingerprint=fp_old,
        )
        self.assertEqual(status, msp.CORRESPONDENCE_PENDING)

    def test_strict_only_when_claimed_matches_current(self) -> None:
        fp = msp.source_snapshot_fingerprint(
            upstream_commit=msp.COMMIT,
            head=msp.COMMIT,
            patch_sha256="cccc",
            untracked_sha256s=[("a.kt", "d" * 64)],
        )
        self.assertEqual(
            msp.correspondence_status(
                apk_sha256="abc",
                current_fingerprint=fp,
                claimed_fingerprint=fp,
            ),
            msp.CORRESPONDENCE_STRICT,
        )

    def test_record_uses_selected_apk_hash_and_pending_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "selected.apk"
            digest = write_fake_apk(apk, b"selected-existing-apk-bytes")
            record = msp.build_correspondence_record(
                apk_path=apk,
                head="deadbeef",
                patch_bytes=b"diff --git a/foo b/foo\n",
                untracked_included=[{"path": "x.kt", "sha256": "e" * 64}],
                claimed_fingerprint=None,
                extras=False,
            )
            apk_meta = record["apk"]
            snapshot = record["source_snapshot"]
            self.assertEqual(apk_meta["sha256"], digest)
            self.assertNotEqual(digest, RETIRED_HASH)
            self.assertEqual(apk_meta["correspondence"], msp.CORRESPONDENCE_PENDING)
            self.assertEqual(len(str(snapshot["fingerprint"])), 64)
            self.assertNotEqual(snapshot["fingerprint"], digest)

    def test_readme_does_not_claim_uninspected_sdk_or_signer(self) -> None:
        text = msp.render_readme(
            bundle_date="2026-10-02",
            patch_files=1,
            untracked_included=0,
            untracked_skipped=0,
            archive_rel=msp.ARCHIVE_NAME,
            terminal_rel=None,
            apk={
                "path": "/tmp/probe.apk",
                "sha256": "aa" * 32,
                "bytes": 12,
                "targetSdk": None,
                "targetSdk_status": "未检查",
                "signing": None,
                "signing_status": "未检查",
                "correspondence": msp.CORRESPONDENCE_PENDING,
            },
            snapshot={"fingerprint": "bb" * 32},
        )
        self.assertIn("待验证", text)
        self.assertIn("aa" * 32, text)
        self.assertNotIn("CN=Xiaohei Common Upload", text)
        self.assertNotIn(RETIRED_HASH, text)


class RecipeWhitelistTests(unittest.TestCase):
    def test_sensitive_and_bulky_paths_are_not_copied(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "recipe"
            (src / "evidence").mkdir(parents=True)
            (src / "build").mkdir()
            (src / "source").mkdir()
            (src / "cache").mkdir()
            (src / "private").mkdir()
            (src / "logs").mkdir()
            (src / "README.md").write_text("ok\n", encoding="utf-8")
            (src / "build.sh").write_text("#!/bin/sh\n", encoding="utf-8")
            (src / "evidence" / "status.json").write_text("{}\n", encoding="utf-8")
            (src / "build" / "huge.bin").write_bytes(b"G" * 1024)
            (src / "source" / "secret.c").write_text("secret\n", encoding="utf-8")
            (src / "cache" / "blob").write_bytes(b"cache")
            (src / "private" / "key.pem").write_text("KEY\n", encoding="utf-8")
            (src / "logs" / "creds.log").write_text("token=abc\n", encoding="utf-8")
            dest = Path(tmp) / "dest"
            copied = msp.copy_whitelisted_recipe(
                src,
                dest,
                ["README.md", "build.sh", "evidence/status.json"],
            )
            names = {item["path"] for item in copied}
            self.assertEqual(names, {"README.md", "build.sh", "evidence/status.json"})
            self.assertTrue((dest / "README.md").is_file())
            self.assertFalse((dest / "build").exists())
            self.assertFalse((dest / "source").exists())
            self.assertFalse((dest / "cache").exists())
            self.assertFalse((dest / "private").exists())
            self.assertFalse((dest / "logs").exists())

    def test_denied_whitelist_entry_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "recipe"
            (src / "private").mkdir(parents=True)
            (src / "private" / "key.pem").write_text("KEY\n", encoding="utf-8")
            dest = Path(tmp) / "dest"
            with self.assertRaises(msp.RecipeCopyError):
                msp.copy_whitelisted_recipe(src, dest, ["private/key.pem"])
            self.assertFalse((dest / "private" / "key.pem").exists())

    def test_missing_whitelist_file_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "recipe"
            src.mkdir()
            (src / "README.md").write_text("ok\n", encoding="utf-8")
            dest = Path(tmp) / "dest"
            with self.assertRaises(msp.RecipeCopyError) as ctx:
                msp.copy_whitelisted_recipe(src, dest, ["README.md", "missing.json"])
            self.assertIn("missing", str(ctx.exception))

    def test_so_suffix_denied_even_if_listed(self) -> None:
        with self.assertRaises(msp.RecipeCopyError):
            msp.assert_whitelist_safe("out/libonnxruntime.so")

    def test_onnx_recipe_spec_has_pin_and_whitelist(self) -> None:
        onnx = next(
            spec
            for spec in msp.BUILD_INPUT_RECIPES
            if "onnxruntime-android-1.29.0-arm64-cpu-16kb.aar" in spec["path"]
        )
        self.assertEqual(onnx["commit"], msp.ONNX_COMMIT)
        self.assertEqual(onnx["tag"], "v1.29.0")
        self.assertTrue(onnx["cpu_only"])
        self.assertEqual(onnx["telemetry"], "OFF")
        self.assertIn("MIT", onnx["license"])
        self.assertIn("not a complete transitive", onnx["license"])
        whitelist = onnx["copy_whitelist"]
        self.assertIn("README.md", whitelist)
        self.assertNotIn("build/android-arm64-cpu", " ".join(whitelist))
        for rel in whitelist:
            msp.assert_whitelist_safe(rel)

    def test_copy_real_onnx_whitelist_skips_build_tree(self) -> None:
        src = msp.upstream_build() / "onnx-16kb-rebuild"
        onnx = next(
            spec
            for spec in msp.BUILD_INPUT_RECIPES
            if spec.get("recipe_root") == "onnx-16kb-rebuild"
        )
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "copied"
            copied = msp.copy_whitelisted_recipe(src, dest, list(onnx["copy_whitelist"]))
            self.assertGreaterEqual(len(copied), 3)
            copied_names = {item["path"] for item in copied}
            self.assertIn("README.md", copied_names)
            self.assertFalse((dest / "build").exists())
            self.assertFalse((dest / "source").exists())
            self.assertFalse((dest / "cache-aar").exists())
            self.assertFalse((dest / "logs").exists())
            self.assertFalse((dest / "private").exists())
            self.assertFalse(list(dest.rglob("*.so")))
            self.assertFalse(list(dest.rglob("*.aar")))


class OnnxInputsListingTests(unittest.TestCase):
    def test_existing_build_inputs_includes_onnx_fields(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            rows = msp.existing_build_inputs(repo)
            onnx = next(r for r in rows if "onnxruntime" in str(r["path"]))
            self.assertFalse(onnx["present"])
            self.assertEqual(onnx["commit"], msp.ONNX_COMMIT)
            self.assertEqual(onnx["telemetry"], "OFF")
            self.assertTrue(onnx["cpu_only"])
            self.assertIsNone(onnx["sha256"])


class ShaHelperTests(unittest.TestCase):
    def test_sha256_file_matches_hashlib(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "x.bin"
            data = b"not-the-retired-apk"
            path.write_bytes(data)
            self.assertEqual(msp.sha256_file(path), hashlib.sha256(data).hexdigest())
            self.assertNotEqual(msp.sha256_file(path), RETIRED_HASH)


if __name__ == "__main__":
    unittest.main()
