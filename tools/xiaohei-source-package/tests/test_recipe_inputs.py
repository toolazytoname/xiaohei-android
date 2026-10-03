import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1] / "recipes"
RECIPES = ["gif-16k-rebuild/build.sh", "native-16kb-rebuilds/graphics-path/build.sh", "native-16kb-rebuilds/filament/build.sh", "onnx-16kb-rebuild/build.sh"]

class RecipeInputTests(unittest.TestCase):
    def test_shell_syntax(self):
        for rel in RECIPES:
            with self.subTest(recipe=rel):
                r = subprocess.run(["bash", "-n", str(ROOT / rel)], capture_output=True, text=True)
                self.assertEqual(r.returncode, 0, r.stderr)

    def test_missing_sdk_fails_before_download_or_build(self):
        env = {k:v for k,v in os.environ.items() if k not in {"ANDROID_SDK_ROOT", "ANDROID_HOME", "NDK", "CMAKE", "NINJA", "GIF_AAR", "GRAPHICS_PATH_AAR", "AAR_FILAMENT", "AAR_UTILS", "AAR_GLTFIO", "JAVA_HOME_OVERRIDE", "ONNX_AAR"}}
        for rel in RECIPES:
            with self.subTest(recipe=rel):
                r = subprocess.run(["bash", str(ROOT / rel)], env=env, capture_output=True, text=True, timeout=10)
                self.assertNotEqual(r.returncode, 0)
                self.assertIn("ANDROID_SDK_ROOT", r.stderr)
                self.assertNotIn("downloading", r.stdout.lower())
