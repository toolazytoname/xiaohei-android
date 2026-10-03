package com.ai.assistance.operit.core.commonbase

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaoheiPublicPreviewVariantTest {

    @Test
    fun commonEnhancedRelease_isOfficiallySignedNonDebugTwinOfCommon() {
        val script = readGradle()
        val common = buildTypeBody(script, "common")
        val store = buildTypeBody(script, "commonRelease")
        val enhancedRelease = buildTypeBody(script, "commonEnhancedRelease")

        assertTrue(common.contains("initWith(getByName(\"debug\"))"))
        assertTrue(common.contains("matchingFallbacks += listOf(\"debug\")"))
        assertTrue(common.contains("applyXiaoheiEnhancedBrand()"))
        assertFalse(common.contains("signingConfig"))
        assertFalse(common.contains("isDebuggable = false"))
        assertFalse(common.contains("COMMON_PUBLIC_PREVIEW\", \"true\""))

        assertFalse(store.contains("applicationIdSuffix"))
        assertTrue(store.contains("matchingFallbacks += listOf(\"release\")"))
        assertTrue(store.contains("isDebuggable = false"))
        assertTrue(store.contains("COMMON_STORE\", \"true\""))
        assertTrue(store.contains("COMMON_ENHANCED\", \"false\""))
        assertTrue(store.contains("COMMON_PUBLIC_PREVIEW\", \"true\""))
        assertTrue(store.contains("signingConfig = signingConfigs.findByName(\"commonStore\")"))
        assertFalse(store.contains("getByName(\"debug\")"))

        assertTrue(enhancedRelease.contains("initWith(getByName(\"release\"))"))
        assertTrue(enhancedRelease.contains("matchingFallbacks += listOf(\"release\")"))
        assertTrue(enhancedRelease.contains("isDebuggable = false"))
        assertTrue(enhancedRelease.contains("applyXiaoheiEnhancedBrand()"))
        assertTrue(enhancedRelease.contains("COMMON_PUBLIC_PREVIEW\", \"true\""))
        assertTrue(
            enhancedRelease.contains("signingConfig = signingConfigs.findByName(\"commonStore\")")
        )
        assertFalse(enhancedRelease.contains("getByName(\"debug\")"))
        assertFalse(enhancedRelease.contains("signingConfigs.getByName(\"debug\")"))
        assertFalse(enhancedRelease.contains("COMMON_STORE\", \"true\""))

        val brand = functionBody(script, "applyXiaoheiEnhancedBrand")
        assertTrue(brand.contains("applicationIdSuffix = \".common\""))
        assertTrue(brand.contains("COMMON_BASE\", \"true\""))
        assertTrue(brand.contains("COMMON_ENHANCED\", \"true\""))
        assertTrue(brand.contains("COMMON_STORE\", \"false\""))
        assertFalse(brand.contains("COMMON_PUBLIC_PREVIEW\", \"true\""))
        assertTrue(brand.contains("小黑·增强"))
        assertTrue(brand.contains("xiaoheiCleartextAllowed\"] = \"true\""))

        assertTrue(script.contains("outputFileName = \"app-common-enhanced-release.apk\""))
        assertTrue(script.contains("outputFileName = \"app-common-release.apk\""))
        assertTrue(script.contains("outputFileName = \"app-common.apk\""))
        assertTrue(
            script.contains(
                "listOf(\"common\", \"commonRelease\", \"commonEnhancedRelease\")"
            )
        )
        assertTrue(script.contains("getByName(\"commonEnhancedRelease\")"))
        assertTrue(script.contains("useXiaoheiCommonOverlay()"))
        assertTrue(script.contains("useXiaoheiPublicPreviewJava()"))
        assertTrue(script.contains("src/publicPreview/java"))
        assertEquals(
            3,
            Regex("""useXiaoheiPublicPreviewJava\(\)""").findAll(script).count()
        )
        assertFalse(
            Regex("""getByName\("common"\)[^{]*\{[^}]*useXiaoheiPublicPreviewJava\(\)""")
                .containsMatchIn(script)
        )
    }

    @Test
    fun publicPreview_trimsStoreNativesAndTerminalJni_debugCommonDoesNot() {
        val script = readGradle()
        assertTrue(
            script.contains("listOf(\"commonRelease\", \"commonEnhancedRelease\")")
        )
        val previewTypes = assignmentBody(script, "xiaoheiPublicPreviewBuildTypes")
        assertTrue(previewTypes.contains("\"commonRelease\""))
        assertTrue(previewTypes.contains("\"commonEnhancedRelease\""))
        assertFalse(previewTypes.contains("\"common\""))
        assertFalse(previewTypes.contains("\"debug\""))

        val jniExcludes = assignmentBody(script, "xiaoheiPublicPreviewJniExcludes")
        assertTrue(jniExcludes.contains("libffmpegkit.so"))
        assertTrue(jniExcludes.contains("libmlkit_google_ocr_pipeline.so"))
        assertTrue(jniExcludes.contains("libbusybox.so"))
        assertTrue(jniExcludes.contains("libbash.so"))
        assertTrue(jniExcludes.contains("liboperit_proot.so"))
        assertTrue(jniExcludes.contains("libsudo.so"))
        assertTrue(script.contains("xiaoheiPublicPreviewJniExcludes.forEach"))

        val common = buildTypeBody(script, "common")
        assertFalse(common.contains("libbusybox.so"))
        assertFalse(common.contains("COMMON_STORE\", \"true\""))
        assertTrue(common.contains("applyXiaoheiEnhancedBrand()"))
        assertFalse(common.contains("isDebuggable = false"))
    }

    @Test
    fun publicPreview_doesNotDependOnHomemadeFfmpegAar() {
        val script = readGradle()
        assertFalse(script.contains("implementation(files(\"libs/ffmpeg-kit-local.aar\"))"))
        assertFalse(script.contains("commonReleaseImplementation"))
        assertFalse(script.contains("commonEnhancedReleaseImplementation"))
        assertTrue(
            script.contains(
                "listOf(\"debug\", \"release\", \"common\", \"clone\", \"nightly\")"
            )
        )
        assertTrue(
            script.contains(
                "add(\"\${buildTypeName}Implementation\", files(\"libs/ffmpeg-kit-local.aar\"))"
            )
        )
        assertTrue(script.contains("smart-exception-common:0.2.1"))
        assertTrue(script.contains("smart-exception-java:0.2.1"))

        val preBuild = blockAfter(script, "tasks.named(\"preBuild\")")
        assertTrue(preBuild.contains("syncMainAssets"))
        assertFalse(preBuild.contains("verifyExternallyBuiltNativeLibraries"))
        assertTrue(script.contains("dependsOn(verifyExternallyBuiltNativeLibraries)"))
        assertTrue(script.contains("xiaoheiPublicPreviewPreBuildPrefixes"))
        assertTrue(script.contains("preCommonRelease"))
        assertTrue(script.contains("preCommonEnhancedRelease"))
    }

    @Test
    fun publicPreview_overridesVersion_otherVariantsKeepDefault() {
        val script = readGradle()
        assertTrue(script.contains("versionCode = 46"))
        assertTrue(script.contains("versionName = \"1.12.1\""))
        assertTrue(script.contains("xiaoheiPublicPreviewVersionCode = 47"))
        assertTrue(script.contains("xiaoheiPublicPreviewVersionName = \"1.0.0-preview.1\""))
        assertTrue(script.contains("versionCodeOverride = xiaoheiPublicPreviewVersionCode"))
        assertTrue(script.contains("versionNameOverride = xiaoheiPublicPreviewVersionName"))
        assertTrue(script.contains("buildType.name in xiaoheiPublicPreviewBuildTypes"))

        val common = buildTypeBody(script, "common")
        val debug = blockAfter(script, "debug {")
        val release = blockAfter(script, "release {")
        assertFalse(common.contains("versionCodeOverride"))
        assertFalse(debug.contains("versionCodeOverride"))
        assertFalse(release.contains("versionCodeOverride"))
        assertFalse(common.contains("1.0.0-preview.1"))
    }

    @Test
    fun storeNativeTrim_isStoreOrPublicPreview_withoutCollapsingEnhanced() {
        assertTrue(
            CommonBaseProfile.resolveStoreNativeTrim(
                commonStore = true,
                commonPublicPreview = false
            )
        )
        assertTrue(
            CommonBaseProfile.resolveStoreNativeTrim(
                commonStore = false,
                commonPublicPreview = true
            )
        )
        assertTrue(
            CommonBaseProfile.resolveStoreNativeTrim(
                commonStore = true,
                commonPublicPreview = true
            )
        )
        assertFalse(
            CommonBaseProfile.resolveStoreNativeTrim(
                commonStore = false,
                commonPublicPreview = false
            )
        )
        assertTrue(
            CommonBaseProfile.resolveEnhancedDevice(
                commonBase = true,
                commonStore = false,
                commonEnhanced = true
            )
        )
        assertFalse(
            CommonBaseProfile.resolveEnhancedDevice(
                commonBase = true,
                commonStore = true,
                commonEnhanced = true
            )
        )
    }

    @Test
    fun rotationSigning_isNotBroadenedToPreviewVariants() {
        val script = readGradle()
        assertTrue(script.contains("it.name == \"assembleRelease\""))
        assertTrue(script.contains("it.name == \"assembleNightly\""))
        assertTrue(script.contains("assembleCommonEnhancedRelease"))
        assertFalse(
            script.contains("finalizedBy(signRotatedReleaseApk)") &&
                Regex("""name == "assembleCommonEnhancedRelease"""")
                    .containsMatchIn(script)
        )
    }

    private fun readGradle(): String {
        val candidates =
            listOf(File("build.gradle.kts"), File("app/build.gradle.kts"))
        val file = candidates.firstOrNull { it.isFile }
        assertTrue("app/build.gradle.kts must exist: $candidates", file != null)
        return file!!.readText(Charsets.UTF_8)
    }

    private fun buildTypeBody(script: String, name: String): String {
        return blockAfter(script, "create(\"$name\")")
    }

    private fun functionBody(script: String, name: String): String {
        return blockAfter(script, "fun com.android.build.api.dsl.ApplicationBuildType.$name()")
    }

    private fun assignmentBody(script: String, name: String): String {
        val start = script.indexOf("val $name")
        assertTrue("missing val $name", start >= 0)
        val listOf = script.indexOf("listOf", start)
        assertTrue("val $name is not listOf", listOf >= 0)
        val paren = script.indexOf('(', listOf)
        assertTrue("val $name missing listOf(", paren >= 0)
        var depth = 0
        for (cursor in paren until script.length) {
            when (script[cursor]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        return script.substring(paren, cursor + 1)
                    }
                }
            }
        }
        throw AssertionError("unclosed val $name")
    }

    private fun blockAfter(script: String, header: String): String {
        val start = script.indexOf(header)
        assertTrue("missing $header", start >= 0)
        val brace = script.indexOf('{', start)
        var depth = 0
        for (index in brace until script.length) {
            when (script[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return script.substring(brace, index + 1)
                    }
                }
            }
        }
        throw AssertionError("unclosed $header")
    }
}
