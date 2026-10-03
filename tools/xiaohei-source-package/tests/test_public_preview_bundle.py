import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('public_bundle',Path(__file__).resolve().parents[1]/'public_preview_bundle.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class PublicBundleTests(unittest.TestCase):
 def test_private_paths_denied(self):
  for p in ['private/config.json','.env.production','app/local.properties','key.p12','../escape','/absolute','app/build/output.txt']:
   self.assertFalse(m.allowed(p),p)
 def test_terminal_and_unneeded_binaries_denied(self):
  for p in ['terminal/src/main/jniLibs/arm64-v8a/liboperit_loader.so','libbusybox.so','libbash.so','liboperit_proot.so','libsudo.so','ffmpeg-kit-local.aar','tool.jar']:
   self.assertFalse(m.allowed(p),p)
 def test_sources_and_wrapper_allowed(self):
  for p in ['app/build.gradle.kts','terminal/src/main/jni/pty.c','gradle/wrapper/gradle-wrapper.jar','LICENSE']:
   self.assertTrue(m.allowed(p),p)
 def test_explicit_aar_list(self):
  self.assertEqual(5,len(m.AARS));self.assertFalse(any('ffmpeg' in p for p in m.AARS))
if __name__=='__main__':unittest.main()
