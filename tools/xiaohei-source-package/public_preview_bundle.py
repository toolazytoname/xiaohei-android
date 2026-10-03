#!/usr/bin/env python3
"""Archive a public-preview worktree with explicit binary inputs, never credentials.
No network, signing, or publishing. Source archive is not a clean-build claim.
"""
import argparse, hashlib, io, json, re, subprocess, tarfile
from pathlib import Path

AARS = (
 'android-gif-drawable-1.2.28-16kb.aar',
 'graphics-path-1.0.1-16kb-arm64.aar',
 'filament-android-1.69.2-arm64-relro.aar',
 'filament-utils-android-1.69.2-arm64-relro.aar',
 'onnxruntime-android-1.29.0-arm64-cpu-16kb.aar',
)
INPUTS = ('ffmpeg-kit-v6.0.tar.gz','jlatexmath-android-v0.2.0.tar.gz','eigen3-build-source.tar.gz','onnx-fetchcontent-license-manifest.json')

def allowed(name):
 p=Path(name)
 if p.is_absolute() or '..' in p.parts: return False
 if any(x in p.parts for x in ('.git','.gradle','private','build','out','node_modules','__pycache__')): return False
 if p.name in ('local.properties','key.properties','keystore.properties','.env'): return False
 if p.name.startswith('.env.'): return False
 if p.suffix.lower() in ('.jks','.keystore','.p12','.p8','.apk','.aab','.so','.aar'): return False
 # Unused bundled tools are not part of the public preview build inputs.
 if p.suffix.lower()=='.jar' and p.name!='gradle-wrapper.jar': return False
 return True

def git(repo,*args):
 return subprocess.check_output(['git',*args],cwd=repo)

def sha(p):
 with p.open('rb') as f: return hashlib.file_digest(f,'sha256').hexdigest()

def main():
 ap=argparse.ArgumentParser(description=__doc__)
 for key in ('repo','store','enhanced','source-inputs','recipes','out'):ap.add_argument('--'+key,required=True,type=Path)
 a=ap.parse_args();repo=a.repo.resolve()
 if a.out.exists():raise SystemExit('Refusing to overwrite archive')
 manifest={'commit':git(repo,'rev-parse','HEAD').decode().strip(),'terminal_commit':git(repo/'terminal','rev-parse','HEAD').decode().strip(),'apks':{p.name:sha(p) for p in (a.store,a.enhanced)},'files':{},'excluded':[],'scope':'Tracked worktree source plus explicit build inputs. Includes terminal tracked edits. Not a bit-identical or clean-environment rebuild certification.'}
 candidates=[]
 for root,prefix in [(repo,'app-source'),(repo/'terminal','app-source/terminal')]:
  for name in git(root,'ls-files','-z').decode().split('\0'):
   if not name:continue
   p=root/name; target=prefix+'/'+name
   if not p.is_file():continue
   if p.is_symlink() or not allowed(name):manifest['excluded'].append(target);continue
   candidates.append((p,target))
 for name in AARS:candidates.append((repo/'app/libs'/name,'app-source/app/libs/'+name))
 for name in INPUTS:candidates.append((a.source_inputs/name,'upstream-sources/'+name))
 for p in a.recipes.rglob('*'):
  if p.is_file() and not p.is_symlink() and allowed(str(p.relative_to(a.recipes))):candidates.append((p,'recipes/'+str(p.relative_to(a.recipes))))
 readme='''# 小黑 Android 1.0.0-preview.1 对应源码材料\n\napp-source 是公开预览实际 tracked 工作树（含 terminal 的 16KB CMake 改动），并非未修改的上游归档。清单记录每个文件哈希及两份 APK 哈希。\n\n## 重建与修改\n使用 JDK21、Android SDK36、项目声明的 NDK/CMake，联网取得 Maven/模型资源。app/libs 已提供五个非 FFmpeg 本地构建输入；原生配方在 recipes。Filament 配方是官方静态包重链 JNI，不是全源重建。Eigen MPL、JLatexMath（含链接例外）、FFmpeg Java 的钉死源码随 upstream-sources 提供。\n\n在 app-source 执行：\n\n```sh\n./gradlew :app:assembleCommonRelease :app:assembleCommonEnhancedRelease -x :app:verifyExternallyBuiltNativeLibraries\n```\n\n-x 仅跳过非公开变体的上游 FFmpeg/ripgrep 外部输入检查；AGP 共享 CMake 任务会关联 preNightlyBuild。公开变体不会打包这些 native，不需下载未知来源自制 FFmpeg AAR。其 Java wrapper 从随包 v6.0 源直接编译。发布前仍需运行 tools/xiaohei-release/verify_candidate.py。\n\n正式私钥不分发。按 app/build.gradle.kts 的 commonStore 配置使用你自己的签名，或对 unsigned APK zipalign/apksigner。不同签名不可覆盖官方安装；先备份并验证，不要直接卸载有数据的包。可修改 applicationId 与官方包并存。\n\n## 被过滤的内容\n所有 tracked .so（包括公开版未使用的终端命令、loader、sherpa-mnn）、非 Gradle wrapper 的预编译 jar、凭据路径不会随此源归档再分发；完整排除列表在 MANIFEST.json。仅此公开构建可用，不承诺原上游 debug/nightly 构建。Gitignored 的五个明确许可 AAR 作为构建输入单独附上，不代替 copyleft 对应源。\n\n主体 LGPL-3.0-only；保留 app-source/LICENSE 和 app-source/app/src/publicPreview/assets/third_party 的许可正文/NOTICE。无额外禁止逆向调试修改版的限制。未完成独立干净环境重建、全传递依赖审计或真机全功能验收，不能把清单哈希当作上述通过。\n'''
 a.out.parent.mkdir(parents=True,exist_ok=True)
 try:
  with tarfile.open(a.out,'w:gz') as tar:
   seen=set()
   for p,target in candidates:
    if target in seen:raise RuntimeError('Duplicate archive member: '+target)
    seen.add(target)
    if not p.is_file():raise RuntimeError('Missing build input: '+str(p))
    if p.suffix in ('.kt','.kts','.java','.json','.md','.properties','.py','.txt'):
     if re.search(rb'sk-[A-Za-z0-9_-]{24,}',p.read_bytes()):raise RuntimeError('Potential private data: '+str(p))
    manifest['files'][target]={'sha256':sha(p),'bytes':p.stat().st_size}
    tar.add(p,arcname=target,recursive=False)
   for name,data in [('README.md',readme.encode()),('MANIFEST.json',json.dumps(manifest,ensure_ascii=False,indent=2).encode())]:
    info=tarfile.TarInfo(name);info.size=len(data);info.mode=0o644;tar.addfile(info,io.BytesIO(data))
 except BaseException:
  a.out.unlink(missing_ok=True);raise
 print(json.dumps({'archive':str(a.out),'sha256':sha(a.out),'files':len(manifest['files']),'excluded':len(manifest['excluded'])}))
if __name__=='__main__':main()
