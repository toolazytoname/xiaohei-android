import importlib.util
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile
import warnings
import unittest

spec = importlib.util.spec_from_file_location('verify_candidate', Path(__file__).parents[1] / 'verify_candidate.py')
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)


def find_readelf() -> Path | None:
    candidates = []
    for env in ('ANDROID_NDK_HOME', 'ANDROID_NDK'):
        root = os.environ.get(env)
        if root:
            candidates.extend(Path(root).glob('toolchains/llvm/prebuilt/*/bin/llvm-readelf'))
    for env in ('ANDROID_SDK_ROOT', 'ANDROID_HOME'):
        sdk = os.environ.get(env)
        if sdk:
            candidates.extend(sorted(Path(sdk).glob('ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-readelf')))
    home_ndk = Path.home() / 'Library/Android/sdk/ndk'
    if home_ndk.is_dir():
        candidates.extend(sorted(home_ndk.glob('*/toolchains/llvm/prebuilt/*/bin/llvm-readelf')))
    brew = Path('/opt/homebrew/opt/llvm/bin/llvm-readelf')
    if brew.is_file():
        candidates.append(brew)
    for name in ('llvm-readelf', 'readelf'):
        found = shutil.which(name)
        if found:
            candidates.append(Path(found))
    for path in candidates:
        if path.is_file() and os.access(path, os.X_OK):
            return path
    return None


READELF = find_readelf()


class ElfTests(unittest.TestCase):
    def elf(self, align=16384, va=0, off=0):
        b = bytearray(128)
        b[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', b, 32, 64)
        struct.pack_into('<HH', b, 54, 56, 1)
        struct.pack_into('<IIQQQQQQ', b, 64, 1, 4, off, va, 0, 0, 16384, align)
        return bytes(b)
    def test_good_load(self): self.assertTrue(v.elf_alignment(self.elf())['load_16kb_pass'])
    def test_4kb(self): self.assertFalse(v.elf_alignment(self.elf(4096))['load_16kb_pass'])
    def test_misaligned_address(self): self.assertFalse(v.elf_alignment(self.elf(va=4096))['load_16kb_pass'])
    def test_non_power_two(self): self.assertFalse(v.elf_alignment(self.elf(24000))['load_16kb_pass'])
    def test_invalid_header(self):
        with self.assertRaises(ValueError): v.elf_alignment(b'not-elf')
    def test_truncated_table(self):
        with self.assertRaises(ValueError): v.elf_alignment(self.elf()[:80])
    def test_missing_load(self):
        b = bytearray(self.elf()); struct.pack_into('<I', b, 64, 0)
        with self.assertRaises(ValueError): v.elf_alignment(b)

class RelroAndArchiveTests(unittest.TestCase):
    def elf(self, end):
        b = bytearray(192)
        b[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', b, 32, 64)
        struct.pack_into('<HH', b, 54, 56, 2)
        struct.pack_into('<IIQQQQQQ', b, 64, 1, 4, 0, 0, 0, 0, 32768, 16384)
        struct.pack_into('<IIQQQQQQ', b, 120, 0x6474e552, 4, 0, 0, 0, 0, end, 1)
        return bytes(b)
    def test_relro_end_aligned(self):
        self.assertTrue(v.elf_alignment(self.elf(16384))['relro_end_16kb_pass'])
    def test_load_pass_does_not_hide_relro_failure(self):
        r = v.elf_alignment(self.elf(4096))
        self.assertTrue(r['load_16kb_pass'])
        self.assertFalse(r['relro_end_16kb_pass'])
    def test_script_so_is_not_counted_as_native(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'test.apk'
            with zipfile.ZipFile(p, 'w') as z:
                z.writestr('lib/arm64-v8a/libgood.so', self.elf(16384))
                z.writestr('lib/arm64-v8a/libsudo.so', b'$@')
            r = v.inspect_archive(p, False)
            self.assertEqual(len(r['native']), 1)
            self.assertEqual(r['non_elf_library_members']['lib/arm64-v8a/libsudo.so']['bytes'], 2)
            self.assertEqual(r['excluded_libraries_present'], ['libsudo.so'])
    def test_duplicate_zip_member_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'test.apk'
            with warnings.catch_warnings():
                warnings.simplefilter('ignore')
                with zipfile.ZipFile(p, 'w') as z:
                    z.writestr('duplicate', b'a'); z.writestr('duplicate', b'b')
            with self.assertRaises(ValueError): v.inspect_archive(p, False)

class BadgingTests(unittest.TestCase):
    def test_actual_format(self):
        t = "package: name='studio.weichao.xiaohei' versionCode='46' versionName='1.12.1' platformBuildVersionName='16'\ntargetSdkVersion:'36'\napplication-label:'小黑'\nuses-permission: name='android.permission.INTERNET'\n"
        r = v.parse_badging(t)
        self.assertFalse(r['debuggable']); self.assertEqual(r['target_sdk'], 36)
        self.assertEqual(r['permissions'], ['android.permission.INTERNET'])
        self.assertTrue(v.parse_badging(t + 'application-debuggable\n')['debuggable'])
    def test_missing_metadata(self):
        with self.assertRaises(ValueError): v.parse_badging('')


class ProfileGateTests(unittest.TestCase):
    def test_store_keeps_16kb_and_non_debug_gates(self):
        self.assertEqual(v.PROFILES, ('store', 'enhanced', 'enhanced_release'))
        self.assertTrue(v.uses_16kb_zipalign('store'))
        self.assertTrue(v.requires_non_debuggable('store'))
        self.assertTrue(v.requires_store_native_trim('store'))

    def test_enhanced_debug_profile_does_not_require_release_gates(self):
        self.assertFalse(v.uses_16kb_zipalign('enhanced'))
        self.assertFalse(v.requires_non_debuggable('enhanced'))
        self.assertFalse(v.requires_store_native_trim('enhanced'))

    def test_enhanced_release_requires_non_debug_without_16kb_trim(self):
        self.assertTrue(v.requires_non_debuggable('enhanced_release'))
        self.assertFalse(v.uses_16kb_zipalign('enhanced_release'))
        self.assertFalse(v.requires_store_native_trim('enhanced_release'))

class ProtectionAndDependencyTests(unittest.TestCase):
    def layout(self, mutable_start):
        b = bytearray(288)
        b[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', b, 32, 64)
        struct.pack_into('<HH', b, 54, 56, 3)
        # Whole first writable LOAD is RELRO; 4KB of data then a gap.
        struct.pack_into('<IIQQQQQQ', b, 64, 1, 6, 0, 16384, 0, 0, 4096, 16384)
        struct.pack_into('<IIQQQQQQ', b, 120, 0x6474e552, 4, 0, 16384, 0, 0, 4096, 1)
        struct.pack_into('<IIQQQQQQ', b, 176, 1, 6, 0, mutable_start, 0, 0, 100, 16384)
        return bytes(b)
    def test_non_aligned_relro_end_with_gap_is_safe(self):
        result = v.elf_alignment(self.layout(32768))
        self.assertFalse(result['relro_end_16kb_pass'])
        self.assertTrue(result['relro_page_protection_pass'])
    def test_mutable_data_in_rounded_relro_tail_is_rejected(self):
        result = v.elf_alignment(self.layout(24576))
        self.assertFalse(result['relro_page_protection_pass'])
        self.assertEqual(result['relro_page_conflicts'], [{'start':24576,'end':24676}])
    def test_missing_required_native_rejected(self):
        libs = {'lib/arm64-v8a/libMNNWrapper.so': {'needed': ['libMNN.so','libc.so']}}
        self.assertEqual(v.missing_dependencies(libs), {'lib/arm64-v8a/libMNNWrapper.so':['libMNN.so']})
        libs['lib/arm64-v8a/libMNN.so'] = {'needed': ['libc.so']}
        self.assertEqual(v.missing_dependencies(libs), {})
    def test_wrong_abi_does_not_satisfy_dependency(self):
        libs = {'lib/arm64-v8a/libMNNWrapper.so': {'needed':['libMNN.so']},
                'lib/x86_64/libMNN.so': {'needed':[]}}
        self.assertTrue(v.missing_dependencies(libs))

class DynamicNeededTests(unittest.TestCase):
    """DT_NEEDED/DT_STRTAB/DT_STRSZ from ELF bytes; ABI closure via inspect_archive."""

    def elf_with_dynamic(self, entries, strtab=b'\0', va=0):
        phnum = 2
        dyn_off = 64 + 56 * phnum
        dyn_size = 16 * len(entries)
        strtab_off = dyn_off + dyn_size
        total = strtab_off + len(strtab)
        b = bytearray(total)
        b[:7] = b'\x7fELF\x02\x01\x01'
        struct.pack_into('<HHIQQQIHHHHHH', b, 16, 3, 183, 1, 0, 64, 0, 0, 64, 56, phnum, 64, 0, 0)
        struct.pack_into('<IIQQQQQQ', b, 64, 1, 4, 0, va, va, total, total, 16384)
        struct.pack_into('<IIQQQQQQ', b, 120, 2, 4, dyn_off, va + dyn_off, va + dyn_off, dyn_size, dyn_size, 8)
        for i, (tag, val) in enumerate(entries):
            struct.pack_into('<qQ', b, dyn_off + i * 16, tag, val)
        b[strtab_off:] = strtab
        return bytes(b)

    def so_with_needed(self, names, va=0):
        blob = bytearray(b'\0')
        offs = []
        for n in names:
            offs.append(len(blob))
            blob.extend(n.encode('ascii') + b'\0')
        strtab = bytes(blob)
        strtab_off = 64 + 56 * 2 + 16 * (len(offs) + 3)
        entries = [(1, o) for o in offs] + [(5, va + strtab_off), (10, len(strtab)), (0, 0)]
        return self.elf_with_dynamic(entries, strtab, va)

    def test_dt_needed_extracted_from_strtab(self):
        data = self.so_with_needed(['libMNN.so', 'libc.so'])
        result = v.elf_alignment(data)
        self.assertEqual(result['needed'], ['libMNN.so', 'libc.so'])
        self.assertTrue(result['load_16kb_pass'])

    def test_strtab_mapped_through_load_vaddr(self):
        data = self.so_with_needed(['libfoo.so'], va=0x10000)
        self.assertEqual(v.elf_alignment(data)['needed'], ['libfoo.so'])

    def test_needed_without_strtab_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Missing dynamic string table'):
            v.elf_alignment(self.elf_with_dynamic([(1, 1), (0, 0)], b'\0libMNN.so\0'))

    def test_unmapped_strtab_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Unmapped dynamic string table'):
            v.elf_alignment(self.elf_with_dynamic(
                [(1, 1), (5, 0xFFFFFFFF), (10, 10), (0, 0)], b'\0libMNN.so\0'))

    def test_needed_offset_past_strtab_rejected(self):
        strtab = b'\0ab\0'
        strtab_off = 64 + 56 * 2 + 16 * 4
        with self.assertRaisesRegex(ValueError, 'Invalid DT_NEEDED string'):
            v.elf_alignment(self.elf_with_dynamic(
                [(1, 10), (5, strtab_off), (10, len(strtab)), (0, 0)], strtab))

    def test_parsed_needed_wrong_abi_not_closed(self):
        wrapper = self.so_with_needed(['libMNN.so', 'libc.so'])
        provider = self.so_with_needed(['libc.so'])
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'test.apk'
            with zipfile.ZipFile(p, 'w') as z:
                z.writestr('lib/arm64-v8a/libMNNWrapper.so', wrapper)
                z.writestr('lib/x86_64/libMNN.so', provider)
            r = v.inspect_archive(p, False)
            self.assertEqual(r['native']['lib/arm64-v8a/libMNNWrapper.so']['needed'],
                             ['libMNN.so', 'libc.so'])
            self.assertEqual(r['missing_native_dependencies'],
                             {'lib/arm64-v8a/libMNNWrapper.so': ['libMNN.so']})

    def test_parsed_needed_same_abi_closed(self):
        wrapper = self.so_with_needed(['libMNN.so', 'libc.so'])
        provider = self.so_with_needed(['libc.so'])
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'test.apk'
            with zipfile.ZipFile(p, 'w') as z:
                z.writestr('lib/arm64-v8a/libMNNWrapper.so', wrapper)
                z.writestr('lib/arm64-v8a/libMNN.so', provider)
            r = v.inspect_archive(p, False)
            self.assertEqual(r['missing_native_dependencies'], {})

    @unittest.skipUnless(READELF, 'llvm-readelf/readelf not found')
    def test_dt_needed_matches_ndk_readelf(self):
        data = self.so_with_needed(['libMNN.so', 'libc.so'])
        parsed = v.elf_alignment(data)['needed']
        with tempfile.TemporaryDirectory() as d:
            so = Path(d) / 'libsyn.so'
            so.write_bytes(data)
            out = subprocess.run(
                [str(READELF), '-d', str(so)], capture_output=True, text=True, timeout=30)
        self.assertEqual(out.returncode, 0, out.stderr)
        readelf_needed = re.findall(r'Shared library: \[([^\]]+)\]', out.stdout)
        self.assertEqual(sorted(readelf_needed), parsed)
        self.assertEqual(parsed, ['libMNN.so', 'libc.so'])

if __name__ == '__main__':
    unittest.main()
