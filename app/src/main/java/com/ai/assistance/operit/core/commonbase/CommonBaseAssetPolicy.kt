package com.ai.assistance.operit.core.commonbase

/**
 * Common-base packaged-asset exclusions. Source files stay on disk; only the `common`
 * buildType drops these paths from merged [android.content.res.AssetManager] output via
 * a variant-scoped `SingleArtifact.ASSETS` transform.
 *
 * Original debug/release/clone/nightly keep every merged asset. Do not exclude from
 * `sourceSets.main` or the shared generated assets directory.
 */
object CommonBaseAssetPolicy {
    const val ACCESSIBILITY_PROVIDER_APK = "accessibility.apk"
    const val DESKTOP_APK = "desktop.apk"
    const val SHIZUKU_APK = "shizuku.apk"
    const val SUBPACK_ANDROID_APK = "subpack/android.apk"
    const val SUBPACK_WINDOWS_ZIP = "subpack/windows.zip"
    const val UBUNTU_NOBLE_ROOTFS = "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"

    val excludedPackagedRelativePaths: Set<String> =
        linkedSetOf(
            ACCESSIBILITY_PROVIDER_APK,
            DESKTOP_APK,
            SHIZUKU_APK,
            SUBPACK_ANDROID_APK,
            SUBPACK_WINDOWS_ZIP,
            UBUNTU_NOBLE_ROOTFS
        )

    fun isExcludedFromCommonPackage(relativePath: String): Boolean =
        relativePath in excludedPackagedRelativePaths

    fun exclusionReason(relativePath: String): String? =
        when (relativePath) {
            ACCESSIBILITY_PROVIDER_APK ->
                "UIHierarchyManager.launchProviderInstall / AccessibilityProviderInstaller are only reached from ShizukuDemoScreen (navigation ShizukuCommands denied). Application bind is skipped by CommonBaseStartupPolicy.skipUiHierarchyProviderBind. UI automation tools are not in CommonBaseToolCatalog."
            DESKTOP_APK ->
                "No Kotlin/XML asset path reference to desktop.apk. ToolPkg desktop widgets are removed from the common manifest and skipToolPkgNavigationRuntime skips tool-package routes."
            SHIZUKU_APK ->
                "ShizukuInstaller.extractApkFromAssets is only reached from ShizukuDemoScreen (ShizukuCommands denied). Common manifest removes ShizukuProvider and REQUEST_INSTALL_PACKAGES. Shell/Shizuku tools are not promised."
            SUBPACK_ANDROID_APK,
            SUBPACK_WINDOWS_ZIP ->
                "ExportDialogs reads these from assets. Workspace export is gated by CommonBaseUiResiduePolicy.allowsWorkspace (false). HtmlPackager navigation is denied. ChatScreenContent export flags are never set true."
            UBUNTU_NOBLE_ROOTFS ->
                "TerminalManager.extractAssets reads this merged :terminal asset. CommonBaseStartupPolicy.skipTerminalMcpRuntimePrep skips MCP/terminal prep; Terminal/TerminalSetup/TerminalAutoConfig/Toolbox are denied; AI computer overlay is refused; file/shell tools are not registered."
            else -> null
        }
}
