# Common non-debug build (`commonRelease`) and target API 36

Product tree only (`integrations/xiaohei-common-base`). Upstream `integrations/operit-upstream-build/source` is unchanged. This document is a build wiring note, not store or device acceptance.

## What changed

- `targetSdk` is **36** in `app/build.gradle.kts` `defaultConfig` (`compileSdk` was already 36). All app variants in this tree inherit that number, including the existing debug `common` package.
- New build type **`commonRelease`**: non-debug (`initWith(release)`, `isDebuggable = false`), `COMMON_BASE=true`, `applicationIdSuffix = ".common"`, dependency `matchingFallbacks` **release** (not debug).
- Debug **`common`** is unchanged: still debuggable, debug-signed, `matchingFallbacks` debug. Daily engineering APK remains `:app:assembleCommon`.
- `commonRelease` shares `src/common` overlays (Manifest / `res` / `java` / `kotlin` / assets / jniLibs / …). Overlay file contents are owned elsewhere; this wiring only points at that tree.
- The same six packaged-asset exclusions as `common` are applied via `SingleArtifact.ASSETS` (`filterCommonReleasePackagedAssets`). `debug` / `release` / `clone` / `nightly` are not filtered.
- `assembleRelease` / `assembleNightly` still uniquely hook Operit APK **rotation** signing. `assembleCommonRelease` is not in that matcher. Do not broaden those task-name equals checks.

## Package identity (candidate)

Current application id for both `common` and `commonRelease` is `com.ai.assistance.operit.common`. That is a **candidate** package name for this derivative tree. Final store product id and branding are **not** decided. Do not treat this id as the shipping Play / 国内商店 identity.

## Signing (not product store signing)

`commonRelease` never assigns the **debug** signing config.

- If the existing optional `signingConfigs.release` block already exists (created from whatever is already in this script’s `local.properties` handling), `commonRelease` **reuses** that config. That is leftover Operit/build-machine signing, **not** a configured product upload/store key.
- If that config is absent, assemble/bundle produce **unsigned** APK/AAB for later out-of-band signing.
- This tree does **not** create a keystore, read extra secret files, or upload. Presence of a signed APK from the optional release config is **not** “正式签名已配置”.
- Store upload still needs a separately chosen upload key (and Play App Signing / 国内商店流程). Unsigned AAB is an input to that later step, not a finished store artifact.

## Commands (not run in this wiring pass)

From `integrations/xiaohei-common-base`:

```bash
# Existing debug common APK (keep using this for engineering)
./gradlew :app:assembleCommon

# Non-debug common APK (unsigned unless optional release signingConfig exists)
./gradlew :app:assembleCommonRelease

# Non-debug common AAB for later signing
./gradlew :app:bundleCommonRelease
```

Expected outputs (names may vary slightly if AGP folder casing differs):

| Task | Typical output |
|---|---|
| `assembleCommon` | `app/build/outputs/apk/common/app-common.apk` |
| `assembleCommonRelease` | `app/build/outputs/apk/commonRelease/app-common-release.apk` |
| `bundleCommonRelease` | `app/build/outputs/bundle/commonRelease/app-common-release.aab` |

Do **not** run `:app:assembleRelease` / `:app:assembleNightly` expecting common-base artifacts; those still trigger rotation signing when their exact assemble tasks run.

## targetSdk 36 is not store-ready

Raising `targetSdk` to 36 only opts the merged app manifest into API 35/36 platform contracts. It does **not** mean Play / 国内商店 submission is ready.

Still required as **human / device / policy acceptance** (not claimed here):

- **Android 15 (API 35)** behavior when targeting 35+: edge-to-edge, foreground-service type rules, stricter intent/package visibility, photo/media permission model, and related runtime changes.
- **Android 16 (API 36)** behavior when targeting 36: large-screen orientation and other 16-era changes Google documents for that target.
- **16KB page-size** native/packaging requirements for Play (prebuilt `.so` / ZIP alignment). Changing `targetSdk` or AGP does not rebuild FFmpegKit / NCNN / other JNI.
- Third-party distribution licenses, minify/R8 policy, and the actual upload key.
- Common-base voice, barge-in, and store UX acceptance (tracked elsewhere; this pass did not test devices).

A successful Gradle assemble that prints `targetSdk=36` is a **number bump**, not an Android 15/16 or store review pass.

## Main-agent compile checks (this pass did not run Gradle)

Suggested static checks after `:app:assembleCommonRelease` / `:app:bundleCommonRelease`:

- Merged manifest `targetSdkVersion` 36; `package` / `applicationId` `com.ai.assistance.operit.common`.
- `BuildConfig.COMMON_BASE` true; `debuggable` false for the `commonRelease` variant.
- APK/AAB does not contain the six trimmed assets; `assembleCommon` still debug-signed and debuggable.
- `assembleCommonRelease` did not run `signRotatedReleaseApk` / `signRotatedNightlyApk`.
- Signing: either unsigned, or only the pre-existing optional `release` config — not a new product key.

## Not in this pass

Gradle was not executed. No ADB, emulator, browser, commit, or keystore work. Overlay XML/Manifest bodies were not edited here.
