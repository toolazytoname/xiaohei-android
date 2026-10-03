#!/usr/bin/env python3
"""Verify candidate bytes, not device behavior or store eligibility. No install/network/secrets."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile
from datetime import datetime, timezone

EXCLUDED_LIBS = {
    'libavcodec.so', 'libavdevice.so', 'libavfilter.so', 'libavformat.so',
    'libavutil.so', 'libswresample.so', 'libswscale.so', 'libffmpegkit.so',
    'libffmpegkit_abidetect.so', 'libc++_shared.so', 'libmediapipe_tasks_vision_jni.so', 'libmediapipe_tasks_text_jni.so',
    'libmlkit_google_ocr_pipeline.so', 'libsudo.so', 'liboperit_ripgrep.so', 'libtensorflowlite_jni.so',
}
STORE_FORBIDDEN_PERMISSIONS = {
    'android.permission.QUERY_ALL_PACKAGES', 'android.permission.MANAGE_EXTERNAL_STORAGE',
    'android.permission.READ_SMS', 'android.permission.SEND_SMS', 'android.permission.CALL_PHONE',
    'android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.PACKAGE_USAGE_STATS',
    'moe.shizuku.manager.permission.API_V23',
}

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def elf_alignment(data: bytes) -> dict:
    """Reject unsupported/malformed ELF; check LOAD alignment and address/file congruence."""
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected little-endian ELF64')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    if size < 56 or not count or offset + size * count > len(data):
        raise ValueError('Invalid ELF program header table')
    loads, relro, headers = [], [], []
    for i in range(count):
        kind, flags, off, va, _, filesz, memsz, alignment = struct.unpack_from('<IIQQQQQQ', data, offset + i * size)
        headers.append((kind, flags, off, va, filesz, memsz))
        if kind == 1:
            if filesz > memsz or off + filesz > len(data):
                raise ValueError('Invalid LOAD range')
            loads.append({'flags': flags, 'address': va, 'memory_size': memsz, 'alignment': alignment, 'offset_mod_16kb': off % 16384,
                          'address_mod_16kb': va % 16384,
                          'aligned_16kb': alignment >= 16384 and alignment & (alignment - 1) == 0
                          and off % 16384 == va % 16384})
        elif kind == 0x6474e552:
            relro.append({'address': va, 'memory_size': memsz, 'end_mod_16kb': (va + memsz) % 16384})
    if not loads:
        raise ValueError('ELF has no LOAD segment')
    # Bionic rounds BOTH boundaries for mprotect. A non-16KB RELRO end is safe
    # when the rounded page tail is padding/gap, not mutable data. Reject only
    # overlap with writable LOAD bytes outside the declared RELRO ranges.
    # Source: platform/bionic linker/linker_phdr.cpp _phdr_table_set_gnu_relro_prot.
    conflicts = []
    for r in relro:
        start = r['address'] // 16384 * 16384
        end = (r['address'] + r['memory_size'] + 16383) // 16384 * 16384
        for load in loads:
            if not load['flags'] & 2:
                continue
            lo, hi = max(start, load['address']), min(end, load['address'] + load['memory_size'])
            pieces = [(lo, hi)] if lo < hi else []
            for known in relro:
                rlo, rhi = known['address'], known['address'] + known['memory_size']
                uncovered = []
                for plo, phi in pieces:
                    if rhi <= plo or rlo >= phi:
                        uncovered.append((plo, phi))
                    else:
                        if plo < rlo: uncovered.append((plo, rlo))
                        if rhi < phi: uncovered.append((rhi, phi))
                pieces = uncovered
            conflicts.extend({'start': lo, 'end': hi} for lo, hi in pieces)
    needed_offsets, string_addr, string_size = [], None, None
    for kind, _, off, _, filesz, _ in headers:
        if kind != 2: continue  # PT_DYNAMIC
        if off + filesz > len(data) or filesz % 16:
            raise ValueError('Invalid DYNAMIC range')
        for pos in range(off, off + filesz, 16):
            tag, value = struct.unpack_from('<qQ', data, pos)
            if tag == 0: break
            if tag == 1: needed_offsets.append(value)
            elif tag == 5: string_addr = value
            elif tag == 10: string_size = value
    needed = []
    if needed_offsets:
        if string_addr is None or string_size is None:
            raise ValueError('Missing dynamic string table')
        string_offset = next((off + string_addr - va for kind, _, off, va, filesz, _ in headers
                              if kind == 1 and va <= string_addr and string_addr + string_size <= va + filesz), None)
        if string_offset is None:
            raise ValueError('Unmapped dynamic string table')
        table = data[string_offset:string_offset + string_size]
        for pos in needed_offsets:
            if pos >= len(table) or b'\0' not in table[pos:]:
                raise ValueError('Invalid DT_NEEDED string')
            needed.append(table[pos:].split(b'\0', 1)[0].decode('utf-8', errors='strict'))
    return {'loads': loads, 'relro': relro, 'needed': sorted(needed),
            'load_16kb_pass': all(x['aligned_16kb'] for x in loads),
            'relro_end_16kb_pass': all(x['end_mod_16kb'] == 0 for x in relro),
            'relro_page_conflicts': conflicts, 'relro_page_protection_pass': not conflicts}


# Android NDK public system libraries, not arbitrary missing vendor libraries.
ANDROID_SYSTEM_LIBS = {
    'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libjnigraphics.so',
    'libz.so', 'libEGL.so', 'libGLESv1_CM.so', 'libGLESv2.so', 'libGLESv3.so',
    'libOpenSLES.so', 'libOpenMAXAL.so', 'libvulkan.so', 'libmediandk.so',
    'libaaudio.so', 'libcamera2ndk.so', 'libnativewindow.so', 'libsync.so',
    'libneuralnetworks.so', 'libbinder_ndk.so',
}

def missing_dependencies(libs: dict) -> dict:
    missing = {}
    for name, lib in libs.items():
        directory = str(Path(name).parent)
        absent = [dep for dep in lib['needed']
                  if dep not in ANDROID_SYSTEM_LIBS and directory + '/' + dep not in libs]
        if absent: missing[name] = absent
    return missing


def inspect_archive(path: Path, bundle: bool) -> dict:
    prefix = 'base/' if bundle else ''
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if len(set(names)) != len(names):
            raise ValueError('Duplicate ZIP members')
        if z.testzip() is not None:
            raise ValueError('ZIP CRC mismatch')
        libs, non_elf = {}, {}
        for name in names:
            if name.startswith(prefix + 'lib/') and name.endswith('.so'):
                data = z.read(name)
                if not data.startswith(b'\x7fELF'):
                    non_elf[name.removeprefix(prefix)] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                    continue
                libs[name.removeprefix(prefix)] = {
                    'sha256': hashlib.sha256(data).hexdigest(), **elf_alignment(data)}
        if not libs:
            raise ValueError('No native libraries: wrong/incomplete artifact')
        notices = {name.removeprefix(prefix): hashlib.sha256(z.read(name)).hexdigest()
                   for name in names if name.startswith(prefix + 'assets/third_party/stt/')
                   and not name.endswith('/')}
        return {'file': path.name, 'bytes': path.stat().st_size, 'sha256': sha256(path),
                'native': libs, 'missing_native_dependencies': missing_dependencies(libs), 'non_elf_library_members': non_elf, 'stt_notices': notices,
                'excluded_libraries_present': sorted(set(Path(x).name for x in list(libs) + list(non_elf)) & EXCLUDED_LIBS),
                'abis': sorted(set(x.split('/')[1] for x in libs)),
                'load_16kb_pass': all(x['load_16kb_pass'] for x in libs.values()),
                'relro_end_16kb_pass': all(x['relro_end_16kb_pass'] for x in libs.values()),
                'relro_page_protection_pass': all(x['relro_page_protection_pass'] for x in libs.values())}


def run(args: list[str]) -> str:
    result = subprocess.run(args, capture_output=True, text=True, timeout=120)
    if result.returncode:
        # Do not serialize process environment or private signing configuration.
        raise RuntimeError(f'{Path(args[0]).name} failed ({result.returncode}): {result.stderr[-1000:]}')
    return result.stdout


def parse_badging(text: str) -> dict:
    def field(pattern: str) -> str:
        match = re.search(pattern, text, re.M)
        if not match:
            raise ValueError('Missing APK badging field: ' + pattern)
        return match.group(1)
    return {'package': field(r"^package: name='([^']+)'"),
            'version_code': int(field(r"^package:.* versionCode='([0-9]+)'")),
            'version_name': field(r"^package:.* versionName='([^']+)'"),
            'target_sdk': int(field(r"^targetSdkVersion:'([0-9]+)'")),
            'label': field(r"^application-label:'([^']+)'"),
            'debuggable': 'application-debuggable' in text,
            'permissions': sorted(set(re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", text, re.M)))}


def verify(apk: Path, aab: Path | None, sdk: Path, profile: str) -> dict:
    a = inspect_archive(apk, False)
    info = parse_badging(run([str(sdk / 'aapt'), 'dump', 'badging', str(apk)]))
    manifest = run([str(sdk / 'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'])
    signature = run([str(sdk / 'apksigner'), 'verify', '--verbose', str(apk)])
    run([str(sdk / 'zipalign'), '-c', '-P', '16' if profile == 'store' else '4', '4', str(apk)])
    expected = 'studio.weichao.xiaohei' if profile == 'store' else 'studio.weichao.xiaohei.common'
    expected_label = '小黑' if profile == 'store' else '小黑·增强'
    checks = {'package': info['package'] == expected, 'label': info['label'] == expected_label,
              'backup_disabled': bool(re.search(r'android:allowBackup[^\n]*\(type 0x12\)0x0\b', manifest)),
              'arm64_only': a['abis'] == ['arm64-v8a'], 'stt_notices_present': len(a['stt_notices']) >= 4,
              'signature_verified': True, 'zip_alignment': True,
              'native_dependency_closure': not a['missing_native_dependencies']}
    if profile == 'store':
        checks.update({'non_debuggable': not info['debuggable'],
                       'manifest_cleartext_disabled': bool(re.search(r'android:usesCleartextTraffic[^\n]*\(type 0x12\)0x0\b', manifest)), 'target_at_least_36': info['target_sdk'] >= 36,
                       # Keep the official guide's conservative criterion separate from the
                       # loader-overlap diagnosis; neither replaces a 16KB runtime test.
                       'native_16kb_relro_guide_alignment': a['relro_end_16kb_pass'],
                       'native_16kb_load': a['load_16kb_pass'], 'no_non_elf_libraries': not a['non_elf_library_members'], 'native_16kb_relro_protection': a['relro_page_protection_pass'],
                       'excluded_libs_absent': not a['excluded_libraries_present'],
                       'forbidden_permissions_absent': not (set(info['permissions']) & STORE_FORBIDDEN_PERMISSIONS),
                       'signature_v2': bool(re.search(r'v2 scheme.*: true', signature)),
                       'signature_v3': bool(re.search(r'v3 scheme.*: true', signature))})
    b = inspect_archive(aab, True) if aab else None
    if b:
        checks['apk_aab_native_identical'] = a['native'] == b['native'] and a['non_elf_library_members'] == b['non_elf_library_members']
        checks['apk_aab_stt_notices_identical'] = a['stt_notices'] == b['stt_notices']
        if profile == 'store':
            checks['aab_excluded_libs_absent'] = not b['excluded_libraries_present']
    return {'schema_version': 3, 'generated_at': datetime.now(timezone.utc).isoformat(),
            'profile': profile, 'apk': a, 'aab': b, 'metadata': info, 'checks': checks,
            'static_checks_pass': all(checks.values()),
            'not_verified': ['device_runtime', 'acoustic_voice', '16kb_device_runtime',
                             'aab_manifest_and_play_processing', 'license_compliance',
                             'source_correspondence', 'account_eligibility', 'store_review']}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--aab', type=Path)
    p.add_argument('--build-tools', type=Path, required=True)
    p.add_argument('--profile', choices=['store', 'enhanced'], required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    try:
        report = verify(a.apk, a.aab, a.build_tools, a.profile)
    except (ValueError, OSError, RuntimeError, zipfile.BadZipFile, subprocess.TimeoutExpired) as e:
        report = {'static_checks_pass': False, 'error': str(e)}
    a.out.parent.mkdir(parents=True, exist_ok=True)
    temporary = a.out.with_suffix(a.out.suffix + '.tmp')
    temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    os.replace(temporary, a.out)
    print(json.dumps({'static_checks_pass': report['static_checks_pass'], 'checks': report.get('checks'),
                      'error': report.get('error'), 'report': str(a.out)}, ensure_ascii=False))
    return 0 if report['static_checks_pass'] else 1

if __name__ == '__main__':
    raise SystemExit(main())
