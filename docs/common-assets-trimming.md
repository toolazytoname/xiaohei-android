# Common buildType packaged-asset trimming

共同版 `common` buildType 在 **merge 之后** 用 AGP 8.13 公共接口 `SingleArtifact.ASSETS` + `wiredWithDirectories().toTransform()` 去掉大体积不可达 assets。源文件不删。`debug` / `release` / `clone` / `nightly` 与共享的 `generatedMainAssetsDir` / `sourceSets.main` 不受影响。

不要改成全局 `packaging.resources.excludes`、`sourceSets.main.assets.exclude`，或 `doLast` 改共享输入。Ubuntu rootfs 来自 `:terminal` 的 merge，只能在应用 variant 的合并 assets 上裁。

## 剔除路径（压缩合计 110,430,652 字节）

清单来源：`integrations/operit-upstream-build/common-asset-residue-5b3af7e0.json`。路径须与 `CommonBaseAssetPolicy.excludedPackagedRelativePaths` 及 `app/build.gradle.kts` 中 `commonBaseExcludedPackagedAssets` 一致。

| 路径 | 压缩字节 | 共同版为何不可达 |
|---|---:|---|
| `accessibility.apk` | 1,654,498 | `UIHierarchyManager.launchProviderInstall` 只从 `ShizukuDemoScreen` 进入（`ShizukuCommands` 已拒绝）；启动 bind 被 `skipUiHierarchyProviderBind` 跳过；UI 自动化不在工具目录 |
| `desktop.apk` | 6,096,909 | 无 Kotlin/XML 资产路径引用；ToolPkg 桌面部件已从共同版 manifest 移除 |
| `shizuku.apk` | 1,789,214 | `ShizukuInstaller.extractApkFromAssets` 只从 `ShizukuDemoScreen` 进入；ShizukuProvider / `REQUEST_INSTALL_PACKAGES` 已移除 |
| `subpack/android.apk` | 25,343,326 | `ExportDialogs` 导出；工作区被 `allowsWorkspace` 拒绝；`HtmlPackager` 导航拒绝 |
| `subpack/windows.zip` | 11,394,267 | 同上 |
| `ubuntu-noble-aarch64-pd-v4.18.0.tar.xz` | 64,152,438 | `:terminal` merge 资产；`skipTerminalMcpRuntimePrep`；Terminal/Toolbox 导航拒绝；AI 电脑 overlay 拒绝；未注册 file/shell 工具 |

## 构建接线

`androidComponents.onVariants(selector().withBuildType("common"))` 注册 `filterCommonPackagedAssets`，对合并目录 `sync` 并按相对路径 exclude。输出只给该 variant 的打包任务。

## 状态

**待编译/产物验证。** 本轮未跑 `:app:assembleCommon`、未解 APK、未做手机/模拟器/声学测试。主 agent 批量构建时应确认 `app-common.apk` 不再含上述六项，且其他 buildType 仍含原资产。

## 主 agent 构建复核

2026-09-16 已构建，最终APK300,674,908字节，六项均缺席；源文件保留，签名/ZIP对齐/STT告知文件检查通过。证据见工作区 `integrations/operit-upstream-build/common-assets-build/review.md`。其他变体未构建，设备验收按用户要求延后。
