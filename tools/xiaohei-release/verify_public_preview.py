#!/usr/bin/env python3
"""Additional fail-closed public-preview gate; complements native/signature verifier."""
import argparse, hashlib, json, re, subprocess, zipfile
from pathlib import Path
FORBIDDEN = {'libbusybox.so','libbash.so','liboperit_proot.so','liboperit_loader.so','libsudo.so','libavcodec.so','libavdevice.so','libavfilter.so','libavformat.so','libavutil.so','libswresample.so','libswscale.so','libffmpegkit.so','libffmpegkit_abidetect.so'}
def inspect(apk, sdk):
    with zipfile.ZipFile(apk) as z:
        names=z.namelist()
        checks={'no_untraceable_native':not any(Path(n).name in FORBIDDEN for n in names)}
        for edition, asset in [('store','xiaohei-privacy-policy.zh-CN.md'),('enhanced','xiaohei-privacy-policy-enhanced.zh-CN.md')]:
            text=z.read('assets/'+asset).decode()
            checks[edition+'_policy_complete']=all(s in text for s in ['韦超','lazywc@gmail.com','2026-10-03']) and '【待填' not in text
        checks['gpl_text_present']='assets/third_party/GPL-3.0.txt' in names
        checks['objectbox_license_present']='assets/third_party/ObjectBox-Binary-License.md' in names
        checks['ffmpeg_source_provenance']='assets/third_party/ffmpegkit/SOURCE.json' in names
    badging=subprocess.check_output([str(sdk/'aapt'),'dump','badging',str(apk)],text=True)
    cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],text=True)
    checks['non_debuggable']='application-debuggable' not in badging
    checks['preview_version']="versionCode='47'" in badging and "versionName='1.0.0-preview.1'" in badging
    match=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',cert)
    checks['signer_present']=match is not None
    checks['not_android_debug_signer']='CN=Android Debug' not in cert
    return {'file':str(apk),'sha256':hashlib.file_digest(apk.open('rb'),'sha256').hexdigest(),'signer_sha256':match.group(1) if match else None,'checks':checks,'passed':all(checks.values())}
def main():
    p=argparse.ArgumentParser();p.add_argument('--store',required=True,type=Path);p.add_argument('--enhanced',required=True,type=Path);p.add_argument('--build-tools',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args()
    result={'store':inspect(a.store,a.build_tools),'enhanced':inspect(a.enhanced,a.build_tools)}
    result['same_release_signer']=result['store']['signer_sha256']==result['enhanced']['signer_sha256']
    result['passed']=all(result[k]['passed'] for k in ['store','enhanced']) and result['same_release_signer']
    result['scope']='Static public package gate only; not device or complete license audit.'
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False));return 0 if result['passed'] else 1
if __name__=='__main__':raise SystemExit(main())
