import com.android.build.api.artifact.SingleArtifact
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    id("io.objectbox")
    id("kotlin-kapt")
}

// Must match CommonBaseAssetPolicy.excludedPackagedRelativePaths. Applied only to the
// common, commonRelease, and commonEnhancedRelease buildTypes via SingleArtifact.ASSETS
// transform (merged assets, including :terminal).
val commonBaseExcludedPackagedAssets =
    linkedSetOf(
        "accessibility.apk",
        "desktop.apk",
        "shizuku.apk",
        "subpack/android.apk",
        "subpack/windows.zip",
        "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz",
    )

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

data class SttModelAsset(
    val targetPath: String,
    val sourceUrl: String,
    val expectedBytes: Long,
    val expectedSha256: String,
)

data class ApkRotationSigningConfig(
    val apksigner: File,
    val oldStoreFile: File,
    val oldStorePassword: String,
    val oldKeyAlias: String,
    val oldKeyPassword: String,
    val newStoreFile: File,
    val newStorePassword: String,
    val newKeyAlias: String,
    val newKeyPassword: String,
    val lineageFile: File,
)

fun requiredLocalProperty(name: String): String {
    val value = localProperties.getProperty(name)?.trim()
    require(!value.isNullOrEmpty()) {
        "local.properties must define $name for Release/Nightly APK rotation signing"
    }
    return value
}

fun configuredFileProperty(name: String): File {
    val configuredPath = File(requiredLocalProperty(name))
    val resolvedPath = if (configuredPath.isAbsolute) configuredPath else rootProject.file(configuredPath)
    require(resolvedPath.isFile) {
        "Configured $name does not point to a file: ${resolvedPath.path}"
    }
    return resolvedPath
}

fun loadApkRotationSigningConfig(): ApkRotationSigningConfig {
    val sdkDirectory = File(requiredLocalProperty("sdk.dir"))
    require(sdkDirectory.isDirectory) {
        "Configured sdk.dir does not point to a directory: ${sdkDirectory.path}"
    }

    val apksignerName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "apksigner.bat"
    } else {
        "apksigner"
    }
    val apksigner = sdkDirectory.resolve("build-tools/35.0.0/$apksignerName")
    require(apksigner.isFile) {
        "Android build-tools 35.0.0 apksigner is required: ${apksigner.path}"
    }

    return ApkRotationSigningConfig(
        apksigner = apksigner,
        oldStoreFile = configuredFileProperty("RELEASE_STORE_FILE"),
        oldStorePassword = requiredLocalProperty("RELEASE_STORE_PASSWORD"),
        oldKeyAlias = requiredLocalProperty("RELEASE_KEY_ALIAS"),
        oldKeyPassword = requiredLocalProperty("RELEASE_KEY_PASSWORD"),
        newStoreFile = configuredFileProperty("APK_ROTATION_NEW_STORE_FILE"),
        newStorePassword = requiredLocalProperty("APK_ROTATION_NEW_STORE_PASSWORD"),
        newKeyAlias = requiredLocalProperty("APK_ROTATION_NEW_KEY_ALIAS"),
        newKeyPassword = requiredLocalProperty("APK_ROTATION_NEW_KEY_PASSWORD"),
        lineageFile = configuredFileProperty("APK_ROTATION_LINEAGE_FILE"),
    )
}

fun signApkWithRotation(apkFile: File) {
    require(apkFile.isFile) { "APK to sign was not produced: ${apkFile.path}" }
    val config = loadApkRotationSigningConfig()
    val rotatedApk = apkFile.resolveSibling(".${apkFile.name}.rotation-signing")
    require(!rotatedApk.exists()) {
        "Refusing to overwrite an existing rotation signing output: ${rotatedApk.path}"
    }

    // API 28 is the first platform that selects V3 and understands proof-of-rotation;
    // API 26/27 therefore continue to select the old signer from the V2 block.
    val signingArguments = listOf(
        "sign",
        "--in", apkFile.path,
        "--out", rotatedApk.path,
        "--min-sdk-version", "26",
        "--v1-signing-enabled", "false",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--v4-signing-enabled", "false",
        "--lineage", config.lineageFile.path,
        "--rotation-min-sdk-version", "28",
        "--ks", config.oldStoreFile.path,
        "--ks-type", "PKCS12",
        "--ks-key-alias", config.oldKeyAlias,
        "--ks-pass", "env:OPERIT_OLD_STORE_PASSWORD",
        "--key-pass", "env:OPERIT_OLD_KEY_PASSWORD",
        "--next-signer",
        "--ks", config.newStoreFile.path,
        "--ks-type", "PKCS12",
        "--ks-key-alias", config.newKeyAlias,
        "--ks-pass", "env:OPERIT_NEW_STORE_PASSWORD",
        "--key-pass", "env:OPERIT_NEW_KEY_PASSWORD",
    )

    project.exec {
        commandLine(listOf(config.apksigner.path) + signingArguments)
        environment("OPERIT_OLD_STORE_PASSWORD", config.oldStorePassword)
        environment("OPERIT_OLD_KEY_PASSWORD", config.oldKeyPassword)
        environment("OPERIT_NEW_STORE_PASSWORD", config.newStorePassword)
        environment("OPERIT_NEW_KEY_PASSWORD", config.newKeyPassword)
    }

    project.exec {
        commandLine(config.apksigner.path, "verify", "--verbose", "--print-certs", rotatedApk.path)
    }

    Files.move(
        rotatedApk.toPath(),
        apkFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
    )
}

val requiredExternallyBuiltNativeLibraries =
    listOf(
        file("src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so"),
    )

val ffmpegKitLocalAar = file("libs/ffmpeg-kit-local.aar")
val requiredFfmpegKitArm64Libraries =
    setOf(
        "jni/arm64-v8a/libavcodec.so",
        "jni/arm64-v8a/libavdevice.so",
        "jni/arm64-v8a/libavfilter.so",
        "jni/arm64-v8a/libavformat.so",
        "jni/arm64-v8a/libavutil.so",
        "jni/arm64-v8a/libc++_shared.so",
        "jni/arm64-v8a/libffmpegkit.so",
        "jni/arm64-v8a/libffmpegkit_abidetect.so",
        "jni/arm64-v8a/libswresample.so",
        "jni/arm64-v8a/libswscale.so",
    )

val verifyExternallyBuiltNativeLibraries by tasks.registering {
    description = "Checks native libraries built outside Gradle before Android packaging."
    group = "verification"
    inputs.property(
        "requiredLibraries",
        requiredExternallyBuiltNativeLibraries.map { library -> library.path },
    )
    inputs.property("ffmpegKitAar", ffmpegKitLocalAar.path)
    inputs.property("ffmpegKitArm64Libraries", requiredFfmpegKitArm64Libraries)
    outputs.upToDateWhen { false }

    doLast {
        val invalidLibraries =
            requiredExternallyBuiltNativeLibraries.filter { library ->
                !library.isFile || library.length() == 0L
            }
        require(invalidLibraries.isEmpty()) {
            "Missing or empty externally built native library: " +
                invalidLibraries.joinToString { library -> library.path } +
                ". Run tools/native_ripgrep/build_native_ripgrep.ps1 before packaging."
        }

        require(ffmpegKitLocalAar.isFile && ffmpegKitLocalAar.length() > 0L) {
            "Missing or empty FFmpegKit AAR: ${ffmpegKitLocalAar.path}. " +
                "Build it with tools/ffmpeg/build_ffmpeg_kit_wsl.sh and import it with " +
                "tools/ffmpeg/import_local_ffmpeg_kit.ps1 before packaging."
        }

        ZipFile(ffmpegKitLocalAar).use { archive ->
            val invalidEntries =
                requiredFfmpegKitArm64Libraries.filter { entryName ->
                    val entry = archive.getEntry(entryName)
                    entry == null || entry.size <= 0L
                }
            require(invalidEntries.isEmpty()) {
                "FFmpegKit AAR is missing or contains empty arm64 native libraries: " +
                    invalidEntries.joinToString()
            }
        }
    }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

fun parseSttModelAssetManifest(manifestFile: File): List<SttModelAsset> {
    return manifestFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapIndexed { index, line ->
            val parts = line.split("|")
            require(parts.size == 6) {
                "Invalid STT model asset manifest line ${index + 1}: expected 6 fields"
            }
            val targetPath = parts[0]
            require(!targetPath.startsWith("/") && !targetPath.contains("..") && !targetPath.contains('\\')) {
                "Invalid STT model asset target path: $targetPath"
            }
            SttModelAsset(
                targetPath = targetPath,
                sourceUrl = parts[1],
                expectedBytes = parts[2].toLong(),
                expectedSha256 = parts[3].lowercase(),
            )
        }
}

fun verifySttModelAsset(file: File, asset: SttModelAsset): Boolean {
    return file.isFile &&
        file.length() == asset.expectedBytes &&
        sha256(file) == asset.expectedSha256
}

fun downloadSttModelAsset(asset: SttModelAsset, destination: File) {
    destination.parentFile.mkdirs()
    require(destination.parentFile.isDirectory) {
        "Unable to create STT model asset directory: ${destination.parent}"
    }

    val tempFile = File(destination.parentFile, "${destination.name}.download")
    if (tempFile.exists()) {
        tempFile.delete()
    }

    val connection = URI(asset.sourceUrl).toURL().openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 30_000
    connection.readTimeout = 120_000
    connection.setRequestProperty("User-Agent", "Operit Android build STT asset sync")
    try {
        val responseCode = connection.responseCode
        require(responseCode in 200..299) {
            "Unable to download ${asset.targetPath}: HTTP $responseCode from ${asset.sourceUrl}"
        }
        connection.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } finally {
        connection.disconnect()
    }

    require(verifySttModelAsset(tempFile, asset)) {
        "Downloaded STT model asset failed verification: ${asset.targetPath}"
    }
    Files.move(tempFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

val sttModelAssetsManifestFile = layout.projectDirectory.file("config/stt-model-assets.properties")
val generatedSttModelAssetsDir = layout.buildDirectory.dir("generated/stt-model-assets")
val generatedMainAssetsDir = layout.buildDirectory.dir("generated/main-assets")

// common debug remains an engineering APK. Public preview enhanced release is a
// separate non-debug type with the same overlay, package id, and brand.
val xiaoheiCommonOverlayBuildTypes =
    listOf("common", "commonRelease", "commonEnhancedRelease")
val xiaoheiPublicPreviewBuildTypes =
    listOf("commonRelease", "commonEnhancedRelease")
val xiaoheiNonPublicFfmpegAarBuildTypes =
    listOf("debug", "release", "common", "clone", "nightly")
val xiaoheiPublicPreviewVersionCode = 47
val xiaoheiPublicPreviewVersionName = "1.0.0-preview.1"
val xiaoheiPublicPreviewJniExcludes =
    listOf(
        "lib/arm64-v8a/libavcodec.so",
        "lib/arm64-v8a/libavdevice.so",
        "lib/arm64-v8a/libavfilter.so",
        "lib/arm64-v8a/libavformat.so",
        "lib/arm64-v8a/libavutil.so",
        "lib/arm64-v8a/libffmpegkit.so",
        "lib/arm64-v8a/libffmpegkit_abidetect.so",
        "lib/arm64-v8a/libswresample.so",
        "lib/arm64-v8a/libswscale.so",
        "lib/arm64-v8a/libc++_shared.so",
        "lib/arm64-v8a/libmediapipe_tasks_text_jni.so",
        "lib/arm64-v8a/libmlkit_google_ocr_pipeline.so",
        // Terminal JNI stays in the private common debug APK. Public preview
        // packages do not ship a terminal environment.
        "lib/arm64-v8a/libbusybox.so",
        "lib/arm64-v8a/libbash.so",
        "lib/arm64-v8a/liboperit_proot.so",
        "lib/arm64-v8a/liboperit_loader.so",
        "lib/arm64-v8a/libsudo.so",
        // Common store has no filesystem/grep tool or TensorFlow consumer.
        "lib/arm64-v8a/liboperit_ripgrep.so",
        "lib/arm64-v8a/libtensorflowlite_jni.so",
        // Store STT is sherpa-ncnn. sherpa-mnn JNI is unreachable (factory
        // has no SHERPA_MNN; prefs remap it to NCNN) and is not packaged.
        "lib/arm64-v8a/libsherpa-mnn-jni.so",
    )

fun com.android.build.api.dsl.ApplicationBuildType.applyXiaoheiEnhancedBrand() {
    applicationIdSuffix = ".common"
    buildConfigField("boolean", "COMMON_BASE", "true")
    buildConfigField("boolean", "COMMON_ENHANCED", "true")
    buildConfigField("boolean", "COMMON_STORE", "false")
    manifestPlaceholders["xiaoheiDspPermission"] =
        "io.github.toolazytoname.xiaohei.permission.WAKEWORD_EVENT"
    manifestPlaceholders["xiaoheiDspCompanionPackage"] = "io.github.toolazytoname.xiaohei.dsp"
    manifestPlaceholders["xiaoheiCleartextAllowed"] = "true"
    manifestPlaceholders["xiaoheiAppLabel"] = "小黑·增强"
    manifestPlaceholders["xiaoheiLauncherIcon"] = "@mipmap/ic_launcher_xiaohei_enhanced"
    manifestPlaceholders["xiaoheiLauncherRoundIcon"] = "@mipmap/ic_launcher_xiaohei_enhanced_round"
    resValue("string", "app_name", "小黑·增强")
}

fun com.android.build.api.dsl.AndroidSourceSet.useXiaoheiCommonOverlay() {
    manifest.srcFile("src/common/AndroidManifest.xml")
    java.srcDir("src/common/java")
    kotlin.srcDir("src/common/java")
    kotlin.srcDir("src/common/kotlin")
    res.srcDir("src/common/res")
    assets.srcDir("src/common/assets")
    resources.srcDir("src/common/resources")
    aidl.srcDir("src/common/aidl")
    renderscript.srcDir("src/common/rs")
    jniLibs.srcDir("src/common/jniLibs")
    shaders.srcDir("src/common/shaders")
}

fun com.android.build.api.dsl.AndroidSourceSet.useXiaoheiPublicPreviewJava() {
    // Official FFmpegKit v6.0 Java only. Do not add this to common debug:
    // that variant still consumes ffmpeg-kit-local.aar.
    java.srcDir("src/publicPreview/java")
    assets.srcDir("src/publicPreview/assets")
}

val syncSttModelAssets by tasks.registering {
    description = "Downloads and verifies generated assets for local STT recognition."
    group = "build setup"

    inputs.file(sttModelAssetsManifestFile)
    outputs.dir(generatedSttModelAssetsDir)
    outputs.upToDateWhen { false }

    doLast {
        val manifestFile = sttModelAssetsManifestFile.asFile
        val assets = parseSttModelAssetManifest(manifestFile)
        val outputRoot = generatedSttModelAssetsDir.get().asFile
        outputRoot.mkdirs()

        val outputRootPath = outputRoot.toPath().toAbsolutePath().normalize()
        val expectedFiles = mutableSetOf<File>()

        assets.forEach { asset ->
            val destinationPath = outputRootPath.resolve(asset.targetPath).normalize()
            require(destinationPath.startsWith(outputRootPath)) {
                "STT model asset target escapes generated assets directory: ${asset.targetPath}"
            }
            val destination = destinationPath.toFile()
            expectedFiles.add(destination.canonicalFile)

            if (!verifySttModelAsset(destination, asset)) {
                if (destination.exists() && !destination.delete()) {
                    error("Unable to replace invalid STT model asset: ${destination.path}")
                }
                downloadSttModelAsset(asset, destination)
            }

            require(verifySttModelAsset(destination, asset)) {
                "STT model asset verification failed after sync: ${asset.targetPath}"
            }
        }

        outputRoot.walkBottomUp()
            .filter { it.isFile && it.canonicalFile !in expectedFiles }
            .forEach { file ->
                require(file.delete()) {
                    "Unable to remove stale STT model asset: ${file.path}"
                }
            }
        outputRoot.walkBottomUp()
            .filter { it.isDirectory && it != outputRoot && it.list()?.isEmpty() == true }
            .forEach { directory ->
                require(directory.delete()) {
                    "Unable to remove empty STT model asset directory: ${directory.path}"
                }
            }
    }
}

val syncMainAssets by tasks.registering(Sync::class) {
    description = "Assembles application assets with verified generated STT model files."
    group = "build setup"
    dependsOn(syncSttModelAssets)

    from("src/main/assets") {
        exclude("models/**")
    }
    from(generatedSttModelAssetsDir)
    into(generatedMainAssetsDir)
}

android {
    namespace = "com.ai.assistance.operit"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf(generatedMainAssetsDir.get().asFile))
        }
    }

    signingConfigs {
        val releaseKeystorePath = localProperties.getProperty("RELEASE_STORE_FILE")
        val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
        val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
        val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")

        if (releaseKeystorePath != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null &&
            File(releaseKeystorePath).exists()
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
            create("commonStore") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    defaultConfig {
        // Own reverse-domain identity (xiaohei.weichao.studio). Intent action
        // strings that still carry the upstream prefix stay valid: they are
        // constants matched between sender/receiver, not the package identity.
        applicationId = "studio.weichao.xiaohei"
        minSdk = 26
        targetSdk = 36
        versionCode = 46
        versionName = "1.12.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            // Explicitly specify the ABIs we package for the app process.
            // terminal now also ships x86_64 runtime binaries for the Android Studio emulator,
            // while the rest of the app remains primarily ARM-focused.
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                // Pin the revisions used by the voice lifecycle and real-device diagnostics.
                // Explicit arguments also replace an existing CMake cache's old "master" refs.
                arguments(
                    "-DOPERIT_SHERPA_NCNN_GIT_REF=c61e50d61e9fbed5972afa4d95bc560e168affe2",
                    "-DOPERIT_NCNN_GIT_REF=713bd01928b350150ad5594218512b8669588fef"
                )
            }
        }

        buildConfigField("boolean", "COMMON_BASE", "false")
        buildConfigField("boolean", "COMMON_STORE", "false")
        buildConfigField("boolean", "COMMON_ENHANCED", "false")
        buildConfigField("boolean", "COMMON_PUBLIC_PREVIEW", "false")
    }

    buildTypes {
        val releaseSigningConfig = signingConfigs.findByName("release")

        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            resValue("string", "app_name", "Operit Debug")
        }
        create("clone") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".clone"
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "Operit Clone")
        }
        create("common") {
            initWith(getByName("debug"))
            // Engineering enhanced APK. Keep debug signing so existing installs
            // continue to update. Public preview uses commonEnhancedRelease.
            matchingFallbacks += listOf("debug")
            applyXiaoheiEnhancedBrand()
            // Large unused assets are dropped after merge by the common-base
            // SingleArtifact.ASSETS transform. Do not exclude them from sourceSets.main
            // or generatedMainAssetsDir (those inputs are shared with other buildTypes).
        }
        create("commonRelease") {
            initWith(getByName("release"))
            // Store identity is exactly the reverse-domain id, no suffix.
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField("boolean", "COMMON_BASE", "true")
            buildConfigField("boolean", "COMMON_STORE", "true")
            // Store identity cannot own the enhanced companion signature permission.
            manifestPlaceholders["xiaoheiDspPermission"] = "studio.weichao.xiaohei.permission.DSP_DISABLED"
            manifestPlaceholders["xiaoheiDspCompanionPackage"] = "studio.weichao.xiaohei"
            manifestPlaceholders["xiaoheiCleartextAllowed"] = "false"
            manifestPlaceholders["xiaoheiAppLabel"] = "小黑"
            manifestPlaceholders["xiaoheiLauncherIcon"] = "@mipmap/ic_launcher_simple"
            manifestPlaceholders["xiaoheiLauncherRoundIcon"] = "@mipmap/ic_launcher_simple_round"
            buildConfigField("boolean", "COMMON_ENHANCED", "false")
            buildConfigField("boolean", "COMMON_PUBLIC_PREVIEW", "true")
            resValue("string", "app_name", "小黑")
            // Same keystore as release, but v2+v3 only. Do not use APK rotation signing.
            // If that config is absent, leave unsigned. Never assign debug signing.
            signingConfig = signingConfigs.findByName("commonStore")
            // Same six-asset trim as common, wired below via SingleArtifact.ASSETS.
        }
        create("commonEnhancedRelease") {
            initWith(getByName("release"))
            // Same package id and brand as common, official commonStore signing.
            // matchingFallbacks must be release so library modules that only
            // publish debug/release resolve. Never inherit debug signing.
            matchingFallbacks += listOf("release")
            isDebuggable = false
            applyXiaoheiEnhancedBrand()
            // Public preview trim is independent of COMMON_STORE so enhancedDevice stays open.
            buildConfigField("boolean", "COMMON_PUBLIC_PREVIEW", "true")
            // If commonStore is absent, leave unsigned. Never assign debug signing.
            signingConfig = signingConfigs.findByName("commonStore")
        }
        create("nightly") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    applicationVariants.all {
        if (buildType.name in xiaoheiPublicPreviewBuildTypes) {
            outputs.all {
                val apkOutput = this as com.android.build.gradle.api.ApkVariantOutput
                apkOutput.versionCodeOverride = xiaoheiPublicPreviewVersionCode
                apkOutput.versionNameOverride = xiaoheiPublicPreviewVersionName
            }
        }
        if (buildType.name == "nightly") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-nightly.apk"
            }
        }
        if (buildType.name == "clone") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-clone.apk"
            }
        }
        if (buildType.name == "common") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-common.apk"
            }
        }
        if (buildType.name == "commonRelease") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-common-release.apk"
            }
        }
        if (buildType.name == "commonEnhancedRelease") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "app-common-enhanced-release.apk"
            }
        }
    }
    sourceSets {
        // common maps src/common by AGP convention. Non-debug types need an explicit overlay.
        getByName("commonRelease") {
            useXiaoheiCommonOverlay()
            useXiaoheiPublicPreviewJava()
        }
        getByName("commonEnhancedRelease") {
            useXiaoheiCommonOverlay()
            useXiaoheiPublicPreviewJava()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    packaging {
        
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE-EPL-1.0.txt"
            excludes += "LICENSE-EPL-1.0.txt"
            excludes += "/META-INF/LICENSE-EDL-1.0.txt"
            excludes += "LICENSE-EDL-1.0.txt"
            
            // Resolve merge conflicts for document libraries
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "META-INF/versions/9/module-info.class"
            
            // Fix for duplicate Netty files
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            
            // Fix for any other potential duplicate files
            pickFirsts += "**/*.so"
        }
    }
//    aaptOptions {
//        noCompress += "tflite"
//    }
}

abstract class FilterCommonPackagedAssetsTask : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val excludedRelativePaths: SetProperty<String>

    @TaskAction
    fun filterAssets() {
        val excluded = excludedRelativePaths.get()
        excluded.forEach { relative ->
            require(!relative.startsWith("/") && !relative.contains("..") && !relative.contains('\\')) {
                "Invalid common-base excluded asset path: $relative"
            }
        }
        fileSystemOperations.sync {
            from(inputDir)
            into(outputDir)
            exclude { element ->
                val relative = element.relativePath.pathString.replace(File.separatorChar, '/')
                relative in excluded
            }
        }
    }
}

androidComponents {
    xiaoheiCommonOverlayBuildTypes.forEach { buildTypeName ->
        onVariants(selector().withBuildType(buildTypeName)) { variant ->
            val capitalizedName =
                variant.name.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            val filterTask =
                tasks.register<FilterCommonPackagedAssetsTask>("filter${capitalizedName}PackagedAssets") {
                    description =
                        "Removes common-base unreachable assets from merged packaging for this variant only."
                    group = "build"
                    excludedRelativePaths.set(commonBaseExcludedPackagedAssets)
                }
            variant.artifacts
                .use(filterTask)
                .wiredWithDirectories(
                    FilterCommonPackagedAssetsTask::inputDir,
                    FilterCommonPackagedAssetsTask::outputDir,
                )
                .toTransform(SingleArtifact.ASSETS)
        }
    }
    xiaoheiPublicPreviewBuildTypes.forEach { buildTypeName ->
        onVariants(selector().withBuildType(buildTypeName)) { variant ->
            xiaoheiPublicPreviewJniExcludes.forEach { path ->
                variant.packaging.jniLibs.excludes.add(path)
            }
        }
    }
}

val signRotatedReleaseApk by tasks.registering {
    description = "Signs the Release APK with the legacy V2 signer and rotated V3 signer."
    group = "distribution"
    dependsOn("packageRelease")
    doLast {
        signApkWithRotation(
            project.layout.buildDirectory
                .file("outputs/apk/release/app-release.apk")
                .get()
                .asFile,
        )
    }
}

val signRotatedNightlyApk by tasks.registering {
    description = "Signs the Nightly APK with the legacy V2 signer and rotated V3 signer."
    group = "distribution"
    dependsOn("packageNightly")
    doLast {
        signApkWithRotation(
            project.layout.buildDirectory
                .file("outputs/apk/nightly/app-nightly.apk")
                .get()
                .asFile,
        )
    }
}

// Exact names only. Do not broaden to assembleCommonRelease or
// assembleCommonEnhancedRelease; those variants must not run Operit
// nightly/release APK rotation signing.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(signRotatedReleaseApk)
}

tasks.matching { it.name == "assembleNightly" }.configureEach {
    finalizedBy(signRotatedNightlyApk)
}

tasks.named("preBuild") {
    dependsOn(syncMainAssets)
}

val xiaoheiPublicPreviewPreBuildPrefixes =
    listOf("preCommonRelease", "preCommonEnhancedRelease")
tasks.matching { task ->
    task.name.startsWith("pre") &&
        task.name.endsWith("Build") &&
        task.name != "preBuild" &&
        xiaoheiPublicPreviewPreBuildPrefixes.none { prefix -> task.name.startsWith(prefix) }
}.configureEach {
    dependsOn(verifyExternallyBuiltNativeLibraries)
}

tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(syncMainAssets)
}

configurations.all {
    exclude(group = "pl.droidsonroids.gif", module = "android-gif-drawable")
    // All Compose variants converge on graphics-path transitively; the local AAR
    // below is the single arm64 implementation used by the app.
    exclude(group = "androidx.graphics", module = "graphics-path")
    // Local RELRO-rebuilt Filament AARs replace the Maven modules globally,
    // including transitive pulls from gltfio.
    exclude(group = "com.google.android.filament", module = "filament-android")
    exclude(group = "com.google.android.filament", module = "filament-utils-android")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.github.jelmerk:hnswlib-core:1.2.1")
    implementation(project(":dragonbones"))
    implementation(project(":terminal"))
    implementation(project(":mnn"))
    implementation(project(":llama"))
    implementation(project(":mmd"))
    implementation(project(":fbx"))
    implementation(project(":showerclient"))
    implementation(project(":quickjs"))

    // glTF runtime rendering (Filament 1.69.2). The two JNI AARs are source
    // rebuilt with 16KB LOAD/RELRO alignment; gltfio's official arm64 JNI
    // already passes the same static gate and remains Maven-resolved.
    implementation(files("libs/filament-android-1.69.2-arm64-relro.aar"))
    implementation("com.google.android.filament:gltfio-android:1.69.2")
    implementation(files("libs/filament-utils-android-1.69.2-arm64-relro.aar"))
    // graphics-path 1.0.1 is transitive from ui-graphics-android.
    // Replace only its arm64 native member with the source-rebuilt 16KB RELRO AAR;
    // store/enhanced packaging is arm64-only, while classes/resources remain byte-identical.
    implementation(libs.androidx.ui.graphics.android) {
        exclude(group = "androidx.graphics", module = "graphics-path")
    }
    implementation(files("libs/graphics-path-1.0.1-16kb-arm64.aar"))
    // Homemade FFmpegKit AAR is for non-public variants only. Public preview
    // compiles official FFmpegKit Java from src/publicPreview/java and does not
    // package those natives.
    xiaoheiNonPublicFfmpegAarBuildTypes.forEach { buildTypeName ->
        add("${buildTypeName}Implementation", files("libs/ffmpeg-kit-local.aar"))
    }
    implementation("com.arthenica:smart-exception-common:0.2.1")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.androidx.animation.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.activity.ktx)

    // Desugaring support for modern Java APIs on older Android
    coreLibraryDesugaring(libs.desugar.jdk)

    // ML Kit - 文本识别
    implementation(libs.mlkit.text.recognition)
    // ML Kit - 多语言识别支持
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.text.devanagari)
    
    implementation(libs.zxing.core)
    
    // diff
    implementation(libs.java.diff.utils)
    
    // APK解析和修改库
    implementation(libs.android.apksig) // APK签名工具
    implementation(libs.apk.parser) // 用于解析和处理AndroidManifest.xml
    implementation(libs.sable.axml) // 用于Android二进制XML的读写
    implementation(libs.zipalign.java) // 用于处理ZIP文件对齐
    
    // ZIP处理库 - 用于APK解压和重打包
    implementation(libs.commons.compress)
    implementation(libs.commons.io) // 添加Apache Commons IO
    
    // 图片处理库
    implementation(libs.glide) // 用于处理图像
    
    // XML处理
    implementation(libs.androidx.core.ktx)
    
    // libsu - root access library
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")
    
    // Add missing SVG support
    implementation(libs.androidsvg)
    
    // 16KB-aligned android-gif-drawable 1.2.28 (arm64 so rebuilt; rest of AAR unchanged)
    implementation(files("libs/android-gif-drawable-1.2.28-16kb.aar"))
    
    // Image Cropper for background image cropping
    implementation(libs.image.cropper)
    
    // ExoPlayer for video background
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    
    // Material 3 Window Size Class
    implementation(libs.material3.window)
    
    // Window metrics library for foldables and adaptive layouts
    implementation(libs.window)
    implementation(libs.androidx.webkit)

    // Document conversion libraries
    implementation(libs.pdfbox)
    implementation(libs.zip4j)
    
    // 图片加载库
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    
    // LaTeX rendering libraries
    implementation(libs.jlatexmath)
    
    // Base Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlin.reflect)
    
    // UUID dependencies
    implementation(libs.uuid)
    
    // Gson for JSON parsing
    implementation(libs.gson)

    // HJSON dependency for human-friendly JSON parsing
    implementation(libs.hjson)

    // 中文分词库 - Jieba Android
    implementation(libs.jieba)

    // 向量搜索库 - 轻量级实现，适合Android
    implementation(libs.hnswlib.core)
    implementation(libs.hnswlib.utils)
    
    // 用于向量嵌入的TF Lite (如果需要自定义嵌入)
    implementation(libs.tensorflow.lite)
    implementation(libs.mediapipe.tasks.text)
    
    // ONNX Runtime 1.29.0 is rebuilt locally for arm64-v8a with 16KB LOAD/RELRO
    // alignment. The app only uses CPU VAD/VITS paths; NNAPI/XNNPACK/WebGPU
    // provider entry points are not used by this product build.
    implementation(files("libs/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar"))

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx) // Kotlin扩展和协程支持
    kapt(libs.room.compiler) // 使用kapt代替ksp

    // ObjectBox
    implementation(libs.objectbox.kotlin)
    kapt(libs.objectbox.processor)
    implementation(libs.commons.compress.v2)
    implementation(libs.junrar)

    // Compose dependencies - use BOM for version consistency
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // Use BOM version for all Compose dependencies
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)

    // Navigation Compose
    implementation(libs.navigation.compose)

    // Shizuku dependencies
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Tasker Plugin Library
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    
    // WorkManager for scheduled workflows
    implementation(libs.work.runtime.ktx)

    // Network dependencies
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.jsoup)

    // DataStore dependencies
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.preferences.core)

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))

    // Apache POI - for Document processing (DOC, DOCX, etc.)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.scratchpad)

    // Color picker for theme customization
    implementation(libs.colorpicker)
    implementation(libs.backdrop)
    implementation(libs.liquid)
    
    // NanoHTTPD for local web server
    implementation(libs.nanohttpd)

    // 添加测试依赖
    testImplementation(libs.junit)
    
    // Android测试依赖
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    
    // 协程测试依赖
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.coroutines.test)
    
    // 模拟测试框架 - 保留现有的 mockito 并新增 mockk
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.mockito.android)
    
    // // 新增的测试依赖 - mockk 和 kotlin-test
    // testImplementation(libs.mockk)
    // testImplementation(libs.ktor.server.test.host)
    // testImplementation(libs.kotlinx.coroutines.debug)
    // androidTestImplementation(libs.mockk)
    
    implementation(libs.reorderable)

    // Swipe to reveal actions
    implementation(libs.swipe)

    // Coroutine
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.mcp.sdk.client)
    implementation(libs.ktor.client.okhttp)
    
    // Exclude bcprov-jdk15to18 from all configurations to avoid duplicate classes
    configurations.all {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // BouncyCastle - explicitly include jdk18on version to avoid conflicts
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation(libs.okhttp.logging.interceptor)


    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // Glance for Widgets (Compose for Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
