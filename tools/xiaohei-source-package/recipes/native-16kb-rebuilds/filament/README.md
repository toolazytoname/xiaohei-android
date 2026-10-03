# Filament 1.69.2 16KB RELRO

Production Maven `com.google.android.filament:{filament,gltfio,filament-utils}-android:1.69.2`.

Store consumers: `GltfSurfaceView` / `GltfRenderer` / `AvatarRendererFactoryImpl` (AvatarType.GLTF). Cannot delete.

`libfilament-jni.so` and `libfilament-utils-jni.so` fail official RELRO; `libgltfio-jni.so` already passes.

No Filament source in this workspace. Rebuild needs google/filament tag matching 1.69.2 plus Android JNI targets with max+common 16KB. Large compile; not attempted without the pinned tree.

## 2026-10-02 rebuild and integration review

官方 `google/filament` tag `v1.69.2`（commit `f0a8dc5b643d32595b0271cab00635c3a5c5fd8d`）的 Android JNI 已用本地 NDK 27.1、官方 android-native 静态分发包和 `-z max-page-size=16384 -z common-page-size=16384` 重链；未修改 ELF header。产物：

- `out/filament-android-1.69.2-arm64-relro.aar` — SHA256 `d551db3445bb827a38149a1a5acb581cdbf4fd46bb34e6f9fc73336739132d58`
- `out/filament-utils-android-1.69.2-arm64-relro.aar` — SHA256 `6185762b717ee553af6cb922cb683bfcfb46e5400db1476d14ab0a695f37a49e`
- arm64 `libfilament-jni.so` — `ab1cfbc1ec99ec083ea457df8a643642be7bacb738c9438913c01a6d1488cc22`
- arm64 `libfilament-utils-jni.so` — `8578e5c0811bc254ec239944054c9d7649e0668f4d7ff1784b09b667666a59ea`

主代理独立运行 `check_elf.py`：两库 LOAD/RELRO/SONAME 通过；独立 JNI 导出比较分别为 `true/true`，Filament 主库只有 4 个非 JNI 编译器/运行时符号差异，不影响 Java/JNI 导出面。共同版通过全局排除 Maven `filament-android` / `filament-utils-android` 并接入两个本地 AAR；`gltfio-android:1.69.2` 保留官方 arm64 库（已通过同一静态检查）。尚未做 glTF 真机运行或 16KB 真机验收。
