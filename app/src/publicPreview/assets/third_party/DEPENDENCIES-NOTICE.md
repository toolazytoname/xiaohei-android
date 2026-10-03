# Third-party NOTICE (generated, local evidence only)

生成器：`integrations/operit-upstream-build/lgpl-source-package/make_notice.py`。
范围：`integrations/xiaohei-common-base/app/build.gradle.kts` 直接声明 + `gradle/libs.versions.toml` catalog。
**不是**完整传递依赖树。未联网核验 SPDX。许可证来源见「依据」列。
应用主体许可证：**LGPL-3.0-only**（Operit / 共同版衍生，commit `4faa5cd2ae0b5ee2ffa94f21d6e43c5ca011f84a`）。

直接声明条目：134。其中许可证 TODO：0。

## Maven / files() / plugins

| 名称 | 版本 | 坐标 | 配置 | 许可证 | 依据 | 备注 |
|---|---|---|---|---|---|---|
| compose-bom | 2026.02.01 | `androidx.compose:compose-bom:2026.02.01` | implementation | Apache-2.0 | POM compose-bom-2026.02.01.pom |  |
| compose-bom | 2026.02.01 | `androidx.compose:compose-bom:2026.02.01` | androidTestImplementation | Apache-2.0 | POM compose-bom-2026.02.01.pom |  |
| android-gif-drawable-1.2.28-16kb.aar | 1.2.28 | `files("libs/android-gif-drawable-1.2.28-16kb.aar")` | implementation | MIT (+ SKIA BSD-3, GIFLIB, ReLinker Apache-2.0 in upstream LICENSE) | gif-16k-rebuild/README.md | Maven pl.droidsonroids.gif:android-gif-drawable is excluded; arm64 lib rebuilt for 16KB. See gif-16k-rebuild/out/SHA256SUMS. |
| ffmpeg-kit-local.aar | sha256:b292b0425d34… | `files("libs/ffmpeg-kit-local.aar")` | implementation | LGPL-3.0 (FFmpeg --enable-version3; enable_gpl_observed=false, enable_nonfree_observed=false) | ffmpeg-build-config.json | SHA256 b292b0425d3445244832894d3370c46c5b1f80f0b27eb17edb5810b6bc00fffc. 37 license members in AAR: res/raw/license.txt, res/raw/license_cpu_features.txt, res/raw/license_dav1d.… |
| filament-android-1.69.2-arm64-relro.aar | local | `files("libs/filament-android-1.69.2-arm64-relro.aar")` | implementation | Apache-2.0 (Google Filament 1.69.2) | native-16kb-rebuilds/filament/LICENSE-filament.txt; README.md; official tag v1.69.2 | SHA256 d551db3445bb827a38149a1a5acb581cdbf4fd46bb34e6f9fc73336739132d58. 仅替换 arm64 libfilament-jni.so；classes/manifest/其他 ABI 保留官方 AAR。JNI 导出与 RELRO 证据见 native-16kb-rebuilds/fil… |
| filament-utils-android-1.69.2-arm64-relro.aar | local | `files("libs/filament-utils-android-1.69.2-arm64-relro.aar")` | implementation | Apache-2.0 (Google Filament 1.69.2) | native-16kb-rebuilds/filament/LICENSE-filament.txt; README.md; official tag v1.69.2 | SHA256 6185762b717ee553af6cb922cb683bfcfb46e5400db1476d14ab0a695f37a49e. 仅替换 arm64 libfilament-utils-jni.so；classes/manifest/其他 ABI 保留官方 AAR。JNI 导出与 RELRO 证据见 native-16kb-rebuil… |
| graphics-path-1.0.1-16kb-arm64.aar | local | `files("libs/graphics-path-1.0.1-16kb-arm64.aar")` | implementation | Apache-2.0 (AndroidX graphics-path 1.0.1) | native-16kb-rebuilds/graphics-path/LICENSE; README.md; AndroidX commit 8a05a22 | SHA256 551a1ce3e12b0212e8d1f17e6bb6d6d5611c8fdd82aa9cf7c6451245b872b6ca. 仅替换 arm64 libandroidx.graphics.path.so；classes/manifest/其他 ABI 保留官方 AAR。Kotlin/source/JNI/RELRO 证据见 nati… |
| onnxruntime-android-1.29.0-arm64-cpu-16kb.aar | 1.29.0 | `files("libs/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar")` | implementation | MIT (ONNX Runtime v1.29.0 commit 2e2543fbe9fae542f921d47a72d21d5a4ef0b710; CPU-only; onnxruntime_USE_TELEMETRY=OFF). 源码传递依赖许可证未全部核完。 | onnx-16kb-rebuild/README.md; evidence/status.json; evidence/providers.json | Local files() SHA256 263f1ea768438829ee145892aa65f8f3ed7cc5d857438cf40a0bf753c00369fc. Recipe AAR SHA256 263f1ea768438829ee145892aa65f8f3ed7cc5d857438cf40a0bf753c00369fc. Not eq… |
| activity-compose | 1.8.2 | `androidx.activity:activity-compose:1.8.2` | implementation | Apache-2.0 | POM activity-compose-1.8.2.pom |  |
| activity-ktx | 1.7.1 | `androidx.activity:activity-ktx:1.7.1` | implementation | Apache-2.0 | POM activity-ktx-1.7.1.pom |  |
| appcompat | 1.6.1 | `androidx.appcompat:appcompat:1.6.1` | implementation | Apache-2.0 | POM appcompat-1.6.1.pom |  |
| animation | (BOM) | `androidx.compose.animation:animation (BOM/catalog, no version.ref)` | implementation | Apache-2.0 | well-known group androidx.compose.animation |  |
| animation-android | 1.10.4 | `androidx.compose.animation:animation-android:1.10.4` | implementation | Apache-2.0 | POM animation-android-1.10.4.pom |  |
| animation-core | (BOM) | `androidx.compose.animation:animation-core (BOM/catalog, no version.ref)` | implementation | Apache-2.0 | well-known group androidx.compose.animation |  |
| material3 | (BOM) | `androidx.compose.material3:material3 (BOM/catalog, no version.ref)` | implementation | Apache-2.0 | well-known group androidx.compose.material3 |  |
| material3-window-size-class | 1.2.0 | `androidx.compose.material3:material3-window-size-class:1.2.0` | implementation | Apache-2.0 | POM material3-window-size-class-1.2.0.pom |  |
| material-icons-extended | (BOM) | `androidx.compose.material:material-icons-extended (BOM/catalog, no version.ref)` | implementation | Apache-2.0 | well-known group androidx.compose.material |  |
| runtime-android | 1.10.4 | `androidx.compose.runtime:runtime-android:1.10.4` | implementation | Apache-2.0 | POM runtime-android-1.10.4.pom |  |
| ui | (BOM) | `androidx.compose.ui:ui (BOM/catalog, no version.ref)` | implementation | Apache-2.0 | well-known group androidx.compose.ui |  |
| ui-android | 1.10.4 | `androidx.compose.ui:ui-android:1.10.4` | implementation | Apache-2.0 | POM ui-android-1.10.4.pom |  |
| ui-graphics | (BOM) | `androidx.compose.ui:ui-graphics (BOM/catalog, no version.ref)` | implementation | Apache-2.0 | well-known group androidx.compose.ui |  |
| ui-graphics-android | 1.10.4 | `androidx.compose.ui:ui-graphics-android:1.10.4` | implementation | Apache-2.0 | POM ui-graphics-android-1.10.4.pom |  |
| ui-test-junit4 | (BOM) | `androidx.compose.ui:ui-test-junit4 (BOM/catalog, no version.ref)` | androidTestImplementation | Apache-2.0 | well-known group androidx.compose.ui |  |
| ui-test-manifest | (BOM) | `androidx.compose.ui:ui-test-manifest (BOM/catalog, no version.ref)` | debugImplementation | Apache-2.0 | well-known group androidx.compose.ui |  |
| ui-text-android | 1.10.4 | `androidx.compose.ui:ui-text-android:1.10.4` | implementation | Apache-2.0 | POM ui-text-android-1.10.4.pom |  |
| ui-tooling | (BOM) | `androidx.compose.ui:ui-tooling (BOM/catalog, no version.ref)` | debugImplementation | Apache-2.0 | well-known group androidx.compose.ui |  |
| ui-tooling-preview | (BOM) | `androidx.compose.ui:ui-tooling-preview (BOM/catalog, no version.ref)` | implementation | Apache-2.0 | well-known group androidx.compose.ui |  |
| core-ktx | 1.18.0 | `androidx.core:core-ktx:1.18.0` | implementation | Apache-2.0 | POM core-ktx-1.18.0.pom |  |
| datastore-preferences-core | 1.0.0 | `androidx.datastore:datastore-preferences-core:1.0.0` | implementation | Apache-2.0 | POM datastore-preferences-core-1.0.0.pom |  |
| datastore-preferences | 1.0.0 | `androidx.datastore:datastore-preferences:1.0.0` | implementation | Apache-2.0 | POM datastore-preferences-1.0.0.pom |  |
| glance-appwidget | 1.0.0 | `androidx.glance:glance-appwidget:1.0.0` | implementation | Apache-2.0 | POM glance-appwidget-1.0.0.pom |  |
| glance-material3 | 1.0.0 | `androidx.glance:glance-material3:1.0.0` | implementation | Apache-2.0 | POM glance-material3-1.0.0.pom |  |
| lifecycle-runtime-ktx | 2.7.0 | `androidx.lifecycle:lifecycle-runtime-ktx:2.7.0` | implementation | Apache-2.0 | POM lifecycle-runtime-ktx-2.7.0.pom |  |
| navigation-compose | 2.7.7 | `androidx.navigation:navigation-compose:2.7.7` | implementation | Apache-2.0 | POM navigation-compose-2.7.7.pom |  |
| room-compiler | 2.8.4 | `androidx.room:room-compiler:2.8.4` | kapt | Apache-2.0 | POM room-compiler-2.8.4.pom |  |
| room-ktx | 2.8.4 | `androidx.room:room-ktx:2.8.4` | implementation | Apache-2.0 | POM room-ktx-2.8.4.pom |  |
| room-runtime | 2.8.4 | `androidx.room:room-runtime:2.8.4` | implementation | Apache-2.0 | POM room-runtime-2.8.4.pom |  |
| security-crypto | 1.1.0-alpha06 | `androidx.security:security-crypto:1.1.0-alpha06` | implementation | Apache-2.0 | POM security-crypto-1.1.0-alpha06.pom |  |
| espresso-core | 3.5.1 | `androidx.test.espresso:espresso-core:3.5.1` | androidTestImplementation | Apache-2.0 | POM espresso-core-3.5.1.pom |  |
| junit | 1.1.5 | `androidx.test.ext:junit:1.1.5` | androidTestImplementation | Apache-2.0 | POM junit-1.1.5.pom |  |
| rules | 1.5.0 | `androidx.test:rules:1.5.0` | androidTestImplementation | Apache-2.0 | POM rules-1.5.0.pom |  |
| runner | 1.5.2 | `androidx.test:runner:1.5.2` | androidTestImplementation | Apache-2.0 | POM runner-1.5.2.pom |  |
| webkit | 1.12.1 | `androidx.webkit:webkit:1.12.1` | implementation | Apache-2.0 | POM webkit-1.12.1.pom |  |
| window | 1.1.0 | `androidx.window:window:1.1.0` | implementation | Apache-2.0 | POM window-1.1.0.pom |  |
| work-runtime-ktx | 2.9.0 | `androidx.work:work-runtime-ktx:2.9.0` | implementation | Apache-2.0 | POM work-runtime-ktx-2.9.0.pom |  |
| apksig | 8.1.0 | `com.android.tools.build:apksig:8.1.0` | implementation | Apache-2.0 | POM apksig-8.1.0.pom |  |
| desugar_jdk_libs | 2.0.4 | `com.android.tools:desugar_jdk_libs:2.0.4` | coreLibraryDesugaring | GNU General Public License, version 2, with the Classpath Exception (https://github.com/google/desugar_jdk_libs/blob/master/LICENSE) | POM desugar_jdk_libs-2.0.4.pom |  |
| smart-exception-common | 0.2.1 | `com.arthenica:smart-exception-common:0.2.1` | implementation | The 3-Clause BSD License (https://opensource.org/licenses/BSD-3-Clause) | POM smart-exception-common-0.2.1.pom |  |
| smart-exception-java | 0.2.1 | `com.arthenica:smart-exception-java:0.2.1` | implementation | The 3-Clause BSD License (https://opensource.org/licenses/BSD-3-Clause) | POM smart-exception-java-0.2.1.pom |  |
| uuid | 0.8.2 | `com.benasher44:uuid:0.8.2` | implementation | MIT | POM uuid-0.8.2.pom |  |
| androidsvg-aar | 1.4 | `com.caverock:androidsvg-aar:1.4` | implementation | Apache-2.0 | POM androidsvg-aar-1.4.pom |  |
| glide | 4.16.0 | `com.github.bumptech.glide:glide:4.16.0` | implementation | Simplified BSD License (http://www.opensource.org/licenses/bsd-license) ; Apache-2.0 | POM glide-4.16.0.pom |  |
| zipalign-java | 1.2.1 | `com.github.iyxan23:zipalign-java:1.2.1` | implementation | MIT | POM zipalign-java-1.2.1.pom |  |
| hnswlib-core | 0.0.46 | `com.github.jelmerk:hnswlib-core:0.0.46` | implementation | Apache-2.0 | well-known group com.github.jelmerk |  |
| hnswlib-core | 1.2.1 | `com.github.jelmerk:hnswlib-core:1.2.1` | implementation | Apache-2.0 | POM hnswlib-core-1.2.1.pom |  |
| hnswlib-utils | 0.0.46 | `com.github.jelmerk:hnswlib-utils:0.0.46` | implementation | Apache-2.0 | well-known group com.github.jelmerk |  |
| junrar | 7.5.5 | `com.github.junrar:junrar:7.5.5` | implementation | UnRar License (https://github.com/junrar/junrar/blob/master/LICENSE) | POM junrar-7.5.5.pom |  |
| axml | 2.0.0 | `com.github.Sable:axml:2.0.0` | implementation | Apache-2.0 | license-residual-evidence-20261002: axml/Axml.java-header-src.java; official tag 2.0.0 source archive; jitpack-axml-2.0.0.pom | Tag 2.0.0 has no top-level LICENSE file, but its shipped source files carry Apache-2.0 headers; retain the source/header evidence. |
| colorpicker-compose | 1.0.6 | `com.github.skydoves:colorpicker-compose:1.0.6` | implementation | Apache-2.0 | POM colorpicker-compose-1.0.6.pom |  |
| core | 6.0.0 | `com.github.topjohnwu.libsu:core:6.0.0` | implementation | Apache-2.0 | well-known group com.github.topjohnwu.libsu |  |
| nio | 6.0.0 | `com.github.topjohnwu.libsu:nio:6.0.0` | implementation | Apache-2.0 | well-known group com.github.topjohnwu.libsu |  |
| service | 6.0.0 | `com.github.topjohnwu.libsu:service:6.0.0` | implementation | Apache-2.0 | well-known group com.github.topjohnwu.libsu |  |
| accompanist-systemuicontroller | 0.32.0 | `com.google.accompanist:accompanist-systemuicontroller:0.32.0` | implementation | Apache-2.0 | POM accompanist-systemuicontroller-0.32.0.pom |  |
| exoplayer-core | 2.19.1 | `com.google.android.exoplayer:exoplayer-core:2.19.1` | implementation | Apache-2.0 | POM exoplayer-core-2.19.1.pom |  |
| exoplayer-ui | 2.19.1 | `com.google.android.exoplayer:exoplayer-ui:2.19.1` | implementation | Apache-2.0 | POM exoplayer-ui-2.19.1.pom |  |
| exoplayer | 2.19.1 | `com.google.android.exoplayer:exoplayer:2.19.1` | implementation | Apache-2.0 | POM exoplayer-2.19.1.pom |  |
| gltfio-android | 1.69.2 | `com.google.android.filament:gltfio-android:1.69.2` | implementation | Apache-2.0 | POM gltfio-android-1.69.2.pom |  |
| material | 1.10.0 | `com.google.android.material:material:1.10.0` | implementation | Apache-2.0 | POM material-1.10.0.pom |  |
| gson | 2.10.1 | `com.google.code.gson:gson:2.10.1` | implementation | Apache-2.0 | POM gson-2.10.1.pom |  |
| tasks-text | 0.10.11 | `com.google.mediapipe:tasks-text:0.10.11` | implementation | Apache-2.0 | POM tasks-text-0.10.11.pom |  |
| text-recognition-chinese | 16.0.0 | `com.google.mlkit:text-recognition-chinese:16.0.0` | implementation | ML Kit Terms of Service (https://developers.google.com/ml-kit/terms) | POM text-recognition-chinese-16.0.0.pom |  |
| text-recognition-devanagari | 16.0.0 | `com.google.mlkit:text-recognition-devanagari:16.0.0` | implementation | ML Kit Terms of Service (https://developers.google.com/ml-kit/terms) | POM text-recognition-devanagari-16.0.0.pom |  |
| text-recognition-japanese | 16.0.0 | `com.google.mlkit:text-recognition-japanese:16.0.0` | implementation | ML Kit Terms of Service (https://developers.google.com/ml-kit/terms) | POM text-recognition-japanese-16.0.0.pom |  |
| text-recognition-korean | 16.0.0 | `com.google.mlkit:text-recognition-korean:16.0.0` | implementation | ML Kit Terms of Service (https://developers.google.com/ml-kit/terms) | POM text-recognition-korean-16.0.0.pom |  |
| text-recognition | 16.0.0 | `com.google.mlkit:text-recognition:16.0.0` | implementation | ML Kit Terms of Service (https://developers.google.com/ml-kit/terms) | POM text-recognition-16.0.0.pom |  |
| core | 3.5.3 | `com.google.zxing:core:3.5.3` | implementation | Apache-2.0 | well-known group com.google.zxing |  |
| jieba-analysis | 1.0.2 | `com.huaban:jieba-analysis:1.0.2` | implementation | Apache-2.0 | POM jieba-analysis-1.0.2.pom |  |
| taskerpluginlibrary | 0.4.10 | `com.joaomgcd:taskerpluginlibrary:0.4.10` | implementation | Tasker Plugin Sample License (https://github.com/joaomgcd/TaskerPluginSample/blob/master/LICENSE) | POM taskerpluginlibrary-0.4.10.pom |  |
| moshi-kotlin | 1.15.0 | `com.squareup.moshi:moshi-kotlin:1.15.0` | implementation | Apache-2.0 | POM moshi-kotlin-1.15.0.pom |  |
| logging-interceptor | 4.12.0 | `com.squareup.okhttp3:logging-interceptor:4.12.0` | implementation | Apache-2.0 | POM logging-interceptor-4.12.0.pom |  |
| okhttp-sse | 4.12.0 | `com.squareup.okhttp3:okhttp-sse:4.12.0` | implementation | Apache-2.0 | POM okhttp-sse-4.12.0.pom |  |
| okhttp | 4.12.0 | `com.squareup.okhttp3:okhttp:4.12.0` | implementation | Apache-2.0 | POM okhttp-4.12.0.pom |  |
| converter-moshi | 2.9.0 | `com.squareup.retrofit2:converter-moshi:2.9.0` | implementation | Apache-2.0 | POM converter-moshi-2.9.0.pom |  |
| retrofit | 2.9.0 | `com.squareup.retrofit2:retrofit:2.9.0` | implementation | Apache-2.0 | POM retrofit-2.9.0.pom |  |
| pdfbox-android | 2.0.27.0 | `com.tom-roush:pdfbox-android:2.0.27.0` | implementation | Apache-2.0 | POM pdfbox-android-2.0.27.0.pom |  |
| android-image-cropper | 4.5.0 | `com.vanniktech:android-image-cropper:4.5.0` | implementation | Apache-2.0 | POM android-image-cropper-4.5.0.pom |  |
| commons-io | 2.13.0 | `commons-io:commons-io:2.13.0` | implementation | Apache-2.0 | well-known group commons-io |  |
| api | 13.1.5 | `dev.rikka.shizuku:api:13.1.5` | implementation | MIT | POM api-13.1.5.pom |  |
| provider | 13.1.5 | `dev.rikka.shizuku:provider:13.1.5` | implementation | MIT | POM provider-13.1.5.pom |  |
| coil-compose | 2.5.0 | `io.coil-kt:coil-compose:2.5.0` | implementation | Apache-2.0 | POM coil-compose-2.5.0.pom |  |
| coil-gif | 2.5.0 | `io.coil-kt:coil-gif:2.5.0` | implementation | Apache-2.0 | POM coil-gif-2.5.0.pom |  |
| coil | 2.5.0 | `io.coil-kt:coil:2.5.0` | implementation | Apache-2.0 | POM coil-2.5.0.pom |  |
| liquid | 1.1.1 | `io.github.fletchmckee.liquid:liquid:1.1.1` | implementation | Apache-2.0 | POM liquid-1.1.1.pom |  |
| java-diff-utils | 4.12 | `io.github.java-diff-utils:java-diff-utils:4.12` | implementation | Apache-2.0 | well-known group io.github.java-diff-utils |  |
| backdrop | 1.0.6 | `io.github.kyant0:backdrop:1.0.6` | implementation | Apache-2.0 | POM backdrop-1.0.6.pom |  |
| ktor-client-okhttp | 3.2.3 | `io.ktor:ktor-client-okhttp:3.2.3` | implementation | Apache-2.0 | POM ktor-client-okhttp-3.2.3.pom |  |
| kotlin-sdk-client | 0.10.0 | `io.modelcontextprotocol:kotlin-sdk-client:0.10.0` | implementation | MIT | POM kotlin-sdk-client-0.10.0.pom |  |
| objectbox-kotlin | 6.0.0-beta | `io.objectbox:objectbox-kotlin:6.0.0-beta` | implementation | Apache-2.0 | POM objectbox-kotlin-6.0.0-beta.pom |  |
| objectbox-processor | 6.0.0-beta | `io.objectbox:objectbox-processor:6.0.0-beta` | kapt | GNU Affero General Public License, Version 3 (https://www.gnu.org/licenses/agpl-3.0.html) | POM objectbox-processor-6.0.0-beta.pom |  |
| junit | 4.13.2 | `junit:junit:4.13.2` | testImplementation | EPL-1.0 | POM junit-4.13.2.pom |  |
| swipe | 1.2.0 | `me.saket.swipe:swipe:1.2.0` | implementation | Apache-2.0 | POM swipe-1.2.0.pom |  |
| apk-parser | 2.6.10 | `net.dongliu:apk-parser:2.6.10` | implementation | The BSD 2-Clause License (https://github.com/hsiafan/apk-parser/blob/master/LICENSE.txt) | POM apk-parser-2.6.10.pom |  |
| zip4j | 2.11.5 | `net.lingala.zip4j:zip4j:2.11.5` | implementation | Apache-2.0 | POM zip4j-2.11.5.pom |  |
| commons-compress | 1.24.0 | `org.apache.commons:commons-compress:1.24.0` | implementation | Apache-2.0 | well-known group org.apache.commons |  |
| commons-compress | 1.25.0 | `org.apache.commons:commons-compress:1.25.0` | implementation | Apache-2.0 | well-known group org.apache.commons |  |
| poi-ooxml | 5.2.3 | `org.apache.poi:poi-ooxml:5.2.3` | implementation | Apache-2.0 | POM poi-ooxml-5.2.3.pom |  |
| poi-scratchpad | 5.2.3 | `org.apache.poi:poi-scratchpad:5.2.3` | implementation | Apache-2.0 | POM poi-scratchpad-5.2.3.pom |  |
| poi | 5.2.3 | `org.apache.poi:poi:5.2.3` | implementation | Apache-2.0 | POM poi-5.2.3.pom |  |
| bcprov-jdk18on | 1.78 | `org.bouncycastle:bcprov-jdk18on:1.78` | implementation | Bouncy Castle Licence (https://www.bouncycastle.org/licence.html) | POM bcprov-jdk18on-1.78.pom |  |
| hjson | 3.0.0 | `org.hjson:hjson:3.0.0` | implementation | The MIT License (MIT) (https://github.com/hjson/hjson-java/blob/master/LICENSE) | POM hjson-3.0.0.pom |  |
| kotlin-reflect | 2.2.21 | `org.jetbrains.kotlin:kotlin-reflect:2.2.21` | implementation | Apache-2.0 | POM kotlin-reflect-2.2.21.pom |  |
| kotlinx-coroutines-android | 1.10.2 | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | implementation | Apache-2.0 | POM kotlinx-coroutines-android-1.10.2.pom |  |
| kotlinx-coroutines-core | 1.10.2 | `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` | implementation | Apache-2.0 | POM kotlinx-coroutines-core-1.10.2.pom |  |
| kotlinx-coroutines-test | 1.7.3 | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3` | testImplementation | Apache-2.0 | POM kotlinx-coroutines-test-1.7.3.pom |  |
| kotlinx-coroutines-test | 1.7.3 | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3` | androidTestImplementation | Apache-2.0 | POM kotlinx-coroutines-test-1.7.3.pom |  |
| kotlinx-serialization-json | 1.9.0 | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` | implementation | Apache-2.0 | POM kotlinx-serialization-json-1.9.0.pom |  |
| jsoup | 1.16.2 | `org.jsoup:jsoup:1.16.2` | implementation | MIT | POM jsoup-1.16.2.pom |  |
| mockito-kotlin | 5.1.0 | `org.mockito.kotlin:mockito-kotlin:5.1.0` | testImplementation | MIT | POM mockito-kotlin-5.1.0.pom |  |
| mockito-android | 5.2.0 | `org.mockito:mockito-android:5.2.0` | androidTestImplementation | MIT | POM mockito-android-5.2.0.pom |  |
| mockito-core | 5.2.0 | `org.mockito:mockito-core:5.2.0` | testImplementation | MIT | POM mockito-core-5.2.0.pom |  |
| nanohttpd | 2.3.1 | `org.nanohttpd:nanohttpd:2.3.1` | implementation | BSD-3-Clause | well-known group org.nanohttpd |  |
| tensorflow-lite | 2.10.0 | `org.tensorflow:tensorflow-lite:2.10.0` | implementation | Apache-2.0 | POM tensorflow-lite-2.10.0.pom |  |
| jlatexmath-android | 0.2.0 | `ru.noties:jlatexmath-android:0.2.0` | implementation | GNU General Public License, version 2 (https://www.gnu.org/licenses/old-licenses/gpl-2.0.html) | POM jlatexmath-android-0.2.0.pom |  |
| reorderable | 2.5.1 | `sh.calvin.reorderable:reorderable:2.5.1` | implementation | Apache-2.0 | POM reorderable-2.5.1.pom |  |
| objectbox-android (libobjectbox-jni.so) | 6.0.0-beta | `io.objectbox:objectbox-android:6.0.0-beta` | runtime native (transitive) | ObjectBox Binary Licence; Apache-2.0 (Flatbuffers); OpenLDAP Public License 2.8 (LMDB) | license-residual-evidence-20261002/official/objectbox-android-6.0.0-beta.pom; native-16kb-rebuilds/objectbox/evidence/license/objectbox-binary-licence-extracted-2026-10-02.md | 传递运行时 native，已进 APK lib/arm64-v8a/libobjectbox-jni.so；6.0.0-beta 的 beta 状态与设备运行验收不得省略。 |
| onnxruntime FetchContent/source dependencies | 1.29.0 | `onnx-16kb-rebuild source/FetchContent (protobuf/onnx/eigen/…)` | native build-input | Mixed: Apache-2.0 (ONNX/Abseil/FlatBuffers), BSD-3-Clause (protobuf/RE2/XNNPACK/pthreadpool/cpuinfo/GoogleTest), MIT (date/FXdiv/GSL/nlohmann-json/SafeInt), MPL-2.0/BSD/Apache (Eigen), Boost-1.0 (Boost.MP11), ARM KleidiAI license | onnx-16kb-rebuild/evidence/fetched-deps.txt; onnx-16kb-rebuild/evidence/dep-cache.json; onnx-16kb-rebuild/evidence/fetchcontent-license-manifest.json | Pin commit 2e2543fbe9fae542f921d47a72d21d5a4ef0b710; CPU-only; onnxruntime_USE_TELEMETRY=OFF. Manifest records the exact FetchContent source directories and license files used b… |
| com.android.application | 8.13.2 | `plugin:com.android.application:8.13.2` | plugins | Apache-2.0 | well-known group com.android.application |  |
| com.android.library | 8.13.2 | `plugin:com.android.library:8.13.2` | plugins | Apache-2.0 | well-known group com.android.library |  |
| io.objectbox | 6.0.0-beta | `plugin:io.objectbox:6.0.0-beta` | plugins | GNU Affero General Public License, Version 3 | POM objectbox-gradle-plugin-6.0.0-beta.pom; Maven coordinate io.objectbox:objectbox-gradle-plugin:6.0.0-beta | 仅构建插件 6.0.0-beta。未进 APK DEX。不等于 objectbox-android ObjectBox Binary Licence / libobjectbox-jni.so。不得用插件 AGPL 覆盖 native。 |
| org.jetbrains.kotlin.android | 2.2.21 | `plugin:org.jetbrains.kotlin.android:2.2.21` | plugins | Apache-2.0 | well-known group org.jetbrains.kotlin.android |  |
| org.jetbrains.kotlin.kapt | 2.2.21 | `plugin:org.jetbrains.kotlin.kapt:2.2.21` | plugins | Apache-2.0 | well-known group org.jetbrains.kotlin.kapt |  |
| org.jetbrains.kotlin.plugin.compose | 2.2.21 | `plugin:org.jetbrains.kotlin.plugin.compose:2.2.21` | plugins | Apache-2.0 | well-known group org.jetbrains.kotlin.plugin.compose |  |
| org.jetbrains.kotlin.plugin.parcelize | 2.2.21 | `plugin:org.jetbrains.kotlin.plugin.parcelize:2.2.21` | plugins | Apache-2.0 | well-known group org.jetbrains.kotlin.plugin.parcelize |  |
| org.jetbrains.kotlin.plugin.serialization | 2.2.21 | `plugin:org.jetbrains.kotlin.plugin.serialization:2.2.21` | plugins | Apache-2.0 | well-known group org.jetbrains.kotlin.plugin.serialization |  |

## FFmpegKit 本地 AAR（合并 ffmpeg-build-config.json）

- 工件（配方路径）：`integrations/operit-upstream-build/source/app/libs/ffmpeg-kit-local.aar`
- SHA256：`b292b0425d3445244832894d3370c46c5b1f80f0b27eb17edb5810b6bc00fffc`
- configure：`enable_version3=True`，`enable_gpl_observed=False`，`enable_nonfree_observed=False`
- AAR 内 license 成员（37）：
  - `res/raw/license.txt`
  - `res/raw/license_cpu_features.txt`
  - `res/raw/license_dav1d.txt`
  - `res/raw/license_expat.txt`
  - `res/raw/license_fontconfig.txt`
  - `res/raw/license_freetype.txt`
  - `res/raw/license_fribidi.txt`
  - `res/raw/license_giflib.txt`
  - `res/raw/license_gmp.txt`
  - `res/raw/license_gnutls.txt`
  - `res/raw/license_harfbuzz.txt`
  - `res/raw/license_jpeg.txt`
  - `res/raw/license_kvazaar.txt`
  - `res/raw/license_lame.txt`
  - `res/raw/license_libass.txt`
  - `res/raw/license_libiconv.txt`
  - `res/raw/license_libilbc.txt`
  - `res/raw/license_libogg.txt`
  - `res/raw/license_libpng.txt`
  - `res/raw/license_libsndfile.txt`
  - `res/raw/license_libtheora.txt`
  - `res/raw/license_libuuid.txt`
  - `res/raw/license_libvorbis.txt`
  - `res/raw/license_libvpx.txt`
  - `res/raw/license_libwebp.txt`
  - `res/raw/license_libxml2.txt`
  - `res/raw/license_nettle.txt`
  - `res/raw/license_opencore_amr.txt`
  - `res/raw/license_opus.txt`
  - `res/raw/license_shine.txt`
  - `res/raw/license_snappy.txt`
  - `res/raw/license_soxr.txt`
  - `res/raw/license_speex.txt`
  - `res/raw/license_tiff.txt`
  - `res/raw/license_twolame.txt`
  - `res/raw/license_vo_amrwbenc.txt`
  - `res/raw/license_zimg.txt`

commonRelease 从 APK 中 exclude 了 FFmpeg/MediaPipe/ML Kit 若干 JNI；Java 依赖与构建输入仍在。

## STT 固定资产（合并 stt-license-evidence）

- notice-packaging.json：source_prepared=True，apk_contains_verified=True
- 随包 NOTICE 文本：
  - `integrations/xiaohei-common-base/app/src/main/assets/third_party/stt/NOTICES.md` SHA256 `d571de69dbbbc701810e0d0c4df6f60656f94d32fa08e420123048998f735887` (1358 bytes)
  - `integrations/xiaohei-common-base/app/src/main/assets/third_party/stt/Silero-MIT.txt` SHA256 `2e63e9a38b6e8fc0c7bc37ce174caca1862870856c6daf5697cfb785e925520b` (1075 bytes)
  - `integrations/xiaohei-common-base/app/src/main/assets/third_party/stt/assets.json` SHA256 `de5cc0c7f718522f5a38e7b995e9cf68eab3cba79b5169c553338c1fdee9a5f4` (3308 bytes)
  - `integrations/xiaohei-common-base/app/src/main/assets/third_party/stt/Apache-2.0.txt` SHA256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` (11358 bytes)

| 资产 | 字节 | SHA256 | 许可证 |
|---|---|---|---|
| `models/silero_vad.onnx` | 1289603 | `7ed98ddbad84ccac4cd0aeb3099049280713df825c610a8ed34543318f1b2c49` | MIT (safestack model card @ pinned revision) |
| `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/decoder_jit_trace-pnnx.ncnn.bin` | 6412296 | `dc4df2d8e1ddee1b90ac72a2de982eb1d320ee6c9a70e1dee4d23d9acfc8b978` | Apache-2.0 (publisher model card @ pinned revision) |
| `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/decoder_jit_trace-pnnx.ncnn.param` | 439 | `cb88f5894978fd3e85369d2f8ea55621809fceb2b5158243fb0cd025eb4f1aaf` | Apache-2.0 (publisher model card @ pinned revision) |
| `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/encoder_jit_trace-pnnx.ncnn.bin` | 127364056 | `4ed65f05b78c0106d3d176018ab01e26a15c200604490d3d49b08cc75a122dd0` | Apache-2.0 (publisher model card @ pinned revision) |
| `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/encoder_jit_trace-pnnx.ncnn.param` | 161888 | `97ad0954fb2cb4730f87a7eb66401b024f756752ece246e4b2063f870ebf3e18` | Apache-2.0 (publisher model card @ pinned revision) |
| `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/joiner_jit_trace-pnnx.ncnn.bin` | 7350724 | `0e6c4370017394de5d74128756233d2e4451209e63ac2abd525da3b089e8bee1` | Apache-2.0 (publisher model card @ pinned revision) |
| `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/joiner_jit_trace-pnnx.ncnn.param` | 490 | `46c339f3869136c2f6d9d9d6983a6cbc2bfbcd0e3dab0f76ae25e9477f00a360` | Apache-2.0 (publisher model card @ pinned revision) |
| `models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/tokens.txt` | 56317 | `a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3` | Apache-2.0 (publisher model card @ pinned revision) |

模型卡声明不等于全部分发工作已完成；详见 `stt-license-evidence/report.md`。

## android-gif-drawable 16KB 重制

- 坐标：`pl.droidsonroids.gif:android-gif-drawable:1.2.28`（Maven 依赖被 exclude，改用 `files("libs/android-gif-drawable-1.2.28-16kb.aar")`）
- 许可证：MIT（上游 LICENSE 另含 SKIA BSD-3、GIFLIB、ReLinker Apache-2.0）
- 配方：`gif-16k-rebuild/README.md`、`build.sh`、`check_elf.py`、`out/SHA256SUMS`

## ONNX Runtime 本地 AAR（CPU-only 16KB）

- 坐标：`files("libs/onnxruntime-android-1.29.0-arm64-cpu-16kb.aar")`（不是 Maven `onnxruntime-android:1.29.0` 多 ABI/多 EP 包）
- 源码 pin：tag `v1.29.0` commit `2e2543fbe9fae542f921d47a72d21d5a4ef0b710`
- 构建：CPU-only；NNAPI/XNNPACK/WebGPU OFF；`onnxruntime_USE_TELEMETRY=OFF`
- 配方 AAR SHA256：`263f1ea768438829ee145892aa65f8f3ed7cc5d857438cf40a0bf753c00369fc`（见 `onnx-16kb-rebuild/README.md` / `evidence/status.json`）
- 许可证：ONNX Runtime 本体 MIT。FetchContent/源码传递依赖 **未** 全部核完；MIT 不是 transitive 已核声明。
- 配方：`onnx-16kb-rebuild/README.md`、`build.sh`、`evidence/status.json`、`evidence/providers.json`

## ObjectBox native（Binary Licence）

- 运行时坐标及版本以本报告依赖表为准；`libobjectbox-jni.so` 的 Binary Licence 证据仍需按实际工件独立核验，不能用旧版本结论替代。
- 构建插件坐标、版本与许可证见本报告 plugins 行。插件许可证 **不能** 替代运行时原生库许可。

## TODO 待核

（直接声明坐标均已填入许可证或 well-known/POM/本地证据。传递依赖未扫。）

本文件不是独立合规验收。未覆盖：传递依赖、CMake FetchContent 全量、Play 政策、密钥。

## Public preview corrections (2026-10-03)

This historical direct-dependency inventory is not a claim every listed module is packaged. Public previews use the bundled unmodified FFmpegKit v6.0 Java source; no custom FFmpeg native AAR is distributed. Terminal BusyBox/bash/PRoot binaries are excluded from public previews. JLaTeXMath v0.2.0 upstream LICENSE includes the independent-module linking exception; see jlatexmath/LICENSE.txt. ObjectBox native runtime uses the included ObjectBox Binary License, not Apache-2.0. Corresponding source archives and build inputs accompany the release.
